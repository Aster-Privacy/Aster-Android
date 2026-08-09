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

package org.astermail.android.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import org.astermail.android.crypto.PasswordKdf
import javax.inject.Inject
import javax.inject.Singleton

data class AppLockConfig(
    val pin_type: String,
    val digits: Int?,
)

@Singleton
class AppLockStore @Inject constructor(@ApplicationContext private val context: Context) {

    private companion object {
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_TYPE = "pin_type"
        const val KEY_PIN_DIGITS = "pin_digits"
        const val KEY_LOCKOUT_COUNT = "lockout_count"
        const val KEY_LOCKOUT_UNTIL = "lockout_until_ms"
        const val KEY_LOCKOUT_UNTIL_ELAPSED = "lockout_until_elapsed_ms"
        const val MAX_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 5L * 60 * 1000
        const val MAX_LOCKOUT_MS = 60L * 60 * 1000
        const val PBKDF2_ITERATIONS = 310_000
        const val HASH_KEY_BITS = 256
    }

    private val prefs: SharedPreferences by lazy {
        org.astermail.android.storage.SecurePrefs.open(context, "aster_app_lock")
    }

    @Volatile private var session_unlocked = false

    private val _is_locked = MutableStateFlow(false)
    val is_locked: StateFlow<Boolean> = _is_locked.asStateFlow()

    private val _config_version = MutableStateFlow(0)
    val config_version: StateFlow<Int> = _config_version.asStateFlow()

    fun is_configured(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun get_config(): AppLockConfig? {
        if (!is_configured()) return null
        return AppLockConfig(
            pin_type = prefs.getString(KEY_PIN_TYPE, "numeric") ?: "numeric",
            digits = prefs.getInt(KEY_PIN_DIGITS, 6).takeIf { prefs.contains(KEY_PIN_DIGITS) },
        )
    }

    fun lock() {
        if (!is_configured()) return
        session_unlocked = false
        _is_locked.value = true
        runCatching { org.astermail.android.folders.folder_lock_store.lock_all() }
    }

    fun mark_session_unlocked() {
        session_unlocked = true
        _is_locked.value = false
    }

    fun check_on_foreground() {
        if (is_configured() && !session_unlocked) {
            _is_locked.value = true
            runCatching { org.astermail.android.folders.folder_lock_store.lock_all() }
        }
    }

    fun is_locked_out(): Boolean = lockout_remaining_ms() > 0L

    fun lockout_remaining_seconds(): Long {
        val remaining = lockout_remaining_ms()
        return if (remaining > 0) (remaining + 999) / 1000 else 0L
    }

    private fun lockout_remaining_ms(): Long {
        val wall_remaining = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()
        val elapsed_remaining = prefs.getLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L) - android.os.SystemClock.elapsedRealtime()
        val elapsed_bounded = if (elapsed_remaining in 1..MAX_LOCKOUT_MS) elapsed_remaining else 0L
        return maxOf(wall_remaining, elapsed_bounded).coerceAtLeast(0L)
    }

    fun failed_attempt_count(): Int = prefs.getInt(KEY_LOCKOUT_COUNT, 0)

    fun setup_pin(pin: String, pin_type: String, digits: Int?) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash_pin(pin, salt)
        prefs.edit().apply {
            putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_PIN_TYPE, pin_type)
            if (digits != null) putInt(KEY_PIN_DIGITS, digits) else remove(KEY_PIN_DIGITS)
            putInt(KEY_LOCKOUT_COUNT, 0)
            putLong(KEY_LOCKOUT_UNTIL, 0L)
            putLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L)
        }.apply()
        _config_version.value += 1
        mark_session_unlocked()
    }

    fun verify_pin(pin: String): Boolean {
        if (is_locked_out()) return false
        val raw_hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val raw_salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val stored = Base64.decode(raw_hash, Base64.NO_WRAP)
        val salt = Base64.decode(raw_salt, Base64.NO_WRAP)
        val candidate = hash_pin(pin, salt)
        val ok = constant_time_equals(stored, candidate)
        if (ok) {
            prefs.edit().putInt(KEY_LOCKOUT_COUNT, 0).putLong(KEY_LOCKOUT_UNTIL, 0L)
                .putLong(KEY_LOCKOUT_UNTIL_ELAPSED, 0L).apply()
            mark_session_unlocked()
        } else {
            record_failed_attempt()
        }
        return ok
    }

    fun disable() {
        prefs.edit().apply {
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_SALT)
            remove(KEY_PIN_TYPE)
            remove(KEY_PIN_DIGITS)
            remove(KEY_LOCKOUT_COUNT)
            remove(KEY_LOCKOUT_UNTIL)
            remove(KEY_LOCKOUT_UNTIL_ELAPSED)
        }.apply()
        session_unlocked = false
        _is_locked.value = false
        _config_version.value += 1
    }

    private fun record_failed_attempt() {
        val count = prefs.getInt(KEY_LOCKOUT_COUNT, 0) + 1
        val over = (count - MAX_ATTEMPTS).coerceAtMost(20)
        val lockout_ms = if (over >= 0) {
            minOf(BASE_LOCKOUT_MS shl over, MAX_LOCKOUT_MS)
        } else 0L
        val until = if (lockout_ms > 0) System.currentTimeMillis() + lockout_ms else 0L
        val until_elapsed = if (lockout_ms > 0) android.os.SystemClock.elapsedRealtime() + lockout_ms else 0L
        prefs.edit()
            .putInt(KEY_LOCKOUT_COUNT, count)
            .putLong(KEY_LOCKOUT_UNTIL, until)
            .putLong(KEY_LOCKOUT_UNTIL_ELAPSED, until_elapsed)
            .apply()
    }

    private fun hash_pin(pin: String, salt: ByteArray): ByteArray =
        PasswordKdf.derive_aes_key(pin, salt, PBKDF2_ITERATIONS, HASH_KEY_BITS)

    private fun constant_time_equals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
