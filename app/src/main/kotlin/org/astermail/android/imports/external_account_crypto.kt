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

package org.astermail.android.imports

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val HMAC_INFO = "external-accounts-hmac-v1"
private const val INTEGRITY_SUFFIX = "external-accounts-v1"

@Serializable
data class ExternalAccountData(
    val email: String,
    val display_name: String = "",
    val label_name: String? = null,
    val label_color: String? = null,
    val created_at: String,
    val _encrypted_at: String? = null,
)

data class EncryptedAccountPayload(
    val encrypted_account_data: String,
    val account_data_nonce: String,
    val integrity_hash: String,
)

private val json = Json {
    encodeDefaults = true
    explicitNulls = false
}

private fun derive_hmac_key(master: ByteArray): SecretKeySpec {
    val info = HMAC_INFO.toByteArray(Charsets.UTF_8)
    val combined = ByteArray(master.size + info.size)
    System.arraycopy(master, 0, combined, 0, master.size)
    System.arraycopy(info, 0, combined, master.size, info.size)
    val digest = MessageDigest.getInstance("SHA-256").digest(combined)
    combined.fill(0)
    return SecretKeySpec(digest, "HmacSHA256")
}

fun generate_account_token(email: String, master_key: ByteArray): String {
    val hmac_key = derive_hmac_key(master_key)
    val mac = Mac.getInstance("HmacSHA256").apply { init(hmac_key) }
    val normalized = email.lowercase().trim().toByteArray(Charsets.UTF_8)
    val signed = mac.doFinal(normalized)
    return Base64.encodeToString(signed, Base64.NO_WRAP)
}

private fun generate_integrity_hash(
    encrypted_data: String,
    nonce: String,
    master_key: ByteArray,
): String {
    val hmac_key = derive_hmac_key(master_key)
    val mac = Mac.getInstance("HmacSHA256").apply { init(hmac_key) }
    val combined = "$encrypted_data:$nonce:$INTEGRITY_SUFFIX".toByteArray(Charsets.UTF_8)
    val signed = mac.doFinal(combined)
    return Base64.encodeToString(signed, Base64.NO_WRAP)
}

fun encrypt_account_data(
    data: ExternalAccountData,
    master_key: ByteArray,
): EncryptedAccountPayload {
    val payload = data.copy(_encrypted_at = java.time.Instant.now().toString())
    val plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val key = SecretKeySpec(master_key, "AES")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
    val ciphertext = cipher.doFinal(plaintext)
    plaintext.fill(0)

    val encrypted_b64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    val nonce_b64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
    val integrity_hash = generate_integrity_hash(encrypted_b64, nonce_b64, master_key)

    return EncryptedAccountPayload(
        encrypted_account_data = encrypted_b64,
        account_data_nonce = nonce_b64,
        integrity_hash = integrity_hash,
    )
}
