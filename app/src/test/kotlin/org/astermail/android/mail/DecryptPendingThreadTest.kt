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
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.api.network.TransientFailureSignal
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DecryptPendingThreadTest {

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

    private fun sealed_message() = ThreadMessageItem(
        id = "m1",
        item_type = "received",
        encrypted_envelope = random_b64(96),
        envelope_nonce = random_b64(16),
    )

    @Test
    fun a_message_opened_before_the_keys_are_ready_waits_instead_of_failing() = runTest {
        every { session_key_store.keys_settled() } returns false

        val decrypted = repo.decrypt_single_thread_message(sealed_message())

        assertFalse("keys still loading must not show the failure panel", decrypted.is_undecryptable)
        assertTrue("the body should wait for the keys", decrypted.is_body_pending)
    }

    @Test
    fun a_message_whose_keys_arrive_mid_attempt_waits_for_a_second_attempt() = runTest {
        every { session_key_store.keys_settled() } returns true
        every { session_key_store.key_readiness_epoch() } returnsMany listOf(5L, 6L)

        val decrypted = repo.decrypt_single_thread_message(sealed_message())

        assertFalse(decrypted.is_undecryptable)
        assertTrue(decrypted.is_body_pending)
    }

    @Test
    fun a_transient_key_fetch_failure_waits_instead_of_failing() = runTest {
        every { session_key_store.keys_settled() } returns true
        every { session_key_store.get_identity_key() } answers {
            TransientFailureSignal.note()
            null
        }

        val decrypted = repo.decrypt_single_thread_message(sealed_message())

        assertFalse(decrypted.is_undecryptable)
        assertTrue(decrypted.is_body_pending)
    }

    @Test
    fun a_cancelled_attempt_waits_instead_of_failing() = runTest {
        every { session_key_store.keys_settled() } returns true
        every { session_key_store.mail_legacy_retired() } throws CancellationException("left the screen")

        val decrypted = repo.decrypt_single_thread_message(sealed_message())

        assertFalse(decrypted.is_undecryptable)
        assertTrue(decrypted.is_body_pending)
    }

    @Test
    fun a_message_that_really_cannot_be_opened_still_says_so() = runTest {
        every { session_key_store.keys_settled() } returns true

        val decrypted = repo.decrypt_single_thread_message(sealed_message())

        assertTrue("a definitive failure must stay visible", decrypted.is_undecryptable)
        assertFalse(decrypted.is_body_pending)
    }
}

internal fun random_b64(size: Int): String =
    java.util.Base64.getEncoder().encodeToString(ByteArray(size).also { SecureRandom().nextBytes(it) })

internal fun pending_test_key_store(): SessionKeyStore {
    mockkStatic(android.util.Base64::class)
    every { android.util.Base64.decode(any<String>(), any()) } answers {
        java.util.Base64.getMimeDecoder().decode(firstArg<String>())
    }
    every { android.util.Base64.encodeToString(any(), any()) } answers {
        java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
    }
    val store = mockk<SessionKeyStore>(relaxed = true)
    every { store.get_data_kek() } returns null
    every { store.get_decrypt_keks() } returns emptyList()
    every { store.get_passphrase() } returns null
    every { store.get_identity_key() } returns null
    every { store.mail_legacy_retired() } returns false
    every { store.key_readiness_epoch() } returns 5L
    return store
}

internal fun build_repository(session_key_store: SessionKeyStore): MailRepository = MailRepository(
    mail_api = mockk(relaxed = true),
    send_api = mockk(relaxed = true),
    snooze_api = mockk(relaxed = true),
    labels_api = mockk(relaxed = true),
    keys_api = mockk(relaxed = true),
    session_key_store = session_key_store,
    scheduled_api = mockk(relaxed = true),
    ratchet_decryptor = mockk(relaxed = true),
    ratchet_encryptor = mockk(relaxed = true),
    ratchet_plaintext_cache = mockk(relaxed = true),
    system_folder_bootstrap = mockk(relaxed = true),
    pending_send_dao_provider = dagger.Lazy { mockk(relaxed = true) },
    message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
    thread_snapshot_dao_provider = dagger.Lazy { mockk(relaxed = true) },
    context = mockk(relaxed = true),
    auth_repository = dagger.Lazy { mockk<AuthRepository>(relaxed = true) },
)
