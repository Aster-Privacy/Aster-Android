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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongAliasLabelChipTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val long_alias = "extremely.long.forwarding.address.for.testing@subdomain.example.com"

    private val label_name = "Receipts"

    private fun thread(): ThreadRow {
        val email = Email(
            id = "m1",
            sender_name = "Aster",
            sender_email = "noreply@astermail.org",
            subject = "Welcome",
            preview = "hi",
            received_at = 1_000L,
            is_read = true,
            is_starred = false,
            has_attachment = false,
            received_on = long_alias,
        )
        return ThreadRow(
            thread_id = "t1",
            newest = email,
            message_count = 1,
            has_unread = false,
            has_encrypted = false,
            total_trackers = 0,
            has_attachment = false,
            is_starred = false,
            label_colors = listOf(Color(0xFFF59E0B)),
            label_names = listOf(label_name),
            label_icons = listOf(""),
        )
    }

    @Test
    fun label_chip_stays_inside_the_row_when_the_alias_is_long() {
        compose_rule.setContent {
            Box(modifier = Modifier.width(360.dp)) {
                ThreadInboxRow(
                    thread = thread(),
                    on_click = {},
                    on_long_click = {},
                    on_toggle_star = {},
                )
            }
        }

        compose_rule.waitForIdle()

        val root_right = compose_rule.onRoot().getUnclippedBoundsInRoot().right
        val label_bounds = compose_rule.onNodeWithText(label_name, useUnmergedTree = true).getUnclippedBoundsInRoot()

        compose_rule.onNodeWithText(label_name, useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            "label right ${label_bounds.right} exceeds root right $root_right",
            label_bounds.right <= root_right,
        )
        assertTrue(
            "label left ${label_bounds.left} is off screen",
            label_bounds.left >= 0.dp,
        )
    }

    @Test
    fun alias_chip_sits_to_the_right_of_the_subject() {
        compose_rule.setContent {
            Box(modifier = Modifier.width(360.dp)) {
                ThreadInboxRow(
                    thread = thread(),
                    on_click = {},
                    on_long_click = {},
                    on_toggle_star = {},
                )
            }
        }

        compose_rule.waitForIdle()

        val root_right = compose_rule.onRoot().getUnclippedBoundsInRoot().right
        val alias_bounds = compose_rule.onNodeWithText(long_alias, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val subject_bounds = compose_rule.onNodeWithText("Welcome", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue(
            "alias left ${alias_bounds.left} is not right of subject right ${subject_bounds.right}",
            alias_bounds.left >= subject_bounds.right,
        )
        assertTrue(
            "alias top ${alias_bounds.top} is below the subject line ${subject_bounds.bottom}",
            alias_bounds.top < subject_bounds.bottom,
        )
        assertTrue(
            "alias right ${alias_bounds.right} exceeds root right $root_right",
            alias_bounds.right <= root_right,
        )
    }
}
