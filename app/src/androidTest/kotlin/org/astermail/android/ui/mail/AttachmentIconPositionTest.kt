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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttachmentIconPositionTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val received_at = 1_700_000_000_000L

    private fun email(has_attachment: Boolean) = Email(
        id = "m1",
        sender_name = "Aster",
        sender_email = "noreply@astermail.org",
        subject = "Invoice",
        preview = "your receipt",
        received_at = received_at,
        is_read = true,
        is_starred = false,
        has_attachment = has_attachment,
    )

    private fun thread(has_attachment: Boolean) = ThreadRow(
        thread_id = "t1",
        newest = email(has_attachment),
        message_count = 1,
        has_unread = false,
        has_encrypted = false,
        total_trackers = 0,
        has_attachment = has_attachment,
        is_starred = false,
        label_colors = emptyList(),
        label_names = emptyList(),
        label_icons = emptyList(),
    )

    private fun time_label(): String =
        received_at.format_relative_time(context.getString(R.string.yesterday))

    private fun attachment_label(): String = context.getString(R.string.has_attachment)

    @Test
    fun single_row_shows_the_attachment_icon_before_the_time() {
        compose_rule.setContent {
            Box(modifier = Modifier.width(360.dp)) {
                EmailRow(
                    email = email(true),
                    on_click = {},
                    on_long_click = {},
                    on_toggle_star = {},
                )
            }
        }

        compose_rule.waitForIdle()

        compose_rule.onNodeWithContentDescription(attachment_label(), useUnmergedTree = true)
            .assertIsDisplayed()

        val icon = compose_rule
            .onNodeWithContentDescription(attachment_label(), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val time = compose_rule
            .onNodeWithText(time_label(), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "icon right ${icon.right} is not before time left ${time.left}",
            icon.right <= time.left,
        )
    }

    @Test
    fun thread_row_shows_the_attachment_icon_before_the_time() {
        compose_rule.setContent {
            Box(modifier = Modifier.width(360.dp)) {
                ThreadInboxRow(
                    thread = thread(true),
                    on_click = {},
                    on_long_click = {},
                    on_toggle_star = {},
                )
            }
        }

        compose_rule.waitForIdle()

        val icon = compose_rule
            .onNodeWithContentDescription(attachment_label(), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val time = compose_rule
            .onNodeWithText(time_label(), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "icon right ${icon.right} is not before time left ${time.left}",
            icon.right <= time.left,
        )
    }

    @Test
    fun rows_without_attachments_show_no_icon() {
        compose_rule.setContent {
            Box(modifier = Modifier.width(360.dp)) {
                EmailRow(
                    email = email(false),
                    on_click = {},
                    on_long_click = {},
                    on_toggle_star = {},
                )
            }
        }

        compose_rule.waitForIdle()

        compose_rule.onNodeWithContentDescription(attachment_label(), useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
