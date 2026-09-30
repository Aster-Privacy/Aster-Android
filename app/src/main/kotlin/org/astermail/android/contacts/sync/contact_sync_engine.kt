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

package org.astermail.android.contacts.sync

data class DeviceContactState(
    val raw_id: Long,
    val source_id: String?,
    val sync_revision: Long?,
    val dirty: Boolean,
    val deleted: Boolean,
    val version: Int,
)

interface ContactSyncDevice {
    fun list_states(): List<DeviceContactState>
    fun read_cards(raw_ids: Collection<Long>): Map<Long, ContactCard>
    fun read_base(raw_id: Long): ContactCard?
    fun insert(source_id: String, revision: Long, card: ContactCard)
    fun apply_remote(
        raw_id: Long,
        current: ContactCard?,
        target: ContactCard,
        revision: Long,
        source_id: String,
        expected_version: Int,
    ): Boolean
    fun set_identity(raw_id: Long, source_id: String, revision: Long, base: ContactCard)
    fun purge(raw_id: Long)
    fun flush()
}

data class RemoteContact(
    val id: String,
    val revision: Long,
    val json: String,
)

data class RemoteChangesPage(
    val contacts: List<RemoteContact>,
    val deleted_ids: List<String>,
    val undecryptable_ids: List<String>,
    val next_since: Long,
    val has_more: Boolean,
)

data class RemoteCreated(
    val id: String,
    val revision: Long,
)

interface ContactSyncRemote {
    suspend fun changes(since: Long, limit: Int, full: Boolean): RemoteChangesPage
    suspend fun fetch(id: String): RemoteContact?
    suspend fun create(json: String): RemoteCreated
    suspend fun update(id: String, json: String, expected_revision: Long): Long
}

interface ContactSyncCursor {
    fun load(): Long
    fun save(since: Long)
}

class ContactResyncRequired : Exception()
class RemoteConflict : Exception()
class RemotePlanLimit : Exception()
class RemoteNotFound : Exception()
class RemoteUndecryptable : Exception()

data class ContactSyncOptions(
    val override_too_many_deletions: Boolean = false,
    val discard_local_deletions: Boolean = false,
    val page_size: Int = 200,
    val mass_delete_min: Int = 50,
)

data class ContactSyncStats(
    var created_remote: Int = 0,
    var updated_remote: Int = 0,
    var deleted_remote: Int = 0,
    var inserted_local: Int = 0,
    var updated_local: Int = 0,
    var deleted_local: Int = 0,
    var conflicts: Int = 0,
    var skipped_plan_limit: Int = 0,
    var skipped_undecryptable: Int = 0,
    var pending_deletions: Int = 0,
    var too_many_deletions: Boolean = false,
    var full_sync: Boolean = false,
)

