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

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.storage.InMemoryPrefs
import org.astermail.android.storage.SecurePrefs

class LoginKeyBlind(secret: ByteArray) {

    private val key = SecretKeySpec(secret.copyOf(), "HmacSHA256")

    private fun mac(domain: String, value: String): String {
        val engine = Mac.getInstance("HmacSHA256")
        engine.init(key)
        engine.update(domain.toByteArray(Charsets.UTF_8))
        engine.update(0)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(engine.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    fun pin(entry: String): String {
        if (is_blinded_pin(entry) || entry.length < 3 || entry[1] != ':') return entry
        return entry.substring(0, 2) + BLINDED_MARK + mac(PIN_DOMAIN, entry)
    }

    fun pins(entries: List<String>): List<String> = entries.map(::pin).distinct()

    fun check(): String = mac(CHECK_DOMAIN, CHECK_DOMAIN)

    fun record(key: String): String = if (is_blinded_record(key)) key else BLINDED_MARK + mac(RECORD_DOMAIN, key)

    fun records(keys: List<String>): List<String> = keys.map(::record).distinct()

    fun owners(entries: List<String>): List<String> = entries.map { entry ->
        val split = entry.indexOf(OWNER_SEPARATOR)
        if (split <= 0) entry else entry.substring(0, split + 1) + pin(entry.substring(split + 1))
    }.distinct()

    fun salt_records(records: List<LoginSaltRecord>): List<LoginSaltRecord> =
        records.map { it.copy(key = record(it.key)) }.reversed().distinctBy { it.key }.reversed()

    companion object {
        const val SECRET_BYTES = 32
        const val BLINDED_MARK = "~"
        private const val OWNER_SEPARATOR = "="
        private const val PIN_DOMAIN = "aster-login-pin-v1"
        private const val RECORD_DOMAIN = "aster-login-salt-record-v1"
        private const val CHECK_DOMAIN = "aster-login-blind-check-v1"
        private const val SECRET_PREFS = "aster_login_key_blind_v1"
        private const val SECRET_KEY = "secret"

        fun is_blinded_pin(entry: String): Boolean = entry.length > 3 && entry[1] == ':' && entry[2] == '~'

        fun is_blinded_record(key: String): Boolean = key.startsWith(BLINDED_MARK)

        fun load_or_create_secret(prefs: SharedPreferences?): ByteArray? {
            if (prefs == null || prefs is InMemoryPrefs) return null
            val stored = runCatching { prefs.getString(SECRET_KEY, null) }.getOrNull()
            val decoded = stored?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            if (decoded != null && decoded.size == SECRET_BYTES) return decoded
            val fresh = ByteArray(SECRET_BYTES).also { SecureRandom().nextBytes(it) }
            val saved = runCatching {
                prefs.edit().putString(SECRET_KEY, Base64.getEncoder().encodeToString(fresh)).commit()
            }.getOrDefault(false)
            return if (saved) fresh else null
        }

        fun device_secret(context: Context): ByteArray? =
            load_or_create_secret(runCatching { SecurePrefs.open(context, SECRET_PREFS) }.getOrNull())
    }
}
