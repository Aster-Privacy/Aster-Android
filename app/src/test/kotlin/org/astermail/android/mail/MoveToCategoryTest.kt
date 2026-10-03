//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.R
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.api.mail.MailUserStatsResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoveToCategoryTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: MailRepository
    private lateinit var vm: MailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        io.mockk.mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns dispatcher
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        val context = mockk<android.content.Context>(relaxed = true)
        every { context.getString(R.string.moved_to_category) } returns "Moved to category"
        every { context.getString(R.string.something_went_wrong) } returns "Something went wrong"
        every { context.getString(R.string.metadata_undecryptable_change) } returns "Cannot open details"
        repository = mockk(relaxed = true)
        val folder_cache_store = mockk<FolderCacheStore>(relaxed = true)
        every { folder_cache_store.cached_stats(any()) } returns null
        every { repository.send_result_events } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { repository.new_mail_events } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { repository.pending_undo_send } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        coEvery { repository.get_stats() } returns Result.success(MailUserStatsResponse())
        val identity_pins = mockk<org.astermail.android.mail.ratchet.RatchetIdentityPinStore>(relaxed = true)
        every { identity_pins.unacknowledged_changes } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        vm = MailViewModel(
            context,
            repository,
            mockk(relaxed = true),
            folder_cache_store,
            identity_pins,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
    }

    @After
    fun teardown() {
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(Dispatchers::class)
        Dispatchers.resetMain()
    }

    private fun deal(id: String = "deal-1") = InboxItem(
        id = id,
        thread_token = "thread-$id",
        thread_message_count = 1,
        sender_name = "Example Deals",
        sender_email = "offers@deals.example.test",
        subject = "Weekend offer",
        preview = "Synthetic preview",
        timestamp = "2026-09-30T08:00:00Z",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        category = "promotions",
        raw_item = MailItem(
            id = id,
            item_type = "received",
            encrypted_metadata = "old-blob",
            metadata_nonce = "old-nonce",
            metadata = MailItemMetadata(is_pinned = true, size_bytes = 4096),
        ),
    )

    private fun kotlinx.coroutines.test.TestScope.load(vararg items: InboxItem) {
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns
            Result.success(InboxPage(items.toList(), has_more = false, next_cursor = null, total = items.size))
        vm.load_inbox()
        advanceUntilIdle()
    }

    private fun item(id: String = "deal-1") = vm.inbox_state.value.items.single { it.id == id }

    @Test
    fun `moving updates the list before the server answers and keeps the written blob`() = runTest {
        load(deal())
        val answer = CompletableDeferred<Result<MailItem>>()
        val written = deal().raw_item.copy(
            encrypted_metadata = "new-blob",
            metadata_nonce = "new-nonce",
            metadata = MailItemMetadata(is_pinned = true, size_bytes = 4096, category = "travel", category_pinned = true),
        )
        val calls = mutableListOf<Triple<String, String, MailItem?>>()
        coEvery { repository.set_category(any(), any(), any()) } coAnswers {
            calls.add(Triple(firstArg(), secondArg(), thirdArg()))
            answer.await()
        }
        val toasts = mutableListOf<String>()
        backgroundScope.launch { vm.toast_events.collect { toasts.add(it.message) } }

        vm.move_to_category("deal-1", "travel")
        runCurrent()

        assertEquals("travel", item().category)
        assertEquals("travel", item().raw_item.metadata?.category)
        assertTrue(item().raw_item.metadata?.category_pinned == true)
        assertTrue(item().raw_item.metadata?.is_pinned == true)

        answer.complete(Result.success(written))
        advanceUntilIdle()

        assertEquals("travel", item().category)
        assertEquals("new-blob", item().raw_item.encrypted_metadata)
        assertEquals(listOf("Moved to category"), toasts)
        assertEquals(listOf(Triple("deal-1", "travel", deal().raw_item)), calls)
    }

    @Test
    fun `a failed move rolls the list back and shows the error toast`() = runTest {
        load(deal())
        coEvery { repository.set_category(any(), any(), any()) } returns
            Result.failure(RuntimeException("server said no"))
        val toasts = mutableListOf<String>()
        backgroundScope.launch { vm.toast_events.collect { toasts.add(it.message) } }

        vm.move_to_category("deal-1", "travel")
        advanceUntilIdle()

        assertEquals("promotions", item().category)
        assertNull(item().raw_item.metadata?.category)
        assertFalse(item().raw_item.metadata?.category_pinned == true)
        assertEquals("old-blob", item().raw_item.encrypted_metadata)
        assertTrue(item().raw_item.metadata?.is_pinned == true)
        assertEquals(1, toasts.size)
        assertFalse(toasts.single() == "Moved to category")
    }

    @Test
    fun `undecryptable metadata shows the same message as the web`() = runTest {
        load(deal())
        coEvery { repository.set_category(any(), any(), any()) } returns
            Result.failure(MetadataUndecryptableException())
        val toasts = mutableListOf<String>()
        backgroundScope.launch { vm.toast_events.collect { toasts.add(it.message) } }

        vm.move_to_category("deal-1", "travel")
        advanceUntilIdle()

        assertEquals("promotions", item().category)
        assertEquals(listOf("Cannot open details"), toasts)
    }

    @Test
    fun `a refresh that lands before the write keeps the new category`() = runTest {
        load(deal())
        val answer = CompletableDeferred<Result<MailItem>>()
        coEvery { repository.set_category(any(), any(), any()) } coAnswers { answer.await() }

        vm.move_to_category("deal-1", "travel")
        runCurrent()
        vm.load_inbox(force = true)
        advanceUntilIdle()

        assertEquals("travel", item().category)
    }

    @Test
    fun `picking the current category does nothing`() = runTest {
        load(deal())
        var calls = 0
        coEvery { repository.set_category(any(), any(), any()) } coAnswers {
            calls++
            Result.failure(IllegalStateException("unexpected"))
        }

        vm.move_to_category("deal-1", "promotions")
        advanceUntilIdle()

        assertEquals(0, calls)
        assertEquals("promotions", item().category)
    }
}
