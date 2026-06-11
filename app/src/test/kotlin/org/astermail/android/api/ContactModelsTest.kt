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

package org.astermail.android.api

import org.astermail.android.api.contacts.ContactItem
import org.astermail.android.api.contacts.ContactsCountResponse
import org.astermail.android.api.contacts.CreateContactRequest
import org.astermail.android.api.contacts.CreateContactResponse
import org.astermail.android.api.contacts.DeleteContactResponse
import org.astermail.android.api.contacts.ListContactsResponse
import org.astermail.android.api.contacts.UpdateContactRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactModelsTest {

    @Test
    fun `ContactItem required id only`() {
        val item = ContactItem(id = "c1")
        assertEquals("c1", item.id)
        assertNull(item.contact_token)
        assertNull(item.encrypted_data)
        assertNull(item.data_nonce)
        assertNull(item.created_at)
        assertNull(item.updated_at)
    }

    @Test
    fun `ContactItem with all fields`() {
        val item = ContactItem(
            id = "c1",
            contact_token = "ct_abc",
            encrypted_data = "enc_data",
            data_nonce = "dn_123",
            created_at = "2026-01-01T00:00:00Z",
            updated_at = "2026-04-26T10:00:00Z",
        )
        assertEquals("ct_abc", item.contact_token)
        assertEquals("enc_data", item.encrypted_data)
        assertEquals("dn_123", item.data_nonce)
        assertEquals("2026-01-01T00:00:00Z", item.created_at)
        assertEquals("2026-04-26T10:00:00Z", item.updated_at)
    }

    @Test
    fun `ContactItem copy`() {
        val original = ContactItem(id = "c1", contact_token = "ct_1")
        val copied = original.copy(encrypted_data = "new_enc", data_nonce = "new_nonce")
        assertEquals("new_enc", copied.encrypted_data)
        assertEquals("new_nonce", copied.data_nonce)
        assertEquals("c1", copied.id)
        assertEquals("ct_1", copied.contact_token)
    }

    @Test
    fun `ListContactsResponse defaults`() {
        val response = ListContactsResponse()
        assertTrue(response.items.isEmpty())
        assertNull(response.next_cursor)
        assertFalse(response.has_more)
    }

    @Test
    fun `ListContactsResponse with items`() {
        val items = listOf(
            ContactItem(id = "c1"),
            ContactItem(id = "c2"),
            ContactItem(id = "c3"),
        )
        val response = ListContactsResponse(
            items = items,
            next_cursor = "cursor_next",
            has_more = true,
        )
        assertEquals(3, response.items.size)
        assertEquals("cursor_next", response.next_cursor)
        assertTrue(response.has_more)
    }

    @Test
    fun `ListContactsResponse with empty items`() {
        val response = ListContactsResponse(items = emptyList(), has_more = false)
        assertTrue(response.items.isEmpty())
        assertFalse(response.has_more)
    }

    @Test
    fun `ContactsCountResponse default`() {
        val response = ContactsCountResponse()
        assertEquals(0, response.count)
    }

    @Test
    fun `ContactsCountResponse with count`() {
        val response = ContactsCountResponse(count = 42)
        assertEquals(42, response.count)
    }

    @Test
    fun `CreateContactRequest stores fields`() {
        val request = CreateContactRequest(
            contact_token = "ct_new",
            encrypted_data = "enc",
            data_nonce = "nonce",
        )
        assertEquals("ct_new", request.contact_token)
        assertEquals("enc", request.encrypted_data)
        assertEquals("nonce", request.data_nonce)
    }

    @Test
    fun `CreateContactRequest copy`() {
        val original = CreateContactRequest(
            contact_token = "ct_1",
            encrypted_data = "enc_1",
            data_nonce = "n_1",
        )
        val copied = original.copy(encrypted_data = "enc_2")
        assertEquals("enc_2", copied.encrypted_data)
        assertEquals("ct_1", copied.contact_token)
    }

    @Test
    fun `CreateContactResponse defaults`() {
        val response = CreateContactResponse()
        assertNull(response.id)
        assertFalse(response.success)
    }

    @Test
    fun `CreateContactResponse with values`() {
        val response = CreateContactResponse(id = "c_new", success = true)
        assertEquals("c_new", response.id)
        assertTrue(response.success)
    }

    @Test
    fun `UpdateContactRequest stores fields`() {
        val request = UpdateContactRequest(
            encrypted_data = "updated_enc",
            data_nonce = "updated_nonce",
        )
        assertEquals("updated_enc", request.encrypted_data)
        assertEquals("updated_nonce", request.data_nonce)
    }

    @Test
    fun `UpdateContactRequest copy`() {
        val original = UpdateContactRequest(encrypted_data = "a", data_nonce = "b")
        val copied = original.copy(data_nonce = "c")
        assertEquals("a", copied.encrypted_data)
        assertEquals("c", copied.data_nonce)
    }

    @Test
    fun `DeleteContactResponse defaults`() {
        val response = DeleteContactResponse()
        assertFalse(response.success)
        assertEquals(0, response.deleted_count)
    }

    @Test
    fun `DeleteContactResponse with values`() {
        val response = DeleteContactResponse(success = true, deleted_count = 1)
        assertTrue(response.success)
        assertEquals(1, response.deleted_count)
    }

    @Test
    fun `ContactItem equality`() {
        val a = ContactItem(id = "c1", contact_token = "ct_1")
        val b = ContactItem(id = "c1", contact_token = "ct_1")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
