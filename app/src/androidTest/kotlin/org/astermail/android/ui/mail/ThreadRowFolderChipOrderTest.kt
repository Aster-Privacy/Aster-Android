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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadRowFolderChipOrderTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun sample_email() = Email(
        id = "m1",
        sender_name = "Acme Store",
        sender_email = "orders@acme.test",
        subject = "Your receipt",
        preview = "Thanks for your order",
        received_at = 1_770_000_000_000L,
        is_read = true,
        is_starred = false,
        has_attachment = false,
    )

    private fun sample_thread(
        folder_chip: list_folder_chip?,
        labels: List<String> = listOf("Receipts", "Shopping"),
    ) = ThreadRow(
        thread_id = "t1",
        newest = sample_email(),
        message_count = 1,
        has_unread = false,
        has_encrypted = true,
        total_trackers = 0,
        has_attachment = false,
        is_starred = false,
        label_colors = labels.map { Color(0xFF22C55E) },
        label_names = labels,
        label_icons = labels.map { "" },
        folder_chip = folder_chip,
    )

    private fun render(folder_chip: list_folder_chip?, labels: List<String> = listOf("Receipts", "Shopping")) {
        compose_rule.setContent {
            AsterTheme {
                ThreadInboxRow(
                    thread = sample_thread(folder_chip, labels),
                    on_click = {},
                    on_long_click = {},
                    on_toggle_star = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Test
    fun folder_chip_renders_before_label_chips() {
        render(
            folder_chip = list_folder_chip(name = "Archive", icon = "archive", color = Color(0xFF3B82F6)),
            labels = listOf("Receipts"),
        )

        compose_rule.onNodeWithTag("list_folder_chip", useUnmergedTree = true).assertExists()
        compose_rule.onNodeWithText("Receipts", useUnmergedTree = true).assertExists()

        val folder_bounds = compose_rule.onNodeWithTag("list_folder_chip", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label_bounds = compose_rule.onNodeWithText("Receipts", useUnmergedTree = true).getUnclippedBoundsInRoot()

        val folder_is_first = folder_bounds.top < label_bounds.top ||
            (folder_bounds.top == label_bounds.top && folder_bounds.left < label_bounds.left)
        assertTrue(
            "folder chip must render before the label chips (folder=$folder_bounds label=$label_bounds)",
            folder_is_first,
        )
    }

    @Test
    fun label_chips_still_render_without_a_folder_chip() {
        render(null)

        compose_rule.onNodeWithTag("list_folder_chip", useUnmergedTree = true).assertDoesNotExist()
        compose_rule.onNodeWithText("Receipts", useUnmergedTree = true).assertExists()
        compose_rule.onNodeWithText("Shopping", useUnmergedTree = true).assertExists()
    }
}
