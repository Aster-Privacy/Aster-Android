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

package org.astermail.android.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

private const val NONCE_BYTES = 24

data class LoginIntegrity(
    val token: String,
    val nonce: String,
)

fun login_integrity_nonce(): String {
    val bytes = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

fun login_integrity_request_hash(user_hash: String, nonce: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("aster_login:$user_hash:$nonce".toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
