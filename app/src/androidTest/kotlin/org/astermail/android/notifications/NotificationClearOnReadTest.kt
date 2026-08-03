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

package org.astermail.android.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationClearOnReadTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private lateinit var item_a: String
    private lateinit var item_b: String

    @Before
    fun setup() {
        item_a = "msg-a-" + System.nanoTime()
        item_b = "msg-b-" + System.nanoTime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, "android.permission.POST_NOTIFICATIONS",
            )
        }
        MailPollingWorker.set_push_enabled(context, false)
        MailPollingWorker.create_channel(context)
        manager.cancelAll()
        await_no_messages()
    }

    @After
    fun teardown() {
        manager.cancelAll()
    }

    private fun active_ids(): Set<Int> = manager.activeNotifications.map { it.id }.toSet()

    private fun await_id(id: Int, present: Boolean) {
        repeat(50) {
            if ((id in active_ids()) == present) return
            Thread.sleep(100)
        }
    }

    private fun await_no_messages() {
        repeat(50) {
            if (manager.activeNotifications.isEmpty()) return
            Thread.sleep(100)
        }
    }

    private fun post_and_await(sender: String, subject: String, preview: String, id: Int) {
        repeat(8) {
            MailPollingWorker.show_message(context, sender, subject, preview, id)
            repeat(12) {
                if (id in active_ids()) return
                Thread.sleep(100)
            }
        }
    }

    @Test
    fun reading_one_message_clears_only_that_notification() {
        val id_a = MailPollingWorker.message_notification_id(item_a.hashCode())
        val id_b = MailPollingWorker.message_notification_id(item_b.hashCode())

        post_and_await("Alice", "First subject", "preview one", id_a)
        post_and_await("Bob", "Second subject", "preview two", id_b)

        val with_both = active_ids()
        assertTrue("message A ($id_a) should be posted; active=$with_both", id_a in with_both)
        assertTrue("message B ($id_b) should be posted; active=$with_both", id_b in with_both)
        assertTrue("group summary should be posted", MailPollingWorker.SUMMARY_NOTIFICATION_ID in with_both)

        MailPollingWorker.cancel_message_notification(context, item_a)

        await_id(id_a, false)
        val after_read_a = active_ids()
        assertFalse("message A cleared after read", id_a in after_read_a)
        assertTrue("message B still present", id_b in after_read_a)
        assertTrue("summary stays while B remains", MailPollingWorker.SUMMARY_NOTIFICATION_ID in after_read_a)
    }

    @Test
    fun reading_last_message_also_clears_group_summary() {
        val id_a = MailPollingWorker.message_notification_id(item_a.hashCode())

        post_and_await("Alice", "Only subject", "preview", id_a)
        assertTrue(id_a in active_ids())
        assertFalse(
            "a single message must not post a group summary",
            MailPollingWorker.SUMMARY_NOTIFICATION_ID in active_ids(),
        )

        MailPollingWorker.cancel_message_notification(context, item_a)

        await_id(id_a, false)
        await_id(MailPollingWorker.SUMMARY_NOTIFICATION_ID, false)
        val after = active_ids()
        assertFalse("last message cleared", id_a in after)
        assertFalse("summary auto-cleared when empty", MailPollingWorker.SUMMARY_NOTIFICATION_ID in after)
    }

    @Test
    fun notified_item_ledger_marks_and_persists() {
        assertFalse(MailPollingWorker.was_item_notified(context, item_a))
        MailPollingWorker.mark_item_notified(context, item_a)
        assertTrue(MailPollingWorker.was_item_notified(context, item_a))
        assertFalse(MailPollingWorker.was_item_notified(context, item_b))

        MailPollingWorker.cancel_message_notification(context, item_a)
        assertTrue(
            "ledger must survive read-cancel so the item is never re-notified",
            MailPollingWorker.was_item_notified(context, item_a),
        )
    }

    @Test
    fun notified_item_ledger_caps_and_keeps_newest() {
        val first = "cap-first-" + System.nanoTime()
        MailPollingWorker.mark_item_notified(context, first)
        repeat(100) { MailPollingWorker.mark_item_notified(context, "cap-fill-$it-" + System.nanoTime()) }
        assertFalse("oldest entry evicted past cap", MailPollingWorker.was_item_notified(context, first))
        val last = "cap-last-" + System.nanoTime()
        MailPollingWorker.mark_item_notified(context, last)
        assertTrue(MailPollingWorker.was_item_notified(context, last))
    }

    private fun inbox_item(id: String, is_read: Boolean) = org.astermail.android.mail.InboxItem(
        id = id,
        thread_token = null,
        thread_message_count = 1,
        sender_name = "Alice",
        sender_email = "alice@example.com",
        subject = "Subject $id",
        preview = "Preview",
        timestamp = "now",
        is_read = is_read,
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        raw_item = org.astermail.android.api.mail.MailItem(id = id),
    )

    @Test
    fun read_email_is_never_renotified_by_later_wake_polls() {
        val id_a = MailPollingWorker.message_notification_id(item_a.hashCode())

        val first_pick = MailPollingWorker.pick_notifiable_candidate(
            context, listOf(inbox_item(item_a, is_read = false)),
        )
        assertTrue("fresh unread mail must be picked", first_pick?.id == item_a)
        MailPollingWorker.mark_item_notified(context, item_a)
        post_and_await("Alice", "Subject", "Preview", id_a)

        MailPollingWorker.cancel_message_notification(context, item_a)
        await_id(id_a, false)
        assertFalse("notification cleared on read", id_a in active_ids())

        repeat(3) {
            val repick_read = MailPollingWorker.pick_notifiable_candidate(
                context, listOf(inbox_item(item_a, is_read = true)),
            )
            assertTrue("read newest item must never be re-picked", repick_read == null)
            val repick_unread_again = MailPollingWorker.pick_notifiable_candidate(
                context, listOf(inbox_item(item_a, is_read = false)),
            )
            assertTrue("already-notified item must never be re-picked", repick_unread_again == null)
        }

        val next_pick = MailPollingWorker.pick_notifiable_candidate(
            context,
            listOf(inbox_item(item_b, is_read = false), inbox_item(item_a, is_read = true)),
        )
        assertTrue("genuinely new mail must still be picked", next_pick?.id == item_b)
    }

    @Test
    fun bulk_read_clears_all_targeted_notifications() {
        val id_a = MailPollingWorker.message_notification_id(item_a.hashCode())
        val id_b = MailPollingWorker.message_notification_id(item_b.hashCode())

        post_and_await("Alice", "A", "pa", id_a)
        post_and_await("Bob", "B", "pb", id_b)

        MailPollingWorker.cancel_message_notifications(context, listOf(item_a, item_b))

        await_id(id_a, false)
        await_id(id_b, false)
        await_id(MailPollingWorker.SUMMARY_NOTIFICATION_ID, false)
        val after = active_ids()
        assertFalse(id_a in after)
        assertFalse(id_b in after)
        assertFalse("summary cleared after bulk read", MailPollingWorker.SUMMARY_NOTIFICATION_ID in after)
    }
}
