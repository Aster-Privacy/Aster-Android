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
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.contacts.ContactGroupEncrypted
import org.astermail.android.api.contacts.ContactItem
import org.astermail.android.api.contacts.ContactsApi
import org.astermail.android.api.contacts.GroupMembersRequest
import org.astermail.android.api.contacts.GroupMembershipChangeResponse
import org.astermail.android.api.contacts.ListContactGroupsResponse
import org.astermail.android.api.contacts.ListContactsResponse
import org.astermail.android.api.contacts.SuccessResponse
import org.astermail.android.api.contacts.UpdateContactRequest
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.contacts.GroupMembershipState
import org.astermail.android.ui.contacts.membership_state_of
import org.json.JSONObject
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

class ContactGroupsTest {

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

    private fun seal(plaintext: String): Pair<String, String> {
        val key = derive_test_key()
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Pair(
            java.util.Base64.getEncoder().encodeToString(ciphertext),
            java.util.Base64.getEncoder().encodeToString(nonce),
        )
    }

    private fun unseal(encrypted: String, nonce: String): String {
        val key = derive_test_key()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, java.util.Base64.getDecoder().decode(nonce)),
        )
        return String(cipher.doFinal(java.util.Base64.getDecoder().decode(encrypted)), Charsets.UTF_8)
    }

    private suspend fun load_one(json: String): Contact {
        val (data, nonce) = seal(json)
        val item = ContactItem(id = "c_1", encrypted_data = data, data_nonce = nonce)
        coEvery { contacts_api.list_contacts(limit = 100, cursor = null) } returns
            ListContactsResponse(items = listOf(item), has_more = false, next_cursor = null)
        return repo.fetch_contacts().getOrThrow()[0]
    }

    private fun capture_update(): CapturingSlot<UpdateContactRequest> {
        val request = slot<UpdateContactRequest>()
        coEvery { contacts_api.update_contact(any(), capture(request)) } returns Unit
        return request
    }

    private fun sent_json(request: CapturingSlot<UpdateContactRequest>): JSONObject =
        JSONObject(unseal(request.captured.encrypted_data, request.captured.data_nonce))

    @Test
    fun `parse reads groups from the blob`() = runTest {
        val contact = load_one(
            """{"first_name":"Ana","last_name":"Diaz","emails":["ana@astermail.org"],"groups":["g_1","g_2"]}""",
        )
        assertEquals(listOf("g_1", "g_2"), contact.groups)
    }

    @Test
    fun `parse skips blank group ids`() = runTest {
        val contact = load_one(
            """{"first_name":"Ana","last_name":"Diaz","emails":["ana@astermail.org"],"groups":["g_1","","  "]}""",
        )
        assertEquals(listOf("g_1"), contact.groups)
    }

    @Test
    fun `update preserves unknown blob fields`() = runTest {
        val contact = load_one(
            """{"first_name":"Ana","last_name":"Diaz","emails":["ana@astermail.org"],""" +
                """"nickname":"Annie","pronouns":"they/them",""" +
                """"email_entries":[{"value":"ana@astermail.org","label":"home"}],""" +
                """"related_people":[{"name":"Mo","label":"partner"}]}""",
        )
        val request = capture_update()
        repo.update_contact("c_1", contact).getOrThrow()
        val json = sent_json(request)
        assertEquals("Annie", json.optString("nickname"))
        assertEquals("they/them", json.optString("pronouns"))
        assertEquals(1, json.optJSONArray("email_entries")?.length())
        assertEquals("Mo", json.optJSONArray("related_people")?.optJSONObject(0)?.optString("name"))
    }

    @Test
    fun `update preserves emails beyond the first two`() = runTest {
        val contact = load_one(
            """{"first_name":"Ana","last_name":"Diaz","emails":["a@x.org","b@x.org","c@x.org","d@x.org"]}""",
        )
        val request = capture_update()
        repo.update_contact("c_1", contact).getOrThrow()
        val emails = sent_json(request).optJSONArray("emails")
        assertEquals(4, emails?.length())
        assertEquals("c@x.org", emails?.optString(2))
        assertEquals("d@x.org", emails?.optString(3))
    }

    @Test
    fun `update merges into an existing address object`() = runTest {
        val contact = load_one(
            """{"first_name":"Ana","last_name":"Diaz","emails":["a@x.org"],""" +
                """"address":{"street":"1 Main","city":"Lisbon","extended_address":"Floor 3"}}""",
        )
        val request = capture_update()
        repo.update_contact("c_1", contact).getOrThrow()
        val addr = sent_json(request).optJSONObject("address")
        assertEquals("1 Main", addr?.optString("street"))
        assertEquals("Lisbon", addr?.optString("city"))
        assertEquals("Floor 3", addr?.optString("extended_address"))
    }

    @Test
    fun `update writes a deduplicated groups array`() = runTest {
        val contact = load_one("""{"first_name":"Ana","last_name":"Diaz","emails":["a@x.org"]}""")
            .copy(groups = listOf("g_1", "g_1", "g_2"))
        val request = capture_update()
        repo.update_contact("c_1", contact).getOrThrow()
        val groups = sent_json(request).optJSONArray("groups")
        assertEquals(2, groups?.length())
        assertEquals("g_1", groups?.optString(0))
        assertEquals("g_2", groups?.optString(1))
    }

    @Test
    fun `update drops an empty groups array`() = runTest {
        val contact = load_one(
            """{"first_name":"Ana","last_name":"Diaz","emails":["a@x.org"],"groups":["g_1"]}""",
        ).copy(groups = emptyList())
        val request = capture_update()
        repo.update_contact("c_1", contact).getOrThrow()
        assertFalse(sent_json(request).has("groups"))
    }

    @Test
    fun `set_contact_groups writes the blob and both membership edges`() = runTest {
        val contact = load_one(
            """{"first_name":"Ana","last_name":"Diaz","emails":["a@x.org"],"groups":["g_old"]}""",
        )
        coEvery { contacts_api.add_contact_to_group(any(), any()) } returns SuccessResponse(success = true)
        coEvery { contacts_api.remove_contact_from_group(any(), any()) } returns SuccessResponse(success = true)
        val request = capture_update()

        val updated = repo.set_contact_groups(contact, listOf("g_new")).getOrThrow()

        assertEquals(listOf("g_new"), updated.groups)
        assertEquals("g_new", sent_json(request).optJSONArray("groups")?.optString(0))
        coVerify(exactly = 1) { contacts_api.add_contact_to_group("c_1", "g_new") }
        coVerify(exactly = 1) { contacts_api.remove_contact_from_group("c_1", "g_old") }
    }

    @Test
    fun `set_group_membership skips contacts already in the desired state`() = runTest {
        val already = Contact(id = "c_a", name = "A", email = "a@x.org", groups = listOf("g_1"))

        val result = repo.set_group_membership(listOf(already), "g_1", should_add = true).getOrThrow()

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { contacts_api.add_group_members(any(), any()) }
        coVerify(exactly = 0) { contacts_api.update_contact(any(), any()) }
    }

    @Test
    fun `set_group_membership batches the additions`() = runTest {
        val a = Contact(id = "c_a", name = "A", email = "a@x.org")
        val b = Contact(id = "c_b", name = "B", email = "b@x.org", groups = listOf("g_1"))
        val request = slot<GroupMembersRequest>()
        coEvery { contacts_api.add_group_members(any(), capture(request)) } returns
            GroupMembershipChangeResponse(success = true, changed = 1)
        capture_update()

        val result = repo.set_group_membership(listOf(a, b), "g_1", should_add = true).getOrThrow()

        assertEquals(1, result.size)
        assertEquals(listOf("g_1"), result[0].groups)
        assertEquals(listOf("c_a"), request.captured.contact_ids)
        coVerify(exactly = 1) { contacts_api.update_contact("c_a", any()) }
    }

    @Test
    fun `set_group_membership batches the removals`() = runTest {
        val a = Contact(id = "c_a", name = "A", email = "a@x.org", groups = listOf("g_1", "g_2"))
        val request = slot<GroupMembersRequest>()
        coEvery { contacts_api.remove_group_members(any(), capture(request)) } returns
            GroupMembershipChangeResponse(success = true, changed = 1)
        capture_update()

        val result = repo.set_group_membership(listOf(a), "g_1", should_add = false).getOrThrow()

        assertEquals(listOf("g_2"), result[0].groups)
        assertEquals(listOf("c_a"), request.captured.contact_ids)
    }

    @Test
    fun `list_contact_groups decrypts names and defaults the color`() = runTest {
        val (name, nonce) = seal("Family")
        coEvery { contacts_api.list_contact_groups() } returns ListContactGroupsResponse(
            groups = listOf(
                ContactGroupEncrypted(
                    id = "g_1",
                    group_token = "t",
                    encrypted_name = name,
                    name_nonce = nonce,
                    color = "",
                    sort_order = 3,
                    contact_count = 7,
                ),
            ),
        )

        val groups = repo.list_contact_groups().getOrThrow()

        assertEquals(1, groups.size)
        assertEquals("Family", groups[0].name)
        assertEquals(DEFAULT_CONTACT_GROUP_COLOR, groups[0].color)
        assertEquals(3, groups[0].sort_order)
        assertEquals(7, groups[0].contact_count)
    }

    @Test
    fun `unreadable group names are dropped`() = runTest {
        coEvery { contacts_api.list_contact_groups() } returns ListContactGroupsResponse(
            groups = listOf(
                ContactGroupEncrypted(
                    id = "g_bad",
                    group_token = "t",
                    encrypted_name = java.util.Base64.getEncoder().encodeToString(ByteArray(48)),
                    name_nonce = java.util.Base64.getEncoder().encodeToString(ByteArray(12)),
                    color = "#ff0000",
                ),
            ),
        )

        assertTrue(repo.list_contact_groups().getOrThrow().isEmpty())
    }

    @Test
    fun `membership_state_of reports none some and all`() {
        val a = Contact(id = "a", name = "A", email = "a@x.org", groups = listOf("g_1"))
        val b = Contact(id = "b", name = "B", email = "b@x.org")
        assertEquals(GroupMembershipState.all, membership_state_of(listOf(a), "g_1"))
        assertEquals(GroupMembershipState.some, membership_state_of(listOf(a, b), "g_1"))
        assertEquals(GroupMembershipState.none, membership_state_of(listOf(b), "g_1"))
        assertEquals(GroupMembershipState.none, membership_state_of(emptyList(), "g_1"))
    }
}
