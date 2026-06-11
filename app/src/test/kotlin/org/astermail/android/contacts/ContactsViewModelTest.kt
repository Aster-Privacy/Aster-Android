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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.contacts.CreateContactResponse
import org.astermail.android.api.contacts.DeleteContactResponse
import org.astermail.android.ui.contacts.Contact
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ContactsRepository
    private lateinit var vm: ContactsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        vm = ContactsViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun fake_contact(
        id: String = "c_1",
        name: String = "Alice Smith",
        email: String = "alice@astermail.org",
    ): Contact = Contact(
        id = id,
        name = name,
        email = email,
    )

    @Test
    fun `initial state has correct defaults`() {
        val state = vm.state.value
        assertTrue(state.contacts.isEmpty())
        assertNull(state.selected_contact)
        assertFalse(state.is_loading)
        assertNull(state.error)
        assertFalse(state.save_success)
        assertFalse(state.delete_success)
    }

    @Test
    fun `load_contacts success populates contacts list`() = runTest {
        val contacts = listOf(
            fake_contact("c_1", "Alice Smith", "alice@astermail.org"),
            fake_contact("c_2", "Bob Jones", "bob@astermail.org"),
            fake_contact("c_3", "Carol White", "carol@astermail.org"),
        )
        coEvery { repository.fetch_contacts() } returns Result.success(contacts)

        vm.load_contacts()
        assertTrue(vm.state.value.is_loading)

        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertNull(state.error)
        assertEquals(3, state.contacts.size)
        assertEquals("c_1", state.contacts[0].id)
        assertEquals("c_2", state.contacts[1].id)
        assertEquals("c_3", state.contacts[2].id)
    }

    @Test
    fun `load_contacts success with empty list`() = runTest {
        coEvery { repository.fetch_contacts() } returns Result.success(emptyList())

        vm.load_contacts()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertNull(state.error)
        assertTrue(state.contacts.isEmpty())
    }

    @Test
    fun `load_contacts error sets error message`() = runTest {
        coEvery { repository.fetch_contacts() } returns
            Result.failure(RuntimeException("network timeout"))

        vm.load_contacts()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertEquals("network timeout", state.error)
        assertTrue(state.contacts.isEmpty())
    }

    @Test
    fun `load_contacts error with null message uses fallback`() = runTest {
        coEvery { repository.fetch_contacts() } returns
            Result.failure(RuntimeException())

        vm.load_contacts()
        advanceUntilIdle()

        assertEquals("failed to load contacts", vm.state.value.error)
    }

    @Test
    fun `load_contacts skips when already loading`() = runTest {
        coEvery { repository.fetch_contacts() } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(listOf(fake_contact()))
        }

        vm.load_contacts()
        vm.load_contacts()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetch_contacts() }
    }

    @Test
    fun `load_contact by id success sets selected_contact`() = runTest {
        val contact = fake_contact("c_42", "Diana Prince", "diana@astermail.org")
        coEvery { repository.fetch_contact("c_42") } returns Result.success(contact)

        vm.load_contact("c_42")
        assertTrue(vm.state.value.is_loading)

        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertNull(state.error)
        assertEquals("c_42", state.selected_contact?.id)
        assertEquals("Diana Prince", state.selected_contact?.name)
    }

    @Test
    fun `load_contact error sets error message`() = runTest {
        coEvery { repository.fetch_contact("c_999") } returns
            Result.failure(RuntimeException("not found"))

        vm.load_contact("c_999")
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertEquals("not found", state.error)
        assertNull(state.selected_contact)
    }

    @Test
    fun `load_contact error with null message uses fallback`() = runTest {
        coEvery { repository.fetch_contact("c_1") } returns
            Result.failure(RuntimeException())

        vm.load_contact("c_1")
        advanceUntilIdle()

        assertEquals("failed to load contact", vm.state.value.error)
    }

    @Test
    fun `save_contact new contact success sets save_success and reloads`() = runTest {
        val new_contact = fake_contact("", "New Person", "new@astermail.org")
        coEvery { repository.create_contact(new_contact) } returns
            Result.success(CreateContactResponse(id = "c_new", success = true))
        coEvery { repository.fetch_contacts() } returns
            Result.success(listOf(fake_contact("c_new", "New Person", "new@astermail.org")))

        vm.save_contact(new_contact)
        assertTrue(vm.state.value.is_loading)

        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.save_success)
        assertNull(state.error)
        coVerify { repository.create_contact(new_contact) }
        coVerify { repository.fetch_contacts() }
    }

    @Test
    fun `save_contact update existing contact success`() = runTest {
        val updated_contact = fake_contact("c_1", "Alice Updated", "alice.new@astermail.org")
        coEvery { repository.update_contact("c_1", updated_contact) } returns Result.success(Unit)
        coEvery { repository.fetch_contacts() } returns Result.success(listOf(updated_contact))

        vm.save_contact(updated_contact, existing_id = "c_1")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.save_success)
        assertNull(state.error)
        coVerify { repository.update_contact("c_1", updated_contact) }
        coVerify(exactly = 0) { repository.create_contact(any()) }
        coVerify { repository.fetch_contacts() }
    }

    @Test
    fun `save_contact failure sets error`() = runTest {
        val contact = fake_contact()
        coEvery { repository.create_contact(contact) } returns
            Result.failure(RuntimeException("server error"))

        vm.save_contact(contact)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertFalse(state.save_success)
        assertEquals("server error", state.error)
        coVerify(exactly = 0) { repository.fetch_contacts() }
    }

    @Test
    fun `save_contact failure with null message uses fallback`() = runTest {
        val contact = fake_contact()
        coEvery { repository.create_contact(contact) } returns
            Result.failure(RuntimeException())

        vm.save_contact(contact)
        advanceUntilIdle()

        assertEquals("failed to save contact", vm.state.value.error)
    }

    @Test
    fun `save_contact update failure sets error`() = runTest {
        val contact = fake_contact("c_1")
        coEvery { repository.update_contact("c_1", contact) } returns
            Result.failure(RuntimeException("forbidden"))

        vm.save_contact(contact, existing_id = "c_1")
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.save_success)
        assertEquals("forbidden", state.error)
    }

    @Test
    fun `delete_contact success sets delete_success and removes from list`() = runTest {
        val contacts = listOf(
            fake_contact("c_1", "Alice", "alice@astermail.org"),
            fake_contact("c_2", "Bob", "bob@astermail.org"),
            fake_contact("c_3", "Carol", "carol@astermail.org"),
        )
        coEvery { repository.fetch_contacts() } returns Result.success(contacts)
        coEvery { repository.delete_contact("c_2") } returns
            Result.success(DeleteContactResponse(success = true, deleted_count = 1))

        vm.load_contacts()
        advanceUntilIdle()
        assertEquals(3, vm.state.value.contacts.size)

        vm.delete_contact("c_2")
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertTrue(state.delete_success)
        assertNull(state.error)
        assertEquals(2, state.contacts.size)
        assertTrue(state.contacts.none { it.id == "c_2" })
        assertEquals("c_1", state.contacts[0].id)
        assertEquals("c_3", state.contacts[1].id)
    }

    @Test
    fun `delete_contact failure sets error`() = runTest {
        coEvery { repository.delete_contact("c_1") } returns
            Result.failure(RuntimeException("unauthorized"))

        vm.delete_contact("c_1")
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertFalse(state.delete_success)
        assertEquals("unauthorized", state.error)
    }

    @Test
    fun `delete_contact failure with null message uses fallback`() = runTest {
        coEvery { repository.delete_contact("c_1") } returns
            Result.failure(RuntimeException())

        vm.delete_contact("c_1")
        advanceUntilIdle()

        assertEquals("failed to delete contact", vm.state.value.error)
    }

    @Test
    fun `clear_flags resets save_success and delete_success and error`() = runTest {
        val contact = fake_contact()
        coEvery { repository.create_contact(contact) } returns
            Result.success(CreateContactResponse(id = "c_new", success = true))
        coEvery { repository.fetch_contacts() } returns Result.success(emptyList())

        vm.save_contact(contact)
        advanceUntilIdle()
        assertTrue(vm.state.value.save_success)

        vm.clear_flags()

        val state = vm.state.value
        assertFalse(state.save_success)
        assertFalse(state.delete_success)
        assertNull(state.error)
    }

    @Test
    fun `clear_flags resets error from failed operation`() = runTest {
        coEvery { repository.fetch_contacts() } returns
            Result.failure(RuntimeException("bad request"))

        vm.load_contacts()
        advanceUntilIdle()
        assertEquals("bad request", vm.state.value.error)

        vm.clear_flags()

        assertNull(vm.state.value.error)
    }

    @Test
    fun `sequential load then delete does not corrupt state`() = runTest {
        val contacts = listOf(
            fake_contact("c_1", "Alice", "alice@astermail.org"),
            fake_contact("c_2", "Bob", "bob@astermail.org"),
        )
        coEvery { repository.fetch_contacts() } returns Result.success(contacts)
        coEvery { repository.delete_contact("c_1") } returns
            Result.success(DeleteContactResponse(success = true, deleted_count = 1))

        vm.load_contacts()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.contacts.size)

        vm.delete_contact("c_1")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.contacts.size)
        assertEquals("c_2", state.contacts[0].id)
        assertTrue(state.delete_success)
    }

    @Test
    fun `save then load_contacts shows refreshed data`() = runTest {
        val original = listOf(fake_contact("c_1", "Alice", "alice@astermail.org"))
        val after_save = listOf(
            fake_contact("c_1", "Alice", "alice@astermail.org"),
            fake_contact("c_2", "Bob", "bob@astermail.org"),
        )
        val new_contact = fake_contact("", "Bob", "bob@astermail.org")

        coEvery { repository.create_contact(new_contact) } returns
            Result.success(CreateContactResponse(id = "c_2", success = true))
        coEvery { repository.fetch_contacts() } returnsMany
            listOf(Result.success(original), Result.success(after_save))

        vm.load_contacts()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.contacts.size)

        vm.save_contact(new_contact)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.contacts.size)
        assertTrue(vm.state.value.save_success)
    }

    @Test
    fun `loading state transitions correctly during load_contacts`() = runTest {
        coEvery { repository.fetch_contacts() } returns Result.success(listOf(fake_contact()))

        assertFalse(vm.state.value.is_loading)

        vm.load_contacts()
        assertTrue(vm.state.value.is_loading)

        advanceUntilIdle()
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `loading state transitions correctly during save_contact`() = runTest {
        val contact = fake_contact()
        coEvery { repository.create_contact(contact) } returns
            Result.success(CreateContactResponse(id = "c_new", success = true))
        coEvery { repository.fetch_contacts() } returns Result.success(emptyList())

        assertFalse(vm.state.value.is_loading)

        vm.save_contact(contact)
        assertTrue(vm.state.value.is_loading)
        assertFalse(vm.state.value.save_success)

        advanceUntilIdle()
    }

    @Test
    fun `loading state transitions correctly during delete_contact`() = runTest {
        coEvery { repository.delete_contact("c_1") } returns
            Result.success(DeleteContactResponse(success = true, deleted_count = 1))

        assertFalse(vm.state.value.is_loading)

        vm.delete_contact("c_1")
        assertTrue(vm.state.value.is_loading)
        assertFalse(vm.state.value.delete_success)

        advanceUntilIdle()
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `error is cleared before new load_contacts call`() = runTest {
        coEvery { repository.fetch_contacts() } returnsMany listOf(
            Result.failure(RuntimeException("first failure")),
            Result.success(listOf(fake_contact())),
        )

        vm.load_contacts()
        advanceUntilIdle()
        assertEquals("first failure", vm.state.value.error)

        vm.load_contacts()
        assertNull(vm.state.value.error)

        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.contacts.size)
    }

    @Test
    fun `delete_contact on nonexistent id still sets delete_success from repo`() = runTest {
        coEvery { repository.delete_contact("c_nonexistent") } returns
            Result.success(DeleteContactResponse(success = true, deleted_count = 0))

        vm.delete_contact("c_nonexistent")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.delete_success)
        assertTrue(state.contacts.isEmpty())
    }
}
