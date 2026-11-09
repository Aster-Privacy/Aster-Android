//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.SentCopySeal
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SealedSentCopyKeyRefreshTest {

    private val passphrase = "sealed sent copy passphrase"
    private val stale_mobile_key = """{"kty":"OKP","crv":"X25519","d":"stale"}"""
    private val envelope_json = """{"subject":"Laptop reply","body_text":"Sent from the web app"}"""

    private val web_key by lazy {
        PgpKeyGenerator.generate("Owner", "owner@astermail.org", passphrase.toCharArray())
    }

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var auth_repository: AuthRepository
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
        session_key_store = SessionKeyStore(null)
        session_key_store.put_passphrase(passphrase.toByteArray(Charsets.UTF_8))
        auth_repository = mockk(relaxed = true)
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
            auth_repository = dagger.Lazy { auth_repository },
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun sealed_sent_copy(): Pair<String, String> =
        SentCopySeal.seal(envelope_json, web_key.armored_private_key, passphrase.toCharArray())
            ?: throw AssertionError("seal failed")

    @Test
    fun candidates_keep_previous_pgp_keys_when_the_current_key_is_not_pgp() {
        val old_pgp = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nold\n-----END PGP PRIVATE KEY BLOCK-----"
        assertEquals(listOf(old_pgp), own_pgp_key_candidates(stale_mobile_key, listOf(old_pgp, old_pgp)))
        assertTrue(own_pgp_key_candidates(stale_mobile_key, null).isEmpty())
        assertTrue(own_pgp_key_candidates(null, null).isEmpty())
    }

    @Test
    fun opens_with_a_previous_pgp_key_when_the_current_key_is_not_pgp() = runBlocking {
        session_key_store.put_identity_key(stale_mobile_key)
        session_key_store.put_previous_keys(listOf(web_key.armored_private_key))
        val (ciphertext, nonce) = sealed_sent_copy()

        val result = repo.decrypt_envelope_with_heal(ciphertext, nonce)

        assertEquals("Sent from the web app", result.envelope?.body_text)
        assertFalse(result.envelope?.is_pgp_placeholder == true)
        coVerify(exactly = 0) { auth_repository.try_refresh_vault_keys() }
    }

    @Test
    fun refreshes_the_vault_and_opens_a_copy_sealed_to_a_newer_web_key() = runBlocking {
        session_key_store.put_identity_key(stale_mobile_key)
        session_key_store.put_previous_keys(emptyList())
        coEvery { auth_repository.try_refresh_vault_keys() } answers {
            session_key_store.put_identity_key(web_key.armored_private_key)
            true
        }
        val (ciphertext, nonce) = sealed_sent_copy()

        val result = repo.decrypt_envelope_with_heal(ciphertext, nonce)

        assertEquals("Sent from the web app", result.envelope?.body_text)
        assertFalse(result.envelope?.is_pgp_placeholder == true)
        coVerify(exactly = 1) { auth_repository.try_refresh_vault_keys() }
    }

    @Test
    fun keeps_a_retryable_placeholder_when_the_vault_has_no_matching_key() = runBlocking {
        session_key_store.put_identity_key(stale_mobile_key)
        session_key_store.put_previous_keys(emptyList())
        coEvery { auth_repository.try_refresh_vault_keys() } returns false
        val (ciphertext, nonce) = sealed_sent_copy()

        val result = repo.decrypt_envelope_with_heal(ciphertext, nonce)

        assertTrue(result.envelope?.is_pgp_placeholder == true)
        assertTrue(result.envelope?.pgp_encrypted == true)
        assertFalse(result.heal_pending)
    }
}
