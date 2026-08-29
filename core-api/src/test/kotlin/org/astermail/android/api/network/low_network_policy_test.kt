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

package org.astermail.android.api.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class low_network_policy_test {

    @Test
    fun page_size_is_reduced_when_low_network_is_on() {
        assertEquals(LOW_NETWORK_PAGE_SIZE, effective_inbox_page_size(50, true))
        assertEquals(LOW_NETWORK_PAGE_SIZE, effective_inbox_page_size(100, true))
    }

    @Test
    fun page_size_is_unchanged_when_low_network_is_off() {
        assertEquals(50, effective_inbox_page_size(50, false))
    }

    @Test
    fun page_size_below_the_low_network_cap_is_kept() {
        assertEquals(10, effective_inbox_page_size(10, true))
    }

    @Test
    fun page_size_is_clamped_to_the_supported_range() {
        assertEquals(MIN_INBOX_PAGE_SIZE, effective_inbox_page_size(1, false))
        assertEquals(MAX_INBOX_PAGE_SIZE, effective_inbox_page_size(500, false))
        assertEquals(LOW_NETWORK_PAGE_SIZE, effective_inbox_page_size(500, true))
    }

    @Test
    fun remote_images_are_blocked_when_low_network_is_on() {
        assertTrue(should_block_remote_images(block_external_images = false, low_network = true))
        assertTrue(should_block_remote_images(block_external_images = true, low_network = false))
        assertFalse(should_block_remote_images(block_external_images = false, low_network = false))
    }

    @Test
    fun avatars_and_logos_are_skipped_when_low_network_is_on() {
        assertFalse(should_load_remote_avatar(true))
        assertFalse(should_load_sender_logo(true))
        assertTrue(should_load_remote_avatar(false))
        assertTrue(should_load_sender_logo(false))
    }

    @Test
    fun prefetch_is_skipped_when_low_network_is_on() {
        assertFalse(should_prefetch_sender_profiles(true))
        assertFalse(should_prefetch_adjacent_messages(true))
        assertTrue(should_prefetch_sender_profiles(false))
        assertTrue(should_prefetch_adjacent_messages(false))
    }

    @Test
    fun thread_messages_are_capped_when_low_network_is_on() {
        assertEquals(
            LOW_NETWORK_THREAD_MESSAGE_LIMIT,
            thread_message_load_limit(true),
        )
        assertNull(thread_message_load_limit(false))
    }

    @Test
    fun previews_are_shortened_when_low_network_is_on() {
        assertEquals(LOW_NETWORK_PREVIEW_CHAR_LIMIT, preview_char_limit(null, true))
        assertEquals(40, preview_char_limit(40, true))
        assertNull(preview_char_limit(null, false))
        assertEquals(200, preview_char_limit(200, false))
    }

    @Test
    fun messages_render_as_plain_text_when_low_network_is_on() {
        assertTrue(should_render_plain_text(null, true))
        assertTrue(should_render_plain_text("plain_text", false))
        assertFalse(should_render_plain_text("rich_text", false))
    }

    @Test
    fun inline_images_are_skipped_when_low_network_is_on() {
        assertFalse(should_resolve_inline_images(true))
        assertTrue(should_resolve_inline_images(false))
    }

    @Test
    fun attachment_metadata_loads_only_after_the_user_opens_it() {
        assertFalse(should_load_attachment_metadata(low_network = true, user_expanded = false))
        assertTrue(should_load_attachment_metadata(low_network = true, user_expanded = true))
        assertTrue(should_load_attachment_metadata(low_network = false, user_expanded = false))
    }

    @Test
    fun draft_autosave_waits_longer_when_low_network_is_on() {
        assertEquals(LOW_NETWORK_DRAFT_AUTOSAVE_DELAY_MS, draft_autosave_delay_ms(true))
        assertEquals(DEFAULT_DRAFT_AUTOSAVE_DELAY_MS, draft_autosave_delay_ms(false))
    }

    @Test
    fun background_polling_slows_down_when_low_network_is_on() {
        assertEquals(LOW_NETWORK_POLL_CHAIN_DELAY_MINUTES, poll_chain_delay_minutes(true))
        assertEquals(DEFAULT_POLL_CHAIN_DELAY_MINUTES, poll_chain_delay_minutes(false))
        assertFalse(should_run_expedited_poll(true))
        assertTrue(should_run_expedited_poll(false))
    }

    @Test
    fun stats_are_cached_longer_when_low_network_is_on() {
        assertEquals(LOW_NETWORK_STATS_TTL_MS, stats_ttl_ms(60_000L, true))
        assertEquals(60_000L, stats_ttl_ms(60_000L, false))
    }

    @Test
    fun a_longer_stats_ttl_is_never_shortened() {
        val longer = LOW_NETWORK_STATS_TTL_MS * 2
        assertEquals(longer, stats_ttl_ms(longer, true))
    }

    @Test
    fun request_timeouts_are_extended_when_low_network_is_on() {
        assertEquals(
            LOW_NETWORK_MIN_REQUEST_TIMEOUT_MS,
            effective_request_timeout_ms(18_000L, true),
        )
        assertEquals(18_000L, effective_request_timeout_ms(18_000L, false))
        assertEquals(60_000L, effective_request_timeout_ms(60_000L, true))
    }

    @Test
    fun motion_is_reduced_when_low_network_is_on() {
        assertTrue(should_reduce_motion(reduce_motion = false, low_network = true))
        assertTrue(should_reduce_motion(reduce_motion = true, low_network = false))
        assertFalse(should_reduce_motion(reduce_motion = false, low_network = false))
    }

    @Test
    fun data_saver_counts_only_on_a_metered_connection() {
        assertTrue(is_data_saver_restricted(true, RESTRICT_BACKGROUND_STATUS_ENABLED))
        assertFalse(is_data_saver_restricted(false, RESTRICT_BACKGROUND_STATUS_ENABLED))
        assertFalse(is_data_saver_restricted(true, RESTRICT_BACKGROUND_STATUS_WHITELISTED))
        assertFalse(is_data_saver_restricted(true, RESTRICT_BACKGROUND_STATUS_DISABLED))
    }
}
