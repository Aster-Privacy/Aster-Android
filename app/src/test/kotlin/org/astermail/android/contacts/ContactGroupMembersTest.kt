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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.contacts.ContactsApi
import org.astermail.android.api.contacts.GroupMembersRequest
import org.astermail.android.api.contacts.GroupMembersResponse
import org.astermail.android.api.contacts.GroupMembershipEntry
import org.astermail.android.api.contacts.SuccessResponse
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.contacts.GroupViewBody
import org.astermail.android.ui.contacts.group_add_candidates
import org.astermail.android.ui.contacts.group_view_body
import org.astermail.android.ui.contacts.group_view_is_gone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactGroupMembersTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ContactsRepository
    private lateinit var vm: ContactsViewModel

    private val alpha = ContactGroup(id = "g_alpha", name = "Alpha", color = "#ef4444", contact_count = 4)
    private val beta = ContactGroup(id = "g_beta", name = "Beta", color = "#22c55e", contact_count = 0)

    private fun contact(id: String, name: String, email: String) = Contact(id = id, name = name, email = email)

    private val active_contacts = listOf(
        contact("c_zed", "zed", "zed@astermail.org"),
        contact("c_amy", "Amy", "amy@astermail.org"),
        contact("c_bob", "", "bob@astermail.org"),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        vm = ContactsViewModel(repository, mockk(relaxed = true))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun stub_loaded(memberships: Map<String, Set<String>>) {
        coEvery { repository.fetch_contacts() } returns Result.success(active_contacts)
        coEvery { repository.list_contact_groups() } returns Result.success(listOf(alpha, beta))
        coEvery { repository.list_group_memberships() } returns Result.success(memberships)
    }

    @Test
    fun `member count ignores trashed and undecryptable members and matches the detail list`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed", "c_amy", "c_trashed", "c_undecryptable")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.memberships_ready)
        assertEquals(2, state.group_member_count(alpha))
        assertEquals(state.group_member_count(alpha), state.group_members("g_alpha").size)
        assertEquals(0, state.group_member_count(beta))
    }

    @Test
    fun `member count falls back to the server count until contacts and memberships load`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed")))
        vm.load_groups()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.memberships_ready)
        assertEquals(4, state.group_member_count(alpha))
        assertEquals(GroupViewBody.loading, group_view_body(state, "g_alpha"))
    }

    @Test
    fun `members are sorted by display name ignoring case`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed", "c_amy", "c_bob")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()

        assertEquals(listOf("c_amy", "c_bob", "c_zed"), vm.state.value.group_members("g_alpha").map { it.id })
    }

    @Test
    fun `adding members merges them into the open group`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()
        coEvery { repository.add_contacts_to_group("g_alpha", any()) } returns Result.success(2)
        coEvery { repository.list_group_memberships() } returns
            Result.success(mapOf("g_alpha" to setOf("c_zed", "c_amy", "c_bob")))

        var added = -1
        vm.add_contacts_to_group("g_alpha", listOf("c_amy", "c_bob", "c_amy")) { added = it }
        advanceUntilIdle()

        assertEquals(2, added)
        coVerify { repository.add_contacts_to_group("g_alpha", listOf("c_amy", "c_bob")) }
        assertEquals(setOf("c_zed", "c_amy", "c_bob"), vm.state.value.group_member_ids("g_alpha"))
        assertFalse(vm.state.value.is_bulk_working)
    }

    @Test
    fun `adding members failure keeps membership and reports zero`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()
        coEvery { repository.add_contacts_to_group(any(), any()) } returns Result.failure(RuntimeException("boom"))

        var added = -1
        vm.add_contacts_to_group("g_alpha", listOf("c_amy")) { added = it }
        advanceUntilIdle()

        assertEquals(0, added)
        assertEquals(setOf("c_zed"), vm.state.value.group_member_ids("g_alpha"))
        assertFalse(vm.state.value.is_bulk_working)
    }

    @Test
    fun `removing a member drops it from the group`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed", "c_amy")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()
        coEvery { repository.remove_contact_from_group("c_amy", "g_alpha") } returns
            Result.success(SuccessResponse(success = true))
        coEvery { repository.list_group_memberships() } returns Result.success(mapOf("g_alpha" to setOf("c_zed")))

        var ok = false
        vm.remove_contact_from_group("g_alpha", "c_amy") { ok = it }
        advanceUntilIdle()

        assertTrue(ok)
        assertEquals(listOf("c_zed"), vm.state.value.group_members("g_alpha").map { it.id })
    }

    @Test
    fun `last member removed shows the empty state`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()
        assertEquals(GroupViewBody.members, group_view_body(vm.state.value, "g_alpha"))
        coEvery { repository.remove_contact_from_group(any(), any()) } returns Result.success(SuccessResponse(true))
        coEvery { repository.list_group_memberships() } returns Result.success(emptyMap())

        vm.remove_contact_from_group("g_alpha", "c_zed")
        advanceUntilIdle()

        assertEquals(GroupViewBody.empty, group_view_body(vm.state.value, "g_alpha"))
    }

    @Test
    fun `deleting a group removes its memberships and marks the view gone`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()
        assertFalse(group_view_is_gone(vm.state.value, "g_alpha"))
        coEvery { repository.delete_contact_group("g_alpha") } returns Result.success(SuccessResponse(true))

        vm.delete_group("g_alpha")
        advanceUntilIdle()

        assertNull(vm.state.value.group_memberships?.get("g_alpha"))
        assertTrue(group_view_is_gone(vm.state.value, "g_alpha"))
    }

    @Test
    fun `group deleted on another device closes the view on next reload`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()
        coEvery { repository.list_contact_groups() } returns Result.success(listOf(beta))
        coEvery { repository.list_group_memberships() } returns Result.success(emptyMap())

        vm.load_groups()
        advanceUntilIdle()

        assertTrue(group_view_is_gone(vm.state.value, "g_alpha"))
    }

    @Test
    fun `rename on another device updates the open group`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()
        coEvery { repository.list_contact_groups() } returns
            Result.success(listOf(alpha.copy(name = "Alpha renamed", color = "#3b82f6"), beta))

        vm.load_groups()
        advanceUntilIdle()

        assertEquals("Alpha renamed", vm.state.value.group_by_id("g_alpha")?.name)
        assertEquals("#3b82f6", vm.state.value.group_by_id("g_alpha")?.color)
    }

    @Test
    fun `failed reload keeps the open group instead of closing it`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()
        coEvery { repository.list_contact_groups() } returns Result.failure(RuntimeException("offline"))
        coEvery { repository.list_group_memberships() } returns Result.failure(RuntimeException("offline"))

        vm.load_groups()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.groups_load_failed)
        assertFalse(group_view_is_gone(state, "g_alpha"))
        assertEquals(GroupViewBody.members, group_view_body(state, "g_alpha"))
    }

    @Test
    fun `first load failure shows the error state`() = runTest {
        coEvery { repository.fetch_contacts() } returns Result.success(active_contacts)
        coEvery { repository.list_contact_groups() } returns Result.failure(RuntimeException("offline"))
        coEvery { repository.list_group_memberships() } returns Result.failure(RuntimeException("offline"))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(group_view_is_gone(state, "g_alpha"))
        assertEquals(GroupViewBody.failed, group_view_body(state, "g_alpha"))
    }

    @Test
    fun `reload requested while one is in flight runs again with fresh data`() = runTest {
        val gate = CompletableDeferred<Result<List<ContactGroup>>>()
        coEvery { repository.fetch_contacts() } returns Result.success(active_contacts)
        coEvery { repository.list_contact_groups() } coAnswers { gate.await() }
        coEvery { repository.list_group_memberships() } returns Result.success(emptyMap())
        vm.load_groups()
        advanceUntilIdle()
        vm.load_groups()
        coEvery { repository.list_contact_groups() } returns Result.success(listOf(beta))
        gate.complete(Result.success(listOf(alpha, beta)))
        advanceUntilIdle()

        assertEquals(listOf("g_beta"), vm.state.value.groups.map { it.id })
        coVerify(exactly = 2) { repository.list_contact_groups() }
    }

    @Test
    fun `add candidates exclude current members and match name or email`() = runTest {
        stub_loaded(mapOf("g_alpha" to setOf("c_zed")))
        vm.load_contacts()
        vm.load_groups()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("c_amy", "c_bob"), group_add_candidates(state, "g_alpha", "").map { it.id })
        assertEquals(listOf("c_bob"), group_add_candidates(state, "g_alpha", "BOB@").map { it.id })
        assertTrue(group_add_candidates(state, "g_alpha", "zed").isEmpty())
    }

    @Test
    fun `membership map groups contact ids by group`() {
        val map = group_membership_map(
            listOf(
                GroupMembershipEntry("g_alpha", "c_1"),
                GroupMembershipEntry("g_beta", "c_2"),
                GroupMembershipEntry("g_alpha", "c_3"),
                GroupMembershipEntry("g_alpha", "c_1"),
            ),
        )
        assertEquals(mapOf("g_alpha" to setOf("c_1", "c_3"), "g_beta" to setOf("c_2")), map)
    }

    @Test
    fun `bulk add sends batches of at most 500 and sums changes`() = runTest {
        val api = mockk<ContactsApi>(relaxed = true)
        val requests = mutableListOf<GroupMembersRequest>()
        coEvery { api.add_contacts_to_group("g_alpha", capture(requests)) } answers {
            GroupMembersResponse(success = true, changed = secondArg<GroupMembersRequest>().contact_ids.size.toLong())
        }
        val repo = ContactsRepository(api, mockk<SessionKeyStore>(relaxed = true))
        val ids = (1..1201).map { "c_$it" } + "c_1"

        val result = repo.add_contacts_to_group("g_alpha", ids)

        assertEquals(1201, result.getOrNull())
        assertEquals(listOf(500, 500, 201), requests.map { it.contact_ids.size })
    }
}
