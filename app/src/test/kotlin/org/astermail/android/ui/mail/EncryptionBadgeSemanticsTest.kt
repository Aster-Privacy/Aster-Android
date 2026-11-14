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
        pgp_signature: org.astermail.android.crypto.PgpSignatureStatus =
            org.astermail.android.crypto.PgpSignatureStatus.NONE,
        item_type: String = "received",
        e2e_verified: Boolean = false,
        own_sealed: Boolean = false,
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
        pgp_signature = pgp_signature,
        item_type = item_type,
        e2e_verified = e2e_verified,
        own_sealed = own_sealed,
    )

    private fun thread_badge_is_e2e(messages: List<ThreadMessage>): Boolean =
        messages.isNotEmpty() && messages.all { it.is_e2e_encrypted }

    private fun thread_badge_is_pgp(messages: List<ThreadMessage>): Boolean =
        messages.isNotEmpty() &&
            messages.any { it.shows_pgp_label } &&
            messages.all { it.is_e2e_encrypted }

    @Test
    fun internal_message_is_end_to_end_encrypted() {
        assertTrue(message(is_external = false, e2e_verified = true).is_e2e_encrypted)
    }

    @Test
    fun internal_flag_alone_does_not_earn_the_end_to_end_badge() {
        assertFalse(message(is_external = false).is_e2e_encrypted)
        assertFalse(thread_is_end_to_end_encrypted(listOf(message(is_external = false))))
    }

    @Test
    fun recipient_key_flag_alone_does_not_earn_the_end_to_end_badge() {
        assertFalse(message(is_external = true, has_recipient_key = true).is_e2e_encrypted)
        assertFalse(message(is_external = false, has_recipient_key = true).is_e2e_encrypted)
    }

    @Test
    fun own_copy_item_type_alone_does_not_earn_the_end_to_end_badge() {
        for (item_type in listOf("sent", "scheduled", "draft")) {
            assertFalse(item_type, message(item_type = item_type).is_e2e_encrypted)
            assertFalse(item_type, message(item_type = item_type, e2e_verified = true).is_e2e_encrypted)
        }
    }

    @Test
    fun own_copy_sealed_on_this_account_is_end_to_end_encrypted() {
        for (item_type in listOf("sent", "scheduled", "draft")) {
            assertTrue(item_type, message(item_type = item_type, own_sealed = true).is_e2e_encrypted)
        }
    }

    @Test
    fun verified_body_from_outside_without_a_recipient_key_is_not_end_to_end_encrypted() {
        assertFalse(message(is_external = true, e2e_verified = true).is_e2e_encrypted)
        assertFalse(
            message(is_external = true, has_recipient_key = false, e2e_verified = true).is_e2e_encrypted,
        )
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
        assertTrue(
            message(is_external = true, has_recipient_key = true, e2e_verified = true).is_e2e_encrypted,
        )
    }

    @Test
    fun external_message_with_explicit_false_recipient_key_is_not_end_to_end_encrypted() {
        assertFalse(message(is_external = true, has_recipient_key = false).is_e2e_encrypted)
    }

    @Test
    fun thread_badge_is_transit_when_any_message_is_external() {
        val messages = listOf(
            message(id = "a", is_external = false, e2e_verified = true),
            message(id = "b", is_external = true),
        )

        assertFalse(thread_badge_is_e2e(messages))
    }

    @Test
    fun thread_badge_is_end_to_end_when_every_message_is_internal() {
        val messages = listOf(
            message(id = "a", is_external = false, e2e_verified = true),
            message(id = "b", is_external = false, e2e_verified = true),
        )

        assertTrue(thread_badge_is_e2e(messages))
    }

    @Test
    fun thread_badge_is_transit_while_no_messages_are_loaded() {
        assertFalse(thread_badge_is_e2e(emptyList()))
    }

    @Test
    fun external_pgp_message_signed_by_the_sender_is_end_to_end_encrypted() {
        val signed = message(
            is_external = true,
            has_recipient_key = null,
            pgp_encrypted = true,
            pgp_signature = org.astermail.android.crypto.PgpSignatureStatus.VALID,
        )

        assertTrue(signed.is_e2e_encrypted)
        assertTrue(signed.shows_pgp_label)
    }

    @Test
    fun external_pgp_message_without_a_valid_sender_signature_is_not_end_to_end_encrypted() {
        for (status in listOf(
            org.astermail.android.crypto.PgpSignatureStatus.NONE,
            org.astermail.android.crypto.PgpSignatureStatus.UNVERIFIED,
            org.astermail.android.crypto.PgpSignatureStatus.INVALID,
        )) {
            val received = message(is_external = true, has_recipient_key = null, pgp_encrypted = true, pgp_signature = status)

            assertFalse(status.name, received.is_e2e_encrypted)
            assertFalse(status.name, received.shows_pgp_label)
        }
    }

    @Test
    fun own_pgp_copy_keeps_the_pgp_label_without_a_signature() {
        for (item_type in listOf("sent", "scheduled")) {
            val own = message(is_external = true, pgp_encrypted = true, item_type = item_type)

            assertTrue(item_type, own.shows_pgp_label)
        }
    }

    @Test
    fun thread_badge_names_pgp_when_a_message_arrived_signed_and_pgp_encrypted() {
        val messages = listOf(
            message(id = "a", is_external = false, e2e_verified = true),
            message(
                id = "b",
                is_external = true,
                pgp_encrypted = true,
                pgp_signature = org.astermail.android.crypto.PgpSignatureStatus.VALID,
            ),
        )

        assertTrue(thread_badge_is_e2e(messages))
        assertTrue(thread_badge_is_pgp(messages))
        assertTrue(thread_is_pgp_encrypted(messages))
    }

    @Test
    fun thread_badge_stays_in_transit_for_an_unsigned_pgp_message() {
        val messages = listOf(
            message(id = "a", is_external = false, e2e_verified = true),
            message(id = "b", is_external = true, pgp_encrypted = true),
        )

        assertFalse(thread_badge_is_e2e(messages))
        assertFalse(thread_badge_is_pgp(messages))
        assertFalse(thread_is_pgp_encrypted(messages))
        assertFalse(thread_is_end_to_end_encrypted(messages))
    }

    @Test
    fun thread_badge_does_not_name_pgp_without_a_pgp_message() {
        assertFalse(thread_badge_is_pgp(listOf(message(is_external = false))))
    }

    @Test
    fun pgp_flags_survive_the_thread_message_mapper() {
        fun decrypted(status: org.astermail.android.crypto.PgpSignatureStatus) = ThreadMessageDecrypted(
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
            pgp_signature = status,
        )

        val unverified = thread_message_to_mock(decrypted(org.astermail.android.crypto.PgpSignatureStatus.UNVERIFIED))
        val valid = thread_message_to_mock(decrypted(org.astermail.android.crypto.PgpSignatureStatus.VALID))

        assertTrue(unverified.pgp_encrypted)
        assertEquals(org.astermail.android.crypto.PgpSignatureStatus.UNVERIFIED, unverified.pgp_signature)
        assertFalse(unverified.is_e2e_encrypted)
        assertTrue(valid.pgp_encrypted)
        assertEquals(org.astermail.android.crypto.PgpSignatureStatus.VALID, valid.pgp_signature)
        assertTrue(valid.is_e2e_encrypted)
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

    @Test
    fun device_evidence_survives_the_mapper_and_the_snapshot() {
        fun decrypted(e2e_verified: Boolean, own_sealed: Boolean, item_type: String) = ThreadMessageDecrypted(
            id = "m1",
            sender_name = "Someone",
            sender_email = "someone@astermail.org",
            to_label = "me",
            timestamp = "2026-08-09T10:00:00Z",
            body_text = "hello",
            body_html = null,
            is_encrypted = true,
            is_read = true,
            raw_item = ThreadMessageItem(id = "m1", item_type = item_type, is_external = false),
            e2e_verified = e2e_verified,
            own_sealed = own_sealed,
        )

        val verified = decrypted(e2e_verified = true, own_sealed = false, item_type = "received")
        val unverified = decrypted(e2e_verified = false, own_sealed = false, item_type = "received")
        val sealed = decrypted(e2e_verified = false, own_sealed = true, item_type = "sent")
        val restore = { message: ThreadMessageDecrypted ->
            org.astermail.android.mail.thread_message_of(org.astermail.android.mail.thread_snapshot_of(message))
        }

        assertTrue(thread_message_to_mock(verified).is_e2e_encrypted)
        assertFalse(thread_message_to_mock(unverified).is_e2e_encrypted)
        assertTrue(thread_message_to_mock(sealed).is_e2e_encrypted)
        assertTrue(thread_message_to_mock(restore(verified)).is_e2e_encrypted)
        assertFalse(thread_message_to_mock(restore(unverified)).is_e2e_encrypted)
        assertTrue(thread_message_to_mock(restore(sealed)).is_e2e_encrypted)
    }

    @Test
    fun snapshot_from_an_older_build_carries_no_device_evidence() {
        val legacy = org.astermail.android.mail.thread_snapshot_json.decodeFromString(
            org.astermail.android.mail.thread_snapshot_message.serializer(),
            "{\"id\":\"m1\",\"sender_name\":\"a\",\"sender_email\":\"a@astermail.org\"," +
                "\"to_label\":\"me\",\"timestamp\":\"2026-08-09T10:00:00Z\",\"body_text\":\"hello\"," +
                "\"raw_item\":{\"id\":\"m1\",\"item_type\":\"received\",\"is_external\":false}}",
        )

        assertFalse(
            thread_message_to_mock(org.astermail.android.mail.thread_message_of(legacy)).is_e2e_encrypted,
        )
    }
}
