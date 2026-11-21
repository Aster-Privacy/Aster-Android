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

package org.astermail.android.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.SessionSnapshotStore
import org.astermail.android.storage.PreferencesCacheStore
import org.astermail.android.storage.SealedPrefs
import org.astermail.android.storage.ThemeStore
import org.astermail.android.storage.TokenStore
import org.astermail.android.storage.TrustedDeviceStore
import org.astermail.android.storage.actions.PendingMailActionDao
import org.astermail.android.storage.contacts.ContactCacheDao
import org.astermail.android.storage.contacts.ContactGroupCacheDao
import org.astermail.android.storage.outbox.PendingSendDao
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.MessageBodyDao
import org.astermail.android.storage.search.MessageBodyIndexDao
import org.astermail.android.storage.search.ThreadSnapshotDao

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provide_pending_send_dao(database: AsterDatabase): PendingSendDao = database.pending_send_dao()

    @Provides
    @Singleton
    fun provide_message_body_dao(database: AsterDatabase): MessageBodyDao = database.message_body_dao()

    @Provides
    @Singleton
    fun provide_message_body_index_dao(database: AsterDatabase): MessageBodyIndexDao =
        database.message_body_index_dao()

    @Provides
    @Singleton
    fun provide_thread_snapshot_dao(database: AsterDatabase): ThreadSnapshotDao = database.thread_snapshot_dao()

    @Provides
    @Singleton
    fun provide_pending_mail_action_dao(database: AsterDatabase): PendingMailActionDao =
        database.pending_mail_action_dao()

    @Provides
    @Singleton
    fun provide_contact_cache_dao(database: AsterDatabase): ContactCacheDao = database.contact_cache_dao()

    @Provides
    @Singleton
    fun provide_contact_group_cache_dao(database: AsterDatabase): ContactGroupCacheDao =
        database.contact_group_cache_dao()

    @Provides
    @Singleton
    fun provide_token_store(@ApplicationContext context: Context): TokenStore = TokenStore(context)

    @Provides
    @Singleton
    fun provide_session_key_store(@ApplicationContext context: Context): SessionKeyStore =
        SessionKeyStore(context).also { store ->
            store.add_clear_listener { org.astermail.android.crypto.PgpDecryptor.clear_caches() }
            org.astermail.android.crypto.VaultBindingFloor.bind_account_source { store.get_user_id() }
            org.astermail.android.crypto.VaultBindingFloor.bind_store(
                SecureVaultBindingFloorStore(
                    prefs = {
                        runCatching {
                            org.astermail.android.storage.SecurePrefs.open(context, SecureVaultBindingFloorStore.PREFS_NAME)
                        }.getOrNull()
                    },
                    backup = {
                        runCatching {
                            context.getSharedPreferences(SecureVaultBindingFloorStore.BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
                        }.getOrNull()
                    },
                ),
            )
            org.astermail.android.mail.InboundMarkerFloor.bind(
                account = { store.get_user_id() },
                floor_store = org.astermail.android.mail.SecureInboundMarkerFloorStore {
                    runCatching {
                        org.astermail.android.storage.SecurePrefs.open(
                            context,
                            org.astermail.android.mail.SecureInboundMarkerFloorStore.PREFS_NAME,
                        )
                    }.getOrNull()
                },
            )
            org.astermail.android.crypto.BoundFieldRecords.bind(
                SecureBoundFieldRecordStore {
                    runCatching {
                        org.astermail.android.storage.SecurePrefs.open(context, SecureBoundFieldRecordStore.PREFS_NAME)
                    }.getOrNull()
                },
            )
        }

    @Provides
    @Singleton
    fun provide_theme_store(@ApplicationContext context: Context): ThemeStore = ThemeStore(context)

    @Provides
    @Singleton
    fun provide_account_store(@ApplicationContext context: Context): AccountStore = AccountStore(context)

    @Provides
    @Singleton
    fun provide_preferences_cache_store(@ApplicationContext context: Context): PreferencesCacheStore =
        PreferencesCacheStore(context)

    @Provides
    @Singleton
    fun provide_session_snapshot_store(@ApplicationContext context: Context): SessionSnapshotStore =
        SessionSnapshotStore(context)

    @Provides
    @Singleton
    fun provide_trusted_device_store(@ApplicationContext context: Context): TrustedDeviceStore =
        TrustedDeviceStore(context)

    @Provides
    @Singleton
    fun provide_guard_device_store(@ApplicationContext context: Context): GuardDeviceStore =
        GuardDeviceStore(SealedPrefs(SecurePrefs.open(context, GuardDeviceStore.PREFS_NAME), GuardDeviceStore.PREFS_NAME))

    @Provides
    @Singleton
    fun provide_database(@ApplicationContext context: Context): AsterDatabase {
        val meta = runCatching { SecurePrefs.open(context, db_meta_prefs) }.getOrNull()
        val database_file = runCatching { context.getDatabasePath(db_name) }.getOrNull()
        database_file?.let { file -> runCatching { restore_set_aside_database(file) } }
        val found_database = runCatching { database_file?.exists() == true }.getOrDefault(false)
        val plaintext_action = if (found_database) {
            plaintext_database_action(
                runCatching { meta?.getBoolean(key_sqlcipher_migrated, false) }.getOrNull(),
                runCatching { database_file?.let { read_database_header(it) } }.getOrNull(),
            )
        } else {
            PlaintextDatabaseAction.KEEP
        }
        if (plaintext_action == PlaintextDatabaseAction.KEEP_AND_USE_MEMORY) return build_in_memory_database(context)
        val set_aside = plaintext_action == PlaintextDatabaseAction.SET_ASIDE &&
            database_file != null && runCatching { set_aside_database(database_file) }.getOrDefault(false)
        if (plaintext_action == PlaintextDatabaseAction.SET_ASIDE && !set_aside) return build_in_memory_database(context)
        if (meta == null) return build_in_memory_database(context)
        val database_exists = found_database &&
            runCatching { context.getDatabasePath(db_name).exists() }.getOrDefault(false)
        val key_material_readable = meta !is org.astermail.android.storage.InMemoryPrefs &&
            !org.astermail.android.storage.SecurePrefs.was_key_material_lost(context)
        if (database_exists && !key_material_readable) {
            return build_in_memory_database(context)
        }
        if (!meta.getBoolean(key_sqlcipher_migrated, false) && !database_exists) {
            runCatching { context.deleteDatabase(db_name) }
        }
        val passphrase = db_passphrase(meta) ?: return build_in_memory_database(context)
        return open_encrypted_database(
            meta = meta,
            database_file = database_file,
            database_exists = database_exists,
            reset = { context.deleteDatabase(db_name) },
            build = { build_mail_database(context, passphrase) },
            fallback = { build_in_memory_database(context) },
            on_error = { message, error ->
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.e("StorageModule", message, error)
            },
        )
    }

    fun <T> open_encrypted_database(
        meta: SharedPreferences,
        database_file: java.io.File?,
        database_exists: Boolean,
        reset: () -> Unit,
        build: () -> T,
        fallback: () -> T,
        on_error: (String, Throwable) -> Unit = { _, _ -> },
    ): T = try {
        build().also { mark_database_opened(meta, database_file) }
    } catch (first_error: Throwable) {
        on_error("encrypted db open failed, retrying fresh", first_error)
        val failures = meta.getInt(key_db_open_failures, 0) + 1
        runCatching { meta.edit().putInt(key_db_open_failures, failures).commit() }
        if (!should_reset_database(database_exists, meta.getBoolean(key_sqlcipher_migrated, false), failures)) {
            fallback()
        } else {
            runCatching { reset() }
            try {
                build().also { mark_database_opened(meta, database_file) }
            } catch (second_error: Throwable) {
                on_error("encrypted db unavailable, falling back to in-memory (unencrypted, non-persistent)", second_error)
                fallback()
            }
        }
    }

    private fun mark_database_opened(meta: SharedPreferences, database_file: java.io.File?) {
        runCatching { meta.edit().putBoolean(key_sqlcipher_migrated, true).commit() }
        runCatching { meta.edit().putInt(key_db_open_failures, 0).commit() }
        database_file?.let { file -> runCatching { discard_set_aside_database(file) } }
    }

    fun is_plaintext_sqlite_header(header: ByteArray?): Boolean =
        header != null && header.size >= plaintext_sqlite_header.size &&
            plaintext_sqlite_header.indices.all { header[it] == plaintext_sqlite_header[it] }

    enum class PlaintextDatabaseAction { KEEP, KEEP_AND_USE_MEMORY, SET_ASIDE }

    fun plaintext_database_action(sqlcipher_migrated: Boolean?, header: ByteArray?): PlaintextDatabaseAction = when {
        !is_plaintext_sqlite_header(header) -> PlaintextDatabaseAction.KEEP
        sqlcipher_migrated == null -> PlaintextDatabaseAction.KEEP_AND_USE_MEMORY
        sqlcipher_migrated -> PlaintextDatabaseAction.KEEP
        else -> PlaintextDatabaseAction.SET_ASIDE
    }

    fun set_aside_database(file: java.io.File): Boolean {
        database_files(file).forEach { (source, target) ->
            if (!source.exists()) return@forEach
            target.delete()
            if (!source.renameTo(target)) return false
        }
        return !file.exists()
    }

    fun restore_set_aside_database(file: java.io.File) {
        if (file.exists()) return
        database_files(file).forEach { (source, target) ->
            if (target.exists() && !source.exists()) target.renameTo(source)
        }
    }

    fun discard_set_aside_database(file: java.io.File) {
        database_files(file).forEach { (_, target) -> target.delete() }
    }

    private fun database_files(file: java.io.File): List<Pair<java.io.File, java.io.File>> =
        listOf("", "-wal", "-shm", "-journal").map { suffix ->
            java.io.File(file.path + suffix) to java.io.File(file.path + set_aside_suffix + suffix)
        }

    private const val set_aside_suffix = ".set_aside"

    private fun read_database_header(file: java.io.File): ByteArray =
        file.inputStream().use { input ->
            val header = ByteArray(plaintext_sqlite_header.size)
            var read = 0
            while (read < header.size) {
                val count = input.read(header, read, header.size - read)
                if (count < 0) break
                read += count
            }
            header.copyOf(read)
        }

    private val plaintext_sqlite_header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun should_reset_database(
        database_exists: Boolean,
        sqlcipher_migrated: Boolean,
        consecutive_failures: Int,
    ): Boolean {
        if (!database_exists) return true
        if (!sqlcipher_migrated) return false
        return consecutive_failures >= db_open_failures_before_reset
    }

    private fun build_mail_database(
        context: Context,
        passphrase: ByteArray,
    ): AsterDatabase {
        System.loadLibrary("sqlcipher")
        val builder = Room.databaseBuilder(context, AsterDatabase::class.java, db_name)
        builder.openHelperFactory(
            net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase, null, false),
        )
        builder.addMigrations(*AsterDatabase.all_migrations)
        builder.fallbackToDestructiveMigration()
        val db = builder.build()
        db.openHelper.writableDatabase
        return db
    }

    private fun build_in_memory_database(context: Context): AsterDatabase {
        org.astermail.android.storage.StorageHealth.mark_database_in_memory()
        return Room.inMemoryDatabaseBuilder(context, AsterDatabase::class.java)
            .fallbackToDestructiveMigration()
            .build()
    }

    private fun db_passphrase(meta: SharedPreferences): ByteArray? = resolve_db_passphrase(
        read_encoded = { runCatching { meta.getString(key_db_key, null) }.getOrNull() },
        write_encoded = { encoded ->
            runCatching { meta.edit().putString(key_db_key, encoded).commit() }.getOrDefault(false)
        },
        new_key = { ByteArray(32).also { java.security.SecureRandom().nextBytes(it) } },
        encode = { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
        decode = { runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull() },
    )

    fun resolve_db_passphrase(
        read_encoded: () -> String?,
        write_encoded: (String) -> Boolean,
        new_key: () -> ByteArray,
        encode: (ByteArray) -> String,
        decode: (String) -> ByteArray?,
    ): ByteArray? {
        read_encoded()?.let { existing -> decode(existing)?.let { return it } }
        val key = new_key()
        val encoded = runCatching { encode(key) }.getOrNull()
        if (encoded != null && write_encoded(encoded)) return key
        java.util.Arrays.fill(key, 0)
        return read_encoded()?.let { decode(it) }
    }

    private const val db_name = "aster_mail_db"
    private const val db_meta_prefs = "aster_db_meta"
    private const val key_sqlcipher_migrated = "sqlcipher_migrated"
    private const val key_db_key = "db_key"
    private const val key_db_open_failures = "db_open_failures"
    private const val db_open_failures_before_reset = 3
}
