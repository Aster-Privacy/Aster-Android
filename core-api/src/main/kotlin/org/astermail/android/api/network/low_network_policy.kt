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

const val LOW_NETWORK_PAGE_SIZE = 15
const val MIN_INBOX_PAGE_SIZE = 10
const val MAX_INBOX_PAGE_SIZE = 100
const val LOW_NETWORK_THREAD_MESSAGE_LIMIT = 4
const val LOW_NETWORK_PREVIEW_CHAR_LIMIT = 80
const val LOW_NETWORK_DRAFT_AUTOSAVE_DELAY_MS = 8_000L
const val DEFAULT_DRAFT_AUTOSAVE_DELAY_MS = 3_000L
const val LOW_NETWORK_POLL_CHAIN_DELAY_MINUTES = 15L
const val DEFAULT_POLL_CHAIN_DELAY_MINUTES = 3L
const val LOW_NETWORK_STATS_TTL_MS = 20L * 60L * 1000L
const val LOW_NETWORK_MIN_REQUEST_TIMEOUT_MS = 45_000L
const val RESTRICT_BACKGROUND_STATUS_DISABLED = 1
const val RESTRICT_BACKGROUND_STATUS_WHITELISTED = 2
const val RESTRICT_BACKGROUND_STATUS_ENABLED = 3

fun effective_inbox_page_size(configured_page_size: Int, low_network: Boolean): Int {
    val clamped = configured_page_size.coerceIn(MIN_INBOX_PAGE_SIZE, MAX_INBOX_PAGE_SIZE)
    if (!low_network) return clamped
    return minOf(clamped, LOW_NETWORK_PAGE_SIZE)
}

fun should_load_remote_avatar(low_network: Boolean): Boolean = !low_network

fun should_load_sender_logo(low_network: Boolean): Boolean = !low_network

fun should_prefetch_sender_profiles(low_network: Boolean): Boolean = !low_network

fun should_prefetch_adjacent_messages(low_network: Boolean): Boolean = !low_network

fun thread_message_load_limit(low_network: Boolean): Int? =
    if (low_network) LOW_NETWORK_THREAD_MESSAGE_LIMIT else null

fun preview_char_limit(configured_limit: Int?, low_network: Boolean): Int? =
    if (low_network) minOf(configured_limit ?: LOW_NETWORK_PREVIEW_CHAR_LIMIT, LOW_NETWORK_PREVIEW_CHAR_LIMIT)
    else configured_limit

fun should_block_remote_images(block_external_images: Boolean, low_network: Boolean): Boolean =
    block_external_images || low_network

fun should_render_plain_text(html_rendering_mode: String?, low_network: Boolean): Boolean =
    low_network || html_rendering_mode == "plain_text"

fun should_resolve_inline_images(low_network: Boolean): Boolean = !low_network

fun should_load_attachment_metadata(low_network: Boolean, user_expanded: Boolean): Boolean =
    user_expanded || !low_network

fun draft_autosave_delay_ms(low_network: Boolean): Long =
    if (low_network) LOW_NETWORK_DRAFT_AUTOSAVE_DELAY_MS else DEFAULT_DRAFT_AUTOSAVE_DELAY_MS

fun poll_chain_delay_minutes(low_network: Boolean): Long =
    if (low_network) LOW_NETWORK_POLL_CHAIN_DELAY_MINUTES else DEFAULT_POLL_CHAIN_DELAY_MINUTES

fun should_run_expedited_poll(low_network: Boolean): Boolean = !low_network

fun stats_ttl_ms(default_ttl_ms: Long, low_network: Boolean): Long =
    if (low_network) maxOf(default_ttl_ms, LOW_NETWORK_STATS_TTL_MS) else default_ttl_ms

fun effective_request_timeout_ms(default_timeout_ms: Long, low_network: Boolean): Long =
    if (low_network) maxOf(default_timeout_ms, LOW_NETWORK_MIN_REQUEST_TIMEOUT_MS) else default_timeout_ms

fun should_reduce_motion(reduce_motion: Boolean, low_network: Boolean): Boolean =
    reduce_motion || low_network

fun is_data_saver_restricted(is_metered: Boolean, restrict_background_status: Int): Boolean =
    is_metered && restrict_background_status == RESTRICT_BACKGROUND_STATUS_ENABLED
