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
    INVALID,
}

object PrekeyBindingVerifier {
    private const val signature_header = "-----BEGIN PGP SIGNATURE-----"
    private const val signed_message_header = "-----BEGIN PGP SIGNED MESSAGE-----"

    fun is_pgp_signature(value: String): Boolean =
        value.contains(signed_message_header) || value.contains(signature_header)

    fun verify(
        signature_block: String,
        recipient_public_key_armored: String?,
        kem_identity_key_b64: String,
        signed_prekey_b64: String,
    ): PrekeyBindingResult {
        if (signature_block.isBlank()) return PrekeyBindingResult.UNSIGNED_LEGACY
        if (!is_pgp_signature(signature_block)) return PrekeyBindingResult.UNSIGNED_LEGACY
        if (recipient_public_key_armored.isNullOrBlank()) return PrekeyBindingResult.UNSIGNED_LEGACY

        return runCatching {
            val signature = extract_signature(signature_block)
                ?: return@runCatching PrekeyBindingResult.INVALID
            val public_key = find_verifying_key(recipient_public_key_armored, signature.keyID)
                ?: return@runCatching PrekeyBindingResult.INVALID

            signature.init(BcPGPContentVerifierBuilderProvider(), public_key)
            val expected = PrekeyBindingSigner
                .canonical_binding(kem_identity_key_b64, signed_prekey_b64)
                .toByteArray(Charsets.UTF_8)
            signature.update(expected)
            if (signature.verify()) PrekeyBindingResult.VERIFIED else PrekeyBindingResult.INVALID
        }.getOrElse { PrekeyBindingResult.INVALID }
    }

    private fun extract_signature(signature_block: String): PGPSignature? {
        val start = signature_block.indexOf(signature_header)
        if (start < 0) return null
        val armored = signature_block.substring(start)
        val input = ArmoredInputStream(ByteArrayInputStream(armored.toByteArray(Charsets.UTF_8)))
        val factory = JcaPGPObjectFactory(input)
        var obj = factory.nextObject()
        while (obj != null) {
            if (obj is PGPSignatureList && !obj.isEmpty) return obj[0]
            obj = factory.nextObject()
        }
        return null
    }

    private fun find_verifying_key(armored_public_key: String, key_id: Long): PGPPublicKey? {
        val collection = PGPPublicKeyRingCollection(
            PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_public_key.toByteArray(Charsets.UTF_8)),
            ),
            BcKeyFingerprintCalculator(),
        )
        collection.getPublicKey(key_id)?.let { return it }
        for (ring in collection) {
            for (key in ring.publicKeys) {
                if (key.isEncryptionKey && !key.isMasterKey) continue
                return key
            }
        }
        return null
    }
}
