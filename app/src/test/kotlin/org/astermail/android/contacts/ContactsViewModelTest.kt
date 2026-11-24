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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.contacts.CreateContactResponse
import org.astermail.android.api.contacts.CreateContactGroupResponse
import org.astermail.android.api.contacts.DeleteContactResponse
import org.astermail.android.api.contacts.SuccessResponse
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
    private lateinit var context: android.content.Context
    private lateinit var vm: ContactsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.list_group_memberships() } returns Result.success(emptyMap())
        context = mockk(relaxed = true)
        every { context.getString(org.astermail.android.R.string.error_no_connection) } returns
            "Could not connect to the server. Check your internet connection."
        every { context.getString(org.astermail.android.R.string.error_timeout) } returns
            "Connection timed out. Please try again."
        every { context.getString(org.astermail.android.R.string.error_ssl) } returns
            "Secure connection failed. Please try again."
        every { context.getString(org.astermail.android.R.string.something_went_wrong) } returns
            "Something went wrong"
        vm = ContactsViewModel(repository, context)
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

    private val updated_ids = java.util.Collections.synchronizedList(mutableListOf<String>())

    private fun import_file_over(existing: Contact, mark_constant_sealed: Boolean) {
        coEvery { repository.fetch_contacts() } returns Result.success(listOf(existing))
        coEvery { repository.update_contact(any(), any()) } answers {
            updated_ids.add(firstArg())
            Result.success(Unit)
        }
        if (mark_constant_sealed) {
            val method = ContactsRepository::class.java.methods.firstOrNull { it.name == "is_constant_sealed" }
            if (method != null) every { method.invoke(repository, existing.id) } returns true
        }
        val vcard = listOf(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "FN:Alice Smith",
            "N:Smith;Alice;;;",
            "EMAIL;TYPE=HOME:alice@astermail.org",
            "EMAIL;TYPE=WORK:alice@work.example",
            "END:VCARD",
            "",
        ).joinToString("\r\n")
        val resolver = mockk<android.content.ContentResolver>(relaxed = true)
        val uri = mockk<android.net.Uri>(relaxed = true)
        every { context.contentResolver } returns resolver
        every { resolver.query(uri, any(), any(), any(), any()) } returns null
        every { uri.lastPathSegment } returns "contacts.vcf"
        every { resolver.openInputStream(uri) } answers { vcard.toByteArray(Charsets.UTF_8).inputStream() }
        vm.import_contacts_from_file(context, uri)
    }

    private fun device_cursor(columns: List<String>, rows: List<List<Any?>>): android.database.Cursor {
        val cursor = mockk<android.database.Cursor>(relaxed = true)
        var position = -1
        every { cursor.getColumnIndex(any()) } answers { columns.indexOf(firstArg<String>()) }
        every { cursor.moveToNext() } answers { position++; position < rows.size }
        every { cursor.getString(any()) } answers { rows[position][firstArg<Int>()]?.toString() }
        every { cursor.getInt(any()) } answers { (rows[position][firstArg<Int>()] as? Int) ?: 0 }
        every { cursor.isNull(any()) } answers { rows[position][firstArg<Int>()] == null }
        return cursor
    }

    private fun sync_device_over(existing: Contact, saved_baselines: MutableList<Set<String>>) {
        coEvery { repository.fetch_contacts() } returns Result.success(listOf(existing))
        every { repository.current_account() } returns "account_1"
        every { repository.is_constant_sealed(existing.id) } returns true
        coEvery { repository.update_contact(any(), any()) } answers {
            updated_ids.add(firstArg())
            Result.success(Unit)
        }
        val app_context = mockk<android.content.Context>(relaxed = true)
        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        val editor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { context.applicationContext } returns app_context
        every { app_context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getStringSet(any(), any()) } returns emptySet()
        every { prefs.edit() } returns editor
        every { editor.putStringSet(any(), any()) } answers {
            saved_baselines.add(secondArg<Set<String>>().toSet())
            editor
        }
        val resolver = mockk<android.content.ContentResolver>(relaxed = true)
        every { context.contentResolver } returns resolver
        val email_type = android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
        every { resolver.query(any(), any(), any(), any(), any()) } answers {
            val projection = secondArg<Array<String>?>().orEmpty().toList()
            when {
                android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY in projection -> device_cursor(
                    listOf("_id", android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, "photo_id"),
                    listOf(listOf("7", "Alice Smith", null)),
                )
                android.provider.ContactsContract.Data.MIMETYPE in projection -> device_cursor(
                    listOf("contact_id", android.provider.ContactsContract.Data.MIMETYPE, "data1", "data2"),
                    listOf(
                        listOf("7", email_type, "alice@astermail.org", 1),
                        listOf("7", email_type, "alice@work.example", 2),
                    ),
                )
                else -> device_cursor(listOf("contact_id"), listOf(listOf("7")))
            }
        }
        vm.sync_device_contacts(context)
    }

    private fun kotlinx.coroutines.test.TestScope.await_sync() {
        repeat(500) {
            advanceUntilIdle()
            if (!vm.state.value.is_syncing) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `a device sync that skips only sealed contacts still records the device baseline`() = runTest {
        val saved_baselines = java.util.Collections.synchronizedList(mutableListOf<Set<String>>())
        sync_device_over(fake_contact(), saved_baselines)
        await_sync()

        assertFalse(vm.state.value.is_syncing)
        assertTrue("a sealed contact must not be merged", updated_ids.isEmpty())
        assertEquals(
            "the device baseline must be saved when every pending merge went through",
            1,
            saved_baselines.size,
        )
        assertTrue(saved_baselines.single().isNotEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.await_transfer() {
        repeat(500) {
            advanceUntilIdle()
            if (!vm.state.value.is_transferring) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `importing a file merges new entries into a stored contact`() = runTest {
        import_file_over(fake_contact(), mark_constant_sealed = false)
        await_transfer()

        assertEquals(listOf("c_1"), updated_ids)
    }

    @Test
    fun `importing a file never merges into a contact sealed under a public key`() = runTest {
        import_file_over(fake_contact(), mark_constant_sealed = true)
        await_transfer()

        assertFalse(vm.state.value.is_transferring)
        assertTrue(
            "an automatic merge must not reseal a contact that opened under a public key",
            updated_ids.isEmpty(),
        )
    }

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
        assertEquals("Connection timed out. Please try again.", state.error)
        assertTrue(state.contacts.isEmpty())
    }

    @Test
    fun `load_contacts error with null message uses fallback`() = runTest {
        coEvery { repository.fetch_contacts() } returns
            Result.failure(RuntimeException())

        vm.load_contacts()
        advanceUntilIdle()

        assertEquals("Something went wrong", vm.state.value.error)
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
        assertTrue(vm.state.value.contact_loading)

        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.contact_loading)
        assertNull(state.error)
        assertEquals("c_42", state.selected_contact?.id)
        assertEquals("Diana Prince", state.selected_contact?.name)
    }

    @Test
    fun `load_contact error sets error message`() = runTest {
        coEvery { repository.fetch_contact("c_999") } returns
            Result.failure(org.astermail.android.api.ApiError.UnknownError("not found"))

        vm.load_contact("c_999")
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.contact_loading)
        assertEquals("not found", state.error)
        assertNull(state.selected_contact)
    }

    @Test
    fun `load_contact error with null message uses fallback`() = runTest {
        coEvery { repository.fetch_contact("c_1") } returns
            Result.failure(RuntimeException())

        vm.load_contact("c_1")
        advanceUntilIdle()

        assertEquals("Something went wrong", vm.state.value.error)
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
            Result.failure(org.astermail.android.api.ApiError.UnknownError("server error"))

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

        assertEquals("Something went wrong", vm.state.value.error)
    }

    @Test
    fun `save_contact update failure sets error`() = runTest {
        val contact = fake_contact("c_1")
        coEvery { repository.update_contact("c_1", contact) } returns
            Result.failure(org.astermail.android.api.ApiError.UnknownError("forbidden"))

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
        coEvery { repository.trash_contact(any()) } returns Result.success(Unit)

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
        assertEquals(listOf("c_2"), state.trashed_contacts.map { it.id })
        coVerify(exactly = 0) { repository.delete_contact("c_2") }
    }

    @Test
    fun `delete_contact failure sets error`() = runTest {
        coEvery { repository.delete_contact("c_1") } returns
            Result.failure(org.astermail.android.api.ApiError.UnknownError("unauthorized"))

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

        assertEquals("Something went wrong", vm.state.value.error)
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
            Result.failure(org.astermail.android.api.ApiError.UnknownError("bad request"))

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
        coEvery { repository.trash_contact(any()) } returns Result.success(Unit)

        vm.load_contacts()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.contacts.size)

        vm.delete_contact("c_1")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.contacts.size)
        assertEquals("c_2", state.contacts[0].id)
        assertEquals(listOf("c_1"), state.trashed_contacts.map { it.id })
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
            Result.failure(org.astermail.android.api.ApiError.UnknownError("first failure")),
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

    @Test
    fun `save_contact double tap creates only one contact`() = runTest {
        val new_contact = fake_contact("", "New Person", "new@astermail.org")
        coEvery { repository.create_contact(new_contact) } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(CreateContactResponse(id = "c_new", success = true))
        }
        coEvery { repository.fetch_contacts() } returns Result.success(emptyList())

        vm.save_contact(new_contact)
        vm.save_contact(new_contact)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.create_contact(new_contact) }
    }

    @Test
    fun `delete_contact double tap deletes only once`() = runTest {
        coEvery { repository.delete_contact("c_1") } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(DeleteContactResponse(success = true, deleted_count = 1))
        }

        vm.delete_contact("c_1")
        vm.delete_contact("c_1")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.delete_contact("c_1") }
    }

    @Test
    fun `load_contacts still runs while a save is in flight`() = runTest {
        val new_contact = fake_contact("", "New Person", "new@astermail.org")
        coEvery { repository.create_contact(new_contact) } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(CreateContactResponse(id = "c_new", success = true))
        }
        coEvery { repository.fetch_contacts() } returns Result.success(listOf(fake_contact()))

        vm.save_contact(new_contact)
        vm.load_contacts()
        advanceUntilIdle()

        assertEquals(1, vm.state.value.contacts.size)
    }

    @Test
    fun `select_tab groups loads groups and clears the selection`() = runTest {
        val groups = listOf(ContactGroup(id = "g_1", name = "Work", color = "#6366f1", contact_count = 2))
        coEvery { repository.list_contact_groups() } returns Result.success(groups)
        coEvery { repository.fetch_contacts() } returns Result.success(listOf(fake_contact()))

        vm.load_contacts()
        advanceUntilIdle()
        vm.toggle_selection("c_1")
        vm.select_tab(ContactsTab.GROUPS)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ContactsTab.GROUPS, state.tab)
        assertTrue(state.selected_ids.isEmpty())
        assertEquals(1, state.groups.size)
        assertEquals("Work", state.groups[0].name)
    }

    @Test
    fun `toggle_selection adds and removes a contact`() {
        vm.toggle_selection("c_1")
        assertTrue(vm.state.value.is_selecting)
        assertEquals(setOf("c_1"), vm.state.value.selected_ids)

        vm.toggle_selection("c_2")
        assertEquals(setOf("c_1", "c_2"), vm.state.value.selected_ids)

        vm.toggle_selection("c_1")
        assertEquals(setOf("c_2"), vm.state.value.selected_ids)

        vm.clear_selection()
        assertFalse(vm.state.value.is_selecting)
    }

    @Test
    fun `load_contacts finds duplicate clusters and drops stale selections`() = runTest {
        coEvery { repository.fetch_contacts() } returns Result.success(
            listOf(
                fake_contact("c_1", "Alice Smith", "alice@astermail.org"),
                fake_contact("c_2", "Alice S", "ALICE@astermail.org"),
                fake_contact("c_3", "Bob Jones", "bob@astermail.org"),
            ),
        )

        vm.toggle_selection("c_gone")
        vm.load_contacts()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.duplicate_clusters.size)
        assertEquals(2, state.duplicate_count)
        assertTrue(state.selected_ids.isEmpty())
    }

    @Test
    fun `dismiss_duplicates hides the banner`() {
        vm.dismiss_duplicates()
        assertTrue(vm.state.value.duplicates_dismissed)
    }

    @Test
    fun `delete_selection trashes and prunes the list`() = runTest {
        coEvery { repository.fetch_contacts() } returns Result.success(
            listOf(
                fake_contact("c_1", "Alice Smith", "alice@astermail.org"),
                fake_contact("c_2", "Bob Jones", "bob@astermail.org"),
            ),
        )
        coEvery { repository.trash_contact(any()) } returns Result.success(Unit)

        vm.load_contacts()
        advanceUntilIdle()
        vm.toggle_selection("c_1")

        var reported = false
        vm.delete_selection { ok -> reported = ok }
        advanceUntilIdle()

        assertTrue(reported)
        coVerify(exactly = 1) { repository.trash_contact(match { it.id == "c_1" }) }
        coVerify(exactly = 0) { repository.bulk_delete_contacts(any()) }
        val state = vm.state.value
        assertEquals(listOf("c_2"), state.contacts.map { it.id })
        assertEquals(listOf("c_1"), state.trashed_contacts.map { it.id })
        assertTrue(state.selected_ids.isEmpty())
        assertFalse(state.is_bulk_working)
    }

    @Test
    fun `delete_selection trashes every selected contact and clears progress`() = runTest {
        val many = (1..25).map { fake_contact("c_$it", "Name $it", "n$it@astermail.org") }
        coEvery { repository.fetch_contacts() } returns Result.success(many)
        coEvery { repository.trash_contact(any()) } returns Result.success(Unit)

        vm.load_contacts()
        advanceUntilIdle()
        vm.set_selection(many.map { it.id }.toSet())

        vm.delete_selection()
        advanceUntilIdle()

        coVerify(exactly = 25) { repository.trash_contact(any()) }
        coVerify(exactly = 0) { repository.bulk_delete_contacts(any()) }
        val state = vm.state.value
        assertTrue(state.contacts.isEmpty())
        assertEquals(25, state.trashed_contacts.size)
        assertEquals(0, state.bulk_total)
        assertEquals(0, state.bulk_done)
        assertFalse(state.is_bulk_working)
    }

    @Test
    fun `delete_selection does nothing without a selection`() = runTest {
        var reported = true
        vm.delete_selection { ok -> reported = ok }
        advanceUntilIdle()

        assertFalse(reported)
        coVerify(exactly = 0) { repository.bulk_delete_contacts(any()) }
    }

    @Test
    fun `add_selection_to_group adds every selected contact and clears the selection`() = runTest {
        coEvery { repository.fetch_contacts() } returns Result.success(
            listOf(
                fake_contact("c_1", "Alice Smith", "alice@astermail.org"),
                fake_contact("c_2", "Bob Jones", "bob@astermail.org"),
            ),
        )
        coEvery { repository.list_contact_groups() } returns Result.success(emptyList())
        coEvery { repository.add_contact_to_group(any(), "g_1") } returns
            Result.success(SuccessResponse(success = true))

        vm.load_contacts()
        advanceUntilIdle()
        vm.toggle_selection("c_1")
        vm.toggle_selection("c_2")

        var added = 0
        vm.add_selection_to_group("g_1") { count -> added = count }
        advanceUntilIdle()

        assertEquals(2, added)
        coVerify(exactly = 1) { repository.add_contact_to_group("c_1", "g_1") }
        coVerify(exactly = 1) { repository.add_contact_to_group("c_2", "g_1") }
        assertTrue(vm.state.value.selected_ids.isEmpty())
    }

    @Test
    fun `create_group creates and reloads the group list`() = runTest {
        coEvery { repository.create_contact_group("Work", "#6366f1") } returns
            Result.success(CreateContactGroupResponse(id = "g_1"))
        coEvery { repository.list_contact_groups() } returns Result.success(
            listOf(ContactGroup(id = "g_1", name = "Work", color = "#6366f1", contact_count = 0)),
        )

        var created = false
        vm.create_group("Work", "#6366f1") { ok -> created = ok }
        advanceUntilIdle()

        assertTrue(created)
        assertEquals(1, vm.state.value.groups.size)
        assertFalse(vm.state.value.is_bulk_working)
    }

    @Test
    fun `create_group from add to group adds every selected contact to the new group`() = runTest {
        val ids = listOf("c_1", "c_2", "c_3", "c_4", "c_5")
        coEvery { repository.fetch_contacts() } returns Result.success(
            ids.mapIndexed { index, id -> fake_contact(id, "Person $index", "p$index@astermail.org") },
        )
        coEvery { repository.create_contact_group("Friends", "#6366f1") } returns
            Result.success(CreateContactGroupResponse(id = "g_new"))
        coEvery { repository.add_contact_to_group(any(), "g_new") } returns
            Result.success(SuccessResponse(success = true))
        coEvery { repository.list_contact_groups() } returns Result.success(
            listOf(ContactGroup(id = "g_new", name = "Friends", color = "#6366f1", contact_count = 5)),
        )

        vm.load_contacts()
        advanceUntilIdle()
        ids.forEach { vm.toggle_selection(it) }

        var created = false
        vm.create_group("Friends", "#6366f1", add_selection = true) { ok -> created = ok }
        advanceUntilIdle()

        assertTrue(created)
        ids.forEach { id ->
            coVerify(exactly = 1) { repository.add_contact_to_group(id, "g_new") }
        }
        assertTrue(vm.state.value.selected_ids.isEmpty())
        assertFalse(vm.state.value.is_bulk_working)
    }

    @Test
    fun `create_group without add_selection leaves the selection out of the group`() = runTest {
        coEvery { repository.fetch_contacts() } returns Result.success(listOf(fake_contact("c_1")))
        coEvery { repository.create_contact_group("Work", "#6366f1") } returns
            Result.success(CreateContactGroupResponse(id = "g_1"))
        coEvery { repository.list_contact_groups() } returns Result.success(emptyList())

        vm.load_contacts()
        advanceUntilIdle()
        vm.toggle_selection("c_1")

        vm.create_group("Work", "#6366f1")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.add_contact_to_group(any(), any()) }
        assertEquals(setOf("c_1"), vm.state.value.selected_ids)
    }

    @Test
    fun `create_group keeps the selection when the group is not created`() = runTest {
        coEvery { repository.fetch_contacts() } returns Result.success(listOf(fake_contact("c_1")))
        coEvery { repository.create_contact_group("Friends", "#6366f1") } returns
            Result.failure(RuntimeException("boom"))

        vm.load_contacts()
        advanceUntilIdle()
        vm.toggle_selection("c_1")

        var created = true
        vm.create_group("Friends", "#6366f1", add_selection = true) { ok -> created = ok }
        advanceUntilIdle()

        assertFalse(created)
        coVerify(exactly = 0) { repository.add_contact_to_group(any(), any()) }
        assertEquals(setOf("c_1"), vm.state.value.selected_ids)
    }

    @Test
    fun `create_group rejects a blank name`() = runTest {
        var created = true
        vm.create_group("   ", "#6366f1") { ok -> created = ok }
        advanceUntilIdle()

        assertFalse(created)
        coVerify(exactly = 0) { repository.create_contact_group(any(), any()) }
    }

    @Test
    fun `delete_group removes it from state`() = runTest {
        coEvery { repository.list_contact_groups() } returns Result.success(
            listOf(
                ContactGroup(id = "g_1", name = "Work", color = "#6366f1", contact_count = 0),
                ContactGroup(id = "g_2", name = "Family", color = "#10b981", contact_count = 3),
            ),
        )
        coEvery { repository.delete_contact_group("g_1") } returns
            Result.success(SuccessResponse(success = true))

        vm.load_groups()
        advanceUntilIdle()
        vm.delete_group("g_1")
        advanceUntilIdle()

        assertEquals(listOf("g_2"), vm.state.value.groups.map { it.id })
    }

    @Test
    fun `merge_duplicates updates the primary and deletes the rest`() = runTest {
        val primary = fake_contact("c_1", "Alice Smith", "alice@astermail.org")
        val secondary = Contact(
            id = "c_2",
            name = "Alice S",
            email = "alice@astermail.org",
            phone = "+1 415 555 0142",
        )
        coEvery { repository.fetch_contacts() } returns Result.success(listOf(primary, secondary))
        coEvery { repository.update_contact("c_1", any()) } returns Result.success(Unit)
        coEvery { repository.bulk_delete_contacts(listOf("c_2")) } returns
            Result.success(DeleteContactResponse(success = true, deleted_count = 1))

        vm.load_contacts()
        advanceUntilIdle()

        var merged = false
        vm.merge_duplicates(listOf(primary, secondary)) { ok -> merged = ok }
        advanceUntilIdle()

        assertTrue(merged)
        coVerify(exactly = 1) {
            repository.update_contact("c_1", merge_contacts(listOf(primary, secondary)))
        }
        coVerify(exactly = 1) { repository.bulk_delete_contacts(listOf("c_2")) }
    }

    @Test
    fun `merge_duplicates needs at least two contacts`() = runTest {
        var merged = true
        vm.merge_duplicates(listOf(fake_contact())) { ok -> merged = ok }
        advanceUntilIdle()

        assertFalse(merged)
        coVerify(exactly = 0) { repository.update_contact(any(), any()) }
    }
}
