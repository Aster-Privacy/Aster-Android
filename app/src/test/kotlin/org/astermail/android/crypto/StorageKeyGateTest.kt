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

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.contacts.ContactItem
import org.astermail.android.api.contacts.ContactsApi
import org.astermail.android.api.contacts.CreateContactRequest
import org.astermail.android.api.contacts.CreateContactResponse
import org.astermail.android.contacts.ContactsRepository
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.ui.contacts.Contact
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class StorageKeyGateTest {

    private lateinit var contacts_api: ContactsApi
    private lateinit var store: SessionKeyStore
    private lateinit var repo: ContactsRepository

    @Volatile private var data_kek: ByteArray? = null
    @Volatile private var user_id: String? = "user-1"
    private val random_kek = ByteArray(32) { (it * 7 + 3).toByte() }
    private val slept = mutableListOf<Long>()

    @Before
    fun setup() {
        contacts_api = mockk(relaxed = true)
        store = mockk(relaxed = true)
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { store.get_passphrase() } returns null
        every { store.get_storage_kdf_version() } returns null
        every { store.get_data_kek() } answers { data_kek?.copyOf() }
        every { store.get_decrypt_keks() } returns emptyList()
        every { store.get_identity_key() } returns null
        every { store.get_user_id() } answers { user_id }
        coEvery { contacts_api.create_contact(any()) } returns CreateContactResponse(id = "c_new", success = true)
        StorageKeyGate.reset()
        StorageKeyGate.sleeper = { slept.add(it) }
        repo = ContactsRepository(contacts_api, store)
    }

    @After
    fun teardown() {
        StorageKeyGate.reset()
        unmockkStatic(Base64::class)
    }

    private fun contact() = Contact(id = "", name = "Ada Lovelace", email = "ada@astermail.org")

    @Test
    fun `a legacy vault saves the first contact once the vault step installs the random key`() = runTest {
        val runs = AtomicInteger()
        StorageKeyGate.install {
            runs.incrementAndGet()
            data_kek = random_kek.copyOf()
            true
        }

        val result = repo.create_contact(contact())

        assertTrue(result.isSuccess)
        assertEquals(1, runs.get())
        coVerify(exactly = 1) { contacts_api.create_contact(any()) }
    }

    @Test
    fun `an offline vault step holds the save and it lands once the step succeeds`() = runTest {
        val runs = AtomicInteger()
        StorageKeyGate.install {
            if (runs.incrementAndGet() < 3) error("offline")
            data_kek = random_kek.copyOf()
            true
        }

        val result = repo.create_contact(contact())

        assertTrue(result.isSuccess)
        assertEquals(3, runs.get())
        assertEquals(StorageKeyGate.retry_delays_ms.subList(1, 3), slept)
        coVerify(exactly = 1) { contacts_api.create_contact(any()) }
    }

    @Test
    fun `a row saved after the vault step opens under the new random key`() = runTest {
        StorageKeyGate.install {
            data_kek = random_kek.copyOf()
            true
        }
        val sent = slot<CreateContactRequest>()
        coEvery { contacts_api.create_contact(capture(sent)) } returns CreateContactResponse(id = "c_new", success = true)

        assertTrue(repo.create_contact(contact()).isSuccess)

        val request = sent.captured
        coEvery { contacts_api.get_contact("c_new") } returns ContactItem(
            id = "c_new",
            encrypted_data = request.encrypted_data,
            data_nonce = request.data_nonce,
            integrity_hash = request.integrity_hash,
            data_version = request.data_version,
        )
        val opened = repo.fetch_contact("c_new").getOrThrow()
        assertEquals("Ada Lovelace", opened.name)
        assertEquals("ada@astermail.org", opened.email)

        data_kek = ByteArray(32) { 1 }
        assertTrue(repo.fetch_contact("c_new").isFailure)
    }

    @Test
    fun `concurrent saves share one vault step`() = runTest {
        val runs = AtomicInteger()
        StorageKeyGate.install {
            runs.incrementAndGet()
            kotlinx.coroutines.delay(50)
            data_kek = random_kek.copyOf()
            true
        }

        val results = (1..4).map { async { repo.create_contact(contact()) } }.awaitAll()

        assertTrue(results.all { it.isSuccess })
        assertEquals(1, runs.get())
        coVerify(exactly = 4) { contacts_api.create_contact(any()) }
    }

    @Test
    fun `a save never falls back to a password key while it waits`() = runTest {
        every { store.get_passphrase() } answers { "pw".toByteArray() }
        val runs = AtomicInteger()
        StorageKeyGate.install {
            if (runs.incrementAndGet() == 2) data_kek = random_kek.copyOf()
            true
        }

        assertTrue(repo.create_contact(contact()).isSuccess)
        assertEquals(2, runs.get())
    }

    @Test
    fun `a save stops when the account changes while it waits`() = runTest {
        StorageKeyGate.install {
            user_id = "user-2"
            false
        }

        val result = repo.create_contact(contact())

        assertTrue(result.exceptionOrNull() is StorageKeyUnavailableException)
        coVerify(exactly = 0) { contacts_api.create_contact(any()) }
    }

    @Test
    fun `a save that cannot get the key in time reports failure and sends nothing`() = runTest {
        val runs = AtomicInteger()
        StorageKeyGate.install {
            runs.incrementAndGet()
            error("offline")
        }

        val result = repo.create_contact(contact())

        val failure = result.exceptionOrNull()
        assertTrue(failure is StorageKeyUnavailableException)
        assertEquals("storage key pending", failure?.message)
        assertEquals(StorageKeyGate.retry_delays_ms.size, runs.get())
        assertEquals(StorageKeyGate.retry_delays_ms.filter { it > 0 }, slept)
        coVerify(exactly = 0) { contacts_api.create_contact(any()) }
    }

    @Test
    fun `without a vault step installed the save fails as before`() = runTest {
        val result = repo.create_contact(contact())

        assertTrue(result.exceptionOrNull() is StorageKeyUnavailableException)
        coVerify(exactly = 0) { contacts_api.create_contact(any()) }
    }
}
