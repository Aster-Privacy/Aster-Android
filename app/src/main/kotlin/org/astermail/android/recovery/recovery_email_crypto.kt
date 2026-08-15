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

package org.astermail.android.recovery

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import org.astermail.android.crypto.AesGcm

private const val RECOVERY_EMAIL_KEY_SUFFIX = "astermail-recovery-email-v1"
private const val RECOVERY_EMAIL_HASH_PREFIX = "aster-recovery-email-uniqueness-v1:"

data class EncryptedRecoveryEmail(val ciphertext_b64: String, val nonce_b64: String)

fun derive_recovery_email_key(identity_key: String): ByteArray {
    val material = (identity_key + RECOVERY_EMAIL_KEY_SUFFIX).toByteArray(Charsets.UTF_8)
    val key = MessageDigest.getInstance("SHA-256").digest(material)
    material.fill(0)
    return key
}

fun encrypt_recovery_email(email: String, identity_key: String): EncryptedRecoveryEmail {
    val data = email.toByteArray(Charsets.UTF_8)
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val key = derive_recovery_email_key(identity_key)
    try {
        val ciphertext = AesGcm.encrypt(key, nonce, data)
        data.fill(0)
        return EncryptedRecoveryEmail(
            ciphertext_b64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            nonce_b64 = Base64.encodeToString(nonce, Base64.NO_WRAP),
        )
    } finally {
        key.fill(0)
    }
}

fun hash_recovery_email(email: String): String {
    val material = (RECOVERY_EMAIL_HASH_PREFIX + email.trim().lowercase())
        .toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(material)
    return Base64.encodeToString(digest, Base64.NO_WRAP)
}

fun normalize_recovery_email(email: String): String = email.trim().lowercase()
