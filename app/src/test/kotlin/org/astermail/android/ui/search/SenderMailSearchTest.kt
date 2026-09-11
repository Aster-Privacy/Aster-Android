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

package org.astermail.android.ui.search

import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderMailSearchTest {

    private fun item(
        sender_email: String,
        sender_name: String = "Sender",
        to_addresses: List<String> = emptyList(),
        is_archived: Boolean = false,
        is_trashed: Boolean = false,
    ) = InboxItem(
        id = "1",
        thread_token = null,
        thread_message_count = 1,
        sender_name = sender_name,
        sender_email = sender_email,
        subject = "hello",
        preview = "preview",
        timestamp = "2026-08-01T00:00:00Z",
        is_read = true,
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = is_trashed,
        is_archived = is_archived,
        is_spam = false,
        labels = emptyList(),
        to_addresses = to_addresses,
        raw_item = MailItem(id = "1"),
    )

    private fun matches(query: String, item: InboxItem): Boolean {
        return matches_item(item, parse_query(query), filter = null)
    }

    @Test
    fun builder_trims_and_lowercases() {
        assertEquals("from:ann@example.com", build_sender_mail_query("  Ann@Example.COM "))
    }

    @Test
    fun builder_returns_null_for_blank_sender() {
        assertNull(build_sender_mail_query("   "))
        assertNull(build_sender_mail_query(null))
    }

    @Test
    fun builder_keeps_plus_addressing_and_unicode() {
        assertEquals("from:ann+news@example.com", build_sender_mail_query("Ann+News@Example.com"))
        assertEquals("from:jörg@exämple.de", build_sender_mail_query("JÖRG@Exämple.de"))
    }

    @Test
    fun builder_output_parses_back_to_one_operator() {
        val query = build_sender_mail_query("Ann \"Smith\"@example.com")!!
        val parsed = parse_query(query)
        assertEquals(1, parsed.operators.size)
        assertEquals("from", parsed.operators[0].key)
        assertEquals("ann smith@example.com", parsed.operators[0].value)
        assertEquals("", parsed.free_text)
    }

    @Test
    fun builder_output_survives_commas() {
        val parsed = parse_query(build_sender_mail_query("a,b@example.com")!!)
        assertEquals("a,b@example.com", parsed.operators[0].value)
    }

    @Test
    fun full_address_matches_exact_sender_ignoring_case() {
        assertTrue(matches(build_sender_mail_query("Ann@Example.com")!!, item("ANN@example.COM")))
    }

    @Test
    fun full_address_does_not_match_longer_local_part() {
        assertFalse(matches("from:ann@example.com", item("joann@example.com")))
    }

    @Test
    fun full_address_does_not_match_longer_domain() {
        assertFalse(matches("from:ann@example.com", item("ann@example.community")))
    }

    @Test
    fun plus_addressed_senders_stay_distinct() {
        assertTrue(matches("from:ann+news@example.com", item("ann+news@example.com")))
        assertFalse(matches("from:ann@example.com", item("ann+news@example.com")))
    }

    @Test
    fun full_address_matches_bracketed_sender_field() {
        assertTrue(matches("from:ann@example.com", item("Ann <Ann@Example.com>")))
    }

    @Test
    fun full_address_matches_forwarded_display_sender() {
        val forwarded = item("relay@aster.test").copy(display_sender_email = "Ann@example.com")
        assertTrue(matches("from:ann@example.com", forwarded))
        val other = item("relay@aster.test").copy(display_sender_email = "joann@example.com")
        assertFalse(matches("from:ann@example.com", other))
    }

    @Test
    fun unicode_address_matches_after_case_folding() {
        assertTrue(matches(build_sender_mail_query("JÖRG@exämple.de")!!, item("jörg@EXÄMPLE.de")))
    }

    @Test
    fun partial_name_or_domain_still_matches_as_substring() {
        assertTrue(matches("from:send", item("x@example.com")))
        assertTrue(matches("from:@example.com", item("x@example.com")))
    }

    @Test
    fun sender_search_includes_archive_and_keeps_trash_rules() {
        val query = build_sender_mail_query("ann@example.com")!!
        assertTrue(matches(query, item("ann@example.com", is_archived = true)))
        assertFalse(matches(query, item("ann@example.com", is_trashed = true)))
        assertTrue(matches("$query in:anywhere", item("ann@example.com", is_trashed = true)))
    }

    @Test
    fun contact_full_address_does_not_over_match_recipient() {
        val sent = item("me@aster.test", to_addresses = listOf("joann@example.com"))
        assertFalse(matches("contact:ann@example.com", sent))
        assertTrue(matches("contact:joann@example.com", sent))
    }
}
