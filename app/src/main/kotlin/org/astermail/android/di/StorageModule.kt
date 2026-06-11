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
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.SessionSnapshotStore
import org.astermail.android.storage.ThemeStore
import org.astermail.android.storage.TokenStore
import org.astermail.android.storage.TrustedDeviceStore
import org.astermail.android.storage.search.AsterDatabase

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

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
    fun provide_session_snapshot_store(@ApplicationContext context: Context): SessionSnapshotStore =
        SessionSnapshotStore(context)

    @Provides
    @Singleton
    fun provide_trusted_device_store(@ApplicationContext context: Context): TrustedDeviceStore =
        TrustedDeviceStore(context)

    @Provides
    @Singleton
    fun provide_database(@ApplicationContext context: Context): AsterDatabase =
        Room.databaseBuilder(context, AsterDatabase::class.java, "aster_mail_db")
            .fallbackToDestructiveMigration()
            .build()
}
