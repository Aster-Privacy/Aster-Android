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

package org.astermail.android.mail

import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRowCacheTest {

    private fun item(
        id: String = "m1",
        subject: String = "Subject",
        is_read: Boolean = false,
        is_starred: Boolean = false,
        labels: List<String> = emptyList(),
        tag_tokens: List<String> = emptyList(),
        to_addresses: List<String> = emptyList(),
        item_type: String? = "received",
        is_pinned: Boolean = false,
        attachment_count: Int = 0,
    ) = InboxItem(
        id = id,
        thread_token = "t-$id",
        thread_message_count = 3,
        sender_name = "Sender",
        sender_email = "sender@example.com",
        subject = subject,
        preview = "Preview",
        timestamp = "2026-09-19T00:00:00Z",
        is_read = is_read,
        is_starred = is_starred,
        is_encrypted = true,
        has_attachments = attachment_count > 0,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = labels,
        tag_tokens = tag_tokens,
        category = "primary",
        received_on = "alias@aster.cx",
        display_sender_name = "Display",
        display_sender_email = "display@example.com",
        to_addresses = to_addresses,
        routing_token = "r-$id",
        raw_item = MailItem(
            id = id,
            item_type = item_type,
            attachment_count = attachment_count,
            metadata = MailItemMetadata(is_pinned = is_pinned),
        ),
    )

    @Test
    fun a_cached_row_round_trips_back_into_a_renderable_item() {
        val original = item(
            labels = listOf("bills", "work"),
            tag_tokens = listOf("tag1"),
            to_addresses = listOf("a@example.com", "b@example.com"),
            is_read = true,
            is_starred = true,
            attachment_count = 2,
            is_pinned = true,
        )

        val restored = folder_cache_rows("inbox", listOf(original), cached_at = 7L).single().to_inbox_item()

        assertEquals(original.id, restored.id)
        assertEquals(original.thread_token, restored.thread_token)
        assertEquals(original.thread_message_count, restored.thread_message_count)
        assertEquals(original.sender_name, restored.sender_name)
        assertEquals(original.subject, restored.subject)
        assertEquals(original.preview, restored.preview)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.labels, restored.labels)
        assertEquals(original.tag_tokens, restored.tag_tokens)
        assertEquals(original.to_addresses, restored.to_addresses)
        assertEquals(original.received_on, restored.received_on)
        assertEquals(original.display_sender_email, restored.display_sender_email)
        assertEquals(original.routing_token, restored.routing_token)
        assertTrue(restored.is_read)
        assertTrue(restored.is_starred)
        assertTrue(restored.has_attachments)
        assertEquals(2, restored.raw_item.attachment_count)
        assertEquals(true, restored.raw_item.metadata?.is_pinned)
        assertEquals("received", restored.raw_item.item_type)
    }

    @Test
    fun a_cached_sent_row_still_matches_the_sent_folder() {
        val row = folder_cache_rows("sent", listOf(item(item_type = "sent")), cached_at = 0L).single()

        assertTrue(folder_matches_item("sent", row.to_inbox_item()))
    }

    @Test
    fun rows_keep_the_display_order_and_stop_at_the_limit() {
        val items = (1..10).map { item(id = "m$it") }

        val rows = folder_cache_rows("inbox", items, cached_at = 0L, limit = 4)

        assertEquals(listOf("m1", "m2", "m3", "m4"), rows.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.position })
        assertTrue(rows.all { it.folder == "inbox" })
    }

    @Test
    fun rows_drop_duplicates_and_blank_identifiers() {
        val items = listOf(item(id = "m1"), item(id = "m1", subject = "Again"), item(id = ""))

        val rows = folder_cache_rows("inbox", items, cached_at = 0L)

        assertEquals(listOf("m1"), rows.map { it.id })
        assertEquals("Subject", rows.single().subject)
    }

    @Test
    fun pruning_removes_only_the_rows_the_server_no_longer_returns() {
        val cached = listOf("m1", "m2", "m3", "m4")
        val fetched = listOf("m2", "m4", "m5")

        assertEquals(listOf("m1", "m3"), folder_cache_prune_ids(cached, fetched))
    }

    @Test
    fun pruning_a_folder_the_server_emptied_removes_everything() {
        assertEquals(listOf("m1", "m2"), folder_cache_prune_ids(listOf("m1", "m2"), emptyList()))
    }

    @Test
    fun pruning_keeps_nothing_stale_when_the_fetch_covers_the_cache() {
        assertEquals(emptyList<String>(), folder_cache_prune_ids(listOf("m1"), listOf("m1", "m2")))
    }

    @Test
    fun local_read_and_star_overrides_win_over_the_persisted_flags() {
        val items = listOf(item(id = "m1", is_read = false, is_starred = false), item(id = "m2"))

        val merged = folder_cache_apply_overrides(
            items,
            read_override = { id -> if (id == "m1") true else null },
            star_override = { id -> if (id == "m1") true else null },
        )

        assertTrue(merged.first().is_read)
        assertTrue(merged.first().is_starred)
        assertFalse(merged.last().is_read)
        assertFalse(merged.last().is_starred)
    }

    @Test
    fun an_absent_override_leaves_the_persisted_row_untouched() {
        val items = listOf(item(id = "m1", is_read = true, is_starred = true))

        val merged = folder_cache_apply_overrides(items, read_override = { null }, star_override = { null })

        assertEquals(items, merged)
    }

    @Test
    fun the_skeleton_waits_while_the_store_is_still_being_read() {
        assertFalse(folder_cache_skeleton_allowed(cache_pending = true, cached_count = 0))
    }

    @Test
    fun the_skeleton_shows_once_the_store_is_known_to_be_empty() {
        assertTrue(folder_cache_skeleton_allowed(cache_pending = false, cached_count = 0))
    }

    @Test
    fun the_skeleton_never_shows_when_rows_are_on_screen() {
        assertFalse(folder_cache_skeleton_allowed(cache_pending = false, cached_count = 12))
        assertFalse(folder_cache_skeleton_allowed(cache_pending = true, cached_count = 12))
    }

    @Test
    fun a_populated_list_is_always_worth_persisting() {
        assertTrue(
            folder_cache_should_persist(
                items_empty = false,
                is_loading = true,
                initial = true,
                has_error = false,
            ),
        )
    }

    @Test
    fun an_empty_list_is_persisted_only_once_the_fetch_settled_without_error() {
        assertTrue(
            folder_cache_should_persist(
                items_empty = true,
                is_loading = false,
                initial = false,
                has_error = false,
            ),
        )
        assertFalse(
            folder_cache_should_persist(
                items_empty = true,
                is_loading = true,
                initial = false,
                has_error = false,
            ),
        )
        assertFalse(
            folder_cache_should_persist(
                items_empty = true,
                is_loading = false,
                initial = true,
                has_error = false,
            ),
        )
        assertFalse(
            folder_cache_should_persist(
                items_empty = true,
                is_loading = false,
                initial = false,
                has_error = true,
            ),
        )
    }

    @Test
    fun `layout signature is stable and tracks every layout input`() {
        val base = folder_cache_layout_signature(grouping = true, list_order = null, custom_categories = 0)
        assertEquals(base, folder_cache_layout_signature(grouping = true, list_order = null, custom_categories = 0))
        assertEquals(base, folder_cache_layout_signature(grouping = true, list_order = "desc", custom_categories = 0))
        assertTrue(base != folder_cache_layout_signature(grouping = false, list_order = null, custom_categories = 0))
        assertTrue(base != folder_cache_layout_signature(grouping = true, list_order = "asc", custom_categories = 0))
        assertTrue(base != folder_cache_layout_signature(grouping = true, list_order = null, custom_categories = 1))
    }
}
