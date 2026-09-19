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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxSkeletonGeometryTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val real_tag = "geometry_real_row"
    private val skeleton_tag = "geometry_skeleton_row"

    private fun thread(preview: String) = ThreadRow(
        thread_id = "t1",
        newest = Email(
            id = "m1",
            sender_name = "Aster",
            sender_email = "noreply@astermail.org",
            subject = "Weekly summary",
            preview = preview,
            received_at = 1_700_000_000_000L,
            is_read = true,
            is_starred = false,
            has_attachment = false,
        ),
        message_count = 1,
        has_unread = false,
        has_encrypted = false,
        total_trackers = 0,
        has_attachment = false,
        is_starred = false,
        label_colors = emptyList(),
        label_names = emptyList(),
        label_icons = emptyList(),
    )

    private fun assert_same_height(density_name: String, font_scale: Float, show_preview: Boolean) {
        val prefs = UserPreferences(
            mail_list_density = density_name,
            show_email_preview = show_preview,
        )
        compose_rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, font_scale)) {
                AsterTheme {
                    Column(modifier = Modifier.width(360.dp)) {
                        Box(modifier = Modifier.testTag(real_tag)) {
                            ThreadInboxRow(
                                thread = thread(if (show_preview) "Here is what happened this week" else ""),
                                on_click = {},
                                on_long_click = {},
                                on_toggle_star = {},
                                user_prefs = prefs,
                            )
                        }
                        Box(modifier = Modifier.testTag(skeleton_tag)) {
                            inbox_skeleton_row(
                                list_density = density_name,
                                show_preview = show_preview,
                            )
                        }
                    }
                }
            }
        }
        compose_rule.waitForIdle()
        val real = compose_rule.onNodeWithTag(real_tag).getUnclippedBoundsInRoot()
        val skeleton = compose_rule.onNodeWithTag(skeleton_tag).getUnclippedBoundsInRoot()
        assertEquals(
            "density=$density_name scale=$font_scale preview=$show_preview",
            real.height.value,
            skeleton.height.value,
            0.5f,
        )
    }

    @Test
    fun comfortable_rows_match_at_default_scale() = assert_same_height("comfortable", 1.0f, true)

    @Test
    fun comfortable_rows_match_at_large_scale() = assert_same_height("comfortable", 1.3f, true)

    @Test
    fun compact_rows_match_at_default_scale() = assert_same_height("compact", 1.0f, true)

    @Test
    fun compact_rows_match_at_large_scale() = assert_same_height("compact", 1.3f, true)

    @Test
    fun rows_without_preview_match_at_large_scale() = assert_same_height("comfortable", 1.3f, false)
}
