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

import org.astermail.android.crypto.PgpSignatureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentStatusLabelTest {

    private fun message(
        is_external: Boolean = false,
        pgp_encrypted: Boolean = false,
        pgp_signature: PgpSignatureStatus = PgpSignatureStatus.NONE,
        item_type: String = "received",
        e2e_verified: Boolean = false,
    ) = ThreadMessage(
        id = "m1",
        sender_name = "Someone",
        sender_email = "someone@example.com",
        to_label = "me",
        timestamp = 0L,
        body = "hello",
        is_external = is_external,
        pgp_encrypted = pgp_encrypted,
        pgp_signature = pgp_signature,
        item_type = item_type,
        e2e_verified = e2e_verified,
    )

    private fun attachment(id: String = "a1", is_unverified: Boolean = true) = MessageAttachment(
        id = id,
        filename = "screenshot.png",
        content_type = "image/png",
        size_bytes = 37_000L,
        is_unverified = is_unverified,
    )

    @Test
    fun outside_mail_in_transit_has_nothing_to_verify() {
        val msg = message(is_external = true)

        assertFalse(msg.is_e2e_encrypted)
        assertFalse(msg.expects_verified_attachments)
        assertFalse(attachment_shows_unverified(msg, attachment()))
    }

    @Test
    fun outside_mail_in_transit_drops_the_label_when_attachments_join() {
        val joined = thread_message_with_attachments(message(is_external = true), listOf(attachment()))

        assertEquals(1, joined.attachments.size)
        assertFalse(joined.attachments.single().is_unverified)
        assertEquals("screenshot.png", joined.attachments.single().filename)
    }

    @Test
    fun unconfirmed_internal_mail_has_nothing_to_verify() {
        val msg = message(is_external = false, e2e_verified = false)

        assertFalse(attachment_shows_unverified(msg, attachment()))
    }

    @Test
    fun end_to_end_mail_keeps_the_label_on_an_uncovered_attachment() {
        val msg = message(e2e_verified = true)

        assertTrue(msg.is_e2e_encrypted)
        assertTrue(attachment_shows_unverified(msg, attachment()))
        val joined = thread_message_with_attachments(msg, listOf(attachment()))
        assertTrue(joined.attachments.single().is_unverified)
    }

    @Test
    fun end_to_end_mail_never_labels_a_covered_attachment() {
        val msg = message(e2e_verified = true)
        val covered = attachment(is_unverified = false)

        assertFalse(attachment_shows_unverified(msg, covered))
        val joined = thread_message_with_attachments(msg, listOf(covered))
        assertSame(covered, joined.attachments.single())
    }

    @Test
    fun signed_pgp_mail_keeps_the_label_on_an_uncovered_attachment() {
        val msg = message(is_external = true, pgp_encrypted = true, pgp_signature = PgpSignatureStatus.VALID)

        assertTrue(attachment_shows_unverified(msg, attachment()))
    }

    @Test
    fun a_failed_signature_keeps_the_label() {
        val msg = message(is_external = true, pgp_encrypted = true, pgp_signature = PgpSignatureStatus.INVALID)

        assertFalse(msg.is_e2e_encrypted)
        assertTrue(msg.expects_verified_attachments)
        assertTrue(attachment_shows_unverified(msg, attachment()))
    }

    @Test
    fun an_unsigned_or_unchecked_signature_has_nothing_to_verify() {
        listOf(PgpSignatureStatus.NONE, PgpSignatureStatus.UNVERIFIED).forEach { status ->
            val msg = message(is_external = true, pgp_signature = status)

            assertFalse(attachment_shows_unverified(msg, attachment()))
        }
    }

    @Test
    fun each_attachment_is_judged_on_its_own() {
        val msg = message(e2e_verified = true)
        val joined = thread_message_with_attachments(
            msg,
            listOf(attachment("a1", is_unverified = true), attachment("a2", is_unverified = false)),
        )

        assertEquals(listOf(true, false), joined.attachments.map { it.is_unverified })
    }

    @Test
    fun the_preview_keeps_the_status_decided_for_the_message() {
        val source = java.io.File("src/main/kotlin/org/astermail/android/ui/mail/mail_detail_screen.kt").readText()

        assertTrue(source.contains("preview_attachment = resolved_att.copy(is_unverified = att.is_unverified)"))
    }
}
