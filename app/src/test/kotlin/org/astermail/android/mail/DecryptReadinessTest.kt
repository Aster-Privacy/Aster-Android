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

import io.ktor.client.plugins.ServerResponseException
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.network.TransientFailureSignal
import org.astermail.android.mail.ratchet.RATCHET_UNDECRYPTABLE_SENTINEL
import org.astermail.android.storage.SessionKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecryptReadinessTest {

    private val ready = DecryptReadiness(keys_settled = true, key_epoch = 3L, failure_epoch = 9L)

    @Test
    fun a_failure_with_settled_keys_and_no_transient_signal_is_definitive() {
        assertEquals(DecryptVerdict.failed, classify_decrypt_failure(ready, ready))
    }

    @Test
    fun a_failure_while_keys_are_not_ready_stays_pending() {
        val locked = ready.copy(keys_settled = false)
        assertEquals(DecryptVerdict.pending, classify_decrypt_failure(locked, ready))
        assertEquals(DecryptVerdict.pending, classify_decrypt_failure(ready, locked))
    }

    @Test
    fun a_failure_while_keys_changed_during_the_attempt_stays_pending() {
        assertEquals(DecryptVerdict.pending, classify_decrypt_failure(ready, ready.copy(key_epoch = 4L)))
    }

    @Test
    fun a_failure_after_a_transient_network_error_stays_pending() {
        assertEquals(DecryptVerdict.pending, classify_decrypt_failure(ready, ready.copy(failure_epoch = 10L)))
        assertEquals(DecryptVerdict.pending, classify_decrypt_failure(ready, ready, transient_error = true))
    }

    @Test
    fun transient_errors_are_recognized_through_the_cause_chain() {
        assertTrue(is_transient_decrypt_error(IOException("offline")))
        assertTrue(is_transient_decrypt_error(IllegalStateException("wrapped", IOException("offline"))))
        assertTrue(is_transient_decrypt_error(CancellationException("stopped")))
        assertTrue(is_transient_decrypt_error(java.security.KeyStoreException("busy")))
        val server_error = mockk<ServerResponseException>(relaxed = true)
        every { server_error.response.status.value } returns 503
        assertTrue(is_transient_decrypt_error(server_error))
        assertFalse(is_transient_decrypt_error(javax.crypto.AEADBadTagException("tag")))
        assertFalse(is_transient_decrypt_error(IllegalArgumentException("bad envelope")))
        assertFalse(is_transient_decrypt_error(null))
    }

    @Test
    fun the_transient_signal_counts_only_retryable_statuses() {
        val before = TransientFailureSignal.epoch()
        TransientFailureSignal.note_status(404)
        TransientFailureSignal.note_status(400)
        assertEquals(before, TransientFailureSignal.epoch())
        TransientFailureSignal.note_status(503)
        TransientFailureSignal.note_status(429)
        TransientFailureSignal.note_status(401)
        assertEquals(before + 3, TransientFailureSignal.epoch())
    }

    @Test
    fun a_ratchet_sentinel_is_kept_only_when_the_failure_is_definitive() {
        assertEquals(RATCHET_UNDECRYPTABLE_SENTINEL, settle_ratchet_result(RATCHET_UNDECRYPTABLE_SENTINEL, ready, ready))
        assertNull(settle_ratchet_result(RATCHET_UNDECRYPTABLE_SENTINEL, ready.copy(keys_settled = false), ready))
        assertNull(settle_ratchet_result(RATCHET_UNDECRYPTABLE_SENTINEL, ready, ready.copy(failure_epoch = 10L)))
        assertEquals("hello", settle_ratchet_result("hello", ready.copy(keys_settled = false), ready))
    }

    @Test
    fun readiness_follows_the_session_key_store() {
        val store = SessionKeyStore()
        assertTrue(capture_decrypt_readiness(store).keys_settled)
        val epoch = store.key_readiness_epoch()
        val ticket = store.begin_account_key_load()
        assertFalse(capture_decrypt_readiness(store).keys_settled)
        store.finish_account_key_load(ticket)
        assertTrue(capture_decrypt_readiness(store).keys_settled)
        assertTrue(store.key_readiness_epoch() >= epoch)
    }

    private fun row(id: String, pending: Boolean, failed: Boolean = true) = InboxItem(
        id = id,
        thread_token = null,
        thread_message_count = 1,
        sender_name = if (pending) "Loading" else "Alice",
        sender_email = "",
        subject = if (pending) "Loading" else "Hi",
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
        raw_item = MailItem(id = id),
        is_undecryptable = failed,
        is_decrypt_pending = pending,
    )

    @Test
    fun a_pending_row_is_replaced_only_once_it_settles() {
        val pending = row("a", pending = true).copy(is_read = true)
        val other = row("b", pending = false, failed = false)
        val still_pending = replace_settled_items(listOf(pending, other), mapOf("a" to row("a", pending = true)))
        assertEquals(listOf(pending, other), still_pending)
        val opened = row("a", pending = false, failed = false)
        val settled = replace_settled_items(listOf(pending, other), mapOf("a" to opened, "b" to row("b", pending = true)))
        assertEquals("Alice", settled[0].sender_name)
        assertTrue(settled[0].is_read)
        assertEquals(other, settled[1])
        assertEquals(setOf("a"), envelope_pending_ids(listOf(pending, other)))
    }

    @Test
    fun a_definitive_failure_replaces_a_pending_row() {
        val pending = row("a", pending = true)
        val failed = row("a", pending = false).copy(sender_name = "Encrypted", subject = "Message couldn't be decrypted")
        val settled = replace_settled_items(listOf(pending), mapOf("a" to failed)).single()
        assertTrue(settled.is_undecryptable)
        assertFalse(settled.is_decrypt_pending)
        assertEquals("Message couldn't be decrypted", settled.subject)
    }
}
