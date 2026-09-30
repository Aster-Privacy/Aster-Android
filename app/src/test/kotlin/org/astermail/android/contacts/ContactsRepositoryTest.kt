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

package org.astermail.android.contacts

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ApiError
import org.astermail.android.api.contacts.ContactItem
import org.astermail.android.api.contacts.ImportContactsResponse
import org.astermail.android.api.contacts.ContactsApi
import org.astermail.android.api.contacts.ContactsCountResponse
import org.astermail.android.api.contacts.CreateContactResponse
import org.astermail.android.api.contacts.DeleteContactResponse
import org.astermail.android.api.contacts.ListContactsResponse
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.ui.contacts.Contact
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ContactsRepositoryTest {

    private lateinit var contacts_api: ContactsApi
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: ContactsRepository

    private val test_passphrase = "test-passphrase-for-contacts".toByteArray(Charsets.UTF_8)

    @Before
    fun setup() {
        contacts_api = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { session_key_store.get_passphrase() } answers { test_passphrase.copyOf() }
        every { session_key_store.get_identity_key() } returns null
        repo = ContactsRepository(contacts_api, session_key_store)
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun derive_test_key(): ByteArray {
        val salt_prefix = "aster-hkdf-salt-v1:"
        val prefix = salt_prefix.toByteArray(Charsets.UTF_8)
        val passphrase = test_passphrase.copyOf()
        val salt_input = ByteArray(prefix.size + passphrase.size)
        System.arraycopy(prefix, 0, salt_input, 0, prefix.size)
        System.arraycopy(passphrase, 0, salt_input, prefix.size, passphrase.size)
        val salt = MessageDigest.getInstance("SHA-256").digest(salt_input)

        val info = "aster-storage-encryption-key-v1".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(passphrase)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1.toByte())
        val okm = mac.doFinal()
        return okm.copyOf(32)
    }

    private fun encrypt_contact_json(json: String): Pair<String, String> {
        val key = derive_test_key()
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val encoded_data = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
        val encoded_nonce = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP)
        return Pair(encoded_data, encoded_nonce)
    }

    private fun fake_encrypted_contact_item(
        id: String,
        first_name: String = "Alice",
        last_name: String = "Smith",
        email: String = "alice@astermail.org",
    ): ContactItem {
        val json = """{"first_name":"$first_name","last_name":"$last_name","emails":["$email"],"is_favorite":false}"""
        val (encrypted_data, data_nonce) = encrypt_contact_json(json)
        return ContactItem(
            id = id,
            encrypted_data = encrypted_data,
            data_nonce = data_nonce,
        )
    }

    @Test
    fun `fetch_contacts returns decrypted contacts`() = runTest {
        val items = listOf(
            fake_encrypted_contact_item("c_1", "Alice", "Smith", "alice@astermail.org"),
            fake_encrypted_contact_item("c_2", "Bob", "Jones", "bob@astermail.org"),
        )
        coEvery { contacts_api.list_contacts(limit = 100, cursor = null) } returns
            ListContactsResponse(items = items, has_more = false, next_cursor = null)

        val result = repo.fetch_contacts()
        assertTrue(result.isSuccess)

        val contacts = result.getOrThrow()
        assertEquals(2, contacts.size)
        assertEquals("c_1", contacts[0].id)
        assertEquals("Alice Smith", contacts[0].name)
        assertEquals("alice@astermail.org", contacts[0].email)
        assertEquals("c_2", contacts[1].id)
        assertEquals("Bob Jones", contacts[1].name)
    }

    @Test
    fun `fetch_contacts with empty response returns empty list`() = runTest {
        coEvery { contacts_api.list_contacts(limit = 100, cursor = null) } returns
            ListContactsResponse(items = emptyList(), has_more = false, next_cursor = null)

        val result = repo.fetch_contacts()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `fetch_contacts api error propagates`() = runTest {
        coEvery { contacts_api.list_contacts(any(), any()) } throws
            RuntimeException("connection refused")

        val result = repo.fetch_contacts()
        assertTrue(result.isFailure)
        assertEquals("connection refused", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetch_contacts pages through multiple results`() = runTest {
        val page1_items = listOf(fake_encrypted_contact_item("c_1"))
        val page2_items = listOf(fake_encrypted_contact_item("c_2"))

        coEvery { contacts_api.list_contacts(limit = 100, cursor = null) } returns
            ListContactsResponse(items = page1_items, has_more = true, next_cursor = "cursor_1")
        coEvery { contacts_api.list_contacts(limit = 100, cursor = "cursor_1") } returns
            ListContactsResponse(items = page2_items, has_more = false, next_cursor = null)

        val result = repo.fetch_contacts()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
        coVerify(exactly = 2) { contacts_api.list_contacts(any(), any()) }
    }

    @Test
    fun `fetch_contacts keeps earlier pages when a later page fails`() = runTest {
        val page1_items = listOf(fake_encrypted_contact_item("c_1"))

        coEvery { contacts_api.list_contacts(limit = 100, cursor = null) } returns
            ListContactsResponse(items = page1_items, has_more = true, next_cursor = "cursor_1")
        coEvery { contacts_api.list_contacts(limit = 100, cursor = "cursor_1") } throws
            RuntimeException("invalid cursor timestamp")

        val result = repo.fetch_contacts()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("c_1", result.getOrThrow()[0].id)
    }

    @Test
    fun `fetch_contacts stops when the server repeats a cursor`() = runTest {
        coEvery { contacts_api.list_contacts(limit = 100, cursor = any()) } returns
            ListContactsResponse(
                items = listOf(fake_encrypted_contact_item("c_1")),
                has_more = true,
                next_cursor = "cursor_1",
            )

        val result = repo.fetch_contacts()
        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { contacts_api.list_contacts(any(), any()) }
    }

    @Test
    fun `fetch_contacts skips items with null encrypted_data`() = runTest {
        val valid_item = fake_encrypted_contact_item("c_1")
        val null_item = ContactItem(id = "c_2", encrypted_data = null, data_nonce = null)

        coEvery { contacts_api.list_contacts(limit = 100, cursor = null) } returns
            ListContactsResponse(items = listOf(valid_item, null_item), has_more = false, next_cursor = null)

        val result = repo.fetch_contacts()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("c_1", result.getOrThrow()[0].id)
    }

    @Test
    fun `fetch_contact by id returns decrypted contact`() = runTest {
        val item = fake_encrypted_contact_item("c_42", "Diana", "Prince", "diana@astermail.org")
        coEvery { contacts_api.get_contact("c_42") } returns item

        val result = repo.fetch_contact("c_42")
        assertTrue(result.isSuccess)

        val contact = result.getOrThrow()
        assertEquals("c_42", contact.id)
        assertEquals("Diana Prince", contact.name)
        assertEquals("diana@astermail.org", contact.email)
    }

    @Test
    fun `fetch_contact with undecryptable data fails`() = runTest {
        val item = ContactItem(
            id = "c_bad",
            encrypted_data = "not-valid-base64-ciphertext",
            data_nonce = "not-valid-nonce",
        )
        coEvery { contacts_api.get_contact("c_bad") } returns item

        val result = repo.fetch_contact("c_bad")
        assertTrue(result.isFailure)
    }

    @Test
    fun `fetch_contact with null encrypted_data fails`() = runTest {
        val item = ContactItem(id = "c_null", encrypted_data = null, data_nonce = null)
        coEvery { contacts_api.get_contact("c_null") } returns item

        val result = repo.fetch_contact("c_null")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("decrypt") == true)
    }

    @Test
    fun `get_count returns count from api`() = runTest {
        coEvery { contacts_api.get_contacts_count() } returns ContactsCountResponse(count = 42)

        val result = repo.get_count()
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun `get_count propagates api error`() = runTest {
        coEvery { contacts_api.get_contacts_count() } throws RuntimeException("forbidden")

        val result = repo.get_count()
        assertTrue(result.isFailure)
        assertEquals("forbidden", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create_contact calls api with encrypted data`() = runTest {
        coEvery { contacts_api.create_contact(any()) } returns
            CreateContactResponse(id = "c_new", success = true)

        val contact = Contact(
            id = "",
            name = "New Person",
            email = "new@astermail.org",
        )

        val result = repo.create_contact(contact)
        assertTrue(result.isSuccess)
        assertEquals("c_new", result.getOrThrow().id)
        assertTrue(result.getOrThrow().success)

        coVerify { contacts_api.create_contact(match {
            it.encrypted_data.isNotBlank() && it.data_nonce.isNotBlank() && it.contact_token.isNotBlank()
        }) }
    }

    @Test
    fun `create_contact propagates api error`() = runTest {
        coEvery { contacts_api.create_contact(any()) } throws RuntimeException("conflict")

        val contact = Contact(id = "", name = "Test", email = "test@astermail.org")
        val result = repo.create_contact(contact)
        assertTrue(result.isFailure)
        assertEquals("conflict", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create_contact fails without passphrase`() = runTest {
        every { session_key_store.get_passphrase() } returns null
        coEvery { contacts_api.create_contact(any()) } returns
            CreateContactResponse(id = "c_new", success = true)

        val contact = Contact(id = "", name = "Test", email = "test@astermail.org")
        val result = repo.create_contact(contact)
        assertTrue(result.isFailure)
    }

    @Test
    fun `update_contact calls api with encrypted data`() = runTest {
        coEvery { contacts_api.update_contact(eq("c_1"), any()) } returns org.astermail.android.api.contacts.UpdateContactResponse(success = true)

        val contact = Contact(
            id = "c_1",
            name = "Updated Name",
            email = "updated@astermail.org",
        )

        val result = repo.update_contact("c_1", contact)
        assertTrue(result.isSuccess)

        coVerify { contacts_api.update_contact(eq("c_1"), match {
            it.encrypted_data.isNotBlank() && it.data_nonce.isNotBlank()
        }) }
    }

    private fun decrypt_request_json(data: String, nonce: String): org.json.JSONObject {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(derive_test_key(), "AES"),
            GCMParameterSpec(128, java.util.Base64.getDecoder().decode(nonce)),
        )
        val plain = cipher.doFinal(java.util.Base64.getDecoder().decode(data))
        return org.json.JSONObject(String(plain, Charsets.UTF_8))
    }

    @Test
    fun `update_contact keeps typed entries in step with primary email and phone`() = runTest {
        val raw = """{"first_name":"Alice","last_name":"Webster",""" +
            """"emails":["alice@example.com","alice@corp.example"],""" +
            """"email_entries":[{"value":"alice@example.com","type":"other"},{"value":"alice@corp.example","type":"work"}],""" +
            """"phone":"(555) 123-4567",""" +
            """"phone_entries":[{"value":"(555) 123-4567","type":"mobile"},{"value":"(555) 000-1111","type":"work"}]}"""
        val captured = slot<org.astermail.android.api.contacts.UpdateContactRequest>()
        coEvery { contacts_api.update_contact(eq("c_1"), capture(captured)) } returns
            org.astermail.android.api.contacts.UpdateContactResponse(success = true)

        val contact = Contact(
            id = "c_1",
            name = "Alice Webster",
            email = "alice@work.example",
            work_email = "alice@corp.example",
            phone = "(555) 999-8888",
            work_phone = "(555) 000-1111",
            raw_json = raw,
        )
        assertTrue(repo.update_contact("c_1", contact).isSuccess)

        val json = decrypt_request_json(captured.captured.encrypted_data, captured.captured.data_nonce)
        val emails = json.getJSONArray("email_entries")
        assertEquals(2, emails.length())
        assertEquals("alice@work.example", emails.getJSONObject(0).getString("value"))
        assertEquals("other", emails.getJSONObject(0).getString("type"))
        assertEquals("alice@corp.example", emails.getJSONObject(1).getString("value"))
        val phones = json.getJSONArray("phone_entries")
        assertEquals("(555) 999-8888", phones.getJSONObject(0).getString("value"))
        assertEquals("(555) 000-1111", phones.getJSONObject(1).getString("value"))

        val card = org.astermail.android.contacts.sync.contact_card_from_json_object(json)
        assertEquals("alice@work.example", card.emails.first().value)
        assertEquals("(555) 999-8888", card.phones.first().value)
    }

    @Test
    fun `update_contact removes the primary typed entry when the primary email is cleared`() = runTest {
        val raw = """{"first_name":"Bob","emails":["bob@example.org"],""" +
            """"email_entries":[{"value":"bob@example.org","type":"other"}]}"""
        val captured = slot<org.astermail.android.api.contacts.UpdateContactRequest>()
        coEvery { contacts_api.update_contact(eq("c_2"), capture(captured)) } returns
            org.astermail.android.api.contacts.UpdateContactResponse(success = true)

        assertTrue(repo.update_contact("c_2", Contact(id = "c_2", name = "Bob", email = "", raw_json = raw)).isSuccess)

        val json = decrypt_request_json(captured.captured.encrypted_data, captured.captured.data_nonce)
        assertTrue(!json.has("email_entries"))
        assertEquals(0, json.getJSONArray("emails").length())
    }

    @Test
    fun `update_contact repairs a typed entry that drifted from the primary email`() = runTest {
        val raw = """{"first_name":"Alice","emails":["alice@work.example"],""" +
            """"email_entries":[{"value":"alice@example.com","type":"other"}]}"""
        val captured = slot<org.astermail.android.api.contacts.UpdateContactRequest>()
        coEvery { contacts_api.update_contact(eq("c_3"), capture(captured)) } returns
            org.astermail.android.api.contacts.UpdateContactResponse(success = true)

        assertTrue(
            repo.update_contact("c_3", Contact(id = "c_3", name = "Alice", email = "alice@new.example", raw_json = raw))
                .isSuccess,
        )

        val json = decrypt_request_json(captured.captured.encrypted_data, captured.captured.data_nonce)
        val entries = json.getJSONArray("email_entries")
        assertEquals(1, entries.length())
        assertEquals("alice@new.example", entries.getJSONObject(0).getString("value"))
    }

    @Test
    fun `update_contact propagates api error`() = runTest {
        coEvery { contacts_api.update_contact(eq("c_1"), any()) } throws
            RuntimeException("not found")

        val contact = Contact(id = "c_1", name = "Test", email = "test@astermail.org")
        val result = repo.update_contact("c_1", contact)
        assertTrue(result.isFailure)
        assertEquals("not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `delete_contact success returns response`() = runTest {
        coEvery { contacts_api.delete_contact("c_1") } returns
            DeleteContactResponse(success = true, deleted_count = 1)

        val result = repo.delete_contact("c_1")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().success)
        assertEquals(1, result.getOrThrow().deleted_count)

        coVerify { contacts_api.delete_contact("c_1") }
    }

    @Test
    fun `delete_contact failure propagates error`() = runTest {
        coEvery { contacts_api.delete_contact("c_1") } throws
            RuntimeException("server error")

        val result = repo.delete_contact("c_1")
        assertTrue(result.isFailure)
        assertEquals("server error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetch_contacts decrypts contact with all fields`() = runTest {
        val json = """{
            "first_name": "Alice",
            "last_name": "Smith",
            "emails": ["alice@astermail.org", "alice.work@company.com"],
            "phone": "+1 555 0100",
            "company": "Aster Labs",
            "job_title": "Engineer",
            "birthday": "1990-01-15",
            "address": {"street": "123 Main St", "city": "Portland", "state": "OR", "postal_code": "97201", "country": "USA"},
            "social_links": {"website": "https://alice.dev", "twitter": "@alice", "linkedin": "alice-smith"},
            "notes": "Met at conference",
            "is_favorite": true
        }""".trimIndent()

        val (encrypted_data, data_nonce) = encrypt_contact_json(json)
        val item = ContactItem(id = "c_full", encrypted_data = encrypted_data, data_nonce = data_nonce)

        coEvery { contacts_api.list_contacts(limit = 100, cursor = null) } returns
            ListContactsResponse(items = listOf(item), has_more = false, next_cursor = null)

        val result = repo.fetch_contacts()
        assertTrue(result.isSuccess)

        val contact = result.getOrThrow()[0]
        assertEquals("c_full", contact.id)
        assertEquals("Alice Smith", contact.name)
        assertEquals("alice@astermail.org", contact.email)
        assertEquals("alice.work@company.com", contact.work_email)
        assertEquals("+1 555 0100", contact.phone)
        assertEquals("Aster Labs", contact.company)
        assertEquals("Engineer", contact.title)
        assertEquals("1990-01-15", contact.birthday)
        assertEquals("123 Main St", contact.address)
        assertEquals("Portland", contact.city)
        assertEquals("OR", contact.region)
        assertEquals("97201", contact.postal_code)
        assertEquals("USA", contact.country)
        assertEquals("https://alice.dev", contact.website)
        assertEquals("@alice", contact.twitter)
        assertEquals("alice-smith", contact.linkedin)
        assertEquals("Met at conference", contact.notes)
        assertTrue(contact.is_favorite)
    }

    @Test
    fun `fetch_contacts contact with only email in name falls back`() = runTest {
        val json = """{"first_name":"","last_name":"","emails":["only@astermail.org"],"is_favorite":false}"""
        val (encrypted_data, data_nonce) = encrypt_contact_json(json)
        val item = ContactItem(id = "c_email_only", encrypted_data = encrypted_data, data_nonce = data_nonce)

        coEvery { contacts_api.list_contacts(limit = 100, cursor = null) } returns
            ListContactsResponse(items = listOf(item), has_more = false, next_cursor = null)

        val result = repo.fetch_contacts()
        val contact = result.getOrThrow()[0]
        assertEquals("only@astermail.org", contact.name)
        assertEquals("only@astermail.org", contact.email)
    }

    @Test
    fun `import_contacts sends contacts in chunks and sums the results`() = runTest {
        val sizes = mutableListOf<Int>()
        coEvery { contacts_api.import_contacts(any()) } answers {
            val request = firstArg<org.astermail.android.api.contacts.ImportContactsRequest>()
            sizes.add(request.contacts.size)
            ImportContactsResponse(imported = request.contacts.size.toLong() - 1, updated = 1, skipped = 0)
        }
        val contacts = (1..201).map { Contact(id = "", name = "Person $it", email = "p$it@astermail.org") }
        val progress = mutableListOf<Pair<Int, Int>>()

        val summary = repo.import_contacts(contacts) { done, total -> progress.add(done to total) }

        assertEquals(listOf(100, 100, 1), sizes)
        assertEquals(listOf(0 to 201, 100 to 201, 200 to 201, 201 to 201), progress)
        assertEquals(198L, summary.imported)
        assertEquals(3L, summary.updated)
        assertEquals(0, summary.failed)
        assertFalse(summary.limit_reached)
        coVerify(exactly = 3) { contacts_api.import_contacts(match { r ->
            r.contacts.all { it.encrypted_data.isNotBlank() && it.data_nonce.isNotBlank() && it.contact_token.isNotBlank() }
        }) }
    }

    @Test
    fun `import_contacts stops at the contact limit`() = runTest {
        coEvery { contacts_api.import_contacts(any()) } returns
            ImportContactsResponse(imported = 10, updated = 0, skipped = 0, errors = listOf("Contact limit reached at index 10"))
        val contacts = (1..900).map { Contact(id = "", name = "Person $it", email = "p$it@astermail.org") }

        val summary = repo.import_contacts(contacts)

        assertEquals(10L, summary.imported)
        assertTrue(summary.limit_reached)
        coVerify(exactly = 1) { contacts_api.import_contacts(any()) }
    }

    @Test
    fun `import_contacts retries a rate limited chunk`() = runTest {
        var calls = 0
        coEvery { contacts_api.import_contacts(any()) } answers {
            calls += 1
            if (calls == 1) throw ApiError.RateLimited() else ImportContactsResponse(imported = 2)
        }
        val contacts = listOf(
            Contact(id = "", name = "A", email = "a@astermail.org"),
            Contact(id = "", name = "B", email = "b@astermail.org"),
        )

        val summary = repo.import_contacts(contacts)

        assertEquals(2, calls)
        assertEquals(2L, summary.imported)
        assertEquals(0, summary.failed)
    }

    @Test
    fun `import_contacts reports a failed chunk without stopping`() = runTest {
        var calls = 0
        coEvery { contacts_api.import_contacts(any()) } answers {
            calls += 1
            if (calls == 1) throw ApiError.NotFoundError else ImportContactsResponse(imported = 1)
        }
        val contacts = (1..101).map { Contact(id = "", name = "Person $it", email = "p$it@astermail.org") }

        val summary = repo.import_contacts(contacts)

        assertEquals(2, calls)
        assertEquals(1L, summary.imported)
        assertEquals(100, summary.failed)
        assertTrue(summary.last_failure is ApiError.NotFoundError)
    }
}
