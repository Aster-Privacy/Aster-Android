//
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.search

import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMailSearchTest {

    private fun item(
        sender_email: String = "someone@example.com",
        sender_name: String = "Someone",
        subject: String = "hello",
        to_addresses: List<String> = emptyList(),
    ) = InboxItem(
        id = "1",
        thread_token = null,
        thread_message_count = 1,
        sender_name = sender_name,
        sender_email = sender_email,
        subject = subject,
        preview = "preview",
        timestamp = "2026-08-01T00:00:00Z",
        is_read = true,
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        to_addresses = to_addresses,
        raw_item = MailItem(id = "1"),
    )

    private fun matches(query: String, item: InboxItem): Boolean {
        return matches_item(item, parse_query(query), filter = null)
    }

    @Test
    fun builder_emits_one_operator_per_address() {
        assertEquals(
            "contact:ann@example.com contact:ann@work.example",
            build_contact_mail_query(listOf("ann@example.com", "ann@work.example")),
        )
    }

    @Test
    fun builder_drops_blanks_and_duplicates_and_lowercases() {
        assertEquals(
            "contact:ann@example.com",
            build_contact_mail_query(listOf(" Ann@Example.com ", "", null, "ann@example.com")),
        )
    }

    @Test
    fun builder_quotes_addresses_with_whitespace() {
        assertEquals(
            "contact:\"ann smith@example.com\"",
            build_contact_mail_query(listOf("Ann Smith@example.com")),
        )
    }

    @Test
    fun contact_matches_sender() {
        assertTrue(matches("contact:ann@example.com", item(sender_email = "ann@example.com")))
    }

    @Test
    fun contact_matches_recipient() {
        assertTrue(
            matches(
                "contact:ann@example.com",
                item(to_addresses = listOf("ann@example.com")),
            ),
        )
    }

    @Test
    fun contact_excludes_unrelated_mail() {
        assertFalse(
            matches(
                "contact:ann@example.com",
                item(sender_email = "bob@example.com", to_addresses = listOf("me@aster.test")),
            ),
        )
    }

    @Test
    fun multiple_contact_operators_are_any_of() {
        val query = build_contact_mail_query(listOf("ann@example.com", "ann@work.example"))
        assertTrue(matches(query, item(sender_email = "ann@work.example")))
        assertTrue(matches(query, item(to_addresses = listOf("ann@example.com"))))
        assertFalse(matches(query, item(sender_email = "bob@example.com")))
    }

    @Test
    fun multiple_from_operators_are_any_of() {
        val query = "from:ann@example.com from:bob@example.com"
        assertTrue(matches(query, item(sender_email = "bob@example.com")))
        assertFalse(matches(query, item(sender_email = "carol@example.com")))
    }

    @Test
    fun multiple_to_operators_are_any_of() {
        val query = "to:ann@example.com to:bob@example.com"
        assertTrue(matches(query, item(to_addresses = listOf("bob@example.com"))))
        assertFalse(matches(query, item(to_addresses = listOf("carol@example.com"))))
    }

    @Test
    fun single_from_operator_still_applies() {
        assertTrue(matches("from:ann@example.com", item(sender_email = "ann@example.com")))
        assertFalse(matches("from:ann@example.com", item(sender_email = "bob@example.com")))
    }

    @Test
    fun contact_still_ands_with_other_operators() {
        val query = "contact:ann@example.com subject:invoice"
        assertTrue(
            matches(query, item(sender_email = "ann@example.com", subject = "Invoice 4")),
        )
        assertFalse(
            matches(query, item(sender_email = "ann@example.com", subject = "Lunch")),
        )
    }

    @Test
    fun negated_contact_excludes_the_contact() {
        assertFalse(matches("-contact:ann@example.com", item(sender_email = "ann@example.com")))
        assertTrue(matches("-contact:ann@example.com", item(sender_email = "bob@example.com")))
    }
}
