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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleNotificationPerEmailTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before
    fun setup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, "android.permission.POST_NOTIFICATIONS",
            )
        }
        MailPollingWorker.create_channel(context)
        MailPollingWorker.reset_new_mail_baseline(context)
        manager.cancelAll()
        await_count(0)
    }

    @After
    fun teardown() {
        manager.cancelAll()
        MailPollingWorker.reset_new_mail_baseline(context)
    }

    private fun active_ids(): Set<Int> = manager.activeNotifications.map { it.id }.toSet()

    private fun await_count(expected: Int) {
        repeat(50) {
            if (active_ids().size == expected) return
            Thread.sleep(100)
        }
    }

    private fun post_message(item_id: String) {
        val id = MailPollingWorker.message_notification_id(item_id.hashCode())
        repeat(8) {
            MailPollingWorker.show_message(context, "Sender", "Subject", "Preview", id, item_id)
            repeat(12) {
                if (id in active_ids()) return
                Thread.sleep(100)
            }
        }
    }

    @Test
    fun one_email_posts_exactly_one_notification() {
        val item_id = "single-" + System.nanoTime()
        post_message(item_id)
        Thread.sleep(500)

        assertEquals(setOf(MailPollingWorker.message_notification_id(item_id.hashCode())), active_ids())
        assertFalse(MailPollingWorker.SUMMARY_NOTIFICATION_ID in active_ids())
    }

    @Test
    fun repeated_delivery_of_the_same_email_does_not_add_a_notification() {
        val item_id = "repeat-" + System.nanoTime()
        post_message(item_id)
        Thread.sleep(500)
        val after_first = active_ids()

        post_message(item_id)
        Thread.sleep(500)

        assertEquals(after_first, active_ids())
        assertEquals(1, active_ids().size)
    }

    @Test
    fun generic_fallback_still_alerts_after_a_message_notification() {
        val item_id = "generic-" + System.nanoTime()
        post_message(item_id)
        Thread.sleep(500)

        assertTrue(MailPollingWorker.show_generic(context, 1))
        Thread.sleep(1_000)

        assertTrue(MailPollingWorker.NOTIFICATION_ID in active_ids())
    }

    @Test
    fun a_message_notification_replaces_the_generic_fallback() {
        assertTrue(MailPollingWorker.show_generic(context, 1))
        Thread.sleep(500)
        assertTrue(MailPollingWorker.NOTIFICATION_ID in active_ids())

        val item_id = "replaces-" + System.nanoTime()
        post_message(item_id)
        Thread.sleep(1_000)

        assertFalse(MailPollingWorker.NOTIFICATION_ID in active_ids())
        assertEquals(setOf(MailPollingWorker.message_notification_id(item_id.hashCode())), active_ids())
    }

    @Test
    fun each_locked_item_gets_its_own_alert() {
        val first = "locked-first-" + System.nanoTime()
        val second = "locked-second-" + System.nanoTime()

        assertTrue(MailPollingWorker.show_generic_for_item(context, first))
        assertTrue(MailPollingWorker.show_generic_for_item(context, second))
        Thread.sleep(1_000)

        assertTrue(MailPollingWorker.message_notification_id(first.hashCode()) in active_ids())
        assertTrue(MailPollingWorker.message_notification_id(second.hashCode()) in active_ids())
    }

    @Test
    fun a_released_claim_can_be_taken_again() {
        val item_id = "claim-" + System.nanoTime()

        assertTrue(MailPollingWorker.claim_item_notification(context, item_id))
        assertFalse(MailPollingWorker.claim_item_notification(context, item_id))

        MailPollingWorker.release_item_claim(context, item_id)

        assertFalse(MailPollingWorker.was_item_notified(context, item_id))
        assertTrue(MailPollingWorker.claim_item_notification(context, item_id))
    }

    @Test
    fun second_email_brings_back_the_group_summary() {
        val first = "first-" + System.nanoTime()
        val second = "second-" + System.nanoTime()
        post_message(first)
        Thread.sleep(500)
        post_message(second)
        Thread.sleep(1_000)

        assertTrue(MailPollingWorker.SUMMARY_NOTIFICATION_ID in active_ids())
        assertEquals(3, active_ids().size)
    }
}
