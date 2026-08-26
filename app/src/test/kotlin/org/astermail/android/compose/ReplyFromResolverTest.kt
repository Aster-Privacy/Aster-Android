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

import org.astermail.android.ui.compose.resolve_reply_from_alias
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyFromResolverTest {

    private val options = listOf("me@astermail.org", "ghost@realiased.me")

    @Test
    fun received_on_alias_wins() {
        assertEquals(
            "ghost@realiased.me",
            resolve_reply_from_alias("ghost@realiased.me", null, "me@astermail.org", options),
        )
    }

    @Test
    fun ghost_match_is_case_insensitive() {
        assertEquals(
            "ghost@realiased.me",
            resolve_reply_from_alias(null, "Ghost@Realiased.ME", "", options),
        )
    }

    @Test
    fun primary_sender_is_case_insensitive() {
        assertEquals(
            "me@astermail.org",
            resolve_reply_from_alias(null, null, "Me@AsterMail.org", options),
        )
    }

    @Test
    fun primary_sender_tolerates_surrounding_space() {
        assertEquals(
            "me@astermail.org",
            resolve_reply_from_alias(null, null, "  me@astermail.org  ", options),
        )
    }

    @Test
    fun unknown_sender_falls_back_to_first_option() {
        assertEquals(
            "me@astermail.org",
            resolve_reply_from_alias(null, null, "other@example.com", options),
        )
    }

    @Test
    fun empty_options_yield_empty_string() {
        assertEquals("", resolve_reply_from_alias(null, null, "me@astermail.org", emptyList()))
    }
}
