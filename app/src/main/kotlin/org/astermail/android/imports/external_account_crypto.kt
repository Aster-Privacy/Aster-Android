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

import java.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.CryptoNative
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

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
    val normalized = email.lowercase(java.util.Locale.ROOT).trim().toByteArray(Charsets.UTF_8)
    val signed = mac.doFinal(normalized)
    return Base64.getEncoder().encodeToString(signed)
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
    return Base64.getEncoder().encodeToString(signed)
}

// Matches web's derived_encryption_key: data KEK for master-key vaults, else passphrase-derived storage key.
fun external_account_master_key(
    data_kek: ByteArray?,
    passphrase: () -> ByteArray?,
    derive_storage_key: (ByteArray) -> ByteArray?,
): ByteArray? {
    if (data_kek != null && data_kek.size == 32) return data_kek
    data_kek?.fill(0)
    val password = passphrase() ?: return null
    return try {
        derive_storage_key(password)
    } finally {
        password.fill(0)
    }
}

fun external_account_kdf_version(
    cached_version: Int?,
    encrypted_vault: Pair<String, String>?,
    passphrase: ByteArray,
): Int? {
    if (cached_version != null) return cached_version
    val (encrypted, nonce) = encrypted_vault ?: return null
    val plaintext = try {
        CryptoNative.decrypt_vault_with_password(
            Base64.getDecoder().decode(encrypted),
            Base64.getDecoder().decode(nonce),
            passphrase,
        )
    } catch (cancelled: java.util.concurrent.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return null
    }
    return try {
        val version = Json.parseToJsonElement(String(plaintext, Charsets.UTF_8))
            .jsonObject["kdf_version"] as? JsonPrimitive
        if (version != null && !version.isString && version.intOrNull == 2) 2 else 1
    } catch (_: Exception) {
        null
    } finally {
        plaintext.fill(0)
    }
}

fun decrypt_account_data_with_keys(
    encrypted_account_data: String,
    account_data_nonce: String,
    integrity_hash: String?,
    keys: List<ByteArray>,
): ExternalAccountData? = keys.firstNotNullOfOrNull { key ->
    try {
        decrypt_account_data(encrypted_account_data, account_data_nonce, key, integrity_hash)
    } catch (cancelled: java.util.concurrent.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

class ExternalAccountDecryptException(message: String) : Exception(message)

fun decrypt_account_data(
    encrypted_account_data: String,
    account_data_nonce: String,
    master_key: ByteArray,
    integrity_hash: String? = null,
): ExternalAccountData {
    if (!integrity_hash.isNullOrEmpty()) {
        val expected = generate_integrity_hash(encrypted_account_data, account_data_nonce, master_key)
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), integrity_hash.toByteArray(Charsets.UTF_8))) {
            throw ExternalAccountDecryptException("External account data integrity check failed")
        }
    }

    val ciphertext = Base64.getDecoder().decode(encrypted_account_data)
    val nonce = Base64.getDecoder().decode(account_data_nonce)
    val plaintext = try {
        AesGcm.decrypt(master_key, nonce, ciphertext)
    } catch (t: Throwable) {
        throw ExternalAccountDecryptException("Failed to decrypt external account data")
    }

    return try {
        json.decodeFromString(ExternalAccountData.serializer(), String(plaintext, Charsets.UTF_8))
    } finally {
        plaintext.fill(0)
    }
}

fun encrypt_account_data(
    data: ExternalAccountData,
    master_key: ByteArray,
): EncryptedAccountPayload {
    val payload = data.copy(_encrypted_at = java.time.Instant.now().toString())
    val plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val ciphertext = AesGcm.encrypt(master_key, nonce, plaintext)
    plaintext.fill(0)

    val encrypted_b64 = Base64.getEncoder().encodeToString(ciphertext)
    val nonce_b64 = Base64.getEncoder().encodeToString(nonce)
    val integrity_hash = generate_integrity_hash(encrypted_b64, nonce_b64, master_key)

    return EncryptedAccountPayload(
        encrypted_account_data = encrypted_b64,
        account_data_nonce = nonce_b64,
        integrity_hash = integrity_hash,
    )
}
