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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.crypto.storage_rotation.LANE_REPLAY_FIELD
import org.astermail.android.crypto.storage_rotation.VaultEdit
import org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome
import org.astermail.android.crypto.storage_rotation.VaultUpdates
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject

@Singleton
open class LaneReplayVault @Inject constructor(
    private val vault_updates: VaultUpdates,
    private val session_key_store: SessionKeyStore,
) {
    private val load_lock = Mutex()
    private val loaded = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()
    private val failed_at = HashMap<String, Long>()
    private val pending = HashMap<String, MutableMap<String, LaneReplayRecord>>()
    private val attempts = HashMap<String, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var flush_job: Job? = null

    open suspend fun record(sender_email: String): LaneReplayRecord? {
        val user = session_key_store.get_user_id() ?: return null
        val map = load_lock.withLock { loaded[user] ?: load(user) } ?: return null
        return synchronized(map) { LaneReplay.sender_record(map, sender_email) }
    }

    private suspend fun load(user: String): JSONObject? {
        val last = failed_at[user]
        if (last != null && System.currentTimeMillis() - last < RETRY_AFTER_MS) return null
        val map = try {
            vault_updates.session().latest()?.vault?.let { it.optJSONObject(LANE_REPLAY_FIELD) ?: JSONObject() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (map == null) {
            failed_at[user] = System.currentTimeMillis()
            return null
        }
        failed_at.remove(user)
        loaded[user] = map
        return map
    }

    open fun queue(sender_email: String, record: LaneReplayRecord) {
        val user = session_key_store.get_user_id() ?: return
        if (sender_email.isBlank()) return
        synchronized(pending) {
            val batch = pending.getOrPut(user) { LinkedHashMap() }
            batch[sender_email] = LaneReplay.merge(record, batch[sender_email])
            attempts[user] = 0
        }
        loaded[user]?.let { map ->
            synchronized(map) { map.put(sender_email, LaneReplay.merge(record, map.opt(sender_email)).to_json()) }
        }
        schedule()
    }

    private fun schedule() {
        synchronized(pending) {
            if (flush_job?.isActive == true) return
            flush_job = scope.launch {
                delay(FLUSH_DELAY_MS)
                synchronized(pending) { flush_job = null }
                flush()
            }
        }
    }

    suspend fun flush(): Boolean {
        val user = session_key_store.get_user_id() ?: return false
        val batch = synchronized(pending) { pending.remove(user) } ?: return true
        if (batch.isEmpty()) return true
        val incoming = JSONObject()
        batch.forEach { (sender, record) -> incoming.put(sender, record.to_json()) }
        val outcome = try {
            vault_updates.session().update { draft ->
                val current = draft.vault.optJSONObject(LANE_REPLAY_FIELD)
                val merged = LaneReplay.merge_maps(current, incoming)
                if (current != null && merged.toString() == LaneReplay.merge_maps(current, null).toString()) {
                    VaultEdit.UNCHANGED
                } else {
                    draft.vault.put(LANE_REPLAY_FIELD, merged)
                    VaultEdit.CHANGED
                }
            }
        } catch (cancelled: CancellationException) {
            requeue(user, batch)
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        val written = when (outcome) {
            is VaultUpdateOutcome.Written -> outcome.vault
            is VaultUpdateOutcome.Unchanged -> outcome.vault
            else -> null
        }
        if (written == null) {
            requeue(user, batch)
            return false
        }
        load_lock.withLock {
            loaded[user] = written.optJSONObject(LANE_REPLAY_FIELD) ?: JSONObject()
        }
        return true
    }

    private fun requeue(user: String, batch: Map<String, LaneReplayRecord>) {
        val retry = synchronized(pending) {
            val target = pending.getOrPut(user) { LinkedHashMap() }
            batch.forEach { (sender, record) -> target[sender] = LaneReplay.merge(target[sender], record) }
            val count = (attempts[user] ?: 0) + 1
            attempts[user] = count
            count < MAX_FLUSH_ATTEMPTS
        }
        if (retry) schedule()
    }

    private companion object {
        const val FLUSH_DELAY_MS = 2_000L
        const val RETRY_AFTER_MS = 60_000L
        const val MAX_FLUSH_ATTEMPTS = 5
    }
}
