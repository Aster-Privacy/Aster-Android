//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail.ratchet

import android.content.SharedPreferences
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface RatchetVaultStore {
    suspend fun load(): JSONObject?
    suspend fun write(floors: Map<String, Long>, state_v2: Boolean): JSONObject?
}

interface RatchetFloorJournal {
    fun load(user_id: String): RatchetVaultFloors
    fun save(user_id: String, pending: RatchetVaultFloors)
    fun load_intents(user_id: String): Map<String, Long> = emptyMap()
    fun save_intents(user_id: String, intents: Map<String, Long>) = Unit
}

class PrefsRatchetFloorJournal(private val prefs: () -> SharedPreferences?) : RatchetFloorJournal {

    override fun load(user_id: String): RatchetVaultFloors {
        val raw = prefs()?.getString(KEY_PREFIX + user_id, null) ?: return RatchetVaultFloors.empty
        val stored = runCatching { JSONObject(raw) }.getOrNull() ?: return RatchetVaultFloors.empty
        return RatchetVaultFields.read(stored)
    }

    override fun save(user_id: String, pending: RatchetVaultFloors) {
        val editor = prefs()?.edit() ?: return
        if (pending.floors.isEmpty() && !pending.state_v2) {
            editor.remove(KEY_PREFIX + user_id).commit()
            return
        }
        val stored = JSONObject()
        RatchetVaultFields.apply(stored, pending.floors, pending.state_v2)
        editor.putString(KEY_PREFIX + user_id, stored.toString()).commit()
    }

    override fun load_intents(user_id: String): Map<String, Long> {
        val raw = prefs()?.getString(INTENT_PREFIX + user_id, null) ?: return emptyMap()
        val stored = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val intents = HashMap<String, Long>()
        for (key in stored.keys()) {
            val value = stored.optLong(key, 0L)
            if (value > 0L) intents[key] = value
        }
        return intents
    }

    override fun save_intents(user_id: String, intents: Map<String, Long>) {
        val editor = prefs()?.edit() ?: return
        if (intents.isEmpty()) {
            editor.remove(INTENT_PREFIX + user_id).commit()
            return
        }
        val stored = JSONObject()
        for ((conversation_id, floor) in intents) stored.put(conversation_id, floor)
        editor.putString(INTENT_PREFIX + user_id, stored.toString()).commit()
    }

    private companion object {
        const val KEY_PREFIX = "pending_floors_"
        const val INTENT_PREFIX = "intent_floors_"
    }
}

