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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesCacheArchiveTest {

    private val backing = fake_prefs()
    private val store = PreferencesCacheStore(backing = backing)

    private fun archived_count(): Int = backing.values.keys.count { it.startsWith("unreadable_archive_") }

    @Test
    fun archived_blobs_can_be_listed_and_dropped() {
        assertTrue(store.archive_unreadable("user_1", "preferences", "ct.nonce", now_ms = 10L))
        assertTrue(store.archive_unreadable("user_1", "tag", "tag_ct.tag_nonce", "t1:name", now_ms = 11L))

        val listed = store.list_archived("user_1", "preferences")

        assertEquals(1, listed.size)
        assertEquals("ct.nonce", listed[0].payload)
        assertEquals(10L, listed[0].archived_ms)
        assertEquals("t1:name", store.list_archived("user_1", "tag").single().target)
        assertTrue(store.drop_archived(listed[0].key))
        assertTrue(store.list_archived("user_1", "preferences").isEmpty())
        assertEquals(1, archived_count())
    }

    @Test
    fun the_archive_keeps_only_the_newest_entries_per_account() {
        for (i in 0 until 20) {
            assertTrue(store.archive_unreadable("user_1", "preferences", "ct$i.nonce", now_ms = i.toLong()))
        }
        store.archive_unreadable("user_2", "preferences", "other.nonce", now_ms = 0L)

        assertEquals(17, archived_count())
        val kept = store.list_archived("user_1", "preferences").map { it.payload }
        assertEquals((4 until 20).map { "ct$it.nonce" }, kept)
        assertEquals(1, store.list_archived("user_2", "preferences").size)
    }

    @Test
    fun archives_of_another_kind_never_evict_a_preferences_archive() {
        assertTrue(store.archive_unreadable("user_1", "preferences", "prefs.nonce", now_ms = 0L))
        assertTrue(store.archive_unreadable("user_1", "recovery_email", "email.nonce", now_ms = 1L))
        for (i in 0 until 17) {
            assertTrue(store.archive_unreadable("user_1", "label", "label$i.nonce", "l$i:name", now_ms = 2L + i))
        }

        assertEquals(listOf("prefs.nonce"), store.list_archived("user_1", "preferences").map { it.payload })
        assertEquals(listOf("email.nonce"), store.list_archived("user_1", "recovery_email").map { it.payload })
        assertEquals((0 until 17).map { "label$it.nonce" }, store.list_archived("user_1", "label").map { it.payload })
    }

    @Test
    fun the_newest_archive_of_a_target_is_kept_when_times_tie() {
        for (i in 0 until 20) {
            assertTrue(store.archive_unreadable("user_1", "tag", "tag$i.nonce", "t1:name", now_ms = 5L))
        }

        assertEquals(16, store.list_archived("user_1", "tag").size)
        assertTrue(store.list_archived("user_1", "tag").any { it.payload == "tag19.nonce" })
    }

    @Test
    fun many_items_of_one_kind_never_evict_another_item_only_copy() {
        for (i in 0 until 17) {
            assertTrue(store.archive_unreadable("user_1", "tag", "tag$i.nonce", "t$i:color", now_ms = i.toLong()))
        }

        val kept = store.list_archived("user_1", "tag")
        assertEquals(17, kept.size)
        assertEquals("tag0.nonce", kept.single { it.target == "t0:color" }.payload)
    }

    @Test
    fun versions_are_capped_per_item() {
        for (i in 0 until 20) {
            assertTrue(store.archive_unreadable("user_1", "tag", "a$i.nonce", "t1:name", now_ms = i.toLong()))
        }
        assertTrue(store.archive_unreadable("user_1", "tag", "b.nonce", "t2:name", now_ms = 30L))

        assertEquals(16, store.list_archived("user_1", "tag").count { it.target == "t1:name" })
        assertEquals(listOf("b.nonce"), store.list_archived("user_1", "tag").filter { it.target == "t2:name" }.map { it.payload })
    }

    @Test
    fun new_items_past_the_ceiling_are_refused_without_evicting() {
        val ceiling = 512
        for (i in 0 until ceiling) {
            assertTrue(store.archive_unreadable("user_1", "label", "l$i.nonce", "l$i:name", now_ms = i.toLong()))
        }

        assertFalse(store.archive_unreadable("user_1", "label", "extra.nonce", "extra:name", now_ms = 9_999L))

        assertEquals(ceiling, archived_count())
        assertEquals(1, store.archive_refused_count("user_1"))
        assertEquals("l0.nonce", store.list_archived("user_1", "label").single { it.target == "l0:name" }.payload)
        assertTrue(store.archive_unreadable("user_1", "label", "l0b.nonce", "l0:name", now_ms = 10_000L))
        assertEquals(2, store.list_archived("user_1", "label").count { it.target == "l0:name" })
        store.clear_all()
        assertEquals(1, store.archive_refused_count("user_1"))
    }

    @Test
    fun a_cleared_field_is_recorded_with_its_archive() {
        assertTrue(store.archive_unreadable("user_1", "tag", "c.nonce", "t1:color", now_ms = 1L, replaced_with_blank = true))
        assertTrue(store.archive_unreadable("user_1", "tag", "n.nonce", "t1:name", now_ms = 2L))

        val listed = store.list_archived("user_1", "tag").associateBy { it.target }
        assertTrue(listed.getValue("t1:color").replaced_with_blank)
        assertEquals(1L, listed.getValue("t1:color").archived_ms)
        assertFalse(listed.getValue("t1:name").replaced_with_blank)
    }

    @Test
    fun clearing_after_an_earlier_archive_of_the_same_blob_records_the_clear() {
        assertTrue(store.archive_unreadable("user_1", "tag", "c.nonce", "t1:color", now_ms = 1L))
        assertTrue(store.archive_unreadable("user_1", "tag", "c.nonce", "t1:color", now_ms = 2L, replaced_with_blank = true))

        val listed = store.list_archived("user_1", "tag").single()
        assertTrue(listed.replaced_with_blank)
        assertEquals(1L, listed.archived_ms)
    }

    @Test
    fun preferences_archives_with_different_baselines_share_one_version_cap() {
        for (i in 0 until 20) {
            assertTrue(store.archive_unreadable("user_1", "preferences", "p$i.nonce", now_ms = i.toLong(), baseline = "base$i"))
        }

        val kept = store.list_archived("user_1", "preferences")
        assertEquals((4 until 20).map { "p$it.nonce" }, kept.map { it.payload })
        assertEquals((4 until 20).map { "base$it" }, kept.map { it.baseline })
        assertTrue(kept.all { it.target == "" })
    }

    @Test
    fun an_archive_written_before_baselines_had_their_own_field_still_reads() {
        backing.values["unreadable_archive_old"] = "7\nuser_1\npreferences\nb2xkX2Jhc2U=\nold.nonce"
        backing.values["unreadable_archive_old_blank"] = "8;blank\nuser_1\ntag\nt1:color\ntag.nonce"

        val old = store.list_archived("user_1", "preferences").single()
        assertEquals("b2xkX2Jhc2U=", old.target)
        assertEquals("", old.baseline)
        assertEquals("old.nonce", old.payload)
        assertEquals(7L, old.archived_ms)
        val tag = store.list_archived("user_1", "tag").single()
        assertTrue(tag.replaced_with_blank)
        assertEquals("tag.nonce", tag.payload)
        assertTrue(store.archive_unreadable("user_1", "preferences", "new.nonce", now_ms = 9L, baseline = "nb"))
        assertEquals(listOf("old.nonce", "new.nonce"), store.list_archived("user_1", "preferences").map { it.payload })
    }

    @Test
    fun recording_a_clear_keeps_the_stored_baseline() {
        assertTrue(store.archive_unreadable("user_1", "preferences", "p.nonce", now_ms = 1L, baseline = "b1"))
        assertTrue(store.archive_unreadable("user_1", "preferences", "p.nonce", now_ms = 2L, replaced_with_blank = true))

        val listed = store.list_archived("user_1", "preferences").single()
        assertEquals("b1", listed.baseline)
        assertTrue(listed.replaced_with_blank)
        assertEquals("p.nonce", listed.payload)
    }

    @Test
    fun archiving_the_same_blob_with_a_newer_baseline_keeps_the_newer_baseline() {
        assertTrue(store.archive_unreadable("user_1", "preferences", "p.nonce", now_ms = 1L, baseline = "b1"))
        assertTrue(store.archive_unreadable("user_1", "preferences", "p.nonce", now_ms = 2L, baseline = "b2"))

        val listed = store.list_archived("user_1", "preferences").single()
        assertEquals("b2", listed.baseline)
        assertEquals(2L, listed.archived_ms)
        assertEquals("p.nonce", listed.payload)
    }

    @Test
    fun a_baseline_with_a_line_break_is_refused() {
        assertFalse(store.archive_unreadable("user_1", "preferences", "p.nonce", now_ms = 1L, baseline = "a\nb"))
    }

    @Test
    fun clear_all_keeps_the_archive() {
        store.write("user_1", "{}")
        store.archive_unreadable("user_1", "preferences", "ct.nonce", now_ms = 1L)

        store.clear_all()

        assertEquals(null, store.read("user_1"))
        assertEquals(1, archived_count())
    }

    @Test
    fun accounts_do_not_see_each_other_archives() {
        store.archive_unreadable("user_1", "preferences", "ct.nonce", now_ms = 1L)

        assertTrue(store.list_archived("user_2", "preferences").isEmpty())
        assertTrue(store.list_archived("user_1", "label").isEmpty())
    }

    @Test
    fun archiving_the_same_blob_twice_keeps_one_entry() {
        assertTrue(store.archive_unreadable("user_1", "preferences", "ct.nonce", now_ms = 1L))
        assertTrue(store.archive_unreadable("user_1", "preferences", "ct.nonce", now_ms = 2L))

        assertEquals(1, archived_count())
    }

    @Test
    fun a_failed_write_reports_false() {
        val failing = PreferencesCacheStore(backing = fake_prefs(commit_result = false))

        assertFalse(failing.archive_unreadable("user_1", "preferences", "ct.nonce", now_ms = 1L))
    }
}
