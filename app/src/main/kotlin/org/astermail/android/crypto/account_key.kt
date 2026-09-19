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

package org.astermail.android.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator

object AccountKey {

    const val LENGTH = 32
    const val TOKEN_TYPE = "aster-account-key"
    const val TOKEN_VERSION = 1

    val DATA_CONTEXTS = listOf(
        "astermail-tags-v1",
        "astermail-labels-v1",
        "astermail-preferences-v1",
        "astermail-devmode-v1",
        "astermail-draft-v1",
        "astermail-draft-v2",
        "astermail-scheduled-v1",
        "astermail-onboarding-v1",
        "astermail-subscriptions-v1",
        "astermail-recovery-email-v1",
    )

    private const val data_salt = "aster-account-data-salt-v1"
    private const val data_info_prefix = "aster-account-data-v1:"

    fun derive_context_key(account_key: ByteArray, context: String): ByteArray {
        require(account_key.size == LENGTH) { "account key must be 32 bytes" }
        return hkdf_sha256(
            ikm = account_key,
            salt = data_salt.toByteArray(Charsets.UTF_8),
            info = (data_info_prefix + context).toByteArray(Charsets.UTF_8),
            length = LENGTH,
        )
    }

    fun derive_context_keks(account_key: ByteArray): List<String> =
        DATA_CONTEXTS.map { context ->
            val raw = derive_context_key(account_key, context)
            try {
                java.util.Base64.getEncoder().encodeToString(raw)
            } finally {
                raw.fill(0)
            }
        }

    fun parse_token_payload(plaintext: String): ByteArray? {
        val payload = runCatching { Json.parseToJsonElement(plaintext) }.getOrNull() as? JsonObject
            ?: return null
        val type = payload["type"] as? JsonPrimitive ?: return null
        val version = payload["version"] as? JsonPrimitive ?: return null
        val key = payload["key"] as? JsonPrimitive ?: return null
        if (!type.isString || type.content != TOKEN_TYPE) return null
        if (version.isString || version.content != TOKEN_VERSION.toString()) return null
        if (!key.isString) return null
        val decoded = runCatching { java.util.Base64.getDecoder().decode(key.content) }.getOrNull()
            ?: return null
        if (decoded.size != LENGTH) {
            decoded.fill(0)
            return null
        }
        return decoded
    }

    fun build_token_payload(account_key: ByteArray): String {
        require(account_key.size == LENGTH) { "account key must be $LENGTH bytes" }
        val encoded = java.util.Base64.getEncoder().encodeToString(account_key)
        return "{\"type\":\"$TOKEN_TYPE\",\"version\":$TOKEN_VERSION,\"key\":\"$encoded\"}"
    }

    fun primary_fingerprint(armored_private_key: String): String? = runCatching {
        val rings = PGPSecretKeyRingCollection(
            PGPUtil.getDecoderStream(ByteArrayInputStream(armored_private_key.toByteArray(Charsets.UTF_8))),
            JcaKeyFingerprintCalculator(),
        )
        val ring = rings.keyRings.asSequence().singleOrNull() ?: return null
        ring.publicKey.fingerprint.joinToString("") { "%02x".format(it) }
    }.getOrNull()

    fun public_key_armored(armored_private_key: String): String? = runCatching {
        val rings = PGPSecretKeyRingCollection(
            PGPUtil.getDecoderStream(ByteArrayInputStream(armored_private_key.toByteArray(Charsets.UTF_8))),
            JcaKeyFingerprintCalculator(),
        )
        val ring = rings.keyRings.asSequence().singleOrNull() ?: return null
        val public_ring = PGPPublicKeyRing(ring.publicKeys.asSequence().toList())
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { public_ring.encode(it) }
        out.toString(Charsets.UTF_8.name())
    }.getOrNull()

    fun seal_token(
        account_key: ByteArray,
        identity_private_key: String,
        passphrase: CharArray,
    ): String? {
        if (account_key.size != LENGTH) return null
        val public_key = public_key_armored(identity_private_key) ?: return null
        return PgpEncryptor.encrypt_and_sign(
            build_token_payload(account_key),
            listOf(public_key),
            identity_private_key,
            passphrase,
        )
    }

    fun open_token(
        armored_token: String,
        own_private_keys: List<String>,
        passphrase: CharArray,
    ): ByteArray? {
        val candidates = own_private_keys.filter { it.contains("-----BEGIN PGP") }
        if (candidates.isEmpty()) return null
        val plaintext = PgpDecryptor.decrypt_signed_by_own_keys(armored_token, candidates, passphrase)
            ?: return null
        return parse_token_payload(plaintext)
    }

    private fun hkdf_sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extract.doFinal(ikm)
        try {
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = ByteArray(length)
            var previous = ByteArray(0)
            var offset = 0
            var counter = 1
            while (offset < length) {
                expand.update(previous)
                expand.update(info)
                expand.update(counter.toByte())
                val block = expand.doFinal()
                previous.fill(0)
                val take = minOf(block.size, length - offset)
                System.arraycopy(block, 0, out, offset, take)
                offset += take
                previous = block
                counter += 1
            }
            previous.fill(0)
            return out
        } finally {
            prk.fill(0)
        }
    }
}
