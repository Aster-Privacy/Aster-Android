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

import android.content.Context
import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.ApiError
import org.astermail.android.api.contacts.ContactGroupEncrypted
import org.astermail.android.api.contacts.ContactItem
import org.astermail.android.api.contacts.ContactsApi
import org.astermail.android.api.contacts.ContactsCountResponse
import org.astermail.android.api.contacts.CreateContactRequest
import org.astermail.android.api.contacts.CreateContactResponse
import org.astermail.android.api.contacts.DeleteContactResponse
import org.astermail.android.api.contacts.ListContactGroupsResponse
import org.astermail.android.api.contacts.ListContactsResponse
import org.astermail.android.api.contacts.UpdateContactRequest
import org.astermail.android.api.contacts.UpdateContactResponse
import org.astermail.android.contacts.sync.REVISION_CONFLICT_CODE
import org.astermail.android.mail.PendingActionKind
import org.astermail.android.mail.PendingMailAction
import org.astermail.android.mail.PendingMailActionQueue
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.actions.PendingMailActionDao
import org.astermail.android.storage.actions.PendingMailActionEntity
import org.astermail.android.storage.contacts.ContactCacheDao
import org.astermail.android.storage.contacts.ContactCacheEntity
import org.astermail.android.storage.contacts.ContactGroupCacheDao
import org.astermail.android.storage.contacts.ContactGroupCacheEntity
import org.astermail.android.ui.contacts.Contact
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class OfflineContactsRepositoryTest {

    private class FakeContactCacheDao : ContactCacheDao {
        val rows = linkedMapOf<Pair<String, String>, ContactCacheEntity>()

        override suspend fun upsert(row: ContactCacheEntity) {
            rows[row.account_id to row.id] = row
        }

        override suspend fun upsert_all(rows: List<ContactCacheEntity>) {
            rows.forEach { upsert(it) }
        }

        override suspend fun get_for_account(account_id: String): List<ContactCacheEntity> =
            rows.values.filter { it.account_id == account_id }

        override suspend fun get(account_id: String, id: String): ContactCacheEntity? = rows[account_id to id]

        override suspend fun count_for_account(account_id: String): Int = get_for_account(account_id).size

        override suspend fun delete(account_id: String, id: String) {
            rows.remove(account_id to id)
        }

        override suspend fun clear_for_account(account_id: String) {
            rows.keys.removeAll { it.first == account_id }
        }

        override suspend fun clear_all() {
            rows.clear()
        }
    }

    private class FakeGroupCacheDao : ContactGroupCacheDao {
        val rows = mutableListOf<ContactGroupCacheEntity>()

        override suspend fun upsert_all(rows: List<ContactGroupCacheEntity>) {
            rows.forEach { row -> this.rows.removeAll { it.account_id == row.account_id && it.id == row.id } }
            this.rows.addAll(rows)
        }

        override suspend fun get_for_account(account_id: String): List<ContactGroupCacheEntity> =
            rows.filter { it.account_id == account_id }.sortedBy { it.position }

        override suspend fun clear_for_account(account_id: String) {
            rows.removeAll { it.account_id == account_id }
        }

        override suspend fun clear_all() {
            rows.clear()
        }
    }

    private class FakePendingDao : PendingMailActionDao {
        val rows = mutableListOf<PendingMailActionEntity>()
        private var next_id = 1L

        override suspend fun insert(row: PendingMailActionEntity): Long {
            val id = next_id++
            rows.add(row.copy(id = id))
            return id
        }

        override suspend fun get_all(): List<PendingMailActionEntity> = rows.sortedBy { it.id }

        override suspend fun get_for_account(account_id: String): List<PendingMailActionEntity> =
            get_all().filter { it.account_id == account_id }

        override suspend fun bump_attempts(id: Long) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = rows[index].copy(attempts = rows[index].attempts + 1)
        }

        override suspend fun update_row(id: Long, kind: String, payload: String): Int {
            val index = rows.indexOfFirst { it.id == id }
            if (index < 0) return 0
            rows[index] = rows[index].copy(kind = kind, payload = payload)
            return 1
        }

        override suspend fun delete_by_id(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun clear_for_account(account_id: String) {
            rows.removeAll { it.account_id == account_id }
        }

        override suspend fun clear_all() {
            rows.clear()
        }
    }

    private lateinit var contacts_api: ContactsApi
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var cache: FakeContactCacheDao
    private lateinit var group_cache: FakeGroupCacheDao
    private lateinit var pending_dao: FakePendingDao
    private lateinit var queue: PendingMailActionQueue
    private lateinit var repo: ContactsRepository

    private var account: String? = ACCOUNT_A
    private var online = true

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
        every { session_key_store.get_storage_kdf_version() } returns null
        every { session_key_store.get_data_kek() } answers { derive_test_key() }
        every { session_key_store.get_decrypt_keks() } returns emptyList()
        every { session_key_store.get_identity_key() } returns null
        every { session_key_store.get_user_id() } answers { account }
        cache = FakeContactCacheDao()
        group_cache = FakeGroupCacheDao()
        pending_dao = FakePendingDao()
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(any<String>()) } returns null
        queue = PendingMailActionQueue({ pending_dao }, context)
        repo = ContactsRepository(contacts_api, session_key_store, { cache }, { group_cache }, { queue })
        repo.network_probe = { online }
        runBlocking { queue.await_loaded() }
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun derive_test_key(): ByteArray {
        val prefix = "aster-hkdf-salt-v1:".toByteArray(Charsets.UTF_8)
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
        return mac.doFinal().copyOf(32)
    }

    private fun encrypt_text(text: String): Pair<String, String> {
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derive_test_key(), "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(ciphertext) to
            java.util.Base64.getEncoder().encodeToString(nonce)
    }

    private fun decrypt_text(encrypted_data: String, data_nonce: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(derive_test_key(), "AES"),
            GCMParameterSpec(128, java.util.Base64.getDecoder().decode(data_nonce)),
        )
        return String(cipher.doFinal(java.util.Base64.getDecoder().decode(encrypted_data)), Charsets.UTF_8)
    }

    private fun item(
        id: String,
        first_name: String,
        last_name: String,
        email: String,
        extra: Map<String, String> = emptyMap(),
        revision: Long = 1,
    ): ContactItem {
        val json = JSONObject()
            .put("first_name", first_name)
            .put("last_name", last_name)
            .put("emails", org.json.JSONArray().put(email))
            .put("is_favorite", false)
        extra.forEach { (key, value) -> json.put(key, value) }
        val (data, nonce) = encrypt_text(json.toString())
        return ContactItem(id = id, encrypted_data = data, data_nonce = nonce, revision = revision)
    }

    private fun serve(vararg items: ContactItem) {
        coEvery { contacts_api.list_contacts(any(), any(), any()) } returns
            ListContactsResponse(items = items.toList(), has_more = false, next_cursor = null)
    }

    private fun contact_ops(): List<PendingMailAction> =
        queue.actions.value.filter { it.payload.contact != null }

    private suspend fun drain_contacts() {
        for (action in contact_ops()) {
            val result = repo.with_op_lock { repo.replay_pending_op(action.account_id, action) }
            if (result.isSuccess) queue.complete(action.id)
        }
    }

    private fun seed_online(vararg items: ContactItem) = runBlocking {
        serve(*items)
        repo.fetch_contacts().getOrThrow()
    }

    @Test
    fun `list is served from the cache while offline`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"), item("c_2", "Bob", "Jones", "bob@astermail.org"))
        online = false
        coEvery { contacts_api.list_contacts(any(), any(), any()) } throws IOException("offline")

        val contacts = repo.fetch_contacts().getOrThrow()

        assertEquals(setOf("Alice Smith", "Bob Jones"), contacts.map { it.name }.toSet())
        coVerify(exactly = 1) { contacts_api.list_contacts(any(), any(), any()) }
    }

    @Test
    fun `cache holds ciphertext only`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))

        val row = cache.rows.values.single()
        val stored = listOf(row.encrypted_data, row.data_nonce, row.integrity_hash.orEmpty(), row.contact_token.orEmpty())
        assertTrue(stored.none { it.contains("Alice") || it.contains("alice@astermail.org") })
        assertTrue(decrypt_text(row.encrypted_data, row.data_nonce).contains("Alice"))
    }

    @Test
    fun `transient list failure falls back to the cache`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        coEvery { contacts_api.list_contacts(any(), any(), any()) } throws ApiError.ServerError(503)

        assertEquals(listOf("Alice Smith"), repo.fetch_contacts().getOrThrow().map { it.name })
    }

    @Test
    fun `non transient list failure is reported`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        coEvery { contacts_api.list_contacts(any(), any(), any()) } throws ApiError.UnknownError("bad")

        assertTrue(repo.fetch_contacts().isFailure)
    }

    @Test
    fun `complete fetch replaces stale rows`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"), item("c_2", "Bob", "Jones", "bob@astermail.org"))
        seed_online(item("c_2", "Bob", "Jones", "bob@astermail.org"))

        assertEquals(listOf("c_2"), cache.rows.values.map { it.id })
    }

    @Test
    fun `cache is isolated per account`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        account = ACCOUNT_B
        online = false

        assertTrue(repo.fetch_contacts().getOrThrow().isEmpty())
        assertEquals(0, repo.get_count().getOrThrow())
        assertTrue(repo.fetch_contact("c_1").isFailure)
        assertTrue(repo.search_contacts("alice").getOrThrow().isEmpty())
    }

    @Test
    fun `clear_cache removes only that account`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        account = ACCOUNT_B
        seed_online(item("c_9", "Zoe", "Ray", "zoe@astermail.org"))

        repo.clear_cache(ACCOUNT_A)

        assertEquals(listOf(ACCOUNT_B), cache.rows.values.map { it.account_id })
        account = ACCOUNT_A
        online = false
        assertTrue(repo.fetch_contacts().getOrThrow().isEmpty())
    }

    @Test
    fun `details and count work offline`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"), item("c_2", "Bob", "Jones", "bob@astermail.org"))
        online = false

        assertEquals("Alice Smith", repo.fetch_contact("c_1").getOrThrow().name)
        assertEquals(2, repo.get_count().getOrThrow())
        coVerify(exactly = 0) { contacts_api.get_contact(any()) }
        coVerify(exactly = 0) { contacts_api.get_contacts_count() }
    }

    @Test
    fun `details fall back to the cache on a transient failure`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        coEvery { contacts_api.get_contact("c_1") } throws IOException("timeout")
        coEvery { contacts_api.get_contacts_count() } throws IOException("timeout")

        assertEquals("Alice Smith", repo.fetch_contact("c_1").getOrThrow().name)
        assertEquals(1, repo.get_count().getOrThrow())
    }

    @Test
    fun `details 404 forgets the cached row`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        coEvery { contacts_api.get_contact("c_1") } throws ApiError.NotFoundError

        assertTrue(repo.fetch_contact("c_1").isFailure)
        assertTrue(cache.rows.isEmpty())
    }

    @Test
    fun `search works offline on name email and company`() = runBlocking {
        seed_online(
            item("c_1", "Alice", "Smith", "alice@astermail.org", mapOf("company" to "Acme")),
            item("c_2", "Bob", "Jones", "bob@astermail.org"),
        )
        online = false

        assertEquals(listOf("c_1"), repo.search_contacts(" Alice Smith ").getOrThrow().map { it.id })
        assertEquals(listOf("c_2"), repo.search_contacts("BOB@astermail.org").getOrThrow().map { it.id })
        assertEquals(listOf("c_1"), repo.search_contacts("acme").getOrThrow().map { it.id })
        assertTrue(repo.search_contacts("ali").getOrThrow().isEmpty())
        assertTrue(repo.search_contacts("   ").getOrThrow().isEmpty())
        coVerify(exactly = 0) { contacts_api.search_contacts(any(), any(), any()) }
    }

    @Test
    fun `groups are served from the cache while offline`() = runBlocking {
        val (name_a, nonce_a) = encrypt_text("""{"name":"Family","color":"#ff0000"}""")
        val (name_b, nonce_b) = encrypt_text("Work")
        coEvery { contacts_api.list_contact_groups() } returns ListContactGroupsResponse(
            groups = listOf(
                ContactGroupEncrypted(id = "g_1", encrypted_name = name_a, name_nonce = nonce_a, contact_count = 2),
                ContactGroupEncrypted(id = "g_2", encrypted_name = name_b, name_nonce = nonce_b, contact_count = 1),
            ),
        )
        repo.list_contact_groups().getOrThrow()
        assertTrue(group_cache.rows.none { it.encrypted_name.contains("Family") })

        online = false
        val groups = repo.list_contact_groups().getOrThrow()

        assertEquals(listOf("Family", "Work"), groups.map { it.name })
        assertEquals(listOf(2, 1), groups.map { it.contact_count })
        coVerify(exactly = 1) { contacts_api.list_contact_groups() }
        account = ACCOUNT_B
        assertTrue(repo.list_contact_groups().getOrThrow().isEmpty())
    }

    @Test
    fun `offline create is queued and visible everywhere`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        online = false

        val response = repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow()

        val id = response.id!!
        assertTrue(is_local_contact_id(id))
        coVerify(exactly = 0) { contacts_api.create_contact(any()) }
        assertEquals(PendingActionKind.contact_create, contact_ops().single().kind)
        assertFalse(pending_dao.rows.single().payload.contains("Carol"))
        assertFalse(pending_dao.rows.single().payload.contains("carol@astermail.org"))
        assertEquals(setOf("Alice Smith", "Carol White"), repo.fetch_contacts().getOrThrow().map { it.name }.toSet())
        assertEquals("Carol White", repo.fetch_contact(id).getOrThrow().name)
        assertEquals(2, repo.get_count().getOrThrow())
        assertEquals(listOf(id), repo.search_contacts("carol@astermail.org").getOrThrow().map { it.id })
        assertEquals(setOf(id), repo.pending_contact_ids.first())
    }

    @Test
    fun `offline duplicate create is rejected`() = runBlocking {
        online = false
        repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow()

        val second = repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org"))

        assertTrue(second.exceptionOrNull() is ApiError.Conflict)
        assertEquals(1, contact_ops().size)
    }

    @Test
    fun `transient create failure queues the contact`() = runBlocking {
        coEvery { contacts_api.create_contact(any()) } throws IOException("reset")

        val response = repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow()

        assertTrue(is_local_contact_id(response.id))
        assertEquals(1, contact_ops().size)
    }

    @Test
    fun `non transient create failure is not queued`() = runBlocking {
        coEvery { contacts_api.create_contact(any()) } throws ApiError.UnknownError("bad")

        assertTrue(repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).isFailure)
        assertTrue(contact_ops().isEmpty())
    }

    @Test
    fun `edit of an offline create rewrites the create and delete drops it`() = runBlocking {
        online = false
        val id = repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow().id!!
        val carol = repo.fetch_contact(id).getOrThrow()

        repo.update_contact(id, carol.copy(name = "Carol Black")).getOrThrow()

        val op = contact_ops().single()
        assertEquals(PendingActionKind.contact_create, op.kind)
        assertEquals("Carol Black", repo.fetch_contact(id).getOrThrow().name)

        repo.delete_contact(id).getOrThrow()

        assertTrue(contact_ops().isEmpty())
        assertTrue(repo.fetch_contacts().getOrThrow().isEmpty())
        assertTrue(repo.pending_contact_ids.first().isEmpty())
    }

    @Test
    fun `offline edit then delete of a server contact becomes one delete`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()

        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()
        repo.update_contact("c_1", alice.copy(name = "Ali Smith")).getOrThrow()

        val update = contact_ops().single()
        assertEquals(PendingActionKind.contact_update, update.kind)
        assertEquals(cache.rows.values.single().data_nonce, update.payload.contact!!.base!!.data_nonce)
        assertEquals("Ali Smith", repo.fetch_contacts().getOrThrow().single().name)

        repo.delete_contact("c_1").getOrThrow()

        assertEquals(PendingActionKind.contact_delete, contact_ops().single().kind)
        assertTrue(repo.fetch_contacts().getOrThrow().isEmpty())
        coVerify(exactly = 0) { contacts_api.update_contact(any(), any()) }
        coVerify(exactly = 0) { contacts_api.delete_contact(any()) }
    }

    @Test
    fun `offline bulk delete queues every contact`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"), item("c_2", "Bob", "Jones", "bob@astermail.org"))
        online = false

        val response = repo.bulk_delete_contacts(listOf("c_1", "c_2")).getOrThrow()

        assertEquals(2, response.deleted_count)
        assertEquals(2, contact_ops().count { it.kind == PendingActionKind.contact_delete })
        assertTrue(repo.fetch_contacts().getOrThrow().isEmpty())
        coVerify(exactly = 0) { contacts_api.bulk_delete_contacts(any()) }
    }

    @Test
    fun `online edit goes straight to the server when nothing is pending`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        coEvery { contacts_api.update_contact("c_1", any()) } returns UpdateContactResponse(success = true, revision = 2)
        val alice = repo.fetch_contacts().getOrThrow().single()

        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()

        assertTrue(contact_ops().isEmpty())
        assertEquals(2L, cache.rows.values.single().revision)
        online = false
        assertEquals("Alicia Smith", repo.fetch_contacts().getOrThrow().single().name)
    }

    @Test
    fun `online edit waits behind a pending op for the same contact`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()
        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()
        online = true

        repo.update_contact("c_1", alice.copy(name = "Ali Smith")).getOrThrow()

        coVerify(exactly = 0) { contacts_api.update_contact(any(), any()) }
        assertEquals(1, contact_ops().size)
    }

    @Test
    fun `replay create posts once and maps the local id`() = runBlocking {
        online = false
        val local_id = repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow().id!!
        online = true
        val request = slot<CreateContactRequest>()
        coEvery { contacts_api.create_contact(capture(request)) } returns
            CreateContactResponse(id = "c_server", success = true, revision = 1)

        drain_contacts()

        assertTrue(contact_ops().isEmpty())
        assertEquals("c_server", repo.resolve_contact_id(local_id))
        assertEquals(listOf("c_server"), cache.rows.values.map { it.id })
        assertTrue(decrypt_text(request.captured.encrypted_data, request.captured.data_nonce).contains("carol@astermail.org"))
        coVerify(exactly = 1) { contacts_api.create_contact(any()) }
        assertTrue(repo.pending_contact_ids.first().isEmpty())
    }

    @Test
    fun `replay create adopts an equivalent contact after a 409`() = runBlocking {
        online = false
        val local_id = repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow().id!!
        online = true
        val sent = slot<CreateContactRequest>()
        coEvery { contacts_api.create_contact(capture(sent)) } throws ApiError.Conflict("exists", null)
        coEvery { contacts_api.list_contacts(any(), any(), any()) } answers {
            val request = sent.captured
            ListContactsResponse(
                items = listOf(
                    ContactItem(
                        id = "c_existing",
                        contact_token = request.contact_token,
                        encrypted_data = request.encrypted_data,
                        data_nonce = request.data_nonce,
                        revision = 4,
                    ),
                ),
            )
        }

        drain_contacts()

        assertTrue(contact_ops().isEmpty())
        assertEquals("c_existing", repo.resolve_contact_id(local_id))
        assertEquals(listOf("c_existing"), cache.rows.values.map { it.id })
        coVerify(exactly = 1) { contacts_api.create_contact(any()) }
    }

    @Test
    fun `replay create keeps a different contact that shares the token`() = runBlocking {
        online = false
        repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org", phone = "555")).getOrThrow()
        online = true
        val calls = mutableListOf<CreateContactRequest>()
        coEvery { contacts_api.create_contact(any()) } answers {
            calls.add(firstArg())
            if (calls.size == 1) throw ApiError.Conflict("exists", null)
            CreateContactResponse(id = "c_new", success = true)
        }
        val other = item("c_other", "Carol", "White", "carol@astermail.org")
        coEvery { contacts_api.list_contacts(any(), any(), any()) } answers {
            ListContactsResponse(items = listOf(other.copy(contact_token = calls.first().contact_token)))
        }

        drain_contacts()

        assertEquals(2, calls.size)
        assertTrue(calls[0].contact_token != calls[1].contact_token)
        assertTrue(cache.rows.values.any { it.id == "c_new" })
    }

    @Test
    fun `replay update merges server and local edits`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org", revision = 3))
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()
        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()
        online = true
        coEvery { contacts_api.get_contact("c_1") } returns
            item("c_1", "Alice", "Smith", "alice@astermail.org", mapOf("notes" to "met at conference"), revision = 5)
        val sent = slot<UpdateContactRequest>()
        coEvery { contacts_api.update_contact("c_1", capture(sent)) } returns UpdateContactResponse(success = true, revision = 6)

        drain_contacts()

        assertTrue(contact_ops().isEmpty())
        val merged = JSONObject(decrypt_text(sent.captured.encrypted_data, sent.captured.data_nonce))
        assertEquals("Alicia", merged.getString("first_name"))
        assertEquals("met at conference", merged.getString("notes"))
        assertEquals(5L, sent.captured.expected_revision)
        assertEquals(6L, cache.rows.values.single().revision)
    }

    @Test
    fun `replay update retries a revision conflict against the newer server copy`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org", revision = 1))
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()
        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()
        online = true
        var fetches = 0
        coEvery { contacts_api.get_contact("c_1") } answers {
            fetches++
            item("c_1", "Alice", "Smith", "alice@astermail.org", revision = fetches.toLong() + 1)
        }
        val revisions = mutableListOf<Long?>()
        coEvery { contacts_api.update_contact("c_1", any()) } answers {
            revisions.add(secondArg<UpdateContactRequest>().expected_revision)
            if (revisions.size == 1) throw ApiError.Conflict("stale", REVISION_CONFLICT_CODE)
            UpdateContactResponse(success = true, revision = 9)
        }

        drain_contacts()

        assertEquals(listOf<Long?>(2, 3), revisions)
        assertTrue(contact_ops().isEmpty())
    }

    @Test
    fun `replay update keeps the op after repeated conflicts`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()
        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()
        online = true
        coEvery { contacts_api.get_contact("c_1") } returns item("c_1", "Alice", "Smith", "alice@astermail.org")
        coEvery { contacts_api.update_contact("c_1", any()) } throws ApiError.Conflict("stale", REVISION_CONFLICT_CODE)

        val result = repo.replay_pending_op(ACCOUNT_A, contact_ops().single())

        assertTrue(result.exceptionOrNull() is IOException)
        coVerify(exactly = 3) { contacts_api.update_contact("c_1", any()) }
    }

    @Test
    fun `replay update recreates a contact the server deleted`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()
        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()
        online = true
        coEvery { contacts_api.get_contact("c_1") } throws ApiError.NotFoundError
        val sent = slot<CreateContactRequest>()
        coEvery { contacts_api.create_contact(capture(sent)) } returns CreateContactResponse(id = "c_2", success = true)

        drain_contacts()

        assertTrue(contact_ops().isEmpty())
        assertEquals("Alicia", JSONObject(decrypt_text(sent.captured.encrypted_data, sent.captured.data_nonce)).getString("first_name"))
        assertEquals("c_2", repo.resolve_contact_id("c_1"))
        assertEquals(listOf("c_2"), cache.rows.values.map { it.id })
    }

    @Test
    fun `replay of a trashed contact the server deleted finishes quietly`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()
        repo.trash_contact(alice).getOrThrow()
        online = true
        coEvery { contacts_api.get_contact("c_1") } throws ApiError.NotFoundError

        drain_contacts()

        assertTrue(contact_ops().isEmpty())
        assertTrue(cache.rows.isEmpty())
        coVerify(exactly = 0) { contacts_api.create_contact(any()) }
    }

    @Test
    fun `replay update that matches the server sends nothing`() = runBlocking {
        val original = item("c_1", "Alice", "Smith", "alice@astermail.org")
        seed_online(original)
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()
        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()
        online = true
        val local = contact_ops().single().payload.contact!!.local!!
        coEvery { contacts_api.get_contact("c_1") } returns
            ContactItem(id = "c_1", encrypted_data = local.encrypted_data, data_nonce = local.data_nonce, revision = 2)

        drain_contacts()

        assertTrue(contact_ops().isEmpty())
        coVerify(exactly = 0) { contacts_api.update_contact(any(), any()) }
    }

    @Test
    fun `replay delete keeps a contact the server changed`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        online = false
        repo.delete_contact("c_1").getOrThrow()
        online = true
        coEvery { contacts_api.get_contact("c_1") } returns item("c_1", "Alice", "Smith", "alice@new.example", revision = 2)

        drain_contacts()

        assertTrue(contact_ops().isEmpty())
        coVerify(exactly = 0) { contacts_api.delete_contact(any()) }
        assertEquals(listOf("c_1"), cache.rows.values.map { it.id })
        assertEquals(2L, cache.rows.values.single().revision)
    }

    @Test
    fun `replay delete removes an unchanged contact and is idempotent`() = runBlocking {
        val original = item("c_1", "Alice", "Smith", "alice@astermail.org")
        seed_online(original)
        online = false
        repo.delete_contact("c_1").getOrThrow()
        online = true
        coEvery { contacts_api.get_contact("c_1") } returns original
        coEvery { contacts_api.delete_contact("c_1") } returns DeleteContactResponse(success = true, deleted_count = 1)
        val action = contact_ops().single()

        assertTrue(repo.replay_pending_op(ACCOUNT_A, action).isSuccess)
        assertTrue(cache.rows.isEmpty())

        coEvery { contacts_api.get_contact("c_1") } throws ApiError.NotFoundError
        assertTrue(repo.replay_pending_op(ACCOUNT_A, action).isSuccess)
        coVerify(exactly = 1) { contacts_api.delete_contact("c_1") }
    }

    @Test
    fun `replay of a create retried after success does not duplicate`() = runBlocking {
        online = false
        repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow()
        online = true
        val action = contact_ops().single()
        val first = slot<CreateContactRequest>()
        coEvery { contacts_api.create_contact(capture(first)) } returns CreateContactResponse(id = "c_server", success = true)
        assertTrue(repo.replay_pending_op(ACCOUNT_A, action).isSuccess)

        coEvery { contacts_api.create_contact(any()) } throws ApiError.Conflict("exists", null)
        coEvery { contacts_api.list_contacts(any(), any(), any()) } answers {
            ListContactsResponse(
                items = listOf(
                    ContactItem(
                        id = "c_server",
                        contact_token = first.captured.contact_token,
                        encrypted_data = first.captured.encrypted_data,
                        data_nonce = first.captured.data_nonce,
                    ),
                ),
            )
        }
        assertTrue(repo.replay_pending_op(ACCOUNT_A, action).isSuccess)

        coVerify(exactly = 2) { contacts_api.create_contact(any()) }
        assertEquals(listOf("c_server"), cache.rows.values.map { it.id })
    }

    @Test
    fun `replay waits while another account is active`() = runBlocking {
        online = false
        repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow()
        online = true
        account = ACCOUNT_B

        val result = repo.replay_pending_op(ACCOUNT_A, contact_ops().single())

        assertTrue(result.exceptionOrNull() is IOException)
        coVerify(exactly = 0) { contacts_api.create_contact(any()) }
        assertTrue(repo.pending_contact_ids.first().isEmpty())
    }

    @Test
    fun `replay with a transient failure keeps the op`() = runBlocking {
        online = false
        repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow()
        online = true
        coEvery { contacts_api.create_contact(any()) } throws IOException("reset")

        drain_contacts()

        assertEquals(1, contact_ops().size)
        online = false
        assertEquals(listOf("Carol White"), repo.fetch_contacts().getOrThrow().map { it.name })
    }

    @Test
    fun `queued ops replay in order`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"))
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()
        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()
        repo.create_contact(Contact(id = "", name = "Carol White", email = "carol@astermail.org")).getOrThrow()
        online = true
        val order = mutableListOf<String>()
        coEvery { contacts_api.get_contact("c_1") } returns item("c_1", "Alice", "Smith", "alice@astermail.org")
        coEvery { contacts_api.update_contact("c_1", any()) } answers {
            order.add("update")
            UpdateContactResponse(success = true)
        }
        coEvery { contacts_api.create_contact(any()) } answers {
            order.add("create")
            CreateContactResponse(id = "c_2", success = true)
        }

        drain_contacts()

        assertEquals(listOf("update", "create"), order)
    }

    @Test
    fun `fetch while ops are pending keeps the local view`() = runBlocking {
        seed_online(item("c_1", "Alice", "Smith", "alice@astermail.org"), item("c_2", "Bob", "Jones", "bob@astermail.org"))
        online = false
        val alice = repo.fetch_contact("c_1").getOrThrow()
        repo.update_contact("c_1", alice.copy(name = "Alicia Smith")).getOrThrow()
        repo.delete_contact("c_2").getOrThrow()
        online = true

        val names = repo.fetch_contacts().getOrThrow().map { it.name }

        assertEquals(listOf("Alicia Smith"), names)
        assertNotNull(cache.rows[ACCOUNT_A to "c_2"])
    }

    @Test
    fun `no cache without a signed in account`() = runBlocking {
        account = null
        serve(item("c_1", "Alice", "Smith", "alice@astermail.org"))

        assertEquals(1, repo.fetch_contacts().getOrThrow().size)
        assertTrue(cache.rows.isEmpty())
        assertNull(contact_ops().firstOrNull())
    }

    @Test
    fun `coalesce rules`() {
        val create = ContactOp(PendingActionKind.contact_create, PendingContactPayload("x", local = cipher("a"), created_at = "t"))
        val update = ContactOp(PendingActionKind.contact_update, PendingContactPayload("x", local = cipher("b"), base = cipher("base"), base_revision = 2))
        val delete = ContactOp(PendingActionKind.contact_delete, PendingContactPayload("x"))

        val create_then_update = coalesce_contact_op(create, update) as ContactOpMerge.Replace
        assertEquals(PendingActionKind.contact_create, create_then_update.op.kind)
        assertEquals("b", create_then_update.op.payload.local!!.encrypted_data)
        assertEquals("t", create_then_update.op.payload.created_at)
        assertEquals(ContactOpMerge.Drop, coalesce_contact_op(create, delete))

        val later = ContactOp(PendingActionKind.contact_update, PendingContactPayload("x", local = cipher("c"), base = cipher("other"), base_revision = 7))
        val update_then_update = coalesce_contact_op(update, later) as ContactOpMerge.Replace
        assertEquals("c", update_then_update.op.payload.local!!.encrypted_data)
        assertEquals("base", update_then_update.op.payload.base!!.encrypted_data)
        assertEquals(2L, update_then_update.op.payload.base_revision)

        val update_then_delete = coalesce_contact_op(update, delete) as ContactOpMerge.Replace
        assertEquals(PendingActionKind.contact_delete, update_then_delete.op.kind)
        assertNull(update_then_delete.op.payload.local)
        assertEquals("base", update_then_delete.op.payload.base!!.encrypted_data)

        assertEquals(ContactOpMerge.Keep, coalesce_contact_op(delete, update))
    }

    @Test
    fun `merge keeps both sides and prefers local on the same field`() {
        val base = """{"first_name":"Alice","last_name":"Smith","notes":"a","phone":"1"}"""
        val local = """{"first_name":"Alicia","last_name":"Smith","notes":"a","phone":"2"}"""
        val remote = """{"first_name":"Alice","last_name":"Smith","notes":"b","phone":"3"}"""

        val merged = JSONObject(merge_contact_json(base, local, remote))

        assertEquals("Alicia", merged.getString("first_name"))
        assertEquals("b", merged.getString("notes"))
        assertEquals("2", merged.getString("phone"))
        assertEquals(local, merge_contact_json(null, local, remote))
    }

    @Test
    fun `merge keeps coupled fields together`() {
        val base = """{"emails":["a@x.org"],"email_entries":[{"value":"a@x.org"}]}"""
        val local = """{"emails":["b@x.org"],"email_entries":[{"value":"b@x.org"}]}"""
        val remote = """{"emails":["a@x.org"],"email_entries":[{"value":"a@x.org","label":"work"}]}"""

        val merged = JSONObject(merge_contact_json(base, local, remote))

        assertEquals("b@x.org", merged.getJSONArray("emails").getString(0))
        assertFalse(merged.getJSONArray("email_entries").getJSONObject(0).has("label"))
    }

    @Test
    fun `merge does not trash a contact the other side edited`() {
        val base = """{"first_name":"Alice"}"""
        val local_trash = """{"first_name":"Alice","deleted_at":"2026-01-01T00:00:00Z"}"""
        val remote_edit = """{"first_name":"Alicia"}"""
        assertFalse(JSONObject(merge_contact_json(base, local_trash, remote_edit)).has("deleted_at"))

        val local_edit = """{"first_name":"Ali"}"""
        val remote_trash = """{"first_name":"Alice","deleted_at":"2026-01-01T00:00:00Z"}"""
        val merged = JSONObject(merge_contact_json(base, local_edit, remote_trash))
        assertFalse(merged.has("deleted_at"))
        assertEquals("Ali", merged.getString("first_name"))

        assertTrue(JSONObject(merge_contact_json(base, local_trash, base)).has("deleted_at"))
    }

    @Test
    fun `equivalence ignores the envelope and empty values`() {
        assertTrue(contact_json_equivalent("""{"a":"1","_version":2,"b":""}""", """{"a":"1","_encrypted_at":"x","c":[]}"""))
        assertFalse(contact_json_equivalent("""{"a":"1"}""", """{"a":"2"}"""))
        assertTrue(contact_json_only_trash_changed("""{"a":"1"}""", """{"a":"1","deleted_at":"t"}"""))
        assertFalse(contact_json_only_trash_changed("""{"a":"1"}""", """{"a":"2","deleted_at":"t"}"""))
    }

    private fun cipher(data: String) = PendingContactCipher(encrypted_data = data, data_nonce = "n_$data")

    private companion object {
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
    }
}
