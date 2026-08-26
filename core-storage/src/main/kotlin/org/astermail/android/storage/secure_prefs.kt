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

package org.astermail.android.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val health_prefs_name = "aster_secure_prefs_health"
private const val failure_key_prefix = "keystore_failure_"
private const val key_material_lost_flag = "key_material_lost"
private const val shared_prefs_dir_name = "shared_prefs"
private const val quarantine_marker = ".quarantine."
private const val master_key_alias = "aster_prefs_master_v2"
private const val migration_prefs_name = "aster_secure_prefs_migration"
private const val rekeyed_key_prefix = "rekeyed_v2_"
private const val rekey_attempts_prefix = "rekey_attempts_"

const val rekey_max_attempts = 3

private const val warm_join_timeout_ms = 5_000L

internal const val rekeyed_suffix = "_v2"

object SecurePrefs {

    @Volatile
    var key_material_lost: Boolean = false
        private set

    private val opened = java.util.concurrent.ConcurrentHashMap<String, SharedPreferences>()

    @Volatile
    private var cached_master_key: MasterKey? = null

    fun open(context: Context, name: String): SharedPreferences {
        opened[name]?.let { return it }
        val app = context.applicationContext
        return opened.computeIfAbsent(name) { open_uncached(app, it) }
    }

    fun warm(context: Context, names: Collection<String>) {
        val app = context.applicationContext
        val pending = names.filterNot { opened.containsKey(it) }
        if (pending.isEmpty()) return
        runCatching { master_key(app) }
        val threads = pending.map { name ->
            Thread { runCatching { open(app, name) } }.apply {
                priority = Thread.NORM_PRIORITY
                start()
            }
        }
        for (thread in threads) {
            runCatching { thread.join(warm_join_timeout_ms) }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun forget_for_test(name: String) {
        opened.remove(name)
    }

    private fun open_uncached(app: Context, name: String): SharedPreferences {
        runCatching { rekey_legacy_prefs(app, name) }
        val file = name + rekeyed_suffix
        return try {
            create_encrypted(app, file).also { clear_failure_record(app, file) }
        } catch (failure: Throwable) {
            open_after_failure(app, file)
        }
    }

    fun was_key_material_lost(context: Context): Boolean =
        key_material_lost || health_prefs(context).getBoolean(key_material_lost_flag, false)

    private fun open_after_failure(app: Context, file: String): SharedPreferences {
        quarantine_prefs(app, file)
        return try {
            create_encrypted(app, file).also { clear_failure_record(app, file) }
        } catch (second: Throwable) {
            if (!record_failure(app, file)) {
                StorageHealth.mark_secure_prefs_degraded()
                return InMemoryPrefs()
            }
            reset_key_material(app, file)
            try {
                create_encrypted(app, file).also { clear_failure_record(app, file) }
            } catch (third: Throwable) {
                key_material_lost = true
                StorageHealth.mark_key_material_lost()
                InMemoryPrefs()
            }
        }
    }

    private fun health_prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(health_prefs_name, Context.MODE_PRIVATE)

    private fun migration_prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(migration_prefs_name, Context.MODE_PRIVATE)

    private fun record_failure(context: Context, name: String): Boolean =
        record_failure_in(health_prefs(context), failure_key_prefix + name)

    @androidx.annotation.VisibleForTesting
    fun record_failure_in(prefs: SharedPreferences, key: String): Boolean {
        val already_failed = runCatching { prefs.getBoolean(key, false) }.getOrNull()
        if (already_failed == null) {
            StorageHealth.mark_secure_prefs_degraded()
            return false
        }
        if (already_failed) return true
        val stored = runCatching { prefs.edit().putBoolean(key, true).commit() }.getOrDefault(false)
        if (!stored) StorageHealth.mark_secure_prefs_degraded()
        return false
    }

    private fun clear_failure_record(context: Context, name: String) {
        clear_failure_record_in(health_prefs(context), failure_key_prefix + name)
    }

    @androidx.annotation.VisibleForTesting
    fun clear_failure_record_in(prefs: SharedPreferences, key: String): Boolean {
        val present = runCatching { prefs.contains(key) }.getOrNull()
        if (present == null) {
            StorageHealth.mark_secure_prefs_degraded()
            return false
        }
        if (!present) return true
        val cleared = runCatching { prefs.edit().remove(key).commit() }.getOrDefault(false)
        if (!cleared) StorageHealth.mark_secure_prefs_degraded()
        return cleared
    }

    private fun reset_key_material(context: Context, name: String) {
        key_material_lost = true
        StorageHealth.mark_key_material_lost()
        runCatching { health_prefs(context).edit().putBoolean(key_material_lost_flag, true).commit() }
        quarantine_prefs(context, name)
        cached_master_key = null
        runCatching {
            val store = java.security.KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (store.containsAlias(master_key_alias)) {
                store.deleteEntry(master_key_alias)
            }
        }
    }

    private fun quarantine_prefs(context: Context, name: String) {
        val directory = shared_prefs_dir(context)
        if (directory != null) {
            runCatching {
                quarantine_prefs_file(directory, name, System.currentTimeMillis())
            }.onFailure {
                StorageHealth.mark_secure_prefs_degraded()
                return
            }
        } else {
            StorageHealth.mark_secure_prefs_degraded()
            return
        }
        runCatching { context.deleteSharedPreferences(name) }
    }

    private fun shared_prefs_dir(context: Context): java.io.File? =
        context.filesDir?.parentFile?.let { java.io.File(it, shared_prefs_dir_name) }

    @Synchronized
    private fun master_key(context: Context): MasterKey {
        cached_master_key?.let { return it }
        val built = MasterKey.Builder(context, master_key_alias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        cached_master_key = built
        return built
    }

    private fun legacy_master_key(context: Context): MasterKey =
        MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun create_encrypted(context: Context, file: String): SharedPreferences =
        create_encrypted_with(context, file, master_key(context))

    private fun create_encrypted_with(
        context: Context,
        file: String,
        key: MasterKey,
    ): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            file,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun rekey_legacy_prefs(app: Context, name: String) {
        val markers = migration_prefs(app)
        val marker_key = rekeyed_key_prefix + name
        if (markers.getBoolean(marker_key, false)) return
        val directory = shared_prefs_dir(app)
        val legacy_file = directory?.let { java.io.File(it, "$name.xml") }
        if (legacy_file == null || !legacy_file.exists()) {
            markers.edit().putBoolean(marker_key, true).commit()
            return
        }
        val snapshot = runCatching {
            create_encrypted_with(app, name, legacy_master_key(app)).all
        }.getOrNull()
        if (snapshot == null) {
            if (!exhausted_rekey_attempts(markers, rekey_attempts_prefix + name)) {
                StorageHealth.mark_secure_prefs_degraded()
                return
            }
            quarantine_prefs(app, name)
            markers.edit().putBoolean(marker_key, true).commit()
            return
        }
        val target = runCatching { create_encrypted(app, name + rekeyed_suffix) }.getOrNull() ?: return
        val target_entries = runCatching { target.all }.getOrNull()
        if (target_entries == null) {
            StorageHealth.mark_secure_prefs_degraded()
            return
        }
        if (target_entries.isNotEmpty()) {
            markers.edit().putBoolean(marker_key, true).commit()
            return
        }
        val copied = runCatching { copy_entries(snapshot, target) }.getOrDefault(false)
        if (!copied) return
        val marked = runCatching { markers.edit().putBoolean(marker_key, true).commit() }.getOrDefault(false)
        if (!marked) {
            StorageHealth.mark_secure_prefs_degraded()
            return
        }
        runCatching { app.deleteSharedPreferences(name) }
    }

    @androidx.annotation.VisibleForTesting
    fun exhausted_rekey_attempts(markers: SharedPreferences, attempts_key: String): Boolean {
        val previous = runCatching { markers.getInt(attempts_key, 0) }.getOrNull() ?: return false
        val attempts = previous + 1
        val recorded = runCatching {
            markers.edit().putInt(attempts_key, attempts).commit()
        }.getOrDefault(false)
        if (!recorded) return false
        return attempts >= rekey_max_attempts
    }

    private fun copy_entries(snapshot: Map<String, Any?>, target: SharedPreferences): Boolean {
        val editor = target.edit()
        editor.clear()
        for ((key, value) in snapshot) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> {}
            }
        }
        return editor.commit()
    }
}

internal fun quarantine_prefs_file(prefs_dir: java.io.File, name: String, now_ms: Long): java.io.File? {
    val source = java.io.File(prefs_dir, "$name.xml")
    if (!source.exists()) return null
    var target = java.io.File(prefs_dir, "$name$quarantine_marker$now_ms.xml")
    var attempt = 0
    while (target.exists() && attempt < 100) {
        attempt += 1
        target = java.io.File(prefs_dir, "$name$quarantine_marker$now_ms-$attempt.xml")
    }
    if (!source.renameTo(target)) {
        throw java.io.IOException("could not quarantine $name")
    }
    return target
}
