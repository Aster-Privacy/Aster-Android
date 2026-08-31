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

package org.astermail.android.ui.mail

import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.mail.ThreadMessageDecrypted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptionBadgeSemanticsTest {

    private fun message(
        id: String = "m1",
        is_external: Boolean = false,
        has_recipient_key: Boolean? = null,
        is_encrypted: Boolean = true,
        pgp_encrypted: Boolean = false,
    ) = ThreadMessage(
        id = id,
        sender_name = "Someone",
        sender_email = "someone@example.com",
        to_label = "me",
        timestamp = 0L,
        body = "hello",
        is_encrypted = is_encrypted,
        is_external = is_external,
        has_recipient_key = has_recipient_key,
        pgp_encrypted = pgp_encrypted,
    )

    private fun thread_badge_is_e2e(messages: List<ThreadMessage>): Boolean =
        messages.isNotEmpty() && messages.all { it.is_e2e_encrypted }

    private fun thread_badge_is_pgp(messages: List<ThreadMessage>): Boolean =
        messages.isNotEmpty() &&
            messages.any { it.pgp_encrypted } &&
            messages.all { it.is_e2e_encrypted }

    @Test
    fun internal_message_is_end_to_end_encrypted() {
        assertTrue(message(is_external = false).is_e2e_encrypted)
    }

    @Test
    fun external_message_without_recipient_key_is_not_end_to_end_encrypted() {
        assertFalse(message(is_external = true).is_e2e_encrypted)
    }

    @Test
    fun external_message_stored_with_an_envelope_is_still_not_end_to_end_encrypted() {
        assertFalse(message(is_external = true, is_encrypted = true).is_e2e_encrypted)
    }

    @Test
    fun external_message_with_recipient_key_is_end_to_end_encrypted() {
        assertTrue(message(is_external = true, has_recipient_key = true).is_e2e_encrypted)
    }

    @Test
    fun external_message_with_explicit_false_recipient_key_is_not_end_to_end_encrypted() {
        assertFalse(message(is_external = true, has_recipient_key = false).is_e2e_encrypted)
    }

    @Test
    fun thread_badge_is_transit_when_any_message_is_external() {
        val messages = listOf(
            message(id = "a", is_external = false),
            message(id = "b", is_external = true),
        )

        assertFalse(thread_badge_is_e2e(messages))
    }

    @Test
    fun thread_badge_is_end_to_end_when_every_message_is_internal() {
        val messages = listOf(
            message(id = "a", is_external = false),
            message(id = "b", is_external = false),
        )

        assertTrue(thread_badge_is_e2e(messages))
    }

    @Test
    fun thread_badge_is_transit_while_no_messages_are_loaded() {
        assertFalse(thread_badge_is_e2e(emptyList()))
    }

    @Test
    fun external_pgp_message_is_end_to_end_encrypted() {
        assertTrue(
            message(is_external = true, has_recipient_key = null, pgp_encrypted = true)
                .is_e2e_encrypted,
        )
    }

    @Test
    fun thread_badge_names_pgp_when_a_message_arrived_pgp_encrypted() {
        val messages = listOf(
            message(id = "a", is_external = false),
            message(id = "b", is_external = true, pgp_encrypted = true),
        )

        assertTrue(thread_badge_is_e2e(messages))
        assertTrue(thread_badge_is_pgp(messages))
    }

    @Test
    fun thread_badge_does_not_name_pgp_without_a_pgp_message() {
        assertFalse(thread_badge_is_pgp(listOf(message(is_external = false))))
    }

    @Test
    fun pgp_flags_survive_the_thread_message_mapper() {
        val decrypted = ThreadMessageDecrypted(
            id = "m1",
            sender_name = "Someone",
            sender_email = "someone@example.com",
            to_label = "me",
            timestamp = "2026-08-09T10:00:00Z",
            body_text = "hello",
            body_html = null,
            is_encrypted = true,
            is_read = true,
            raw_item = ThreadMessageItem(
                id = "m1",
                item_type = "received",
                is_external = true,
                has_recipient_key = null,
            ),
            pgp_encrypted = true,
            pgp_signature = org.astermail.android.crypto.PgpSignatureStatus.UNVERIFIED,
        )

        val mapped = thread_message_to_mock(decrypted)

        assertTrue(mapped.pgp_encrypted)
        assertTrue(mapped.is_e2e_encrypted)
        assertEquals(
            org.astermail.android.crypto.PgpSignatureStatus.UNVERIFIED,
            mapped.pgp_signature,
        )
    }

    @Test
    fun external_flag_survives_the_thread_message_mapper() {
        val decrypted = ThreadMessageDecrypted(
            id = "m1",
            sender_name = "Someone",
            sender_email = "someone@example.com",
            to_label = "me",
            timestamp = "2026-08-09T10:00:00Z",
            body_text = "hello",
            body_html = null,
            is_encrypted = true,
            is_read = true,
            raw_item = ThreadMessageItem(
                id = "m1",
                item_type = "received",
                is_external = true,
                has_recipient_key = false,
            ),
        )

        val mapped = thread_message_to_mock(decrypted)

        assertTrue(mapped.is_external)
        assertFalse(mapped.is_e2e_encrypted)
    }
}
