// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReconcileTest {

    @Test
    fun group_summary_stays_visible_on_the_lock_screen() {
        assertNotEquals(NotificationCompat.VISIBILITY_SECRET, MailPollingWorker.GROUP_SUMMARY_VISIBILITY)
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, MailPollingWorker.GROUP_SUMMARY_VISIBILITY)
    }

    @Test
    fun any_handled_flag_dismisses_the_notification() {
        assertTrue(MailPollingWorker.is_notification_handled_elsewhere(true, false, false, false))
        assertTrue(MailPollingWorker.is_notification_handled_elsewhere(false, true, false, false))
        assertTrue(MailPollingWorker.is_notification_handled_elsewhere(false, false, true, false))
        assertTrue(MailPollingWorker.is_notification_handled_elsewhere(false, false, false, true))
    }

    @Test
    fun unread_or_unknown_state_keeps_the_notification() {
        assertFalse(MailPollingWorker.is_notification_handled_elsewhere(false, false, false, false))
        assertFalse(MailPollingWorker.is_notification_handled_elsewhere(null, null, null, null))
        assertFalse(MailPollingWorker.is_notification_handled_elsewhere(null, false, null, false))
    }

    @Test
    fun mutation_for_a_shown_item_triggers_reconcile() {
        val shown = setOf("abc", "def")
        assertTrue(MailPollingWorker.should_reconcile_for_mutation("mark_read", listOf("abc"), shown))
        assertTrue(MailPollingWorker.should_reconcile_for_mutation("trash", listOf("zzz", "def"), shown))
        assertTrue(MailPollingWorker.should_reconcile_for_mutation("update_metadata", listOf("abc"), shown))
    }

    @Test
    fun mutation_for_other_items_does_not_trigger_reconcile() {
        val shown = setOf("abc")
        assertFalse(MailPollingWorker.should_reconcile_for_mutation("mark_read", listOf("zzz"), shown))
        assertFalse(MailPollingWorker.should_reconcile_for_mutation("star", listOf("abc"), shown))
        assertFalse(MailPollingWorker.should_reconcile_for_mutation("", listOf("abc"), shown))
    }

    @Test
    fun bulk_or_unspecified_mutation_triggers_reconcile_only_when_something_is_shown() {
        assertTrue(MailPollingWorker.should_reconcile_for_mutation("bulk", emptyList(), setOf("abc")))
        assertTrue(MailPollingWorker.should_reconcile_for_mutation("mark_read", emptyList(), setOf("abc")))
        assertFalse(MailPollingWorker.should_reconcile_for_mutation("bulk", emptyList(), emptySet()))
        assertFalse(MailPollingWorker.should_reconcile_for_mutation("mark_read", listOf("abc"), emptySet()))
    }

    @Test
    fun notified_item_id_accepts_only_safe_identifiers() {
        assertTrue(MailPollingWorker.is_valid_notified_item_id("3f2a-B9_c"))
        assertTrue(MailPollingWorker.is_valid_notified_item_id("a".repeat(128)))
        assertFalse(MailPollingWorker.is_valid_notified_item_id(""))
        assertFalse(MailPollingWorker.is_valid_notified_item_id("a".repeat(129)))
        assertFalse(MailPollingWorker.is_valid_notified_item_id("../etc"))
        assertFalse(MailPollingWorker.is_valid_notified_item_id("abc?x=1"))
        assertFalse(MailPollingWorker.is_valid_notified_item_id("abc def"))
        assertFalse(MailPollingWorker.is_valid_notified_item_id("abc\n"))
    }

    @Test
    fun account_tag_is_stable_opaque_and_distinct() {
        val a = MailPollingWorker.account_tag("user-1")
        assertEquals(a, MailPollingWorker.account_tag("user-1"))
        assertEquals(24, a.length)
        assertFalse(a.contains("user"))
        assertNotEquals(a, MailPollingWorker.account_tag("user-2"))
        assertEquals("", MailPollingWorker.account_tag(null))
        assertEquals("", MailPollingWorker.account_tag("  "))
    }
}
