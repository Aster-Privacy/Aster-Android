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

import io.mockk.mockk
import org.astermail.android.crypto.SentEncryptionScope
import org.astermail.android.mail.ThreadMessageDecrypted
import org.astermail.android.mail.thread_message_of
import org.astermail.android.mail.thread_snapshot_of
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentCopyEncryptionBadgeTest {

    private fun sent(
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        scope: SentEncryptionScope? = null,
        item_type: String = "sent",
    ) = ThreadMessage(
        id = "s1",
        sender_name = "Me",
        sender_email = "me@astermail.org",
        to_label = "them",
        to_addresses = to,
        cc_addresses = cc,
        bcc_addresses = bcc,
        timestamp = 0L,
        body = "hello",
        item_type = item_type,
        sent_encryption_scope = scope,
    )

    @Test
    fun sent_copy_to_internal_recipients_is_end_to_end() {
        assertTrue(sent(listOf("alice@astermail.org"), cc = listOf("bob@aster.cx")).is_e2e_encrypted)
    }

    @Test
    fun sent_copy_to_outside_recipient_without_evidence_is_not_end_to_end() {
        assertFalse(sent(listOf("bob@example.com")).is_e2e_encrypted)
        assertFalse(sent(listOf("alice@astermail.org"), bcc = listOf("bob@example.com")).is_e2e_encrypted)
        assertFalse(sent(listOf("bob@example.com"), item_type = "scheduled").is_e2e_encrypted)
    }

    @Test
    fun sent_copy_with_outside_evidence_shows_the_pgp_label() {
        val message = sent(listOf("bob@example.com"), scope = SentEncryptionScope.OUTSIDE)
        assertTrue(message.is_e2e_encrypted)
        assertTrue(message.shows_pgp_label)
        assertTrue(thread_is_pgp_encrypted(listOf(message)))
        assertTrue(thread_reaches_outside_with_pgp(listOf(message)))
    }

    @Test
    fun internal_scope_is_end_to_end_without_the_pgp_label() {
        val message = sent(listOf("alice@astermail.org"), scope = SentEncryptionScope.INTERNAL)
        assertTrue(message.is_e2e_encrypted)
        assertFalse(message.shows_pgp_label)
        assertFalse(thread_reaches_outside_with_pgp(listOf(message)))
    }

    @Test
    fun one_unencrypted_sent_copy_clears_the_thread_badge() {
        val messages = listOf(
            sent(listOf("bob@example.com"), scope = SentEncryptionScope.OUTSIDE),
            sent(listOf("bob@example.com")),
        )
        assertFalse(thread_is_end_to_end_encrypted(messages))
        assertFalse(thread_is_pgp_encrypted(messages))
    }

    @Test
    fun received_mail_keeps_the_old_rule() {
        assertTrue(sent(listOf("bob@example.com"), item_type = "received").is_e2e_encrypted)
    }

    private fun decrypted(pgp_encrypted: Boolean, scope: SentEncryptionScope?) = ThreadMessageDecrypted(
        id = "m1",
        sender_name = "Me",
        sender_email = "me@astermail.org",
        to_label = "them",
        timestamp = "2026-11-08T10:00:00Z",
        body_text = "hello",
        body_html = null,
        is_encrypted = true,
        is_read = true,
        raw_item = mockk(relaxed = true),
        pgp_encrypted = pgp_encrypted,
        sent_encryption_scope = scope,
    )

    @Test
    fun snapshot_keeps_scope_and_pgp_flag() {
        val restored = thread_message_of(thread_snapshot_of(decrypted(true, SentEncryptionScope.OUTSIDE)))
        assertTrue(restored.pgp_encrypted)
        assertEquals(SentEncryptionScope.OUTSIDE, restored.sent_encryption_scope)
    }

    @Test
    fun snapshot_from_an_older_build_drops_unproven_pgp_claims() {
        val legacy = thread_snapshot_of(decrypted(true, SentEncryptionScope.OUTSIDE)).copy(encryption_rules = 0)
        val restored = thread_message_of(legacy)
        assertFalse(restored.pgp_encrypted)
        assertNull(restored.sent_encryption_scope)
    }

    @Test
    fun unknown_scope_name_is_ignored() {
        val snapshot = thread_snapshot_of(decrypted(false, null)).copy(sent_encryption_scope = "SOMETHING")
        assertNull(thread_message_of(snapshot).sent_encryption_scope)
    }
}
