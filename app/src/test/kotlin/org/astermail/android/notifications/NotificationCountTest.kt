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

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCountTest {

    private fun fake_context(stored: Map<String, Any>): Context {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.contains(any()) } answers { stored.containsKey(firstArg()) }
        every { prefs.getString(any(), any()) } answers { stored[firstArg()] as? String ?: secondArg() }
        every { prefs.getInt(any(), any()) } answers { stored[firstArg()] as? Int ?: secondArg() }
        every { prefs.getLong(any(), any()) } answers { stored[firstArg()] as? Long ?: secondArg() }
        every { prefs.getBoolean(any(), any()) } answers { stored[firstArg()] as? Boolean ?: secondArg() }
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }

    private fun item(id: String, folder_token: String? = null): InboxItem = InboxItem(
        id = id,
        thread_token = "t_$id",
        thread_message_count = 1,
        sender_name = "Alice",
        sender_email = "alice@example.com",
        subject = "Subject",
        preview = "Preview",
        timestamp = "2026-11-26T10:00:00Z",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = listOfNotNull(folder_token),
        raw_item = MailItem(id = id, folder_token = folder_token),
    )

    @Test
    fun `mail in a muted folder is left out of the count`() {
        val context = fake_context(mapOf("muted_folder_tokens" to "tok_muted"))
        val items = listOf(
            item("a"),
            item("b", folder_token = "tok_muted"),
            item("c", folder_token = "tok_muted"),
            item("d", folder_token = "tok_other"),
        )
        val eligible = MailPollingWorker.pick_notifiable_candidates(context, items)
        assertEquals(listOf("a", "d"), eligible.map { it.id })
        assertEquals(2, MailPollingWorker.generic_notification_count(inbox_arrivals = 0, eligible = eligible.size, page_limit = 5))
    }

    @Test
    fun `large stats delta never becomes the count`() {
        assertEquals(1, MailPollingWorker.generic_notification_count(inbox_arrivals = 1, eligible = 1, page_limit = 5))
    }

    @Test
    fun `full page falls back to inbox arrivals`() {
        assertEquals(12, MailPollingWorker.generic_notification_count(inbox_arrivals = 12, eligible = 5, page_limit = 5))
    }

    @Test
    fun `full page keeps the eligible count when inbox arrivals are lower`() {
        assertEquals(5, MailPollingWorker.generic_notification_count(inbox_arrivals = 2, eligible = 5, page_limit = 5))
    }

    @Test
    fun `no fetched page uses inbox arrivals`() {
        assertEquals(3, MailPollingWorker.generic_notification_count(inbox_arrivals = 3, eligible = null, page_limit = 5))
    }

    @Test
    fun `count is at least one`() {
        assertEquals(1, MailPollingWorker.generic_notification_count(inbox_arrivals = -4, eligible = null, page_limit = 5))
        assertEquals(1, MailPollingWorker.generic_notification_count(inbox_arrivals = 0, eligible = 0, page_limit = 5))
    }
}
