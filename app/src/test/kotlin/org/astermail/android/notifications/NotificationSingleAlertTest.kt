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

class NotificationSingleAlertTest {

    private val stored = linkedMapOf<String, Any>()
    private val t0 = 1_790_000_000_000L

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

    private fun fake_context(): Context {
        val prefs = fake_prefs()
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }

    private fun item(
        id: String,
        sender_email: String = "alice@example.com",
        created_at: String? = null,
        missing: Boolean = false,
        settling: Boolean = false,
        undecryptable: Boolean = false,
    ): InboxItem = InboxItem(
        id = id,
        thread_token = "t_$id",
        thread_message_count = 1,
        sender_name = "",
        sender_email = sender_email,
        subject = "",
        preview = "",
        timestamp = "",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        raw_item = MailItem(id = id, created_at = created_at),
        is_undecryptable = undecryptable || settling,
        is_decrypt_pending = missing || settling,
        is_envelope_missing = missing,
        is_envelope_settling = settling,
    )

    @Test
    fun a_message_alert_right_after_a_generic_alert_for_the_same_mail_is_quiet() {
        val context = fake_context()
        assertEquals(GenericPost.Audible, MailPollingWorker.claim_generic_post(context, 1, false, t0))
        assertTrue(MailPollingWorker.message_post_quiet(context, false, t0 - 2_000L, t0 + 5_000L))
    }

    @Test
    fun a_message_alert_long_after_a_generic_alert_stays_quiet_for_mail_it_covered() {
        val context = fake_context()
        MailPollingWorker.claim_generic_post(context, 1, false, t0)
        assertTrue(MailPollingWorker.message_post_quiet(context, false, t0 - 2_000L, t0 + 120_000L))
    }

    @Test
    fun mail_that_arrived_after_the_generic_alert_still_alerts() {
        val context = fake_context()
        MailPollingWorker.claim_generic_post(context, 1, false, t0)
        assertFalse(MailPollingWorker.message_post_quiet(context, false, t0 + 100_000L, t0 + 120_000L))
    }

    @Test
    fun a_quiet_generic_post_does_not_silence_the_next_message() {
        val context = fake_context()
        assertEquals(GenericPost.Quiet, MailPollingWorker.claim_generic_post(context, 1, true, t0))
        assertFalse(MailPollingWorker.message_post_quiet(context, false, t0 - 2_000L, t0 + 5_000L))
    }

    @Test
    fun a_second_generic_alert_inside_the_window_is_quiet() {
        val context = fake_context()
        assertEquals(GenericPost.Audible, MailPollingWorker.claim_generic_post(context, 1, false, t0))
        assertEquals(GenericPost.Quiet, MailPollingWorker.claim_generic_post(context, 2, false, t0 + 30_000L))
    }

    @Test
    fun two_deliveries_of_the_same_message_id_post_one_generic_notification() {
        val context = fake_context()
        var shown = 0
        val first = post_push_generic(context, "item_a", show = { shown++; true })
        val second = post_push_generic(context, "item_a", show = { shown++; true })
        assertEquals(PushResult.Shown, first)
        assertEquals(PushResult.Ignore, second)
        assertEquals(1, shown)
    }

    @Test
    fun a_generic_that_was_not_posted_releases_its_claim() {
        val context = fake_context()
        post_push_generic(context, "item_b", show = { false })
        assertFalse(MailPollingWorker.was_item_notified(context, "item_b"))
    }

    @Test
    fun a_placeholder_row_never_puts_its_placeholder_text_in_a_notification() {
        assertEquals("" to "", MailPollingWorker.notification_text("Loading", "", true, true, true))
    }

    @Test
    fun a_neutral_notice_names_the_sender_only_when_the_envelope_decrypted() {
        assertEquals("alice@example.com", MailPollingWorker.neutral_sender_label(item("a", undecryptable = true)))
        assertEquals("", MailPollingWorker.neutral_sender_label(item("b", sender_email = "", settling = true)))
        assertEquals("", MailPollingWorker.neutral_sender_label(item("c", sender_email = "", missing = true)))
    }

    @Test
    fun arrival_time_comes_from_the_server_receive_time() {
        val arrived = java.time.Instant.ofEpochMilli(t0).toString()
        assertEquals(t0, MailPollingWorker.item_arrived_ms(item("a", created_at = arrived)))
        assertNull(MailPollingWorker.item_arrived_ms(item("b")))
    }
}
