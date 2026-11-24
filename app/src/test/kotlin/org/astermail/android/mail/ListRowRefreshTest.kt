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
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.api.mail.ThreadWithMessages
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ListRowRefreshTest {

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var mail_api: MailApi
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        session_key_store = pending_test_key_store()
        every { session_key_store.keys_settled() } returns true
        mail_api = mockk(relaxed = true)
        repo = build_repository(session_key_store, mail_api)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun sealed_item(id: String) = MailItem(
        id = id,
        item_type = "received",
        encrypted_envelope = random_b64(96),
        envelope_nonce = random_b64(16),
    )

    private fun row(
        id: String,
        subject: String = "Subject",
        sender_email: String = "sender@example.com",
        raw: MailItem = MailItem(id = id, item_type = "received"),
        is_undecryptable: Boolean = false,
        is_decrypt_pending: Boolean = false,
        is_envelope_missing: Boolean = false,
    ) = InboxItem(
        id = id,
        thread_token = "thread_$id",
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = sender_email,
        subject = subject,
        preview = if (subject.isBlank()) "" else "Preview",
        timestamp = "2026-11-23T10:00:00Z",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        raw_item = raw,
        is_undecryptable = is_undecryptable,
        is_decrypt_pending = is_decrypt_pending,
        is_envelope_missing = is_envelope_missing,
    )

    private fun missing_row(id: String) = row(
        id,
        subject = "",
        sender_email = "",
        is_decrypt_pending = true,
        is_envelope_missing = true,
    )

    @Test
    fun a_scheduled_item_without_an_envelope_is_not_refetched() {
        assertFalse(needs_envelope_backfill(MailItem(id = "s1", item_type = "scheduled")))
        assertFalse(needs_envelope_backfill(MailItem(id = "d1", item_type = "draft")))
        assertTrue(needs_envelope_backfill(MailItem(id = "r1", item_type = "received")))
    }

    @Test
    fun a_row_the_batch_cannot_fill_is_fetched_again_by_id() = runTest {
        coEvery { mail_api.batch_messages(any()) } returns
            MailItemsListResponse(items = listOf(MailItem(id = "m1", item_type = "received")))
        coEvery { mail_api.get_message("m1") } returns sealed_item("m1")

        val refilled = repo.refill_missing_envelopes(listOf(missing_row("m1")), budget_ms = 2_000L)

        coVerify { mail_api.get_message("m1") }
        val filled = refilled.getValue("m1")
        assertFalse(filled.is_envelope_missing)
        assertFalse(filled.raw_item.encrypted_envelope.isNullOrBlank())
    }

    @Test
    fun fetching_by_id_is_capped_per_pass() = runTest {
        coEvery { mail_api.batch_messages(any()) } returns MailItemsListResponse()
        coEvery { mail_api.get_message(any()) } answers { MailItem(id = firstArg(), item_type = "received") }

        repo.refill_missing_envelopes((1..12).map { missing_row("m$it") }, budget_ms = 2_000L)

        coVerify(exactly = envelope_single_refetch_cap) { mail_api.get_message(any()) }
    }

    @Test
    fun a_blank_thread_message_is_fetched_again_by_id() = runTest {
        coEvery { mail_api.get_thread_messages("t1") } returns ThreadWithMessages(
            messages = listOf(ThreadMessageItem(id = "m1", item_type = "received")),
        )
        coEvery { mail_api.get_message("m1") } returns sealed_item("m1")

        val messages = repo.fetch_thread("t1").getOrThrow()

        coVerify { mail_api.get_message("m1") }
        assertFalse(messages.single().raw_item.encrypted_envelope.isNullOrBlank())
    }

    @Test
    fun a_received_message_without_an_envelope_waits_instead_of_rendering_blank() = runTest {
        val decrypted = repo.decrypt_single_thread_message(ThreadMessageItem(id = "m1", item_type = "received"))

        assertTrue(decrypted.is_body_pending)
        assertFalse(decrypted.is_undecryptable)
    }

    @Test
    fun a_draft_without_an_envelope_is_left_alone() = runTest {
        val decrypted = repo.decrypt_single_thread_message(ThreadMessageItem(id = "d1", item_type = "draft"))

        assertFalse(decrypted.is_body_pending)
    }

    @Test
    fun an_opened_message_replaces_its_failed_row_and_keeps_the_row_state() {
        val failed = row("m1", subject = "Could not decrypt", is_undecryptable = true).copy(
            is_read = true,
            is_starred = true,
            is_trashed = true,
            labels = listOf("work"),
            tag_tokens = listOf("tag"),
            thread_message_count = 3,
        )
        val other = row("m2")
        val opened = row("m1", subject = "Hello", raw = sealed_item("m1"))

        val adopted = adopt_opened_row(listOf(failed, other), opened)

        val first = adopted.first()
        assertEquals("Hello", first.subject)
        assertFalse(first.is_undecryptable)
        assertTrue(first.is_read)
        assertTrue(first.is_starred)
        assertTrue(first.is_trashed)
        assertEquals(listOf("work"), first.labels)
        assertEquals(listOf("tag"), first.tag_tokens)
        assertEquals(3, first.thread_message_count)
        assertSame(other, adopted[1])
    }

    @Test
    fun an_opened_message_replaces_a_blank_or_loading_row() {
        val opened = row("m1", subject = "Hello", raw = sealed_item("m1"))

        assertEquals("Hello", adopt_opened_row(listOf(missing_row("m1")), opened).single().subject)
        assertEquals(
            "Hello",
            adopt_opened_row(listOf(row("m1", subject = "", sender_email = "")), opened).single().subject,
        )
    }

    @Test
    fun a_failed_open_never_replaces_a_row() {
        val good = row("m1", subject = "Hello")
        val failed_open = row("m1", subject = "Could not decrypt", is_undecryptable = true)
        val rows = listOf(good)

        assertSame(rows, adopt_opened_row(rows, failed_open))
        assertSame(rows, adopt_opened_row(rows, missing_row("m1")))
        val failed_rows = listOf(row("m1", is_undecryptable = true))
        assertSame(failed_rows, adopt_opened_row(failed_rows, failed_open))
    }

    @Test
    fun a_settled_open_leaves_a_good_row_untouched() {
        val rows = listOf(row("m1", subject = "Original"))

        assertSame(rows, adopt_opened_row(rows, row("m1", subject = "Hello", raw = sealed_item("m1"))))
    }

    @Test
    fun a_blank_thread_copy_takes_the_opened_message_whole() {
        val blank = ThreadMessageDecrypted(
            id = "m1",
            sender_name = "",
            sender_email = "",
            to_label = "me",
            timestamp = "2026-11-23T10:00:00Z",
            body_text = "",
            body_html = null,
            is_encrypted = false,
            is_read = true,
            raw_item = ThreadMessageItem(id = "m1", item_type = "received"),
        )
        val opened = blank.copy(
            sender_name = "Alice",
            sender_email = "alice@example.com",
            subject = "Hello",
            body_text = "Body",
            is_encrypted = true,
            raw_item = ThreadMessageItem(
                id = "m1",
                item_type = "received",
                encrypted_envelope = "e",
                envelope_nonce = "n",
            ),
        )

        val merged = include_opened_message(listOf(blank), opened).single()

        assertEquals("alice@example.com", merged.sender_email)
        assertEquals("Hello", merged.subject)
        assertEquals("Body", merged.body_text)
    }
}
