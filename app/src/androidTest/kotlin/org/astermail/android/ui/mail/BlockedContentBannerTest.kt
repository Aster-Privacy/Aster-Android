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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class BlockedContentBannerTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private var loads = 0
    private var always = 0

    private fun string(res: Int, tag: String = "pt-PT"): String {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config).getString(res)
    }

    private fun plural(res: Int, n: Int): String {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("pt-PT")) }
        return base.createConfigurationContext(config).resources.getQuantityString(res, n, n)
    }

    private fun show(
        width: Dp = 411.dp,
        font_scale: Float = 1f,
        counts: ExternalContentCounts = ExternalContentCounts(image_count = 3, tracker_count = 1, font_count = 0, css_count = 0),
        offer_always: Boolean = true,
    ) {
        compose_rule.setContent {
            val base = LocalContext.current
            val config = Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag("pt-PT"))
                fontScale = font_scale
            }
            val context = base.createConfigurationContext(config)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalResources provides context.resources,
                LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, font_scale),
            ) {
                AsterTheme(use_dark_theme = true) {
                    Box(Modifier.width(width)) {
                        external_content_banner(
                            counts = counts,
                            on_allow_once = { loads++ },
                            on_always_allow = if (offer_always) ({ always++ }) else null,
                            on_show_trackers = {},
                        )
                    }
                }
            }
        }
        compose_rule.waitForIdle()
    }

    private fun bounds(text: String): Rect = compose_rule.onNodeWithText(text, substring = true).getBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    private fun tag_bounds(tag: String): Rect = compose_rule.onNodeWithTag(tag).getBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    private val images get() = plural(R.plurals.n_images, 3)
    private val tracker get() = plural(R.plurals.n_tracking_pixels, 1)
    private val load get() = string(R.string.detail_external_load)

    @Test
    fun summary_and_actions_share_one_line_at_phone_width() {
        show()
        compose_rule.onAllNodesWithTag("banner_trackers").assertCountEquals(1)
        val text = bounds(images)
        val trackers = tag_bounds("banner_trackers")
        val pill = bounds(load)
        val more = tag_bounds("compact_banner_more")
        assertEquals(text.center.y, trackers.center.y, 2f)
        assertEquals(text.center.y, pill.center.y, 2f)
        assertTrue("pill beside the summary", pill.left >= trackers.right)
        assertTrue("menu button after the pill", more.left >= pill.right)
    }

    @Test
    fun summary_takes_two_rows_beside_the_actions_at_larger_text() {
        show(font_scale = 1.3f)
        val text = bounds(images)
        val trackers = tag_bounds("banner_trackers")
        val pill = bounds(load)
        assertTrue("tracker row under the images row", trackers.top >= text.bottom - 1f)
        assertTrue("pill beside the summary", pill.left >= maxOf(text.right, trackers.right))
        assertTrue("pill centred on the summary", pill.top < trackers.bottom && pill.bottom > text.top)
    }

    @Test
    fun actions_move_under_the_summary_when_nothing_fits_beside_it() {
        show(font_scale = 2f)
        val trackers = tag_bounds("banner_trackers")
        val pill = bounds(load)
        assertTrue("pill under the summary", pill.top >= trackers.bottom - 1f)
        compose_rule.onNodeWithText(tracker).assertExists()
    }

    @Test
    fun load_runs_the_load_once_action() {
        show()
        compose_rule.onNodeWithText(load).performClick()
        compose_rule.waitForIdle()
        assertEquals(1, loads)
        assertEquals(0, always)
    }

    @Test
    fun always_allow_is_one_tap_away_in_the_menu() {
        show()
        compose_rule.onNodeWithTag("compact_banner_more")
            .assertContentDescriptionEquals(string(R.string.more_options))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose_rule.onAllNodesWithText(string(R.string.detail_external_always_allow)).assertCountEquals(0)
        compose_rule.onNodeWithTag("compact_banner_more").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText(string(R.string.detail_external_always_allow)).performClick()
        compose_rule.waitForIdle()
        assertEquals(1, always)
        assertEquals(0, loads)
    }

    @Test
    fun without_always_allow_there_is_only_the_load_pill() {
        show(offer_always = false)
        compose_rule.onAllNodesWithTag("compact_banner_more").assertCountEquals(0)
        compose_rule.onNodeWithText(load).assertExists()
    }

    @Test
    fun actions_keep_a_touch_target() {
        show()
        val min_px = with(compose_rule.density) { 48.dp.toPx() } - 1f
        for (node in listOf(
            compose_rule.onNodeWithText(load).fetchSemanticsNode(),
            compose_rule.onNodeWithTag("compact_banner_more").fetchSemanticsNode(),
        )) {
            assertTrue("touch height ${node.touchBoundsInRoot.height}", node.touchBoundsInRoot.height >= min_px)
            assertTrue("touch width ${node.touchBoundsInRoot.width}", node.touchBoundsInRoot.width >= min_px)
        }
    }

    @Test
    fun images_only_banner_keeps_one_row() {
        show(counts = ExternalContentCounts(image_count = 3, tracker_count = 0, font_count = 0, css_count = 0))
        val text = bounds(images)
        val pill = bounds(load)
        assertEquals(text.center.y, pill.center.y, 2f)
    }
}
