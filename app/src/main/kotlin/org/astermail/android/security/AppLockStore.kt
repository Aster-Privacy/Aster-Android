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
import org.astermail.android.storage.LockKeyHolder
import org.astermail.android.storage.LockSeal
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLockMode { PIN, DEVICE }

data class AppLockConfig(
    val pin_type: String,
    val digits: Int?,
    val mode: AppLockMode = AppLockMode.PIN,
)

@Singleton
class AppLockStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session_key_store: dagger.Lazy<org.astermail.android.storage.SessionKeyStore>,
    private val session_snapshot_store: dagger.Lazy<org.astermail.android.storage.SessionSnapshotStore>,
) {

    private companion object {
        const val PREFS_NAME = "aster_app_lock"
        const val PRESENCE_PREFS_NAME = "aster_app_lock_presence"
        const val KEY_PRESENCE_MARKER = "configured"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_TYPE = "pin_type"
        const val KEY_PIN_DIGITS = "pin_digits"
        const val KEY_LOCK_KEY_WRAP = "lock_key_wrap"
        const val KEY_MODE = "unlock_mode"
        const val MODE_DEVICE = "device"
        const val KEY_TIMEOUT = "lock_timeout"
        const val KEY_HIDE_NOTIFICATION_CONTENT = "hide_notification_content"
        const val KEY_LOCKOUT_COUNT = "lockout_count"
        const val KEY_LEGACY_LOCKOUT_UNTIL = "lockout_until_ms"
        const val KEY_LEGACY_LOCKOUT_UNTIL_ELAPSED = "lockout_until_elapsed_ms"
        const val KEY_LOCKOUT_DURATION = "lockout_duration_ms"
        const val KEY_LOCKOUT_STARTED_ELAPSED = "lockout_started_elapsed_ms"
        const val KEY_LOCKOUT_BOOT_COUNT = "lockout_boot_count"
        const val KEY_LOCKOUT_BOOT_WALL = "lockout_boot_wall_ms"
        val LOCKOUT_KEYS = listOf(
            KEY_LOCKOUT_COUNT,
            KEY_LEGACY_LOCKOUT_UNTIL,
            KEY_LEGACY_LOCKOUT_UNTIL_ELAPSED,
            KEY_LOCKOUT_DURATION,
            KEY_LOCKOUT_STARTED_ELAPSED,
            KEY_LOCKOUT_BOOT_COUNT,
            KEY_LOCKOUT_BOOT_WALL,
        )
        const val PBKDF2_ITERATIONS = 310_000
        const val HASH_KEY_BITS = 256
    }

    private val prefs: SharedPreferences by lazy {
        org.astermail.android.storage.SecurePrefs.open(context, PREFS_NAME)
    }

    private val presence_prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PRESENCE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val session = AppLockSession(
        elapsed_clock = { android.os.SystemClock.elapsedRealtime() },
        wall_clock = { System.currentTimeMillis() },
    )

    private val lock_key_guard = Any()

    init {
        LockKeyHolder.add_open_listener { seal_stores() }
    }

    private fun seal_stores() {
        runCatching { session_key_store.get().seal_persisted() }
        runCatching { session_snapshot_store.get().seal_persisted() }
    }

    private fun unseal_stores(lock_key: ByteArray?): Boolean =
        runCatching {
            session_key_store.get().unseal_persisted(lock_key) &&
                session_snapshot_store.get().unseal_persisted(lock_key)
        }.getOrDefault(false)

    private fun stores_hold_sealed_values(): Boolean =
        runCatching {
            session_key_store.get().has_sealed_values() || session_snapshot_store.get().has_sealed_values()
        }.getOrDefault(true)

    private fun open_lock_key(pin: String, prepared: PreparedUnwrap? = null) {
        synchronized(lock_key_guard) {
            val wrapped = prefs.getString(KEY_LOCK_KEY_WRAP, null)
            val unwrapped = if (prepared != null && prepared.wrapped == wrapped) {
                prepared.unwrapped
            } else {
                prepared?.unwrapped?.fill(0)
                LockKeyWrap.unwrap(pin, wrapped, PBKDF2_ITERATIONS)
            }
            if (unwrapped == null && (wrapped != null || stores_hold_sealed_values()) && !LockKeyHolder.is_open()) return
            val lock_key = unwrapped ?: LockKeyHolder.current() ?: LockSeal.new_lock_key()
            try {
                if (unwrapped == null) {
                    val stored = prefs.edit()
                        .putString(KEY_LOCK_KEY_WRAP, LockKeyWrap.wrap(pin, lock_key, PBKDF2_ITERATIONS))
                        .commit()
                    if (!stored) return
                }
                LockKeyHolder.open(lock_key)
            } finally {
                lock_key.fill(0)
            }
        }
    }

    private val _is_locked = MutableStateFlow(marker_present())
    val is_locked: StateFlow<Boolean> = _is_locked.asStateFlow()

    private val _reveal_started = MutableStateFlow(false)
    val reveal_started: StateFlow<Boolean> = _reveal_started.asStateFlow()

    fun begin_reveal() {
        _reveal_started.value = true
    }

    fun cancel_reveal() {
        _reveal_started.value = false
    }

    private val _config_version = MutableStateFlow(0)
    val config_version: StateFlow<Int> = _config_version.asStateFlow()

    private fun marker_present(): Boolean =
        runCatching { presence_prefs.getBoolean(KEY_PRESENCE_MARKER, false) }.getOrDefault(false)

    private fun write_marker(present: Boolean): Boolean =
        runCatching {
            presence_prefs.edit().apply {
                if (present) putBoolean(KEY_PRESENCE_MARKER, true) else remove(KEY_PRESENCE_MARKER)
            }.commit()
        }.getOrDefault(false)

    private fun device_mode_set(): Boolean =
        runCatching { prefs.getString(KEY_MODE, null) == MODE_DEVICE }.getOrDefault(false)

    fun presence(): AppLockPresence {
        val has_pin_hash = prefs.contains(KEY_PIN_HASH) || device_mode_set()
        val marker = marker_present()
        if (AppLockPresenceRule.should_write_marker(has_pin_hash, marker)) write_marker(true)
        return AppLockPresenceRule.resolve(has_pin_hash, marker)
    }

    fun is_configured(): Boolean = AppLockPresenceRule.requires_lock(presence())

    fun forget_unverifiable_lock(signed_in: Boolean) {
        if (!AppLockPresenceRule.may_forget_marker(presence(), signed_in)) return
        if (!write_marker(false)) return
        session.mark_locked()
        _is_locked.value = false
        _config_version.value += 1
    }

    fun is_store_open(): Boolean = org.astermail.android.storage.SecurePrefs.is_open(PREFS_NAME)

    fun get_config(): AppLockConfig? {
        if (!is_configured()) return null
        return AppLockConfig(
            pin_type = prefs.getString(KEY_PIN_TYPE, "numeric") ?: "numeric",
            digits = prefs.getInt(KEY_PIN_DIGITS, 6).takeIf { prefs.contains(KEY_PIN_DIGITS) },
            mode = unlock_mode(),
        )
    }

    fun unlock_mode(): AppLockMode =
        if (device_mode_set() && !prefs.contains(KEY_PIN_HASH)) AppLockMode.DEVICE else AppLockMode.PIN

    fun is_device_mode(): Boolean = runCatching { is_configured() && unlock_mode() == AppLockMode.DEVICE }.getOrDefault(false)

    fun timeout(): AppLockTimeout =
        runCatching { AppLockTimeout.from_key(prefs.getString(KEY_TIMEOUT, null)) }
            .getOrDefault(AppLockTimeout.DEFAULT)

    fun set_timeout(value: AppLockTimeout): Boolean {
        val stored = runCatching { prefs.edit().putString(KEY_TIMEOUT, value.key).commit() }.getOrDefault(false)
        if (stored) _config_version.value += 1
        return stored
    }

    fun notification_content_hidden_setting(): Boolean =
        runCatching { prefs.getBoolean(KEY_HIDE_NOTIFICATION_CONTENT, true) }.getOrDefault(true)

    fun set_notification_content_hidden(hidden: Boolean): Boolean {
        val stored = runCatching {
            prefs.edit().putBoolean(KEY_HIDE_NOTIFICATION_CONTENT, hidden).commit()
        }.getOrDefault(false)
        if (stored) _config_version.value += 1
        return stored
    }

    fun hides_notification_content(): Boolean {
        val configured = runCatching { is_configured() }.getOrDefault(true)
        return AppLockNotificationPolicy.hides_content(configured, notification_content_hidden_setting())
    }

    private fun engage_lock() {
        _reveal_started.value = false
        _is_locked.value = true
        runCatching { org.astermail.android.folders.folder_lock_store.lock_all() }
        org.astermail.android.mail.message_preloader.clear()
    }

    fun lock() {
        if (!is_configured()) return
        session.mark_locked()
        engage_lock()
    }

    fun mark_session_unlocked() {
        session.mark_unlocked()
        _is_locked.value = false
        _reveal_started.value = false
    }

    fun check_on_foreground() {
        if (!is_configured()) return
        if (session.on_return(timeout())) engage_lock()
    }

    fun on_background(): Long? {
        if (!is_configured()) return null
        val current = timeout()
        val external = session.external_flow_active()
        if (session.on_leave(current)) {
            engage_lock()
            return null
        }
        if (!session.unlocked) return null
        return AppLockTimeoutPolicy.background_lock_delay_ms(current, external)
    }

    fun expire_in_background() {
        if (!is_configured()) return
        if (session.expire_if_due(timeout())) engage_lock()
    }

    fun begin_external_flow() {
        if (runCatching { is_configured() }.getOrDefault(false)) session.begin_external_flow()
    }

    fun end_external_flow() {
        session.end_external_flow()
    }

    fun is_locked_out(): Boolean = lockout_remaining_ms() > 0L

    fun lockout_remaining_seconds(): Long {
        val remaining = lockout_remaining_ms()
        return if (remaining > 0) (remaining + 999) / 1000 else 0L
    }

    private fun lockout_remaining_ms(): Long {
        val now = android.os.SystemClock.elapsedRealtime()
        val stored = load_lockout(now)
        val anchored = PinLockout.anchored(stored, now, boot_count(), System.currentTimeMillis())
        if (anchored != stored) save_lockout(anchored)
        return PinLockout.remaining_ms(anchored, now)
    }

    private fun boot_count(): Int =
        runCatching {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.BOOT_COUNT,
                PinLockoutState.UNKNOWN_BOOT_COUNT,
            )
        }.getOrDefault(PinLockoutState.UNKNOWN_BOOT_COUNT)

    private fun load_lockout(now_elapsed_ms: Long): PinLockoutState {
        val attempts = prefs.getInt(KEY_LOCKOUT_COUNT, 0)
        if (prefs.contains(KEY_LOCKOUT_DURATION)) {
            return PinLockoutState(
                failed_attempts = attempts,
                duration_ms = prefs.getLong(KEY_LOCKOUT_DURATION, 0L),
                started_elapsed_ms = prefs.getLong(KEY_LOCKOUT_STARTED_ELAPSED, 0L),
                boot_count = prefs.getInt(KEY_LOCKOUT_BOOT_COUNT, PinLockoutState.UNKNOWN_BOOT_COUNT),
                boot_wall_ms = prefs.getLong(KEY_LOCKOUT_BOOT_WALL, PinLockoutState.UNKNOWN_BOOT_WALL),
            )
        }
        val migrated = PinLockout.from_legacy_deadlines(
            failed_attempts = attempts,
            wall_deadline_ms = prefs.getLong(KEY_LEGACY_LOCKOUT_UNTIL, 0L),
            elapsed_deadline_ms = prefs.getLong(KEY_LEGACY_LOCKOUT_UNTIL_ELAPSED, 0L),
            now_wall_ms = System.currentTimeMillis(),
            now_elapsed_ms = now_elapsed_ms,
            boot_count = boot_count(),
        )
        save_lockout(migrated)
        return migrated
    }

    private fun save_lockout(state: PinLockoutState) {
        prefs.edit()
            .putInt(KEY_LOCKOUT_COUNT, state.failed_attempts)
            .putLong(KEY_LOCKOUT_DURATION, state.duration_ms)
            .putLong(KEY_LOCKOUT_STARTED_ELAPSED, state.started_elapsed_ms)
            .putInt(KEY_LOCKOUT_BOOT_COUNT, state.boot_count)
            .putLong(KEY_LOCKOUT_BOOT_WALL, state.boot_wall_ms)
            .remove(KEY_LEGACY_LOCKOUT_UNTIL)
            .remove(KEY_LEGACY_LOCKOUT_UNTIL_ELAPSED)
            .commit()
    }

    private fun clear_lockout(editor: SharedPreferences.Editor): SharedPreferences.Editor =
        editor.apply { LOCKOUT_KEYS.forEach { remove(it) } }

    fun failed_attempt_count(): Int = prefs.getInt(KEY_LOCKOUT_COUNT, 0)

    fun attempts_left(): Int = PinLockout.attempts_left(failed_attempt_count())

    fun attempt_notice(): PinAttemptNotice = PinLockout.notice_for(failed_attempt_count())

    fun can_setup(): Boolean = LockKeyHolder.is_open() || !stores_hold_sealed_values()

    fun new_device_lock_key(): ByteArray? {
        if (!can_setup()) return null
        return LockKeyHolder.current() ?: LockSeal.new_lock_key()
    }

    fun setup_device(cipher: javax.crypto.Cipher, lock_key: ByteArray): Boolean {
        try {
            if (!can_setup()) return false
            if (!DeviceCredentialUnlockGate.complete_enroll(context, cipher, lock_key)) {
                runCatching { DeviceCredentialUnlockGate.reset(context) }
                return false
            }
            val stored = runCatching {
                prefs.edit().apply {
                    putString(KEY_MODE, MODE_DEVICE)
                    remove(KEY_LOCK_KEY_WRAP)
                    remove(KEY_PIN_HASH)
                    remove(KEY_PIN_SALT)
                    remove(KEY_PIN_TYPE)
                    remove(KEY_PIN_DIGITS)
                    clear_lockout(this)
                }.commit()
            }.getOrDefault(false)
            if (!stored || !write_marker(true)) {
                runCatching { prefs.edit().remove(KEY_MODE).commit() }
                runCatching { DeviceCredentialUnlockGate.reset(context) }
                return false
            }
            LockKeyHolder.open(lock_key)
            runCatching { BiometricUnlockGate.reset(context) }
            _config_version.value += 1
            mark_session_unlocked()
            return true
        } finally {
            lock_key.fill(0)
        }
    }

    fun complete_device_unlock(cipher: javax.crypto.Cipher, unlock_session: Boolean = true): Boolean {
        val ok = DeviceCredentialUnlockGate.complete_verify(context, cipher)
        if (ok && unlock_session) mark_session_unlocked()
        return ok
    }

    fun complete_biometric_unlock(cipher: javax.crypto.Cipher, unlock_session: Boolean = true): Boolean {
        val ok = BiometricUnlockGate.complete_verify(context, cipher)
        if (ok && unlock_session) mark_session_unlocked()
        return ok
    }

    fun forget_after_last_sign_out() {
        if (!runCatching { is_configured() }.getOrDefault(false)) return
        if (LockKeyHolder.is_open() && disable()) {
            runCatching { prefs.edit().remove(KEY_TIMEOUT).remove(KEY_HIDE_NOTIFICATION_CONTENT).commit() }
            return
        }
        if (stores_hold_sealed_values()) return
        runCatching { LockKeyHolder.close() }
        clear_pin_settings()
        runCatching { prefs.edit().remove(KEY_TIMEOUT).remove(KEY_HIDE_NOTIFICATION_CONTENT).commit() }
        write_marker(false)
        runCatching { BiometricUnlockGate.reset(context) }
        runCatching { DeviceCredentialUnlockGate.reset(context) }
        session.mark_locked()
        _is_locked.value = false
        _config_version.value += 1
    }

    fun setup_pin(pin: String, pin_type: String, digits: Int?) {
        if (!can_setup()) return
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val lock_key = LockKeyHolder.current() ?: LockSeal.new_lock_key()
        val (hash, wrapped) = derive_in_parallel(
            { hash_pin(pin, salt) },
            { LockKeyWrap.wrap(pin, lock_key, PBKDF2_ITERATIONS) },
        )
        val stored = prefs.edit().apply {
            putString(KEY_LOCK_KEY_WRAP, wrapped)
            putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_PIN_TYPE, pin_type)
            remove(KEY_MODE)
            if (digits != null) putInt(KEY_PIN_DIGITS, digits) else remove(KEY_PIN_DIGITS)
            clear_lockout(this)
        }.commit()
        if (stored) {
            write_marker(true)
            LockKeyHolder.open(lock_key)
        }
        lock_key.fill(0)
        runCatching { BiometricUnlockGate.reset(context) }
        if (stored) runCatching { DeviceCredentialUnlockGate.reset(context) }
        _config_version.value += 1
        mark_session_unlocked()
    }

    fun verify_pin(pin: String, unlock_session: Boolean = true): Boolean {
        if (is_locked_out()) return false
        val raw_hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val raw_salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val stored = Base64.decode(raw_hash, Base64.NO_WRAP)
        val salt = Base64.decode(raw_salt, Base64.NO_WRAP)
        val wrapped = runCatching { prefs.getString(KEY_LOCK_KEY_WRAP, null) }.getOrNull()
        val (candidate, unwrapped) = derive_in_parallel(
            { hash_pin(pin, salt) },
            { LockKeyWrap.unwrap(pin, wrapped, PBKDF2_ITERATIONS) },
        )
        val ok = constant_time_equals(stored, candidate)
        if (ok) {
            clear_lockout(prefs.edit()).commit()
            runCatching { open_lock_key(pin, PreparedUnwrap(wrapped, unwrapped)) }
            if (unlock_session) mark_session_unlocked()
        } else {
            unwrapped?.fill(0)
            record_failed_attempt()
        }
        return ok
    }

    private class PreparedUnwrap(val wrapped: String?, val unwrapped: ByteArray?)

    private fun <A, B> derive_in_parallel(first: () -> A, second: () -> B): Pair<A, B> {
        val task = java.util.concurrent.FutureTask(java.util.concurrent.Callable { second() })
        Thread(task, "aster-lock-kdf").start()
        val a = first()
        val b = try {
            task.get()
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        }
        return a to b
    }

    fun disable(): Boolean {
        val lock_key = LockKeyHolder.current()
        val disabled = try {
            run_app_lock_disable(
                AppLockDisableSteps(
                    unseal = { unseal_stores(lock_key) },
                    reseal = { seal_stores() },
                    close_key = { LockKeyHolder.close() },
                    reopen_key = { lock_key?.let { LockKeyHolder.open(it) } },
                    write_marker = { write_marker(it) },
                    clear_pin = { clear_pin_settings() },
                ),
            )
        } finally {
            lock_key?.fill(0)
        }
        if (!disabled) return false
        runCatching { BiometricUnlockGate.reset(context) }
        runCatching { DeviceCredentialUnlockGate.reset(context) }
        session.mark_locked()
        _is_locked.value = false
        _config_version.value += 1
        return true
    }

    private fun clear_pin_settings(): Boolean =
        runCatching {
            prefs.edit().apply {
                remove(KEY_LOCK_KEY_WRAP)
                remove(KEY_PIN_HASH)
                remove(KEY_PIN_SALT)
                remove(KEY_PIN_TYPE)
                remove(KEY_PIN_DIGITS)
                remove(KEY_MODE)
                clear_lockout(this)
            }.commit()
        }.getOrDefault(false)

    private fun record_failed_attempt() {
        val now = android.os.SystemClock.elapsedRealtime()
        save_lockout(PinLockout.after_failure(load_lockout(now), now, boot_count(), System.currentTimeMillis()))
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
