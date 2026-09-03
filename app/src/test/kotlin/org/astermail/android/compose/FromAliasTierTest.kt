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

package org.astermail.android.compose

import org.astermail.android.ui.compose.from_tier_fallback
import org.astermail.android.ui.compose.from_tier_ghost
import org.astermail.android.ui.compose.from_tier_pinned
import org.astermail.android.ui.compose.from_tier_thread
import org.astermail.android.ui.compose.next_from_alias
import org.astermail.android.ui.compose.resolve_from_alias_tiered
import org.astermail.android.ui.compose.resolved_from_alias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FromAliasTierTest {

    private val primary = "me@astermail.org"
    private val pinned = "shopping@astermail.org"
    private val received = "support@astermail.org"
    private val options = listOf(primary, pinned, received, "ghost@realiased.me")

    @Test
    fun received_alias_reports_thread_tier() {
        val resolved = resolve_from_alias_tiered(received, null, pinned, options)
        assertEquals(received, resolved.address)
        assertEquals(from_tier_thread, resolved.tier)
    }

    @Test
    fun ghost_match_reports_ghost_tier() {
        val resolved = resolve_from_alias_tiered(null, "ghost@realiased.me", pinned, options)
        assertEquals("ghost@realiased.me", resolved.address)
        assertEquals(from_tier_ghost, resolved.tier)
    }

    @Test
    fun pinned_sender_reports_pinned_tier() {
        val resolved = resolve_from_alias_tiered(null, null, pinned, options)
        assertEquals(pinned, resolved.address)
        assertEquals(from_tier_pinned, resolved.tier)
    }

    @Test
    fun unknown_sender_reports_fallback_tier() {
        val resolved = resolve_from_alias_tiered(null, null, "nobody@example.com", options)
        assertEquals(primary, resolved.address)
        assertEquals(from_tier_fallback, resolved.tier)
    }

    @Test
    fun late_pinned_sender_replaces_a_stale_pinned_value() {
        val next = next_from_alias(
            current = primary,
            current_tier = from_tier_pinned,
            resolved = resolved_from_alias(pinned, from_tier_pinned),
            alias_options = options,
            manually_selected = false,
        )
        assertEquals(resolved_from_alias(pinned, from_tier_pinned), next)
    }

    @Test
    fun late_received_alias_replaces_the_pinned_value_on_a_reply() {
        val next = next_from_alias(
            current = pinned,
            current_tier = from_tier_pinned,
            resolved = resolved_from_alias(received, from_tier_thread),
            alias_options = options,
            manually_selected = false,
        )
        assertEquals(resolved_from_alias(received, from_tier_thread), next)
    }

    @Test
    fun a_weaker_tier_never_replaces_a_stronger_one() {
        assertNull(
            next_from_alias(
                current = received,
                current_tier = from_tier_thread,
                resolved = resolved_from_alias(pinned, from_tier_pinned),
                alias_options = options,
                manually_selected = false,
            ),
        )
    }

    @Test
    fun a_manual_selection_is_never_replaced() {
        assertNull(
            next_from_alias(
                current = primary,
                current_tier = from_tier_fallback,
                resolved = resolved_from_alias(received, from_tier_thread),
                alias_options = options,
                manually_selected = true,
            ),
        )
    }

    @Test
    fun an_unchanged_resolution_reports_no_work() {
        assertNull(
            next_from_alias(
                current = pinned,
                current_tier = from_tier_pinned,
                resolved = resolved_from_alias(pinned, from_tier_pinned),
                alias_options = options,
                manually_selected = false,
            ),
        )
    }

    @Test
    fun an_address_that_left_the_option_list_is_replaced_by_any_tier() {
        val next = next_from_alias(
            current = "deleted@astermail.org",
            current_tier = from_tier_thread,
            resolved = resolved_from_alias(primary, from_tier_fallback),
            alias_options = options,
            manually_selected = false,
        )
        assertEquals(resolved_from_alias(primary, from_tier_fallback), next)
    }

    @Test
    fun no_options_means_no_change() {
        assertNull(
            next_from_alias(
                current = "",
                current_tier = from_tier_fallback,
                resolved = resolved_from_alias("", from_tier_fallback),
                alias_options = emptyList(),
                manually_selected = false,
            ),
        )
    }
}
