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

package org.astermail.android.ui.compose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class compose_unsaved_changes_test {

    private fun unsaved(
        to_chips: List<String> = listOf("a@example.com"),
        initial_to_chips: List<String> = listOf("a@example.com"),
        cc_chips: List<String> = emptyList(),
        initial_cc_chips: List<String> = emptyList(),
        bcc_chips: List<String> = emptyList(),
        initial_bcc_chips: List<String> = emptyList(),
        subject: String = "Hello",
        initial_subject: String = "Hello",
        body: String = "Body\n\n-- \nSig",
        initial_body: String = "Body\n\n-- \nSig",
        to_input: String = "",
        cc_input: String = "",
        bcc_input: String = "",
        attachments_changed: Boolean = false,
        has_inline_images: Boolean = false,
    ): Boolean = compose_has_unsaved_changes(
        to_chips = to_chips,
        initial_to_chips = initial_to_chips,
        cc_chips = cc_chips,
        initial_cc_chips = initial_cc_chips,
        bcc_chips = bcc_chips,
        initial_bcc_chips = initial_bcc_chips,
        subject = subject,
        initial_subject = initial_subject,
        body = body,
        initial_body = initial_body,
        to_input = to_input,
        cc_input = cc_input,
        bcc_input = bcc_input,
        attachments_changed = attachments_changed,
        has_inline_images = has_inline_images,
    )

    @Test
    fun untouched_compose_has_no_unsaved_changes() {
        assertFalse(unsaved())
    }

    @Test
    fun single_typed_character_in_body_is_unsaved() {
        assertTrue(unsaved(body = "Body!\n\n-- \nSig"))
    }

    @Test
    fun deleting_the_last_body_character_is_unsaved() {
        assertTrue(unsaved(body = "Body\n\n-- \nSi"))
    }

    @Test
    fun whitespace_only_body_edit_is_unsaved() {
        assertTrue(unsaved(body = "Body\n\n-- \nSig "))
    }

    @Test
    fun recipient_chip_changes_are_unsaved() {
        assertTrue(unsaved(to_chips = listOf("a@example.com", "b@example.com")))
        assertTrue(unsaved(cc_chips = listOf("c@example.com")))
        assertTrue(unsaved(bcc_chips = listOf("d@example.com")))
    }

    @Test
    fun subject_change_is_unsaved() {
        assertTrue(unsaved(subject = "Hello!"))
    }

    @Test
    fun pending_recipient_input_is_unsaved() {
        assertTrue(unsaved(to_input = "b@exa"))
        assertTrue(unsaved(cc_input = "c"))
        assertTrue(unsaved(bcc_input = "d"))
    }

    @Test
    fun blank_recipient_input_is_not_unsaved() {
        assertFalse(unsaved(to_input = "  ", cc_input = "\n", bcc_input = ""))
    }

    @Test
    fun attachment_change_is_unsaved() {
        assertTrue(unsaved(attachments_changed = true))
    }

    @Test
    fun inline_images_are_unsaved() {
        assertTrue(unsaved(has_inline_images = true))
    }

    @Test
    fun matches_the_original_inline_predicate_for_every_flag_combination() {
        for (mask in 0 until (1 shl 10)) {
            fun bit(i: Int) = mask and (1 shl i) != 0
            val to_chips = if (bit(0)) listOf("x@example.com") else listOf("a@example.com")
            val cc_chips = if (bit(1)) listOf("c@example.com") else emptyList()
            val bcc_chips = if (bit(2)) listOf("d@example.com") else emptyList()
            val subject = if (bit(3)) "Changed" else "Hello"
            val body = if (bit(4)) "Body." else "Body"
            val to_input = if (bit(5)) "t" else " "
            val cc_input = if (bit(6)) "c" else ""
            val bcc_input = if (bit(7)) "b" else "\t"
            val attachments_changed = bit(8)
            val has_inline_images = bit(9)
            val expected = to_chips != listOf("a@example.com") ||
                cc_chips != emptyList<String>() ||
                bcc_chips != emptyList<String>() ||
                subject != "Hello" ||
                body != "Body" ||
                to_input.isNotBlank() ||
                cc_input.isNotBlank() ||
                bcc_input.isNotBlank() ||
                attachments_changed ||
                has_inline_images
            val actual = unsaved(
                to_chips = to_chips,
                cc_chips = cc_chips,
                bcc_chips = bcc_chips,
                subject = subject,
                initial_subject = "Hello",
                body = body,
                initial_body = "Body",
                to_input = to_input,
                cc_input = cc_input,
                bcc_input = bcc_input,
                attachments_changed = attachments_changed,
                has_inline_images = has_inline_images,
            )
            if (expected) assertTrue(actual) else assertFalse(actual)
            assertTrue(expected == (mask != 0))
        }
    }
}
