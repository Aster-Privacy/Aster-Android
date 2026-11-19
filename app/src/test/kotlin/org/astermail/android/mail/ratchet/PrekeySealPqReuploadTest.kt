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

package org.astermail.android.mail.ratchet

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ratchet.PqSecretResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.StorePqSecretRequest
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrekeySealPqReuploadTest {

    private val sync_key = ByteArray(32) { (it + 1).toByte() }
    private val seal_raw = ByteArray(32) { (it + 40).toByte() }

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun seal(): PrekeySeal {
        val keys = SessionKeyStore()
        keys.put_prekey_seal(seal_raw.copyOf(), true)
        return PrekeySeal.from_store(keys)
    }

    private fun older_copy(key_id: Int, secret: ByteArray): PqSecretResponse {
        val wrapped = checkNotNull(EcOneTimeSecretWrap.wrap(secret, sync_key))
        return PqSecretResponse(key_id, wrapped.encrypted_secret, wrapped.secret_nonce)
    }

    @Test
    fun an_older_pq_copy_opens_after_migration_and_is_stored_again_under_the_vault_seal() = runTest {
        val secret = ByteArray(32) { 8 }
        val api = mockk<RatchetApi>(relaxed = true)
        val stored = slot<StorePqSecretRequest>()
        coEvery { api.store_pq_secret(capture(stored)) } returns true

        val opened = open_and_reseal_pq_secret(api, seal(), 9, older_copy(9, secret), writes_ready = true) {
            listOf(sync_key.copyOf())
        }

        assertArrayEquals(secret, opened)
        assertEquals(9, stored.captured.key_id)
        assertArrayEquals(
            secret,
            seal().open(PrekeySealKind.PQ, 9, stored.captured.encrypted_secret, stored.captured.secret_nonce),
        )
    }

    private fun constant_state_key(): ByteArray = org.astermail.android.crypto.ratchet.RatchetCrypto.hkdf_sha256(
        ikm = org.astermail.android.crypto.StorageWriteKey.public_mail_kek_raws().first(),
        salt = "Aster Mail_Ratchet_State_Encryption".toByteArray(Charsets.UTF_8),
        info = "ratchet_state_key".toByteArray(Charsets.UTF_8),
        length = 32,
    )

    @Test
    fun a_pq_copy_under_a_public_constant_state_key_is_neither_opened_nor_stored_again() = runTest {
        val secret = ByteArray(32) { 6 }
        val constant_key = constant_state_key()
        val wrapped = checkNotNull(EcOneTimeSecretWrap.wrap(secret, constant_key))
        val api = mockk<RatchetApi>(relaxed = true)

        val opened = open_and_reseal_pq_secret(
            api,
            seal(),
            9,
            PqSecretResponse(9, wrapped.encrypted_secret, wrapped.secret_nonce),
            writes_ready = true,
        ) { listOf(constant_state_key(), sync_key.copyOf()) }

        assertNull("a secret opened under a public constant must not be trusted", opened)
        coVerify(exactly = 0) { api.store_pq_secret(any()) }
    }

    @Test
    fun an_older_pq_copy_is_not_stored_again_before_the_server_takes_the_new_format() = runTest {
        val secret = ByteArray(32) { 3 }
        val api = mockk<RatchetApi>(relaxed = true)

        val opened = open_and_reseal_pq_secret(api, seal(), 9, older_copy(9, secret), writes_ready = false) {
            listOf(sync_key.copyOf())
        }

        assertArrayEquals(secret, opened)
        coVerify(exactly = 0) { api.store_pq_secret(any()) }
    }

    @Test
    fun a_pq_copy_already_under_the_vault_seal_is_not_stored_again() = runTest {
        val secret = ByteArray(32) { 5 }
        val sealed = checkNotNull(seal().seal(PrekeySealKind.PQ, 9, secret))
        val api = mockk<RatchetApi>(relaxed = true)

        val opened = open_and_reseal_pq_secret(
            api,
            seal(),
            9,
            PqSecretResponse(9, sealed.encrypted_secret, sealed.secret_nonce),
            writes_ready = true,
        ) { emptyList() }

        assertArrayEquals(secret, opened)
        coVerify(exactly = 0) { api.store_pq_secret(any()) }
    }

    @Test
    fun an_owner_key_pinned_during_a_send_is_trusted_again_once_that_send_is_over() {
        assertTrue(owner_key_pinned_during_send(1_000L, 1_000L))
        assertTrue(owner_key_pinned_during_send(1_000L, 1_000L + OWNER_KEY_SEND_PIN_WINDOW_MS - 1))
        assertFalse(owner_key_pinned_during_send(1_000L, 1_000L + OWNER_KEY_SEND_PIN_WINDOW_MS))
        assertFalse(owner_key_pinned_during_send(null, 1_000L))
        assertFalse(owner_key_pinned_during_send(5_000L, 1_000L))
    }
}