class ContactSyncEngine(
    private val device: ContactSyncDevice,
    private val remote: ContactSyncRemote,
    private val cursor: ContactSyncCursor,
    private val ensure_active: () -> Unit,
    private val now_iso: () -> String,
) {
    suspend fun sync(options: ContactSyncOptions = ContactSyncOptions()): ContactSyncStats {
        val stats = ContactSyncStats()
        var force_full = purge_duplicate_sources(device.list_states(), stats)
        force_full = push(options, stats) || force_full
        pull(options, stats, force_full)
        return stats
    }

    private fun purge_duplicate_sources(states: List<DeviceContactState>, stats: ContactSyncStats): Boolean {
        var purged = false
        states.filter { it.source_id != null && !it.deleted }
            .groupBy { it.source_id }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                val keep = group.firstOrNull { it.dirty } ?: group.first()
                group.filter { it !== keep && !it.dirty }.forEach {
                    device.purge(it.raw_id)
                    stats.deleted_local++
                    purged = true
                }
            }
        return purged
    }

    private suspend fun push(options: ContactSyncOptions, stats: ContactSyncStats): Boolean {
        val states = device.list_states()
        var force_full = false

        val deleted = states.filter { it.deleted }
        val deleted_synced = deleted.filter { it.source_id != null }
        for (row in deleted.filter { it.source_id == null }) {
            device.purge(row.raw_id)
        }
        if (deleted_synced.isNotEmpty()) {
            if (options.discard_local_deletions) {
                deleted_synced.forEach { device.purge(it.raw_id) }
                force_full = true
            } else {
                val synced_total = states.count { it.source_id != null }
                val too_many = deleted_synced.size > options.mass_delete_min &&
                    deleted_synced.size * 4 > synced_total
                if (too_many && !options.override_too_many_deletions) {
                    stats.too_many_deletions = true
                    stats.pending_deletions = deleted_synced.size
                } else {
                    for (row in deleted_synced) {
                        try {
                            push_deletion(row, stats)
                        } catch (_: RemoteUndecryptable) {
                            stats.skipped_undecryptable++
                        }
                    }
                }
            }
        }

        val created = states.filter { !it.deleted && it.dirty && it.source_id == null }
        if (created.isNotEmpty()) {
            val cards = device.read_cards(created.map { it.raw_id })
            for (row in created) {
                val card = cards[row.raw_id] ?: continue
                if (!card.has_content()) continue
                ensure_active()
                val result = try {
                    remote.create(new_contact_json(card))
                } catch (_: RemotePlanLimit) {
                    stats.skipped_plan_limit += created.size - created.indexOf(row)
                    break
                }
                stats.created_remote++
                if (!device.apply_remote(row.raw_id, card, card, result.revision, result.id, row.version)) {
                    device.set_identity(row.raw_id, result.id, result.revision, card)
                }
            }
        }

        val edited = states.filter { !it.deleted && it.dirty && it.source_id != null }
        if (edited.isNotEmpty()) {
            val cards = device.read_cards(edited.map { it.raw_id })
            for (row in edited) {
                val local = cards[row.raw_id] ?: continue
                try {
                    push_edit(row, local, stats)
                } catch (_: RemoteUndecryptable) {
                    stats.skipped_undecryptable++
                }
            }
        }
        return force_full
    }

    private suspend fun push_deletion(row: DeviceContactState, stats: ContactSyncStats) {
        val source_id = row.source_id ?: return
        val current = remote.fetch(source_id)
        if (current != null && !is_trashed_contact_json(current.json)) {
            ensure_active()
            try {
                remote.update(source_id, trash_contact_json(current.json, now_iso()), current.revision)
                stats.deleted_remote++
            } catch (_: RemoteNotFound) {
            } catch (_: RemoteConflict) {
                stats.conflicts++
                return
            }
        }
        device.purge(row.raw_id)
    }

    private suspend fun push_edit(row: DeviceContactState, local: ContactCard, stats: ContactSyncStats) {
        val source_id = row.source_id ?: return
        val base = device.read_base(row.raw_id)
        repeat(2) { attempt ->
            val current = remote.fetch(source_id)
            if (current == null || is_trashed_contact_json(current.json)) {
                device.purge(row.raw_id)
                stats.deleted_local++
                return
            }
            val remote_card = contact_card_from_json(current.json)
            val merged = merge_contact_cards(base, local, remote_card)
            var revision = current.revision
            if (merged != remote_card) {
                ensure_active()
                try {
                    revision = remote.update(
                        source_id,
                        patch_contact_json(current.json, remote_card, merged),
                        current.revision,
                    )
                    stats.updated_remote++
                } catch (_: RemoteConflict) {
                    stats.conflicts++
                    if (attempt == 0) return@repeat
                    return
                } catch (_: RemoteNotFound) {
                    device.purge(row.raw_id)
                    stats.deleted_local++
                    return
                }
            }
            if (device.apply_remote(row.raw_id, local, merged, revision, source_id, row.version) && merged != local) {
                stats.updated_local++
            }
            return
        }
    }

    private suspend fun pull(options: ContactSyncOptions, stats: ContactSyncStats, force_full: Boolean) {
        var since = if (force_full) 0L else cursor.load()
        var full = since == 0L
        var seen = mutableSetOf<String>()
        var restarted = false

        while (true) {
            ensure_active()
            val page = try {
                remote.changes(since, options.page_size, full)
            } catch (e: ContactResyncRequired) {
                if (restarted) throw e
                restarted = true
                since = 0L
                full = true
                seen = mutableSetOf()
                continue
            }
            val states = device.list_states().filter { it.source_id != null }.associateBy { it.source_id!! }
            seen.addAll(page.undecryptable_ids)

            for (id in page.deleted_ids) {
                val state = states[id] ?: continue
                if (state.dirty || state.deleted) continue
                device.purge(state.raw_id)
                stats.deleted_local++
            }

            val updates = mutableListOf<Pair<DeviceContactState, RemoteContact>>()
            for (contact in page.contacts) {
                seen.add(contact.id)
                val state = states[contact.id]
                val trashed = is_trashed_contact_json(contact.json)
                if (state == null) {
                    if (trashed) continue
                    val card = contact_card_from_json(contact.json)
                    device.insert(contact.id, contact.revision, card)
                    stats.inserted_local++
                    continue
                }
                if (state.dirty || state.deleted) continue
                if (trashed) {
                    device.purge(state.raw_id)
                    stats.deleted_local++
                    continue
                }
                if (state.sync_revision == contact.revision) continue
                updates.add(state to contact)
            }

            if (updates.isNotEmpty()) {
                val current = device.read_cards(updates.map { it.first.raw_id })
                for ((state, contact) in updates) {
                    val target = contact_card_from_json(contact.json)
                    val applied = device.apply_remote(
                        state.raw_id,
                        current[state.raw_id],
                        target,
                        contact.revision,
                        contact.id,
                        state.version,
                    )
                    if (applied) stats.updated_local++
                }
            }
            device.flush()

            if (page.has_more && page.next_since <= since) throw IllegalStateException("contact changes cursor did not advance")
            since = page.next_since
            if (!full) cursor.save(since)
            if (!page.has_more) break
        }

        if (full) {
            stats.full_sync = true
            for (state in device.list_states()) {
                val id = state.source_id ?: continue
                if (state.dirty || state.deleted || id in seen) continue
                device.purge(state.raw_id)
                stats.deleted_local++
            }
            cursor.save(since)
        }
    }
}
