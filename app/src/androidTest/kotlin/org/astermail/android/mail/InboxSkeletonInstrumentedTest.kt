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


package org.astermail.android.mail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.mail.inbox_skeleton
import org.astermail.android.ui.mail.inbox_skeleton_row_tag
import org.astermail.android.ui.mail.inbox_skeleton_tag
import org.astermail.android.ui.theme.AccessibilityState
import org.astermail.android.ui.theme.local_accessibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class InboxSkeletonInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val comfortable_min_height_dp = 88f

    private fun render(
        row_count: Int = 10,
        list_density: String? = null,
        reduce_motion: Boolean = false,
    ) {
        compose.setContent {
            AsterTheme {
                CompositionLocalProvider(
                    local_accessibility provides AccessibilityState(reduce_motion = reduce_motion),
                ) {
                    inbox_skeleton(list_density = list_density, row_count = row_count)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun visible_rows(): Int =
        compose.onAllNodesWithTag(inbox_skeleton_row_tag).fetchSemanticsNodes().size

    @Test
    fun the_shell_lays_out_rows() {
        render(row_count = 6)

        compose.onNodeWithTag(inbox_skeleton_tag).assertExists()
        assertEquals(6, visible_rows())
    }

    @Test
    fun every_row_has_real_size() {
        render(row_count = 4)

        val nodes = compose.onAllNodesWithTag(inbox_skeleton_row_tag).fetchSemanticsNodes()
        nodes.forEach { node ->
            assertTrue(node.size.width > 0)
            assertTrue(node.size.height > 0)
        }
    }

    @Test
    fun a_comfortable_row_honors_its_minimum_height() {
        render(row_count = 1, list_density = "comfortable")

        val height = compose.onAllNodesWithTag(inbox_skeleton_row_tag)
            .fetchSemanticsNodes()
            .first()
            .size
            .height
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .displayMetrics
            .density

        assertTrue(height >= (comfortable_min_height_dp * density).roundToInt())
    }

    @Test
    fun the_shell_renders_without_motion() {
        render(row_count = 3, reduce_motion = true)

        assertEquals(3, visible_rows())
    }
}
