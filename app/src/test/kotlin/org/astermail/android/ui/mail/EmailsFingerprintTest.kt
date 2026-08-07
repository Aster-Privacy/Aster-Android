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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EmailsFingerprintTest {

    private fun email(
        id: String = "1",
        label_names: List<String> = emptyList(),
        label_colors: List<Color> = emptyList(),
        label_icons: List<String> = emptyList(),
    ) = Email(
        id = id,
        sender_name = "Sender",
        sender_email = "sender@astermail.org",
        subject = "Subject",
        preview = "Preview",
        received_at = 1_700_000_000_000,
        is_read = false,
        is_starred = false,
        has_attachment = false,
        label_names = label_names,
        label_colors = label_colors,
        label_icons = label_icons,
    )

    @Test
    fun adding_a_label_changes_the_fingerprint() {
        val before = listOf(email())
        val after = listOf(
            email(
                label_names = listOf("Receipts"),
                label_colors = listOf(Color(0xFF4CAF50)),
                label_icons = listOf("tag"),
            ),
        )
        assertNotEquals(emails_fingerprint_of(before), emails_fingerprint_of(after))
    }

    @Test
    fun renaming_a_label_changes_the_fingerprint() {
        val before = listOf(email(label_names = listOf("Work")))
        val after = listOf(email(label_names = listOf("Personal")))
        assertNotEquals(emails_fingerprint_of(before), emails_fingerprint_of(after))
    }

    @Test
    fun recoloring_a_label_changes_the_fingerprint() {
        val before = listOf(email(label_colors = listOf(Color(0xFF4CAF50))))
        val after = listOf(email(label_colors = listOf(Color(0xFFE91E63))))
        assertNotEquals(emails_fingerprint_of(before), emails_fingerprint_of(after))
    }

    @Test
    fun removing_a_label_changes_the_fingerprint() {
        val before = listOf(email(label_names = listOf("Work"), label_icons = listOf("tag")))
        val after = listOf(email())
        assertNotEquals(emails_fingerprint_of(before), emails_fingerprint_of(after))
    }

    @Test
    fun an_unchanged_list_keeps_the_same_fingerprint() {
        val labels = listOf("Work")
        assertEquals(
            emails_fingerprint_of(listOf(email(label_names = labels))),
            emails_fingerprint_of(listOf(email(label_names = labels))),
        )
    }
}
