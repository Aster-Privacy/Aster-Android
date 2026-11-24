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

import io.mockk.every
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.MailItem
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DecryptPendingInboxTest {

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        session_key_store = pending_test_key_store()
        repo = build_repository(session_key_store)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun waiting_row(): InboxItem = InboxItem(
        id = "m1",
        thread_token = null,
        thread_message_count = 1,
        sender_name = "",
        sender_email = "",
        subject = "",
        preview = "",
        timestamp = "",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        raw_item = MailItem(
            id = "m1",
            item_type = "received",
            encrypted_envelope = random_b64(96),
            envelope_nonce = random_b64(16),
        ),
        is_undecryptable = true,
        is_decrypt_pending = true,
    )

    @Test
    fun an_inbox_row_decrypted_before_the_keys_are_ready_stays_a_loading_row() = runTest {
        every { session_key_store.keys_settled() } returns false

        val row = repo.settle_pending_rows(listOf(waiting_row())).getValue("m1")

        assertTrue(row.is_decrypt_pending)
        assertTrue(row.is_envelope_settling)
        assertTrue(is_envelope_pending_row(row))
        assertFalse(row.is_envelope_missing)
    }

    @Test
    fun an_inbox_row_that_really_cannot_be_opened_settles_as_failed() = runTest {
        every { session_key_store.keys_settled() } returns true

        val row = repo.settle_pending_rows(listOf(waiting_row())).getValue("m1")

        assertTrue(row.is_undecryptable)
        assertFalse(row.is_decrypt_pending)
        assertFalse(row.is_envelope_settling)
        val replaced = replace_settled_items(listOf(waiting_row()), mapOf("m1" to row)).single()
        assertFalse(replaced.is_decrypt_pending)
        assertTrue(replaced.is_undecryptable)
    }
}
