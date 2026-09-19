//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail.ratchet

import java.io.ByteArrayInputStream
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider

enum class PrekeyBindingResult {
    VERIFIED,
    UNSIGNED_LEGACY,
    UNVERIFIABLE,
    INVALID,
}

object PrekeyBindingVerifier {
    private const val signature_header = "-----BEGIN PGP SIGNATURE-----"
    private const val signed_message_header = "-----BEGIN PGP SIGNED MESSAGE-----"
    private const val canonical_prefix_v1 = "aster-ratchet-prekey-v1:"
    private const val canonical_prefix_v2 = "aster-ratchet-prekey-v2:"

    fun is_pgp_signature(value: String): Boolean = decode_signature_field(value) != null

    fun decode_signature_field(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith(signed_message_header)) return trimmed
        val decoded = decode_b64(trimmed) ?: return null
        val text = runCatching { String(decoded, Charsets.UTF_8) }.getOrNull() ?: return null
        return if (text.trimStart().startsWith(signed_message_header)) text.trimStart() else null
    }

    fun verify(
        signature_block: String,
        recipient_public_key_armored: String?,
        kem_identity_key_b64: String,
        signed_prekey_b64: String,
        pq_identity_key_b64: String? = null,
    ): PrekeyBindingResult {
        val armored = decode_signature_field(signature_block)
            ?: return PrekeyBindingResult.UNSIGNED_LEGACY
        if (recipient_public_key_armored.isNullOrBlank()) return PrekeyBindingResult.UNVERIFIABLE

        val signed_text = runCatching { extract_signed_text(armored) }.getOrNull()
            ?: return PrekeyBindingResult.UNVERIFIABLE
        val signature = runCatching { extract_signature(armored) }.getOrNull()
            ?: return PrekeyBindingResult.UNVERIFIABLE
        val public_key = runCatching {
            find_verifying_key(recipient_public_key_armored, signature.keyID)
        }.getOrNull() ?: return PrekeyBindingResult.UNVERIFIABLE

        val signature_valid = runCatching {
            signature.init(BcPGPContentVerifierBuilderProvider(), public_key)
            signature.update(canonical_text_bytes(signed_text))
            signature.verify()
        }.getOrNull() ?: return PrekeyBindingResult.UNVERIFIABLE
        if (!signature_valid) return PrekeyBindingResult.INVALID

        val parts = when {
            signed_text.startsWith(canonical_prefix_v2) ->
                signed_text.removePrefix(canonical_prefix_v2).split('.')
                    .takeIf { it.size == 3 }
            signed_text.startsWith(canonical_prefix_v1) ->
                signed_text.removePrefix(canonical_prefix_v1).split('.')
                    .takeIf { it.size == 2 }
            else -> null
        } ?: return PrekeyBindingResult.UNVERIFIABLE

        if (!same_key(parts[0], kem_identity_key_b64)) return PrekeyBindingResult.INVALID
        if (!same_key(parts[1], signed_prekey_b64)) return PrekeyBindingResult.INVALID
        if (parts.size == 3) {
            if (pq_identity_key_b64.isNullOrBlank()) return PrekeyBindingResult.INVALID
            if (!same_key(parts[2], pq_identity_key_b64)) return PrekeyBindingResult.INVALID
        }
        return PrekeyBindingResult.VERIFIED
    }

    private fun same_key(signed_b64: String, published_b64: String): Boolean {
        if (signed_b64 == published_b64) return true
        val signed = decode_b64(signed_b64) ?: return false
        val published = decode_b64(published_b64) ?: return false
        return signed.isNotEmpty() && signed.contentEquals(published)
    }

    private fun decode_b64(value: String): ByteArray? {
        val compact = value.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return null
        return runCatching { java.util.Base64.getDecoder().decode(compact) }.getOrNull()
            ?: runCatching { java.util.Base64.getUrlDecoder().decode(compact) }.getOrNull()
    }

    private fun extract_signed_text(armored: String): String? {
        val normalized = armored.replace("\r\n", "\n")
        val header_end = normalized.indexOf("\n\n")
        if (header_end < 0) return null
        val body_start = header_end + 2
        val body_end = normalized.indexOf("\n$signature_header", body_start - 1)
        if (body_end < body_start - 1) return null
        val body = if (body_end < body_start) "" else normalized.substring(body_start, body_end)
        return body.split('\n').joinToString("\n") { line ->
            if (line.startsWith("- ")) line.substring(2) else line
        }
    }

    private fun canonical_text_bytes(text: String): ByteArray =
        text.split('\n')
            .joinToString("\r\n") { it.trimEnd(' ', '\t', '\r') }
            .toByteArray(Charsets.UTF_8)

    private fun extract_signature(armored: String): PGPSignature? {
        val start = armored.indexOf(signature_header)
        if (start < 0) return null
        val block = armored.substring(start)
        val input = ArmoredInputStream(ByteArrayInputStream(block.toByteArray(Charsets.UTF_8)))
        val factory = JcaPGPObjectFactory(input)
        var obj = factory.nextObject()
        while (obj != null) {
            if (obj is PGPSignatureList && !obj.isEmpty) return obj[0]
            obj = factory.nextObject()
        }
        return null
    }

    private fun find_verifying_key(armored_public_key: String, key_id: Long): PGPPublicKey? {
        if (key_id == 0L) return null
        val collection = PGPPublicKeyRingCollection(
            PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_public_key.toByteArray(Charsets.UTF_8)),
            ),
            BcKeyFingerprintCalculator(),
        )
        return collection.getPublicKey(key_id)
    }
}
