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

import org.astermail.android.ui.compose.html_signature_with_separator
import org.astermail.android.ui.compose.plain_signature_with_separator
import org.astermail.android.ui.compose.signature_below_quote
import org.astermail.android.ui.compose.split_trailing_signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturePlacementTest {
    @Test
    fun signature_placement_above_wins_over_preference() {
        assertFalse(signature_below_quote(1, "below"))
    }

    @Test
    fun signature_placement_below_wins_over_preference() {
        assertTrue(signature_below_quote(0, "above"))
    }

    @Test
    fun inherited_placement_follows_preference() {
        assertFalse(signature_below_quote(null, "above"))
        assertTrue(signature_below_quote(null, "below"))
    }

    @Test
    fun missing_preference_defaults_to_below() {
        assertTrue(signature_below_quote(null, null))
        assertTrue(signature_below_quote(null, ""))
    }

    @Test
    fun trailing_signature_is_split_off() {
        val split = split_trailing_signature("Hello there\n\nAda\nAster Mail", "Ada\nAster Mail")
        assertEquals("Hello there", split?.first)
        assertEquals("Ada\nAster Mail", split?.second)
    }

    @Test
    fun trailing_whitespace_does_not_block_the_split() {
        val split = split_trailing_signature("Hi\n\nAda\n \n", "Ada")
        assertEquals("Hi", split?.first)
    }

    @Test
    fun body_without_the_signature_is_left_alone() {
        assertNull(split_trailing_signature("Hello there", "Ada"))
    }

    @Test
    fun blank_signature_is_ignored() {
        assertNull(split_trailing_signature("Hello there", ""))
    }

    @Test
    fun plain_signature_gets_separator_by_default() {
        assertEquals("--\nAdam", plain_signature_with_separator("Adam", null))
        assertEquals("--\nAdam", plain_signature_with_separator("Adam", true))
    }

    @Test
    fun plain_signature_separator_can_be_disabled() {
        assertEquals("Adam", plain_signature_with_separator("Adam", false))
    }

    @Test
    fun blank_signature_never_gets_a_separator() {
        assertEquals("", plain_signature_with_separator("", true))
        assertEquals("", html_signature_with_separator("", true))
    }

    @Test
    fun html_signature_separator_matches_web_markup() {
        assertEquals("--<br><b>Adam</b>", html_signature_with_separator("<b>Adam</b>", null))
        assertEquals("<b>Adam</b>", html_signature_with_separator("<b>Adam</b>", false))
    }
}
