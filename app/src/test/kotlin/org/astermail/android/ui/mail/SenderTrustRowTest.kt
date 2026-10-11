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

package org.astermail.android.ui.mail

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderTrustRowTest {

    @Test
    fun single_sender_collapses_to_the_newest_shown_sender() {
        val shown = vetted_participants(
            participants = listOf("Bank" to "a@evil.example", "Other" to "b@c.example"),
            newest_name = "a@evil.example",
            newest_email = "a@evil.example",
            single_sender = true,
            newest_vetted = true,
        )

        assertEquals(listOf("a@evil.example" to "a@evil.example"), shown)
    }

    @Test
    fun empty_participants_fall_back_to_the_newest_sender() {
        val shown = vetted_participants(
            participants = emptyList(),
            newest_name = "Ana",
            newest_email = "ana@b.example",
            single_sender = false,
            newest_vetted = false,
        )

        assertEquals(listOf("Ana" to "ana@b.example"), shown)
    }

    @Test
    fun newest_participant_takes_the_vetted_name() {
        val shown = vetted_participants(
            participants = listOf("Stored" to "Ana@B.example", "Bo" to "bo@c.example"),
            newest_name = "Shown",
            newest_email = "ana@b.example",
            single_sender = false,
            newest_vetted = true,
        )

        assertEquals(listOf("Shown" to "Ana@B.example", "Bo" to "bo@c.example"), shown)
    }

    @Test
    fun participant_name_that_claims_another_address_is_dropped() {
        val shown = vetted_participants(
            participants = listOf(
                "ceo@bank.example" to "x@evil.example",
                "y@plain.example" to "y@plain.example",
            ),
            newest_name = "Newest",
            newest_email = "n@n.example",
            single_sender = false,
            newest_vetted = true,
        )

        assertEquals(listOf("" to "x@evil.example", "" to "y@plain.example"), shown)
    }

    @Test
    fun participant_name_that_imitates_a_brand_is_dropped() {
        val shown = vetted_participants(
            participants = listOf(
                "PaypaI Support" to "x@evil.example",
                "Jane Doe" to "jane@company.example",
            ),
            newest_name = "Newest",
            newest_email = "n@n.example",
            single_sender = false,
            newest_vetted = true,
        )

        assertEquals(listOf("" to "x@evil.example", "Jane Doe" to "jane@company.example"), shown)
    }

    @Test
    fun slot_value_is_isolated_and_loses_direction_controls() {
        val isolated = isolated_slot_value("a\u202Eb\u2066c\u200Fd")

        assertEquals("\u2068abcd\u2069", isolated)
    }

    @Test
    fun every_occurrence_of_a_slot_is_emphasized() {
        val slot = isolated_slot_value("a@b.example")
        val sentence = "From $slot and again $slot."
        val styled = emphasized_slots(sentence, listOf(slot), SpanStyle(fontWeight = FontWeight.SemiBold))

        assertEquals(sentence, styled.text)
        assertEquals(2, styled.spanStyles.size)
        assertTrue(styled.spanStyles.all { sentence.substring(it.start, it.end) == slot })
    }

    @Test
    fun empty_slot_adds_no_emphasis() {
        val slot = isolated_slot_value("")
        val styled = emphasized_slots("Plain text.", listOf(slot), SpanStyle(fontWeight = FontWeight.SemiBold))

        assertEquals("Plain text.", styled.text)
        assertFalse(styled.spanStyles.isNotEmpty())
    }
}
