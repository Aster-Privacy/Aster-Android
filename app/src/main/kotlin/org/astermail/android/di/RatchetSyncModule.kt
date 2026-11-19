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

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.astermail.android.mail.ratchet.RatchetSyncVault
import org.astermail.android.mail.ratchet.SessionRatchetSyncVault
import org.astermail.android.mail.ratchet.SessionSyncFormatGate
import org.astermail.android.mail.ratchet.SyncFormatGate

@Module
@InstallIn(SingletonComponent::class)
abstract class RatchetSyncModule {

    @Binds
    abstract fun bind_ratchet_sync_vault(vault: SessionRatchetSyncVault): RatchetSyncVault

    @Binds
    abstract fun bind_sync_format_gate(gate: SessionSyncFormatGate): SyncFormatGate
}
