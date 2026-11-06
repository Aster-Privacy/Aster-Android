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

package org.astermail.android.settings

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.StorageWriteKey
import org.astermail.android.storage.SessionKeyStore

private const val alias_hmac_info = "astermail-alias-hmac-v1"

fun normalize_alias_local_part(local_part: String): String =
    local_part.lowercase(Locale.ROOT).replace(".", "")

suspend fun derive_alias_encryption_key(session_key_store: SessionKeyStore): ByteArray =
    org.astermail.android.crypto.StorageKeyGate.await_write_key(session_key_store)

suspend fun encrypt_alias_field_with(session_key_store: SessionKeyStore, plaintext: String): Pair<String, String> {
    val key = derive_alias_encryption_key(session_key_store)
    try {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = AesGcm.encrypt(key, nonce, plaintext.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP) to
            android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP)
    } finally {
        key.fill(0)
    }
}

suspend fun compute_alias_address_hash_with(
    session_key_store: SessionKeyStore,
    local_part: String,
    domain: String,
): String {
    val enc_key = derive_alias_encryption_key(session_key_store)
    try {
        val combined = enc_key + alias_hmac_info.toByteArray(Charsets.UTF_8)
        val hmac_key_bytes = MessageDigest.getInstance("SHA-256").digest(combined)
        combined.fill(0)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmac_key_bytes, "HmacSHA256"))
        val sig = mac.doFinal("${normalize_alias_local_part(local_part)}@$domain".toByteArray(Charsets.UTF_8))
        hmac_key_bytes.fill(0)
        return android.util.Base64.encodeToString(sig, android.util.Base64.NO_WRAP)
    } finally {
        enc_key.fill(0)
    }
}

fun compute_routing_address_hash_for(local_part: String, domain: String): String {
    val data = "${normalize_alias_local_part(local_part)}@$domain".toByteArray(Charsets.UTF_8)
    val hash = MessageDigest.getInstance("SHA-256").digest(data)
    return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
}

fun compute_legacy_routing_address_hash_for(local_part: String, domain: String): String? {
    val lowered = local_part.trim().lowercase(Locale.ROOT)
    if (!lowered.contains('.')) return null
    val data = "$lowered@${domain.lowercase(Locale.ROOT)}".toByteArray(Charsets.UTF_8)
    val hash = MessageDigest.getInstance("SHA-256").digest(data)
    return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
}
