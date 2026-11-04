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

import java.util.Base64
import org.astermail.android.crypto.AuthSaltCollisionException
import org.astermail.android.crypto.AuthSaltGuard

enum class LoginSaltCheck { sign_in, step_up }

class LegacyLoginSaltException(val check: LoginSaltCheck) :
    AuthSaltCollisionException("older login salt refused without a matching record on this device")

interface LoginSaltRecordSource {
    fun find(keys: List<String>): ByteArray?
    fun put(keys: List<String>, salt: ByteArray)
    fun remove(keys: List<String>)
}

data class LoginSaltRecord(val key: String, val salt_b64: String)

object LoginSaltRule {
    const val min_v1_login_salt_bytes = 32
    const val max_login_salt_records = 64
    private const val max_record_salt_bytes = 128
    private const val record_separator = "\n"
    private const val field_separator = ":"

    fun is_legacy_v1(kdf_version: Int, salt: ByteArray): Boolean =
        kdf_version != 2 && salt.size < min_v1_login_salt_bytes

    fun needs_rotation(kdf_version: Int, salt: ByteArray): Boolean = is_legacy_v1(kdf_version, salt)

    fun is_v1_proof_allowed(
        salt: ByteArray,
        kdf_version: Int,
        recorded_salt: ByteArray?,
        check: LoginSaltCheck,
        account_seen_before: Boolean,
    ): Boolean {
        if (!is_legacy_v1(kdf_version, salt)) return true
        if (recorded_salt != null) return AuthSaltGuard.constant_time_equals(salt, recorded_salt)
        return check == LoginSaltCheck.sign_in && account_seen_before
    }

    fun require_v1_proof_allowed(
        salt: ByteArray,
        kdf_version: Int,
        recorded_salt: ByteArray?,
        check: LoginSaltCheck,
        account_seen_before: Boolean,
    ) {
        if (!is_v1_proof_allowed(salt, kdf_version, recorded_salt, check, account_seen_before)) {
            throw LegacyLoginSaltException(check)
        }
    }

    fun record_keys(vararg user_hashes: String?): List<String> =
        user_hashes.mapNotNull { it?.trim()?.takeIf(::is_valid_record_key) }.distinct()

    fun is_valid_record_key(key: String): Boolean {
        if (key.isEmpty() || key.length > 256) return false
        return key.none { it.isWhitespace() || it.isISOControl() || it == ':' }
    }

    fun encode_salt(salt: ByteArray): String = Base64.getEncoder().encodeToString(salt)

    fun decode_salt(salt_b64: String): ByteArray? {
        val decoded = runCatching { Base64.getDecoder().decode(salt_b64) }.getOrNull() ?: return null
        if (decoded.isEmpty() || decoded.size > max_record_salt_bytes) return null
        return decoded
    }

    fun parse_records(stored: String?): List<LoginSaltRecord> {
        if (stored.isNullOrEmpty()) return emptyList()
        val parsed = stored.split(record_separator).mapNotNull { line ->
            val separator = line.indexOf(field_separator)
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator)
            val salt_b64 = line.substring(separator + 1)
            if (!is_valid_record_key(key) || decode_salt(salt_b64) == null) return@mapNotNull null
            LoginSaltRecord(key, salt_b64)
        }
        return parsed.reversed().distinctBy { it.key }.reversed().takeLast(max_login_salt_records)
    }

    fun serialize_records(records: List<LoginSaltRecord>): String =
        records.joinToString(record_separator) { "${it.key}$field_separator${it.salt_b64}" }

    fun put_records(records: List<LoginSaltRecord>, keys: List<String>, salt: ByteArray): List<LoginSaltRecord> {
        val fresh_keys = keys.filter(::is_valid_record_key).distinct()
        if (fresh_keys.isEmpty() || salt.isEmpty() || salt.size > max_record_salt_bytes) return records
        val encoded = encode_salt(salt)
        val fresh = fresh_keys.map { LoginSaltRecord(it, encoded) }
        return (records.filter { it.key !in fresh_keys } + fresh).takeLast(max_login_salt_records)
    }

    fun remove_records(records: List<LoginSaltRecord>, keys: List<String>): List<LoginSaltRecord> =
        records.filter { it.key !in keys }

    fun find_record(records: List<LoginSaltRecord>, keys: List<String>): ByteArray? {
        for (key in keys) {
            val match = records.lastOrNull { it.key == key } ?: continue
            return decode_salt(match.salt_b64)
        }
        return null
    }
}
