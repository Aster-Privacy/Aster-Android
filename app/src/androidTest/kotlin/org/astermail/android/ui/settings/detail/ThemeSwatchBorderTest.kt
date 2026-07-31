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

package org.astermail.android.ui.settings.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterColorThemes
import org.astermail.android.design.AsterTheme
import org.astermail.android.design.ColorThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSwatchBorderTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val canvas_bg = Color(0xFF123456)

    @Composable
    private fun swatch_under_test(selected: Boolean) {
        AsterTheme {
            Box(modifier = Modifier.background(canvas_bg).padding(12.dp)) {
                theme_swatch(
                    label = "Purple",
                    palette = AsterColorThemes.palette_for(ColorThemeId.purple)!!,
                    selected = selected,
                    on_click = {},
                    modifier = Modifier.testTag("swatch_under_test"),
                )
            }
        }
    }

    private fun ring_pixel(): Color {
        var last: Throwable? = null
        repeat(5) {
            compose_rule.waitForIdle()
            try {
                val image = compose_rule.onNodeWithTag("swatch_under_test").captureToImage()
                val pixels = image.toPixelMap()
                val circle_center_y = (33f * compose_rule.density.density).toInt()
                return pixels[1, circle_center_y]
            } catch (error: Throwable) {
                last = error
                compose_rule.mainClock.advanceTimeBy(64L)
            }
        }
        throw last ?: IllegalStateException("could not capture the swatch")
    }

    @Test
    fun an_unselected_swatch_draws_no_ring_at_all() {
        compose_rule.setContent { swatch_under_test(selected = false) }
        compose_rule.waitForIdle()

        assertEquals(
            "an unselected swatch must show only the page background at its edge",
            canvas_bg,
            ring_pixel(),
        )
    }

    @Test
    fun the_selected_swatch_still_draws_its_ring() {
        compose_rule.setContent { swatch_under_test(selected = true) }
        compose_rule.waitForIdle()

        assertNotEquals(
            "the selected swatch must remain visibly ringed",
            canvas_bg,
            ring_pixel(),
        )
    }
}
