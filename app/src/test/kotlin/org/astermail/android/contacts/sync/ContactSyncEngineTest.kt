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

import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSyncEngineTest {

    private data class Row(
        var source_id: String?,
        var revision: Long?,
        var card: ContactCard,
        var base: ContactCard?,
        var dirty: Boolean,
        var deleted: Boolean,
        var version: Int,
        var photo: String? = null,
        var link: String = "",
        var schema: Int = CARD_SCHEMA,
    )

    private class FakeDevice : ContactSyncDevice {
        val rows = linkedMapOf<Long, Row>()
        var next_id = 1L
        var before_apply: ((Long) -> Unit)? = null

        val photo_reads = mutableListOf<Long>()

        private fun device_fp(row: Row) = row.photo?.let { device_photo_fingerprint(it.toByteArray()) }

        private fun photo_value(row: Row): String {
            val fp = device_fp(row) ?: return ""
            val linked = row.link.substringBefore('|')
            val remote_fp = row.link.substringAfter('|', "")
            return if (linked == fp && remote_fp.isNotEmpty()) remote_fp else DEVICE_PHOTO_PREFIX + fp
        }

        private fun refresh(row: Row) {
            row.card = row.card.copy(photo = photo_value(row))
        }

        private fun stored_photo(data_uri: String) = "dev:$data_uri"

        override fun list_states() = rows.map { (id, r) ->
            DeviceContactState(id, r.source_id, r.revision, r.dirty, r.deleted, r.version, r.schema)
        }

        override fun read_cards(raw_ids: Collection<Long>) =
            raw_ids.mapNotNull { id -> rows[id]?.let { refresh(it); id to it.card } }.toMap()

        override fun read_base(raw_id: Long) = rows[raw_id]?.base

        override fun read_photo(raw_id: Long): String? {
            photo_reads.add(raw_id)
            val photo = rows[raw_id]?.photo ?: return null
            return "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(photo.toByteArray())
        }

        override fun insert(source_id: String, revision: Long, card: ContactCard, photo: String?) {
            val row = Row(source_id, revision, card, card, dirty = false, deleted = false, version = 1)
            if (card.photo.isNotEmpty() && photo != null) {
                row.photo = stored_photo(photo)
                row.link = "${device_fp(row)}|${card.photo}"
            }
            refresh(row)
            rows[next_id++] = row
        }

        override fun apply_remote(
            raw_id: Long,
            current: ContactCard?,
            target: ContactCard,
            revision: Long,
            source_id: String,
            expected_version: Int,
            photo: String?,
        ): Boolean {
            before_apply?.invoke(raw_id)
            val row = rows[raw_id] ?: return false
            if (row.version != expected_version || row.deleted) return false
            val current_photo = current?.photo.orEmpty()
            if (current_photo != target.photo) {
                when {
                    target.photo.isNotEmpty() && photo != null -> {
                        row.photo = stored_photo(photo)
                        row.link = "${device_fp(row)}|${target.photo}"
                    }
                    target.photo.isEmpty() -> if (!is_device_photo(current_photo)) {
                        row.photo = null
                        row.link = ""
                    }
                    is_device_photo(current_photo) ->
                        row.link = current_photo.removePrefix(DEVICE_PHOTO_PREFIX) + "|" + target.photo
                }
            }
            row.card = target
            refresh(row)
            row.base = target
            row.revision = revision
            row.source_id = source_id
            row.dirty = false
            row.schema = CARD_SCHEMA
            row.version++
            return true
        }

        override fun set_identity(raw_id: Long, source_id: String, revision: Long, base: ContactCard) {
            val row = rows[raw_id] ?: return
            row.source_id = source_id
            row.revision = revision
            row.base = base
            row.schema = CARD_SCHEMA
        }

        override fun purge(raw_id: Long) {
            rows.remove(raw_id)
        }

        override fun flush() {}

        fun user_create(card: ContactCard): Long {
            val id = next_id++
            rows[id] = Row(null, null, card.normalized(), null, dirty = true, deleted = false, version = 1)
            return id
        }

        fun user_set_photo(raw_id: Long, photo: String?) {
            val row = rows.getValue(raw_id)
            row.photo = photo
            refresh(row)
            row.dirty = true
            row.version++
        }

        fun user_edit(raw_id: Long, change: (ContactCard) -> ContactCard) {
            val row = rows.getValue(raw_id)
            row.card = change(row.card).normalized()
            row.dirty = true
            row.version++
        }

        fun user_delete(raw_id: Long) {
            val row = rows.getValue(raw_id)
            row.deleted = true
            row.dirty = true
            row.version++
        }

        fun by_source(id: String) = rows.entries.firstOrNull { it.value.source_id == id }
    }

    private class FakeRemote : ContactSyncRemote {
        val store = linkedMapOf<String, Pair<Long, String>>()
        val log = mutableListOf<Triple<Long, String, Boolean>>()
        var seq = 0L
        var floor = 0L
        var next_id = 1
        var before_update: ((String) -> Unit)? = null
        var create_limit = Int.MAX_VALUE
        val undecryptable = mutableSetOf<String>()
        var stuck_cursor = false

        private fun touch(id: String, deleted: Boolean) {
            log.add(Triple(++seq, id, deleted))
        }

        fun server_create(card: ContactCard): String {
            val id = "c${next_id++}"
            store[id] = 1L to new_contact_json(card)
            touch(id, false)
            return id
        }

        fun server_edit(id: String, change: (ContactCard) -> ContactCard) {
            val (rev, json) = store.getValue(id)
            val card = contact_card_from_json(json)
            store[id] = rev + 1 to patch_contact_json(json, card, change(card).normalized())
            touch(id, false)
        }

        fun server_set_avatar(id: String, avatar: String?) {
            val (rev, json) = store.getValue(id)
            val obj = JSONObject(json)
            if (avatar == null) obj.remove("avatar_url") else obj.put("avatar_url", avatar)
            store[id] = rev + 1 to obj.toString()
            touch(id, false)
        }

        fun avatar(id: String) = contact_inline_avatar(store.getValue(id).second)

        fun server_trash(id: String) {
            val (rev, json) = store.getValue(id)
            store[id] = rev + 1 to trash_contact_json(json, "2026-09-29T00:00:00Z")
            touch(id, false)
        }

        fun server_hard_delete(id: String) {
            store.remove(id)
            touch(id, true)
        }

        fun prune_tombstones() {
            floor = seq
            log.removeAll { it.third }
        }

        fun card(id: String) = contact_card_from_json(store.getValue(id).second)

        override suspend fun changes(since: Long, limit: Int, full: Boolean): RemoteChangesPage {
            if (!full && since in 1 until floor) throw ContactResyncRequired()
            val latest = log.filter { it.first > since }
                .groupBy { it.second }
                .map { it.value.last() }
                .sortedBy { it.first }
            val page = latest.take(limit)
            val next = page.lastOrNull()?.first ?: maxOf(since, seq)
            return RemoteChangesPage(
                contacts = page.filter { !it.third && it.second in store }.map {
                    val (rev, json) = store.getValue(it.second)
                    RemoteContact(it.second, rev, json)
                },
                deleted_ids = page.filter { it.third }.map { it.second },
                undecryptable_ids = emptyList(),
                next_since = if (stuck_cursor) since else next,
                has_more = stuck_cursor || latest.size > limit,
            )
        }

        override suspend fun fetch(id: String): RemoteContact? {
            if (id in undecryptable) throw RemoteUndecryptable()
            return store[id]?.let { RemoteContact(id, it.first, it.second) }
        }

        override suspend fun create(json: String): RemoteCreated {
            if (store.size >= create_limit) throw RemotePlanLimit()
            val id = "c${next_id++}"
            store[id] = 1L to json
            touch(id, false)
            return RemoteCreated(id, 1L)
        }

        override suspend fun update(id: String, json: String, expected_revision: Long): Long {
            before_update?.invoke(id)
            val (rev, _) = store[id] ?: throw RemoteNotFound()
            if (rev != expected_revision) throw RemoteConflict()
            store[id] = rev + 1 to json
            touch(id, false)
            return rev + 1
        }
    }

    private class FakeCursor : ContactSyncCursor {
        var since = 0L
        override fun load() = since
        override fun save(since: Long) {
            this.since = since
        }
    }

    private val device = FakeDevice()
    private val remote = FakeRemote()
    private val cursor = FakeCursor()
    private var active = true
    private val engine = ContactSyncEngine(
        device,
        remote,
        cursor,
        ensure_active = { if (!active) throw ContactSyncSessionLost() },
        now_iso = { "2026-09-29T12:00:00Z" },
    )

    private fun person(n: Int) = ContactCard(
        first_name = "Person$n",
        last_name = "Test",
        emails = listOf(CardEntry("p$n@example.com", "home")),
    ).normalized()

    @Test
    fun initial_pull_inserts_every_contact_across_pages() = runTest {
        val ids = (1..5).map { remote.server_create(person(it)) }
        val stats = engine.sync(ContactSyncOptions(page_size = 2))
        assertEquals(5, stats.inserted_local)
        assertTrue(stats.full_sync)
        ids.forEachIndexed { i, id -> assertEquals(person(i + 1), device.by_source(id)!!.value.card) }
        assertEquals(remote.seq, cursor.since)
        assertEquals(ContactSyncStats(), engine.sync(ContactSyncOptions(page_size = 2)))
    }

    @Test
    fun local_create_is_pushed_and_linked() = runTest {
        val raw = device.user_create(person(7))
        val stats = engine.sync()
        assertEquals(1, stats.created_remote)
        val row = device.rows.getValue(raw)
        assertNotNull(row.source_id)
        assertFalse(row.dirty)
        assertEquals(person(7), remote.card(row.source_id!!))
        assertEquals(1, device.rows.size)
        assertEquals(0, engine.sync().created_remote)
        assertEquals(1, device.rows.size)
    }

    @Test
    fun empty_local_contact_is_not_pushed() = runTest {
        device.user_create(ContactCard())
        assertEquals(0, engine.sync().created_remote)
        assertTrue(remote.store.isEmpty())
    }

    @Test
    fun edits_on_both_sides_merge() = runTest {
        val id = remote.server_create(person(1))
        engine.sync()
        val raw = device.by_source(id)!!.key
        remote.server_edit(id) { it.copy(company = "Web Co") }
        device.user_edit(raw) { it.copy(nickname = "Phone nick") }
        val stats = engine.sync()
        assertEquals(1, stats.updated_remote)
        val expected = person(1).copy(company = "Web Co", nickname = "Phone nick")
        assertEquals(expected, remote.card(id))
        assertEquals(expected, device.rows.getValue(raw).card)
        assertFalse(device.rows.getValue(raw).dirty)
        assertEquals(remote.store.getValue(id).first, device.rows.getValue(raw).revision)
        assertEquals(0, engine.sync().updated_local)
    }

    @Test
    fun remote_edit_is_pulled() = runTest {
        val id = remote.server_create(person(1))
        engine.sync()
        remote.server_edit(id) { it.copy(notes = "from web") }
        val stats = engine.sync()
        assertEquals(1, stats.updated_local)
        assertFalse(stats.full_sync)
        assertEquals("from web", device.by_source(id)!!.value.card.notes)
    }

    @Test
    fun patch_keeps_fields_the_phone_does_not_know() = runTest {
        val id = remote.server_create(person(1))
        val (rev, json) = remote.store.getValue(id)
        remote.store[id] = rev to JSONObject(json).put("groups", JSONArray().put("g1")).toString()
        engine.sync()
        device.user_edit(device.by_source(id)!!.key) { it.copy(notes = "x") }
        engine.sync()
        val stored = JSONObject(remote.store.getValue(id).second)
        assertEquals("g1", stored.getJSONArray("groups").getString(0))
        assertEquals("x", stored.getString("notes"))
    }

    @Test
    fun local_delete_moves_remote_contact_to_trash() = runTest {
        val id = remote.server_create(person(1))
        engine.sync()
        device.user_delete(device.by_source(id)!!.key)
        val stats = engine.sync()
        assertEquals(1, stats.deleted_remote)
        assertTrue(is_trashed_contact_json(remote.store.getValue(id).second))
        assertTrue(device.rows.isEmpty())
        assertEquals(0, engine.sync().inserted_local)
        assertTrue(device.rows.isEmpty())
    }

    @Test
    fun mass_delete_waits_for_confirmation() = runTest {
        repeat(60) { remote.server_create(person(it)) }
        engine.sync()
        device.rows.keys.toList().forEach { device.user_delete(it) }
        val stats = engine.sync()
        assertTrue(stats.too_many_deletions)
        assertEquals(60, stats.pending_deletions)
        assertEquals(0, stats.deleted_remote)
        assertTrue(remote.store.values.none { is_trashed_contact_json(it.second) })
        assertEquals(60, device.rows.size)

        val confirmed = engine.sync(ContactSyncOptions(override_too_many_deletions = true))
        assertEquals(60, confirmed.deleted_remote)
        assertTrue(remote.store.values.all { is_trashed_contact_json(it.second) })
        assertTrue(device.rows.isEmpty())
    }

    @Test
    fun discarding_mass_delete_restores_contacts() = runTest {
        repeat(60) { remote.server_create(person(it)) }
        engine.sync()
        device.rows.keys.toList().forEach { device.user_delete(it) }
        engine.sync()
        val restored = engine.sync(ContactSyncOptions(discard_local_deletions = true))
        assertEquals(60, restored.inserted_local)
        assertEquals(60, device.rows.size)
        assertTrue(device.rows.values.none { it.deleted || it.dirty })
        assertTrue(remote.store.values.none { is_trashed_contact_json(it.second) })
    }

    @Test
    fun small_delete_is_not_blocked() = runTest {
        repeat(60) { remote.server_create(person(it)) }
        engine.sync()
        device.rows.keys.take(10).forEach { device.user_delete(it) }
        val stats = engine.sync()
        assertFalse(stats.too_many_deletions)
        assertEquals(10, stats.deleted_remote)
        assertEquals(50, device.rows.size)
    }

    @Test
    fun remote_trash_and_hard_delete_remove_local_copy() = runTest {
        val a = remote.server_create(person(1))
        val b = remote.server_create(person(2))
        engine.sync()
        remote.server_trash(a)
        remote.server_hard_delete(b)
        val stats = engine.sync()
        assertEquals(2, stats.deleted_local)
        assertTrue(device.rows.isEmpty())
    }

    @Test
    fun resync_required_runs_full_sync_and_drops_unseen_contacts() = runTest {
        val a = remote.server_create(person(1))
        val b = remote.server_create(person(2))
        engine.sync()
        remote.server_hard_delete(b)
        remote.server_create(person(3))
        remote.prune_tombstones()
        remote.server_edit(a) { it.copy(notes = "later") }
        val stats = engine.sync()
        assertTrue(stats.full_sync)
        assertNull(device.by_source(b))
        assertEquals("later", device.by_source(a)!!.value.card.notes)
        assertEquals(2, device.rows.size)
        assertEquals(remote.seq, cursor.since)
        assertFalse(engine.sync().full_sync)
    }

    @Test
    fun full_sync_pages_past_purged_tombstones() = runTest {
        val ids = (1..5).map { remote.server_create(person(it)) }
        remote.server_hard_delete(ids[4])
        remote.prune_tombstones()
        remote.server_edit(ids[0]) { it.copy(notes = "after purge") }
        val stats = engine.sync(ContactSyncOptions(page_size = 2))
        assertTrue(stats.full_sync)
        assertEquals(4, device.rows.size)
        assertEquals("after purge", device.by_source(ids[0])!!.value.card.notes)
        assertTrue(cursor.since >= remote.floor)
        assertFalse(engine.sync(ContactSyncOptions(page_size = 2)).full_sync)
    }

    @Test
    fun concurrent_web_edit_is_retried_and_merged() = runTest {
        val id = remote.server_create(person(1))
        engine.sync()
        device.user_edit(device.by_source(id)!!.key) { it.copy(nickname = "Phone") }
        var injected = false
        remote.before_update = {
            if (!injected) {
                injected = true
                remote.server_edit(id) { c -> c.copy(company = "Web") }
            }
        }
        val stats = engine.sync()
        assertEquals(1, stats.conflicts)
        assertEquals(1, stats.updated_remote)
        val expected = person(1).copy(nickname = "Phone", company = "Web")
        assertEquals(expected, remote.card(id))
        assertEquals(expected, device.by_source(id)!!.value.card)
    }

    @Test
    fun phone_edit_during_apply_is_kept_and_pushed_next_time() = runTest {
        val id = remote.server_create(person(1))
        engine.sync()
        val raw = device.by_source(id)!!.key
        remote.server_edit(id) { it.copy(notes = "web") }
        device.before_apply = {
            device.before_apply = null
            device.user_edit(raw) { c -> c.copy(nickname = "typed meanwhile") }
        }
        engine.sync()
        assertTrue(device.rows.getValue(raw).dirty)
        engine.sync()
        val expected = person(1).copy(notes = "web", nickname = "typed meanwhile")
        assertEquals(expected, remote.card(id))
        assertEquals(expected, device.rows.getValue(raw).card)
        assertFalse(device.rows.getValue(raw).dirty)
    }

    @Test
    fun duplicate_rows_for_one_contact_are_collapsed() = runTest {
        val id = remote.server_create(person(1))
        engine.sync()
        device.insert(id, 1L, person(1), null)
        assertEquals(2, device.rows.size)
        engine.sync()
        assertEquals(1, device.rows.size)
    }

    @Test
    fun plan_limit_stops_creating_and_keeps_rows() = runTest {
        remote.create_limit = 1
        device.user_create(person(1))
        device.user_create(person(2))
        device.user_create(person(3))
        val stats = engine.sync()
        assertEquals(1, stats.created_remote)
        assertEquals(2, stats.skipped_plan_limit)
        assertEquals(3, device.rows.size)
        assertEquals(2, device.rows.values.count { it.dirty && it.source_id == null })
    }

    @Test
    fun remote_deleted_while_phone_edited_drops_local_row() = runTest {
        val id = remote.server_create(person(1))
        engine.sync()
        device.user_edit(device.by_source(id)!!.key) { it.copy(notes = "x") }
        remote.server_hard_delete(id)
        engine.sync()
        assertTrue(device.rows.isEmpty())
    }

    @Test
    fun lost_session_stops_before_writing() = runTest {
        device.user_create(person(1))
        active = false
        var failed = false
        try {
            engine.sync()
        } catch (_: ContactSyncSessionLost) {
            failed = true
        }
        assertTrue(failed)
        assertTrue(remote.store.isEmpty())
    }

    @Test
    fun undecryptable_contact_does_not_block_other_changes() = runTest {
        val a = remote.server_create(person(1))
        val b = remote.server_create(person(2))
        engine.sync()
        remote.undecryptable.add(a)
        device.user_edit(device.by_source(a)!!.key) { it.copy(notes = "a") }
        device.user_edit(device.by_source(b)!!.key) { it.copy(notes = "b") }
        remote.server_create(person(3))
        val stats = engine.sync()
        assertEquals(1, stats.skipped_undecryptable)
        assertEquals(1, stats.updated_remote)
        assertEquals(1, stats.inserted_local)
        assertEquals("b", remote.card(b).notes)
        assertTrue(device.by_source(a)!!.value.dirty)
    }

    @Test
    fun stalled_cursor_stops_the_pull() = runTest {
        remote.server_create(person(1))
        engine.sync()
        remote.server_create(person(2))
        remote.stuck_cursor = true
        var failed = false
        try {
            engine.sync()
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    private fun avatar(n: Int) = "data:image/jpeg;base64," +
        java.util.Base64.getEncoder().encodeToString("image$n".toByteArray())

    private fun device_avatar(photo: String) = "data:image/jpeg;base64," +
        java.util.Base64.getEncoder().encodeToString(photo.toByteArray())

    @Test
    fun remote_avatar_is_written_to_the_phone() = runTest {
        val id = remote.server_create(person(1))
        remote.server_set_avatar(id, avatar(1))
        engine.sync()
        val row = device.by_source(id)!!.value
        assertEquals("dev:${avatar(1)}", row.photo)
        assertEquals(remote_photo_fingerprint(avatar(1)), row.card.photo)
        assertEquals(ContactSyncStats(), engine.sync())
    }

    @Test
    fun phone_photo_is_uploaded_with_a_new_contact() = runTest {
        val raw = device.user_create(person(2))
        device.user_set_photo(raw, "phone-photo")
        assertEquals(1, engine.sync().created_remote)
        val row = device.rows.getValue(raw)
        assertEquals(device_avatar("phone-photo"), remote.avatar(row.source_id!!))
        assertEquals(remote_photo_fingerprint(device_avatar("phone-photo")), row.card.photo)
        assertEquals("phone-photo", row.photo)
        val again = engine.sync()
        assertEquals(0, again.updated_remote)
        assertEquals(0, again.updated_local)
    }

    @Test
    fun phone_photo_change_is_uploaded() = runTest {
        val id = remote.server_create(person(1))
        remote.server_set_avatar(id, avatar(1))
        engine.sync()
        val raw = device.by_source(id)!!.key
        device.user_set_photo(raw, "new-photo")
        assertEquals(1, engine.sync().updated_remote)
        assertEquals(device_avatar("new-photo"), remote.avatar(id))
        assertEquals("new-photo", device.rows.getValue(raw).photo)
        assertFalse(device.rows.getValue(raw).dirty)
        val again = engine.sync()
        assertEquals(0, again.updated_remote)
        assertEquals(0, again.updated_local)
    }

    @Test
    fun phone_photo_removal_clears_the_avatar() = runTest {
        val id = remote.server_create(person(1))
        remote.server_set_avatar(id, avatar(1))
        engine.sync()
        device.user_set_photo(device.by_source(id)!!.key, null)
        engine.sync()
        assertNull(remote.avatar(id))
        assertFalse(JSONObject(remote.store.getValue(id).second).has("avatar_url"))
    }

    @Test
    fun remote_avatar_change_and_removal_reach_the_phone() = runTest {
        val id = remote.server_create(person(1))
        remote.server_set_avatar(id, avatar(1))
        engine.sync()
        remote.server_set_avatar(id, avatar(2))
        engine.sync()
        assertEquals("dev:${avatar(2)}", device.by_source(id)!!.value.photo)
        remote.server_set_avatar(id, null)
        engine.sync()
        assertNull(device.by_source(id)!!.value.photo)
        assertEquals("", device.by_source(id)!!.value.card.photo)
    }

    @Test
    fun unsynced_phone_photo_survives_a_remote_edit() = runTest {
        val id = remote.server_create(person(1))
        engine.sync()
        val raw = device.by_source(id)!!.key
        device.rows.getValue(raw).photo = "added-offline"
        remote.server_edit(id) { it.copy(company = "Web Co") }
        engine.sync()
        val row = device.rows.getValue(raw)
        assertEquals("added-offline", row.photo)
        assertEquals("Web Co", row.card.company)
        assertEquals(device_avatar("added-offline"), remote.avatar(id))
        assertEquals("Web Co", remote.card(id).company)
    }

    @Test
    fun schema_upgrade_reapplies_unchanged_contacts() = runTest {
        val id = remote.server_create(person(1))
        remote.server_set_avatar(id, avatar(1))
        engine.sync()
        val row = device.by_source(id)!!.value
        row.photo = null
        row.link = ""
        row.schema = 1
        row.card = row.card.copy(photo = "")
        cursor.since = 0
        engine.sync()
        val after = device.by_source(id)!!.value
        assertEquals("dev:${avatar(1)}", after.photo)
        assertEquals(CARD_SCHEMA, after.schema)
    }
}
