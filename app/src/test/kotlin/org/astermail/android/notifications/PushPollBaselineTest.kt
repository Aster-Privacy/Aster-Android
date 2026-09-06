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

package org.astermail.android.notifications

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PushPollBaselineTest {

    private val stored = linkedMapOf<String, Any>()

    @Before
    fun set_up() {
        stored.clear()
    }

    private fun fake_prefs(): SharedPreferences {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.contains(any()) } answers { stored.containsKey(firstArg()) }
        every { prefs.getString(any(), any()) } answers { stored[firstArg()] as? String ?: secondArg() }
        every { prefs.getInt(any(), any()) } answers { stored[firstArg()] as? Int ?: secondArg() }
        every { prefs.getLong(any(), any()) } answers { stored[firstArg()] as? Long ?: secondArg() }
        every { prefs.getBoolean(any(), any()) } answers { stored[firstArg()] as? Boolean ?: secondArg() }
        every { prefs.edit() } answers {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            val pending = linkedMapOf<String, Any>()
            every { editor.putString(any(), any()) } answers { pending[firstArg()] = secondArg<String>(); editor }
            every { editor.putInt(any(), any()) } answers { pending[firstArg()] = secondArg<Int>(); editor }
            every { editor.putLong(any(), any()) } answers { pending[firstArg()] = secondArg<Long>(); editor }
            every { editor.commit() } answers { stored.putAll(pending); true }
            every { editor.apply() } answers { stored.putAll(pending) }
            editor
        }
        return prefs
    }

    private fun fake_context(prefs: SharedPreferences): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }

    private fun item(id: String, timestamp: String): InboxItem = InboxItem(
        id = id,
        thread_token = "t_$id",
        thread_message_count = 1,
        sender_name = "Alice",
        sender_email = "alice@example.com",
        subject = "Subject",
        preview = "Preview",
        timestamp = timestamp,
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        raw_item = MailItem(id = id),
    )

    private val floor_ms = 1_780_000_000_000L
    private val slack_ms = MailPollingWorker.BASELINE_SLACK_MS

    @Test
    fun no_baseline_accepts_every_item() {
        assertTrue(MailPollingWorker.is_newer_than_baseline("2020-01-01T00:00:00Z", 0L))
    }

    @Test
    fun unparsable_timestamp_fails_open() {
        assertTrue(MailPollingWorker.is_newer_than_baseline("", floor_ms))
        assertTrue(MailPollingWorker.is_newer_than_baseline("not a date", floor_ms))
    }

    @Test
    fun item_older_than_the_baseline_is_rejected() {
        val old = java.time.Instant.ofEpochMilli(floor_ms - slack_ms - 60_000L).toString()
        assertFalse(MailPollingWorker.is_newer_than_baseline(old, floor_ms))
    }

    @Test
    fun item_inside_the_slack_window_is_accepted() {
        val recent = java.time.Instant.ofEpochMilli(floor_ms - slack_ms + 60_000L).toString()
        assertTrue(MailPollingWorker.is_newer_than_baseline(recent, floor_ms))
    }

    @Test
    fun item_after_the_baseline_is_accepted_in_offset_format() {
        val later = java.time.OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(floor_ms + 5_000L),
            java.time.ZoneOffset.ofHours(2),
        ).toString()
        assertTrue(MailPollingWorker.is_newer_than_baseline(later, floor_ms))
    }

    @Test
    fun push_bump_is_ignored_before_the_first_poll_established_a_baseline() {
        val prefs = fake_prefs()
        MailPollingWorker.advance_baseline_for_push(prefs)
        assertNull(stored["cached_unread_count"])
        assertNull(stored["cached_notifiable_count"])
    }

    @Test
    fun push_bump_advances_both_cached_counts() {
        stored["cached_unread_count"] = 7
        stored["cached_notifiable_count"] = 3
        val prefs = fake_prefs()
        MailPollingWorker.advance_baseline_for_push(prefs)
        assertEquals(8, stored["cached_unread_count"])
        assertEquals(4, stored["cached_notifiable_count"])
    }

    @Test
    fun push_bump_leaves_nothing_for_the_next_poll_to_notify() {
        stored["cached_unread_count"] = 7
        stored["cached_notifiable_count"] = 3
        val prefs = fake_prefs()
        MailPollingWorker.advance_baseline_for_push(prefs)
        val server_unread = 8
        val server_notifiable = 4
        val arrived = maxOf(
            server_unread - prefs.getInt("cached_unread_count", 0),
            server_notifiable - prefs.getInt("cached_notifiable_count", 0),
        )
        assertEquals(0, arrived)
    }

    @Test
    fun candidate_selection_skips_unread_mail_that_predates_the_baseline() {
        val context = fake_context(fake_prefs())
        val stale = item("old", java.time.Instant.ofEpochMilli(floor_ms - 3 * slack_ms).toString())
        val fresh = item("new", java.time.Instant.ofEpochMilli(floor_ms + 1_000L).toString())
        assertEquals(fresh, MailPollingWorker.pick_notifiable_candidate(context, listOf(stale, fresh), floor_ms))
        assertNull(MailPollingWorker.pick_notifiable_candidate(context, listOf(stale), floor_ms))
    }

    @Test
    fun candidate_selection_without_a_baseline_keeps_the_legacy_behavior() {
        val context = fake_context(fake_prefs())
        val stale = item("old", "2020-01-01T00:00:00Z")
        assertEquals(stale, MailPollingWorker.pick_notifiable_candidate(context, listOf(stale)))
    }
}
