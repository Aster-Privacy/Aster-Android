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

package org.astermail.android.mail.ratchet

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.astermail.android.crypto.storage_rotation.VaultEdit
import org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome
import org.astermail.android.crypto.storage_rotation.VaultUpdates
import org.astermail.android.crypto.sync_format_readiness
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject

data class RatchetVaultFloors(val state_v2: Boolean, val floors: Map<String, Long>) {
    fun floor(conversation_id: String): Long = floors[conversation_id] ?: 0L

    companion object {
        val empty = RatchetVaultFloors(false, emptyMap())
    }
}

interface RatchetSyncVault {
    suspend fun read(): RatchetVaultFloors
    suspend fun record(conversation_id: String, floor: Long, state_v2: Boolean)
    suspend fun intend(conversation_id: String, floor: Long) = Unit
    suspend fun unconfirmed(): Map<String, Long> = emptyMap()
    suspend fun drop_intent(conversation_id: String, floor: Long) = Unit
    suspend fun flush_pending(): Boolean = true

    companion object {
        val none: RatchetSyncVault = object : RatchetSyncVault {
            override suspend fun read(): RatchetVaultFloors = RatchetVaultFloors.empty
            override suspend fun record(conversation_id: String, floor: Long, state_v2: Boolean) = Unit
        }
    }
}

fun interface SyncFormatGate {
    suspend fun ratchet_state_v2_ready(): Boolean

    companion object {
        val never = SyncFormatGate { false }
        val ready = SyncFormatGate { true }
    }
}

object RatchetVaultFields {
    const val STATE_V2 = "ratchet_state_v2"
    const val FLOORS = "ratchet_sync_floors"

    fun read(vault: JSONObject): RatchetVaultFloors {
        val floors = HashMap<String, Long>()
        vault.optJSONObject(FLOORS)?.let { stored ->
            for (key in stored.keys()) {
                val value = stored.optLong(key, 0L)
                if (value > 0L) floors[key] = value
            }
        }
        return RatchetVaultFloors(vault.optBoolean(STATE_V2, false), floors)
    }

    fun apply(vault: JSONObject, floors: Map<String, Long>, state_v2: Boolean): Boolean {
        var changed = false
        if (state_v2 && !vault.optBoolean(STATE_V2, false)) {
            vault.put(STATE_V2, true)
            changed = true
        }
        val stored = vault.optJSONObject(FLOORS) ?: JSONObject()
        for ((conversation_id, floor) in floors) {
            if (floor > stored.optLong(conversation_id, 0L)) {
                stored.put(conversation_id, floor)
                changed = true
            }
        }
        if (changed && stored.length() > 0) vault.put(FLOORS, stored)
        return changed
    }
}

@Singleton
class SessionSyncFormatGate @Inject constructor(
    private val session_key_store: SessionKeyStore,
) : SyncFormatGate {
    override suspend fun ratchet_state_v2_ready(): Boolean =
        sync_format_readiness.current(session_key_store.get_user_id()).ratchet_state_v2
}

class SessionRatchetVaultStore(private val vault_updates: VaultUpdates) : RatchetVaultStore {
    override suspend fun load(): JSONObject? = vault_updates.session().latest()?.vault

    override suspend fun write(floors: Map<String, Long>, state_v2: Boolean): JSONObject? {
        val outcome = vault_updates.session().update { draft ->
            if (RatchetVaultFields.apply(draft.vault, floors, state_v2)) VaultEdit.CHANGED else VaultEdit.UNCHANGED
        }
        return when (outcome) {
            is VaultUpdateOutcome.Written -> outcome.vault
            is VaultUpdateOutcome.Unchanged -> outcome.vault
            else -> null
        }
    }
}

@Singleton
class SessionRatchetSyncVault @Inject constructor(
    @ApplicationContext context: Context,
    vault_updates: VaultUpdates,
    session_key_store: SessionKeyStore,
) : RatchetSyncVault by DurableRatchetSyncVault(
    user_id = { session_key_store.get_user_id() },
    store = SessionRatchetVaultStore(vault_updates),
    journal = PrefsRatchetFloorJournal { runCatching { SecurePrefs.open(context, JOURNAL_PREFS_NAME) }.getOrNull() },
    journal_context = kotlinx.coroutines.Dispatchers.IO,
)

private const val JOURNAL_PREFS_NAME = "ratchet_sync_floor_journal"
