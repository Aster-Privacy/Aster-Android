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
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.SessionSnapshotStore
import org.astermail.android.storage.PreferencesCacheStore
import org.astermail.android.storage.ThemeStore
import org.astermail.android.storage.TokenStore
import org.astermail.android.storage.TrustedDeviceStore
import org.astermail.android.storage.outbox.PendingSendDao
import org.astermail.android.storage.search.AsterDatabase

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provide_pending_send_dao(database: AsterDatabase): PendingSendDao = database.pending_send_dao()

    @Provides
    @Singleton
    fun provide_token_store(@ApplicationContext context: Context): TokenStore = TokenStore(context)

    @Provides
    @Singleton
    fun provide_session_key_store(@ApplicationContext context: Context): SessionKeyStore = SessionKeyStore(context)

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
    fun provide_database(@ApplicationContext context: Context): AsterDatabase {
        val meta = runCatching { SecurePrefs.open(context, db_meta_prefs) }.getOrNull()
            ?: return build_in_memory_database(context)
        if (!meta.getBoolean(key_sqlcipher_migrated, false)) {
            runCatching { context.deleteDatabase(db_name) }
        }
        return try {
            val db = build_mail_database(context, meta)
            runCatching { meta.edit().putBoolean(key_sqlcipher_migrated, true).apply() }
            db
        } catch (first_error: Throwable) {
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.e("StorageModule", "encrypted db open failed, retrying fresh", first_error)
            }
            runCatching { context.deleteDatabase(db_name) }
            try {
                val db = build_mail_database(context, meta)
                runCatching { meta.edit().putBoolean(key_sqlcipher_migrated, true).apply() }
                db
            } catch (second_error: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.e(
                        "StorageModule",
                        "encrypted db unavailable, falling back to in-memory (unencrypted, non-persistent)",
                        second_error,
                    )
                }
                build_in_memory_database(context)
            }
        }
    }

    private fun build_mail_database(
        context: Context,
        meta: SharedPreferences,
    ): AsterDatabase {
        System.loadLibrary("sqlcipher")
        val builder = Room.databaseBuilder(context, AsterDatabase::class.java, db_name)
        builder.openHelperFactory(
            net.zetetic.database.sqlcipher.SupportOpenHelperFactory(db_passphrase(meta), null, false),
        )
        builder.addMigrations(
            AsterDatabase.migration_4_5,
            AsterDatabase.migration_5_6,
            AsterDatabase.migration_6_7,
            AsterDatabase.migration_7_8,
            AsterDatabase.migration_8_9,
            AsterDatabase.migration_9_10,
        )
        builder.fallbackToDestructiveMigration()
        val db = builder.build()
        db.openHelper.writableDatabase
        return db
    }

    private fun build_in_memory_database(context: Context): AsterDatabase {
        return Room.inMemoryDatabaseBuilder(context, AsterDatabase::class.java)
            .fallbackToDestructiveMigration()
            .build()
    }

    private fun db_passphrase(meta: SharedPreferences): ByteArray {
        meta.getString(key_db_key, null)?.let {
            return android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
        }
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        meta.edit()
            .putString(key_db_key, android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP))
            .apply()
        return key
    }

    private const val db_name = "aster_mail_db"
    private const val db_meta_prefs = "aster_db_meta"
    private const val key_sqlcipher_migrated = "sqlcipher_migrated"
    private const val key_db_key = "db_key"
}
