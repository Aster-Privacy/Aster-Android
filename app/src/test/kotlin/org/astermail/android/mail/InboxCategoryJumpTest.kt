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

package org.astermail.android.mail

import org.astermail.android.api.mail.MailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxCategoryJumpTest {

    private fun envelope(
        from_email: String,
        from_name: String = "",
        subject: String = "",
        raw_headers: List<Pair<String, String>> = emptyList(),
        list_unsubscribe: String? = null,
    ): DecryptedEnvelope = DecryptedEnvelope(
        subject = subject,
        body_text = "",
        body_html = null,
        from_name = from_name,
        from_email = from_email,
        to = emptyList(),
        cc = emptyList(),
        sent_at = "2026-10-08T00:00:00Z",
        raw_headers = raw_headers,
        list_unsubscribe = list_unsubscribe,
        sender_verification = null,
    )

    private val bank_headers = listOf(
        "Auto-Submitted" to "auto-generated",
        "Precedence" to "bulk",
        "DKIM-Signature" to "v=1; a=rsa-sha256; d=payments.interac.ca; s=s1; bh=abc",
    )

    private fun item(
        id: String,
        category: String,
        is_read: Boolean = false,
        thread: String? = null,
        timestamp: String = "2026-10-08T08:00:00Z",
        is_spam: Boolean = false,
    ): InboxItem = InboxItem(
        id = id,
        thread_token = thread,
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@example.test",
        subject = "Subject",
        preview = "Preview",
        timestamp = timestamp,
        is_read = is_read,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = is_spam,
        labels = emptyList(),
        category = category,
        raw_item = MailItem(id = id, item_type = "received"),
    )

    @Test
    fun interac_e_transfer_notification_lands_in_primary() {
        val env = envelope(
            from_email = "notify@payments.interac.ca",
            from_name = "INTERAC e-Transfer",
            subject = "INTERAC e-Transfer: Alex sent you money.",
            raw_headers = bank_headers,
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun interac_deposit_notice_lands_in_primary() {
        val env = envelope(
            from_email = "notify@payments.interac.ca",
            subject = "INTERAC e-Transfer: Your money transfer to Alex was deposited.",
            raw_headers = bank_headers,
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun french_interac_notification_lands_in_primary() {
        val env = envelope(
            from_email = "notify@payments.interac.ca",
            subject = "Virement INTERAC : Alex vous a envoyé des fonds.",
            raw_headers = bank_headers,
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun payment_app_sent_you_money_lands_in_primary() {
        val env = envelope(
            from_email = "no-reply@pay.example.test",
            subject = "Sam sent you \$20.00",
            raw_headers = bank_headers,
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun money_request_lands_in_primary() {
        val env = envelope(
            from_email = "notify@payments.interac.ca",
            subject = "INTERAC e-Transfer: Alex requested money from you.",
            raw_headers = bank_headers,
        )
        assertEquals("primary", classify(env, null))
    }

    @Test
    fun plain_notify_sender_without_payment_subject_is_unchanged() {
        val env = envelope(
            from_email = "notify@service.example.test",
            subject = "Your weekly account summary",
            raw_headers = bank_headers,
        )
        assertTrue(classify(env, null) != "primary")
    }

    @Test
    fun other_category_mail_lists_tabs_with_unread_first() {
        val items = listOf(
            item("a", "promotions", is_read = true),
            item("b", "updates", is_read = false),
            item("c", "updates", is_read = true),
            item("d", "social", is_read = false, is_spam = true),
        )
        val result = other_category_mail(items, "primary", CATEGORY_TABS)
        assertEquals(listOf("updates", "promotions"), result.map { it.id })
        assertEquals(1, result[0].unread)
        assertEquals(2, result[0].threads)
        assertEquals(0, result[1].unread)
    }

    @Test
    fun other_category_mail_skips_active_tab_and_dedupes_threads() {
        val items = listOf(
            item("a", "updates", thread = "t1", timestamp = "2026-10-08T08:00:00Z"),
            item("b", "updates", thread = "t1", timestamp = "2026-10-08T09:00:00Z", is_read = true),
            item("c", "primary"),
        )
        val result = other_category_mail(items, "primary", CATEGORY_TABS)
        assertEquals(1, result.size)
        assertEquals(1, result[0].threads)
        assertEquals(1, result[0].unread)
    }

    @Test
    fun other_category_mail_is_empty_when_everything_is_in_active_tab() {
        val items = listOf(item("a", "primary"), item("b", "primary", is_read = true))
        assertTrue(other_category_mail(items, "primary", CATEGORY_TABS).isEmpty())
    }
}
