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

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectCopyTest {

    private val chips = File("src/main/kotlin/org/astermail/android/ui/mail/detail_context_chips.kt").readText()
    private val detail = File("src/main/kotlin/org/astermail/android/ui/mail/mail_detail_screen.kt").readText()

    private fun press_block(): String {
        val start = chips.indexOf("fun Modifier.subject_press_actions(")
        val end = chips.indexOf("internal const val subject_pressed_alpha", start)
        return chips.substring(start, end)
    }

    @Test
    fun long_press_copies_the_subject_as_plain_text() {
        val block = press_block()
        assertTrue(block.contains("write_to_clipboard(context, ClipData.newPlainText(\"subject\", value))"))
        assertTrue(block.contains("HapticFeedbackType.LongPress"))
        assertTrue(block.contains("onLongClickLabel = if (copy_value != null) copy_label else null"))
    }

    @Test
    fun the_toast_only_shows_where_the_system_has_no_clipboard_preview() {
        val block = press_block()
        assertTrue(block.contains("!copied -> app_toast.show(failed_message)"))
        assertTrue(block.contains("!system_confirms_copy() -> app_toast.show(copied_message)"))
        assertTrue(block.contains("R.string.subject_copied"))
    }

    @Test
    fun the_pressed_state_stays_inside_the_subject_bounds() {
        val block = press_block()
        assertTrue(block.contains("indication = null"))
        assertTrue(block.contains("drawRoundRect("))
        assertFalse(block.contains("topLeft"))
    }

    @Test
    fun the_detail_header_copies_the_full_subject_without_the_count() {
        assertTrue(detail.contains("copy_value = subject_copy_value"))
        assertTrue(
            detail.contains(
                "val subject_copy_value = translated_thread_subject?.ifBlank { null } ?: email?.subject?.ifBlank { null }",
            ),
        )
    }
}
