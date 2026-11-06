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

package org.astermail.android.crypto

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.encryption.EncryptionApi
import org.astermail.android.api.encryption.ExternalPgpKeyInfo
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExternalPgpKeyStoreTest {

    private lateinit var vector: JSONObject
    private lateinit var session: SessionKeyStore
    private lateinit var api: EncryptionApi

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Before
    fun set_up() {
        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("external_pgp_key_vectors.json"),
        )
        vector = JSONObject(stream.bufferedReader().use { it.readText() })
        session = SessionKeyStore(null)
        session.put_user_id(vector.getString("user_id"))
        session.put_data_kek(hex(vector.getString("data_kek_hex")))
        api = mockk(relaxed = true)
    }

    private fun row(published: Boolean, wrapped: String = vector.getString("wrapped_private_key")) =
        ExternalPgpKeyInfo(
            fingerprint = vector.getString("fingerprint"),
            key_id = vector.getString("key_id"),
            algorithm = vector.getString("algorithm"),
            key_size = 256,
            public_key_armored = vector.getString("public_key_armored"),
            wrapped_private_key = wrapped,
            created_at = "2026-01-01T00:00:00Z",
            published = published,
        )

    private fun store() = ExternalPgpKeyStore(session, dagger.Lazy { api })

    @Test
    fun a_published_key_is_used_for_signing_and_decryption() = runTest {
        coEvery { api.get_external_pgp_key() } returns row(published = true)
        val store = store()
        store.load()

        assertNotNull(store.signing_key_if_ready())
        assertEquals(1, store.decryption_keys().size)
        assertEquals(true, store.info.value?.published)
    }

    @Test
    fun an_unpublished_key_still_decrypts_but_never_signs() = runTest {
        coEvery { api.get_external_pgp_key() } returns row(published = false)
        val store = store()
        store.load()

        assertNull(store.signing_key_if_ready())
        assertEquals(1, store.decryption_keys().size)
    }

    @Test
    fun no_external_key_means_no_signer_and_no_keys() = runTest {
        coEvery { api.get_external_pgp_key() } returns null
        val store = store()
        store.load()

        assertNull(store.info.value)
        assertNull(store.signing_key_if_ready())
        assertTrue(store.decryption_keys().isEmpty())
    }

    @Test
    fun a_blob_that_fails_to_open_is_skipped_silently() = runTest {
        val bytes = java.util.Base64.getDecoder().decode(vector.getString("wrapped_private_key"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        coEvery { api.get_external_pgp_key() } returns row(true, java.util.Base64.getEncoder().encodeToString(bytes))
        val store = store()
        store.load()

        assertNull(store.signing_key_if_ready())
        assertTrue(store.decryption_keys().isEmpty())
        assertNotNull(store.info.value)
    }

    @Test
    fun the_key_is_locked_without_a_data_kek() = runTest {
        session = SessionKeyStore(null)
        session.put_user_id(vector.getString("user_id"))
        coEvery { api.get_external_pgp_key() } returns row(published = true)
        val store = store()
        store.load()

        assertNull(store.signing_key_if_ready())
        assertTrue(store.decryption_keys().isEmpty())
    }

    @Test
    fun the_cached_row_is_used_when_the_fetch_fails() = runTest {
        coEvery { api.get_external_pgp_key() } returns row(published = true)
        store().load()

        coEvery { api.get_external_pgp_key() } throws RuntimeException("offline")
        val second = store()
        second.load()

        assertNotNull(second.info.value)
        assertNotNull(second.signing_key_if_ready())
    }

    @Test
    fun clearing_the_session_forgets_the_key() = runTest {
        coEvery { api.get_external_pgp_key() } returns row(published = true)
        val store = store()
        store.load()
        assertNotNull(store.signing_key_if_ready())

        session.clear()

        assertNull(store.info.value)
        assertNull(store.signing_key_if_ready())
        assertTrue(store.decryption_keys().isEmpty())
        assertNull(session.get_external_pgp_blob())
    }

    @Test
    fun stop_publishing_turns_signing_off_and_keeps_the_wrapped_blob() = runTest {
        coEvery { api.get_external_pgp_key() } returns row(published = true)
        coEvery { api.set_external_pgp_key_published(false) } returns row(published = false, wrapped = "")
        val store = store()
        store.load()

        assertTrue(store.stop_publishing())

        coVerify(exactly = 1) { api.set_external_pgp_key_published(false) }
        assertEquals(false, store.info.value?.published)
        assertEquals(vector.getString("wrapped_private_key"), store.info.value?.wrapped_private_key)
        assertNull(store.signing_key_if_ready())
        assertEquals(1, store.decryption_keys().size)
    }

    @Test
    fun a_failed_stop_publishing_keeps_the_current_state() = runTest {
        coEvery { api.get_external_pgp_key() } returns row(published = true)
        coEvery { api.set_external_pgp_key_published(false) } throws RuntimeException("server")
        val store = store()
        store.load()

        assertFalse(store.stop_publishing())
        assertEquals(true, store.info.value?.published)
    }

    @Test
    fun a_fetch_that_finishes_after_an_account_switch_is_dropped() = runTest {
        val store = store()
        coEvery { api.get_external_pgp_key() } answers {
            session.clear()
            session.put_user_id("00000000-0000-0000-0000-00000000beef")
            row(published = true)
        }

        store.load()

        assertNull(store.info.value)
        assertNull(store.signing_key_if_ready())
        assertTrue(store.decryption_keys().isEmpty())
        assertNull(session.get_external_pgp_blob())
    }

    @Test
    fun stop_publishing_that_finishes_after_an_account_switch_changes_nothing() = runTest {
        coEvery { api.get_external_pgp_key() } returns row(published = true)
        val store = store()
        store.load()
        coEvery { api.set_external_pgp_key_published(false) } answers {
            session.clear()
            session.put_user_id("00000000-0000-0000-0000-00000000beef")
            row(published = false)
        }

        assertFalse(store.stop_publishing())

        assertNull(store.info.value)
        assertNull(session.get_external_pgp_blob())
    }
}
