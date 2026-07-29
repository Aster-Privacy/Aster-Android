// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import compose.icons.TablerIcons
import compose.icons.tablericons.Star
import org.astermail.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StarTooltipAndCopyToastTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun set_star(is_starred: Boolean) {
        compose_rule.setContent {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                star_toggle_icon(
                    is_starred = is_starred,
                    icon = TablerIcons.Star,
                    onClick = {},
                    modifier = Modifier.testTag("star"),
                )
            }
        }
    }

    @Test
    fun long_press_shows_not_starred_tooltip_above_the_icon() {
        set_star(is_starred = false)
        val label = context.getString(R.string.not_starred)

        compose_rule.onNodeWithTag("star").performTouchInput { longClick() }

        val tooltip = compose_rule.onAllNodesWithText(label)
            .filterToOne(hasAnyAncestor(isPopup()))
        tooltip.assertIsDisplayed()

        val anchor_top = compose_rule.onNodeWithTag("star").fetchSemanticsNode()
            .boundsInRoot.top
        val tooltip_bottom = tooltip.fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            "tooltip should sit above the icon, got $tooltip_bottom vs $anchor_top",
            tooltip_bottom <= anchor_top,
        )
    }

    @Test
    fun long_press_shows_starred_tooltip_when_already_starred() {
        set_star(is_starred = true)
        val label = context.getString(R.string.starred)

        compose_rule.onNodeWithTag("star").performTouchInput { longClick() }

        compose_rule.onAllNodesWithText(label)
            .filterToOne(hasAnyAncestor(isPopup()))
            .assertIsDisplayed()
    }

    @Test
    fun copy_toast_includes_the_copied_value() {
        assertEquals(
            context.getString(R.string.copied_value, "user@example.com"),
            copied_toast_text(context, "user@example.com"),
        )
    }

    @Test
    fun copy_toast_trims_surrounding_whitespace() {
        assertEquals(
            context.getString(R.string.copied_value, "user@example.com"),
            copied_toast_text(context, "  user@example.com  "),
        )
    }

    @Test
    fun copy_toast_truncates_very_long_values() {
        val long_value = "a".repeat(200)
        val text = copied_toast_text(context, long_value)
        assertTrue(text.endsWith("…"))
        assertTrue(text.length < long_value.length)
    }

    @Test
    fun copy_toast_falls_back_to_plain_copied_for_blank_values() {
        assertEquals(context.getString(R.string.copied), copied_toast_text(context, "   "))
    }
}
