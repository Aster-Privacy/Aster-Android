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
import org.junit.Assert.assertNotNull
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
        every { context.getString(R.string.undo) } returns "Undo"
        repository = mockk(relaxed = true)
        val folder_cache_store = mockk<FolderCacheStore>(relaxed = true)
        every { folder_cache_store.cached_stats(any()) } returns null
        every { repository.send_result_events } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { repository.new_mail_events } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { repository.pending_undo_send } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        every { repository.optimistic_sends } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        every { repository.reaction_holds } returns
            ReactionHoldQueue(kotlinx.coroutines.CoroutineScope(dispatcher)) { null }
        coEvery { repository.get_stats() } returns Result.success(MailUserStatsResponse())
        coEvery { repository.category_thread_targets(any()) } returns Result.success(emptyList())
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
            mockk(relaxed = true),
        )
    }

    @After
    fun teardown() {
        io.mockk.unmockkStatic(android.util.Log::class)
        io.mockk.unmockkStatic(Dispatchers::class)
        Dispatchers.resetMain()
    }

    private fun deal(id: String = "deal-1", thread: String = "thread-$id", timestamp: String = "2026-09-30T08:00:00Z") = InboxItem(
        id = id,
        thread_token = thread,
        thread_message_count = 1,
        sender_name = "Example Deals",
        sender_email = "offers@deals.example.test",
        subject = "Weekend offer",
        preview = "Synthetic preview",
        timestamp = timestamp,
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
        val answer = CompletableDeferred<Result<CategoryWrite>>()
        val written = deal().raw_item.copy(
            encrypted_metadata = "new-blob",
            metadata_nonce = "new-nonce",
            metadata = MailItemMetadata(is_pinned = true, size_bytes = 4096, category = "travel", category_pinned = true),
        )
        val calls = mutableListOf<Triple<String, String?, MailItem?>>()
        coEvery { repository.write_category(any(), any(), any(), any()) } coAnswers {
            calls.add(Triple(firstArg(), secondArg(), arg(3)))
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

        answer.complete(Result.success(CategoryWrite(written, prior_category = null, prior_pinned = false)))
        advanceUntilIdle()

        assertEquals("travel", item().category)
        assertEquals("new-blob", item().raw_item.encrypted_metadata)
        assertEquals(listOf("Moved to category"), toasts)
        assertEquals(listOf(Triple("deal-1", "travel", deal().raw_item)), calls)
    }

    @Test
    fun `a failed move rolls the list back and shows the error toast`() = runTest {
        load(deal())
        coEvery { repository.write_category(any(), any(), any(), any()) } returns
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
        coEvery { repository.write_category(any(), any(), any(), any()) } returns
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
        val answer = CompletableDeferred<Result<CategoryWrite>>()
        coEvery { repository.write_category(any(), any(), any(), any()) } coAnswers { answer.await() }

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
        coEvery { repository.write_category(any(), any(), any(), any()) } coAnswers {
            calls++
            Result.failure(IllegalStateException("unexpected"))
        }

        vm.move_to_category("deal-1", "promotions")
        advanceUntilIdle()

        assertEquals(0, calls)
        assertEquals("promotions", item().category)
    }

    private fun written(id: String, category: String, prior: String?, prior_pinned: Boolean) = CategoryWrite(
        item = MailItem(
            id = id,
            item_type = "received",
            encrypted_metadata = "blob-$id-$category",
            metadata_nonce = "nonce-$id-$category",
            metadata = MailItemMetadata(category = category, category_pinned = true),
        ),
        prior_category = prior,
        prior_pinned = prior_pinned,
    )

    private fun record_writes(
        fail_ids: Set<String> = emptySet(),
        prior: Map<String, Pair<String?, Boolean>> = emptyMap(),
    ): MutableList<Triple<String, String?, Boolean>> {
        val calls = mutableListOf<Triple<String, String?, Boolean>>()
        coEvery { repository.write_category(any(), any(), any(), any()) } coAnswers {
            val id = firstArg<String>()
            val category = secondArg<String?>()
            val pinned = thirdArg<Boolean>()
            calls.add(Triple(id, category, pinned))
            if (id in fail_ids) {
                Result.failure(RuntimeException("server said no"))
            } else {
                val (prior_category, prior_pinned) = prior[id] ?: (null to false)
                Result.success(written(id, category ?: "none", prior_category, prior_pinned))
            }
        }
        return calls
    }

    @Test
    fun `moving a conversation pins every received message in the thread`() = runTest {
        load(deal())
        coEvery { repository.category_thread_targets("thread-deal-1") } returns Result.success(
            listOf(
                MailItem(id = "deal-1", item_type = "received"),
                MailItem(id = "older-1", item_type = "received", encrypted_metadata = "b", metadata_nonce = "n"),
            ),
        )
        val calls = record_writes()

        vm.move_to_category("deal-1", "primary")
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("deal-1", "primary", true), Triple("older-1", "primary", true)),
            calls,
        )
        assertEquals("primary", item().category)
        assertTrue(item().raw_item.metadata?.category_pinned == true)
    }

    @Test
    fun `bulk move covers every loaded message of each selected thread and skips sent mail`() = runTest {
        val sent = deal("sent-1", thread = "thread-a").let {
            it.copy(raw_item = it.raw_item.copy(item_type = "sent"))
        }
        load(
            deal("a-new", thread = "thread-a", timestamp = "2026-09-30T09:00:00Z"),
            deal("a-old", thread = "thread-a", timestamp = "2026-09-29T09:00:00Z"),
            sent,
            deal("b-1", thread = "thread-b"),
            deal("other", thread = "thread-other"),
        )
        val calls = record_writes()

        vm.move_to_category_bulk(listOf("a-new", "b-1"), "primary")
        advanceUntilIdle()

        assertEquals(setOf("a-new", "a-old", "b-1"), calls.map { it.first }.toSet())
        assertEquals("primary", item("a-old").category)
        assertEquals("promotions", item("other").category)
        assertEquals("promotions", item("sent-1").category)
    }

    @Test
    fun `undo restores an auto sorted message to unpinned`() = runTest {
        load(deal())
        val calls = record_writes(prior = mapOf("deal-1" to (null to false)))
        val toasts = mutableListOf<MailViewModel.ToastEvent>()
        backgroundScope.launch { vm.toast_events.collect { toasts.add(it) } }

        vm.move_to_category("deal-1", "primary")
        advanceUntilIdle()
        assertEquals("primary", item().category)
        val undo = toasts.single().on_undo
        assertNotNull(undo)
        undo!!.invoke()
        advanceUntilIdle()

        assertEquals(Triple("deal-1", null as String?, false), calls.last())
        assertEquals("promotions", item().category)
        assertNull(item().raw_item.metadata?.category)
        assertFalse(item().raw_item.metadata?.category_pinned == true)
    }

    @Test
    fun `undo restores a previously pinned category exactly`() = runTest {
        val pinned = deal().let {
            it.copy(
                category = "social",
                raw_item = it.raw_item.copy(
                    metadata = MailItemMetadata(is_pinned = true, category = "social", category_pinned = true),
                ),
            )
        }
        load(pinned)
        val calls = record_writes(prior = mapOf("deal-1" to ("social" to true)))
        val toasts = mutableListOf<MailViewModel.ToastEvent>()
        backgroundScope.launch { vm.toast_events.collect { toasts.add(it) } }

        vm.move_to_category("deal-1", "primary")
        advanceUntilIdle()
        toasts.single().on_undo!!.invoke()
        advanceUntilIdle()

        assertEquals(Triple("deal-1", "social" as String?, true), calls.last())
        assertEquals("social", item().category)
        assertEquals("social", item().raw_item.metadata?.category)
        assertTrue(item().raw_item.metadata?.category_pinned == true)
    }

    @Test
    fun `a partial failure rolls back only the messages that failed`() = runTest {
        load(deal("a-1", thread = "thread-a"), deal("b-1", thread = "thread-b"))
        record_writes(fail_ids = setOf("b-1"))
        val toasts = mutableListOf<MailViewModel.ToastEvent>()
        backgroundScope.launch { vm.toast_events.collect { toasts.add(it) } }

        vm.move_to_category_bulk(listOf("a-1", "b-1"), "primary")
        advanceUntilIdle()

        assertEquals("primary", item("a-1").category)
        assertEquals("promotions", item("b-1").category)
        assertEquals("old-blob", item("b-1").raw_item.encrypted_metadata)
        assertEquals(2, toasts.size)
        assertNull(toasts.first().on_undo)
        assertNotNull(toasts.last().on_undo)
    }

    @Test
    fun `an undecryptable message in a bulk move keeps its category`() = runTest {
        load(deal("a-1", thread = "thread-a"), deal("b-1", thread = "thread-b"))
        coEvery { repository.write_category(any(), any(), any(), any()) } coAnswers {
            if (firstArg<String>() == "b-1") {
                Result.failure(MetadataUndecryptableException())
            } else {
                Result.success(written(firstArg(), "primary", null, false))
            }
        }
        val toasts = mutableListOf<String>()
        backgroundScope.launch { vm.toast_events.collect { toasts.add(it.message) } }

        vm.move_to_category_bulk(listOf("a-1", "b-1"), "primary")
        advanceUntilIdle()

        assertEquals("promotions", item("b-1").category)
        assertEquals(listOf("Cannot open details", "Moved to category"), toasts)
    }
}
