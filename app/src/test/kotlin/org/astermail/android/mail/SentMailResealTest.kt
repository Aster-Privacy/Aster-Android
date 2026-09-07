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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.AttachmentListResponse
import org.astermail.android.api.mail.AttachmentResponse
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.api.mail.UpdateAttachmentMetaRequest
import org.astermail.android.api.mail.UpdateMailItemEnvelopeRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentMailResealTest {
    private val old_pass = "old passphrase 123".toByteArray()
    private val new_pass = "new passphrase 456".toByteArray()

    @Test
    fun seal_and_open_round_trip_with_the_same_passphrase() {
        val sealed = SentMailResealCrypto.seal("{\"subject\":\"hi\"}".toByteArray(), old_pass)
        assertArrayEquals("{\"subject\":\"hi\"}".toByteArray(), SentMailResealCrypto.open(sealed, old_pass))
        assertNull(SentMailResealCrypto.open(sealed, new_pass))
    }

    @Test
    fun sentinel_nonce_is_the_single_one_byte() {
        assertTrue(SentMailResealCrypto.is_sentinel_nonce(SentMailResealCrypto.sentinel_nonce_b64()))
        assertFalse(SentMailResealCrypto.is_sentinel_nonce(Base64.getEncoder().encodeToString(ByteArray(12))))
        assertFalse(SentMailResealCrypto.is_sentinel_nonce(null))
        assertFalse(SentMailResealCrypto.is_sentinel_nonce("not base64!!"))
    }

    @Test
    fun rewrites_old_copies_and_counts_unreadable_and_failed_ones() = runTest {
        val other_pass = "someone else".toByteArray()
        val items = listOf(
            sent_item("a", SentMailResealCrypto.seal("a".toByteArray(), old_pass)),
            sent_item("b", SentMailResealCrypto.seal("b".toByteArray(), other_pass)),
            sent_item("c", SentMailResealCrypto.seal("c".toByteArray(), new_pass)),
            sent_item("d", SentMailResealCrypto.seal("d".toByteArray(), old_pass)),
            MailItem(
                id = "e",
                item_type = "sent",
                encrypted_envelope = "x",
                envelope_nonce = Base64.getEncoder().encodeToString(ByteArray(12)),
            ),
        )
        val api = mockk<MailApi>()
        coEvery { api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = items, has_more = false)
        val written = mutableMapOf<String, UpdateMailItemEnvelopeRequest>()
        coEvery { api.update_envelope("d", any()) } throws IllegalStateException("server rejected")
        coEvery { api.update_envelope("a", any()) } answers { written[firstArg()] = secondArg() }

        val summary = SentMailResealer(api).run(old_pass, new_pass)

        assertEquals(SentMailResealSummary(checked = 5, rewritten = 1, unreadable = 1, failed = 1), summary)
        assertTrue(SentMailResealCrypto.is_sentinel_nonce(written.getValue("a").envelope_nonce))
        assertArrayEquals("a".toByteArray(), SentMailResealCrypto.open(written.getValue("a").encrypted_envelope, new_pass))
    }

    @Test
    fun reseals_sender_attachment_meta_with_the_envelope() = runTest {
        val api = mockk<MailApi>()
        coEvery { api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(
                items = listOf(sent_item("a", SentMailResealCrypto.seal("a".toByteArray(), old_pass)).copy(has_attachments = true)),
                has_more = false,
            )
        coEvery { api.update_envelope(any(), any()) } returns Unit
        coEvery { api.list_attachments("a") } returns AttachmentListResponse(
            attachments = listOf(
                AttachmentResponse(
                    id = "att-1",
                    mail_item_id = "a",
                    encrypted_data = "",
                    data_nonce = "",
                    size_bytes = 0,
                    encrypted_meta = SentMailResealCrypto.seal("{\"filename\":\"f.txt\"}".toByteArray(), old_pass),
                    meta_nonce = Base64.getEncoder().encodeToString(ByteArray(12)),
                ),
            ),
        )
        val meta = slot<UpdateAttachmentMetaRequest>()
        coEvery { api.update_attachment_meta("att-1", capture(meta)) } returns Unit

        val summary = SentMailResealer(api).run(old_pass, new_pass)

        assertEquals(1, summary.rewritten)
        assertEquals(0, summary.failed)
        assertEquals(12, Base64.getDecoder().decode(meta.captured.meta_nonce).size)
        assertArrayEquals("{\"filename\":\"f.txt\"}".toByteArray(), SentMailResealCrypto.open(meta.captured.encrypted_meta, new_pass))
        coVerify(exactly = 1) { api.update_attachment_meta("att-1", any()) }
    }

    private fun sent_item(id: String, sealed: String) = MailItem(
        id = id,
        item_type = "sent",
        encrypted_envelope = sealed,
        envelope_nonce = SentMailResealCrypto.sentinel_nonce_b64(),
    )
}
