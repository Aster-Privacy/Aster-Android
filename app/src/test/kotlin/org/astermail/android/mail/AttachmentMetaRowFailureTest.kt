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
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttachmentMetaRowFailureTest {

    private class FatalTestError : Error("fatal")

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getMimeDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_data_kek() } returns null
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        every { session_key_store.get_passphrase() } returns null
        every { session_key_store.mail_legacy_retired() } returns false
        repo = MailRepository(
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
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun random_bytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private val legacy_row = b64("not an attachment record".toByteArray(Charsets.UTF_8))

    @Test
    fun a_sealed_nonce_row_whose_open_fails_unexpectedly_becomes_a_placeholder() {
        every { session_key_store.get_identity_key() } throws IllegalStateException("key store unavailable")
        val nonce = random_bytes(12).also { it[0] = 7 }

        val meta = runCatching { repo.decrypt_attachment_meta(b64(random_bytes(64)), b64(nonce), size_bytes = 42L) }

        assertTrue("one bad sealed-nonce row should not fail the attachment list", meta.isSuccess)
        assertTrue("the bad row should show as a placeholder", meta.getOrThrow().is_placeholder)
        assertEquals("the bad row should carry no key", "", meta.getOrThrow().session_key)
        assertEquals(42L, meta.getOrThrow().size_bytes)
    }

    private fun level_two_open_throws(error: Throwable) {
        mockkObject(LevelTwoEnvelope)
        every { LevelTwoEnvelope.open_with_store(any(), any(), any()) } throws error
        every { LevelTwoEnvelope.read_keys(any()) } returns listOf(ByteArray(32) { 3 })
        every { LevelTwoEnvelope.open_b64(any(), any()) } throws error
    }

    @Test
    fun a_fatal_error_while_opening_a_legacy_row_is_not_swallowed() {
        level_two_open_throws(FatalTestError())

        assertThrows(FatalTestError::class.java) {
            repo.decrypt_attachment_meta(legacy_row, null)
        }
    }

    @Test
    fun cancellation_while_opening_a_legacy_row_is_not_swallowed() {
        level_two_open_throws(CancellationException("stopped"))

        assertThrows(CancellationException::class.java) {
            repo.decrypt_attachment_meta(legacy_row, null)
        }
    }

    @Test
    fun an_ordinary_failure_while_opening_a_legacy_row_still_becomes_a_placeholder() {
        level_two_open_throws(IllegalArgumentException("bad row"))

        val meta = repo.decrypt_attachment_meta(legacy_row, null)

        assertTrue(meta.is_placeholder)
        assertEquals("", meta.session_key)
    }
}
