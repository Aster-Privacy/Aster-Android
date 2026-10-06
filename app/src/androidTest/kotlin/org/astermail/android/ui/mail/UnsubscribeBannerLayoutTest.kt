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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.R
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class UnsubscribeBannerLayoutTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private var pill_label = ""

    private fun show(width: Dp, font_scale: Float, tag: String = "pt-PT") {
        compose_rule.setContent {
            val base = LocalContext.current
            val config = Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
                fontScale = font_scale
            }
            val context = base.createConfigurationContext(config)
            pill_label = context.getString(R.string.unsubscribe)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalResources provides context.resources,
                LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, font_scale),
            ) {
                AsterTheme(use_dark_theme = true) {
                    Box(Modifier.width(width)) {
                        unsubscribe_banner(on_unsubscribe = {})
                    }
                }
            }
        }
        compose_rule.waitForIdle()
    }

    private fun label_bounds(): Rect = compose_rule.onNodeWithTag("compact_banner_label").getBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    private fun pill_bounds(): Rect = compose_rule.onNodeWithText(pill_label).getBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    private fun label_layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose_rule.onNodeWithTag("compact_banner_label").fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    private fun assert_label_not_truncated() {
        val layout = label_layout()
        assertFalse("label is cut off", layout.hasVisualOverflow)
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    private fun assert_inline() {
        val label = label_bounds()
        val pill = pill_bounds()
        assertTrue("pill should sit beside the text, label=$label pill=$pill", pill.left >= label.right)
        assertTrue("pill should overlap the text vertically", pill.top < label.bottom && pill.bottom > label.top)
        assertEquals(label.center.y, pill.center.y, 2f)
    }

    private fun assert_stacked() {
        val label = label_bounds()
        val pill = pill_bounds()
        assertTrue("pill should sit under the text, label=$label pill=$pill", pill.top >= label.bottom - 1f)
    }

    @Test
    fun pill_sits_beside_the_text_at_phone_width() {
        show(width = 411.dp, font_scale = 1f)
        assert_inline()
        assert_label_not_truncated()
        assertTrue(label_layout().lineCount <= 3)
    }

    @Test
    fun pill_sits_beside_the_text_in_english() {
        show(width = 411.dp, font_scale = 1f, tag = "en")
        assert_inline()
        assert_label_not_truncated()
    }

    @Test
    fun pill_moves_under_the_text_on_a_narrow_screen() {
        show(width = 260.dp, font_scale = 1f)
        assert_stacked()
        assert_label_not_truncated()
    }

    @Test
    fun pill_moves_under_the_text_at_double_font_scale() {
        show(width = 411.dp, font_scale = 2f)
        assert_stacked()
        assert_label_not_truncated()
    }

    @Test
    fun pill_keeps_a_touch_target() {
        show(width = 411.dp, font_scale = 1f)
        val node = compose_rule.onNodeWithText(pill_label).fetchSemanticsNode()
        val min_px = with(compose_rule.density) { 48.dp.toPx() } - 1f
        assertTrue("touch height ${node.touchBoundsInRoot.height}", node.touchBoundsInRoot.height >= min_px)
        assertTrue("touch width ${node.touchBoundsInRoot.width}", node.touchBoundsInRoot.width >= min_px)
    }
}
