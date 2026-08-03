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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDedupeTest {

    private val window = MailPollingWorker.MESSAGE_NOTIFY_DEDUPE_WINDOW_MS

    @Test
    fun wake_right_after_a_message_notification_is_deduped() {
        val now = 1_700_000_000_000L
        assertTrue(MailPollingWorker.is_within_message_dedupe_window(now - 1_000L, now))
        assertTrue(MailPollingWorker.is_within_message_dedupe_window(now - (window - 1L), now))
    }

    @Test
    fun wake_after_the_window_still_notifies() {
        val now = 1_700_000_000_000L
        assertFalse(MailPollingWorker.is_within_message_dedupe_window(now - window, now))
        assertFalse(MailPollingWorker.is_within_message_dedupe_window(now - (window * 4), now))
    }

    @Test
    fun no_previous_message_notification_never_dedupes() {
        val now = 1_700_000_000_000L
        assertFalse(MailPollingWorker.is_within_message_dedupe_window(0L, now))
        assertFalse(MailPollingWorker.is_within_message_dedupe_window(-1L, now))
    }

    @Test
    fun clock_moving_backwards_does_not_dedupe_forever() {
        val now = 1_700_000_000_000L
        assertFalse(MailPollingWorker.is_within_message_dedupe_window(now + 60_000L, now))
    }

    @Test
    fun group_summary_needs_at_least_two_children() {
        assertFalse(MailPollingWorker.should_post_group_summary(0))
        assertFalse(MailPollingWorker.should_post_group_summary(1))
        assertTrue(MailPollingWorker.should_post_group_summary(2))
        assertTrue(MailPollingWorker.should_post_group_summary(7))
    }
}