class DurableRatchetSyncVault(
    private val user_id: () -> String?,
    private val store: RatchetVaultStore,
    private val journal: RatchetFloorJournal,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val flush_delay_ms: Long = FLUSH_DELAY_MS,
    private val journal_context: CoroutineContext = EmptyCoroutineContext,
) : RatchetSyncVault {

    private class JournalSnapshot(
        val sequence: Long,
        val user_id: String,
        val pending: RatchetVaultFloors,
        val intents: Map<String, Long>,
    )

    private val lock = Mutex()
    private val journal_lock = Mutex()
    private var owner: String? = null
    private var known: RatchetVaultFloors? = null
    private val pending = HashMap<String, Long>()
    private var pending_v2 = false
    private val intents = HashMap<String, Long>()
    private var flush_job: Job? = null
    private var snapshot_sequence = 0L
    private var saved_sequence = 0L

    private fun reset_for(user_id: String) {
        if (owner == user_id) return
        owner = user_id
        known = null
        pending.clear()
        pending_v2 = false
        intents.clear()
        flush_job?.cancel()
        flush_job = null
        val journaled = runCatching { journal.load(user_id) }.getOrDefault(RatchetVaultFloors.empty)
        pending.putAll(journaled.floors)
        pending_v2 = journaled.state_v2
        intents.putAll(runCatching { journal.load_intents(user_id) }.getOrDefault(emptyMap()))
        schedule_flush(user_id)
    }

    private fun has_pending(): Boolean = pending.isNotEmpty() || pending_v2

    private fun schedule_flush(user_id: String) {
        if (!has_pending() || flush_job?.isActive == true) return
        flush_job = scope.launch { flush_with_backoff(user_id) }
    }

    private suspend fun flush_with_backoff(user_id: String) {
        var wait = flush_delay_ms
        repeat(MAX_FLUSH_ATTEMPTS) {
            delay(wait)
            if (flush(user_id)) return
            wait = minOf(wait * 2, MAX_FLUSH_DELAY_MS)
        }
    }

    override suspend fun read(): RatchetVaultFloors {
        val user_id = user_id() ?: return RatchetVaultFloors.empty
        lock.withLock {
            reset_for(user_id)
            known?.let { return merged(it) }
        }
        val loaded = try {
            store.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }?.let(RatchetVaultFields::read)
        return lock.withLock {
            if (owner != user_id) return@withLock RatchetVaultFloors.empty
            if (loaded != null) known = loaded
            merged(known ?: RatchetVaultFloors.empty)
        }
    }

    private fun merged(base: RatchetVaultFloors): RatchetVaultFloors {
        if (!has_pending()) return base
        val floors = HashMap(base.floors)
        for ((conversation_id, floor) in pending) {
            if (floor > (floors[conversation_id] ?: 0L)) floors[conversation_id] = floor
        }
        return RatchetVaultFloors(base.state_v2 || pending_v2, floors)
    }

    override suspend fun intend(conversation_id: String, floor: Long) {
        val user_id = user_id() ?: return
        val snapshot = lock.withLock {
            reset_for(user_id)
            if (floor <= (intents[conversation_id] ?: 0L)) return
            intents[conversation_id] = floor
            snapshot_locked(user_id)
        }
        save(snapshot)
    }

    override suspend fun unconfirmed(): Map<String, Long> {
        val user_id = user_id() ?: return emptyMap()
        return lock.withLock {
            reset_for(user_id)
            HashMap(intents)
        }
    }

    override suspend fun drop_intent(conversation_id: String, floor: Long) {
        val user_id = user_id() ?: return
        val snapshot = lock.withLock {
            reset_for(user_id)
            val intended = intents[conversation_id] ?: return
            if (intended > floor) return
            intents.remove(conversation_id)
            snapshot_locked(user_id)
        }
        save(snapshot)
    }

    override suspend fun record(conversation_id: String, floor: Long, state_v2: Boolean) {
        val user_id = user_id() ?: return
        val snapshot = lock.withLock {
            reset_for(user_id)
            val current = merged(known ?: RatchetVaultFloors.empty)
            val raises = floor > current.floor(conversation_id)
            val marks = state_v2 && !current.state_v2
            val settles = (intents[conversation_id] ?: Long.MAX_VALUE) <= floor
            if (raises) pending[conversation_id] = floor
            if (marks) pending_v2 = true
            if (settles) intents.remove(conversation_id)
            val changed = if (raises || marks || settles) snapshot_locked(user_id) else null
            schedule_flush(user_id)
            changed
        }
        snapshot?.let { save(it) }
    }

    override suspend fun flush_pending(): Boolean {
        val user_id = user_id() ?: return true
        lock.withLock { reset_for(user_id) }
        if (flush(user_id)) return true
        lock.withLock { if (owner == user_id) schedule_flush(user_id) }
        return false
    }

    private fun snapshot_locked(user_id: String): JournalSnapshot {
        snapshot_sequence += 1
        return JournalSnapshot(
            snapshot_sequence,
            user_id,
            RatchetVaultFloors(pending_v2, HashMap(pending)),
            HashMap(intents),
        )
    }

    private suspend fun save(snapshot: JournalSnapshot) {
        withContext(journal_context) {
            journal_lock.withLock {
                if (snapshot.sequence <= saved_sequence) return@withLock
                saved_sequence = snapshot.sequence
                runCatching { journal.save(snapshot.user_id, snapshot.pending) }
                runCatching { journal.save_intents(snapshot.user_id, snapshot.intents) }
            }
        }
    }

    private suspend fun flush(user_id: String): Boolean {
        val (floors, v2) = lock.withLock {
            if (owner != user_id) return false
            if (!has_pending()) return true
            HashMap(pending) to pending_v2
        }
        val stored = try {
            store.write(floors, v2)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return false
        val (snapshot, done) = lock.withLock {
            if (owner != user_id) return false
            val settled = RatchetVaultFields.read(stored)
            known = settled
            pending.entries.removeAll { (conversation_id, floor) -> floor <= settled.floor(conversation_id) }
            if (settled.state_v2) pending_v2 = false
            snapshot_locked(user_id) to !has_pending()
        }
        save(snapshot)
        return done
    }

    private companion object {
        const val FLUSH_DELAY_MS = 5_000L
        const val MAX_FLUSH_DELAY_MS = 5L * 60L * 1_000L
        const val MAX_FLUSH_ATTEMPTS = 8
    }
}
