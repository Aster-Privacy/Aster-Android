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
package org.astermail.android.ui.contacts.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.design.components.aster_spinner
import org.astermail.android.ui.contacts.contacts_search_top_bar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class ContactsSyncSpinnerTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun capture(root: View): Bitmap {
        val main = root.rootView
        val out = Bitmap.createBitmap(main.width, main.height, Bitmap.Config.ARGB_8888)
        main.draw(Canvas(out))
        return out
    }

    private fun is_ink(pixel: Int, ink: Int): Boolean {
        val dr = abs(((pixel shr 16) and 0xff) - ((ink shr 16) and 0xff))
        val dg = abs(((pixel shr 8) and 0xff) - ((ink shr 8) and 0xff))
        val db = abs((pixel and 0xff) - (ink and 0xff))
        return dr + dg + db < 120
    }

    private fun ring_violations(
        crop: Bitmap,
        ink: Int,
        center_x: Float,
        center_y: Float,
        radius: Float,
        half_stroke: Float,
    ): Pair<Int, Int> {
        var ink_count = 0
        var off_ring = 0
        for (y in 0 until crop.height) {
            for (x in 0 until crop.width) {
                if (!is_ink(crop.getPixel(x, y), ink)) continue
                ink_count++
                val dx = x + 0.5f - center_x
                val dy = y + 0.5f - center_y
                val distance = sqrt(dx * dx + dy * dy)
                if (abs(distance - radius) > half_stroke + 1.5f) off_ring++
            }
        }
        return ink_count to off_ring
    }

    @Test
    fun spinner_stays_centered_when_its_bounds_are_not_square() {
        var root: View? = null
        var ink = 0
        compose_rule.mainClock.autoAdvance = false
        compose_rule.setContent {
            root = LocalView.current
            AsterTheme(use_dark_theme = false) {
                ink = AsterMaterial.colors.accent_blue.copy(alpha = 1f).toArgb()
                Box(Modifier.fillMaxSize().background(AsterMaterial.colors.bg_primary).padding(top = 24.dp, start = 24.dp)) {
                    aster_spinner(
                        color = AsterMaterial.colors.accent_blue,
                        stroke_width = 2.dp,
                        stroke_cap = StrokeCap.Round,
                        modifier = Modifier.size(20.dp).padding(end = 8.dp).testTag("narrow_spinner"),
                    )
                }
            }
        }
        compose_rule.mainClock.advanceTimeBy(16)
        compose_rule.waitForIdle()
        val bounds = compose_rule.onNodeWithTag("narrow_spinner", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val margin = 8
        val left = bounds.left.toInt() - margin
        val top = bounds.top.toInt() - margin
        val width = bounds.width.toInt() + margin * 2
        val height = bounds.height.toInt() + margin * 2
        val side = minOf(bounds.width, bounds.height)
        val stroke = 4f
        val center_x = bounds.width / 2f + margin
        val center_y = bounds.height / 2f + margin
        val report = StringBuilder("bounds=$bounds side=$side\n")
        var total_off = 0
        repeat(36) { index ->
            compose_rule.mainClock.advanceTimeBy(71)
            compose_rule.waitForIdle()
            val frame = compose_rule.runOnIdle { capture(requireNotNull(root)) }
            val crop = Bitmap.createBitmap(frame, left, top, width, height)
            val (ink_count, off_ring) = ring_violations(crop, ink, center_x, center_y, (side - stroke) / 2f, stroke / 2f)
            report.append("frame=$index ink=$ink_count off_ring=$off_ring\n")
            total_off += off_ring
        }
        val out_dir = File(System.getProperty("user.dir"), "build/screenshots/contacts_sync_spinner").apply { mkdirs() }
        File(out_dir, "narrow_report.txt").writeText(report.toString())
        assertEquals(report.toString(), 0, total_off)
    }

    @Test
    fun sync_spinner_turns_around_its_own_center() {
        var root: View? = null
        var ink = 0
        compose_rule.mainClock.autoAdvance = false
        compose_rule.setContent {
            root = LocalView.current
            AsterTheme(use_dark_theme = false) {
                ink = AsterMaterial.colors.accent_blue.copy(alpha = 1f).toArgb()
                Box(Modifier.fillMaxSize().background(AsterMaterial.colors.bg_primary).padding(top = 24.dp)) {
                    contacts_search_top_bar(
                        query = "",
                        on_query_change = {},
                        placeholder = "Search contacts",
                        is_syncing = true,
                        on_open_drawer = {},
                        on_back = {},
                        focus_requester = remember { FocusRequester() },
                        is_focused = false,
                        on_focus_change = {},
                        on_exit_search = {},
                        trailing = {},
                    )
                }
            }
        }
        compose_rule.mainClock.advanceTimeBy(16)
        compose_rule.waitForIdle()
        val bounds = compose_rule.onNodeWithTag("contacts_sync_spinner", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val margin = 12
        val left = bounds.left.toInt() - margin
        val top = bounds.top.toInt() - margin
        val right = bounds.right.toInt() + margin
        val bottom = bounds.bottom.toInt() + margin
        var min_x = Int.MAX_VALUE
        var min_y = Int.MAX_VALUE
        var max_x = Int.MIN_VALUE
        var max_y = Int.MIN_VALUE
        val frames = mutableListOf<Bitmap>()
        repeat(48) {
            compose_rule.mainClock.advanceTimeBy(53)
            compose_rule.waitForIdle()
            val frame = compose_rule.runOnIdle { capture(requireNotNull(root)) }
            val crop = Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
            frames += crop
            val (_, off_ring) = ring_violations(crop, ink, crop.width / 2f, crop.height / 2f, (bounds.width - 4f) / 2f, 2f)
            assertEquals("frame off ring bounds=$bounds", 0, off_ring)
            for (y in 0 until crop.height) {
                for (x in 0 until crop.width) {
                    if (is_ink(crop.getPixel(x, y), ink)) {
                        if (x < min_x) min_x = x
                        if (y < min_y) min_y = y
                        if (x > max_x) max_x = x
                        if (y > max_y) max_y = y
                    }
                }
            }
        }
        val out_dir = File(System.getProperty("user.dir"), "build/screenshots/contacts_sync_spinner").apply { mkdirs() }
        val cell = right - left
        val sheet = Bitmap.createBitmap(cell * 8, (bottom - top) * 6, Bitmap.Config.ARGB_8888)
        val sheet_canvas = Canvas(sheet)
        frames.forEachIndexed { index, crop ->
            sheet_canvas.drawBitmap(crop, ((index % 8) * cell).toFloat(), ((index / 8) * (bottom - top)).toFloat(), null)
        }
        File(out_dir, "frames.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val ink_center_x = (min_x + max_x) / 2f
        val ink_center_y = (min_y + max_y) / 2f
        val box_center_x = (right - left) / 2f
        val box_center_y = (bottom - top) / 2f
        val report = "bounds=$bounds ink=[$min_x,$min_y,$max_x,$max_y] ink_center=($ink_center_x,$ink_center_y) box_center=($box_center_x,$box_center_y)"
        File(out_dir, "report.txt").writeText(report)
        assertTrue(report, max_x > min_x && max_y > min_y)
        assertTrue(report, abs(ink_center_x - box_center_x) <= 1.5f)
        assertTrue(report, abs(ink_center_y - box_center_y) <= 1.5f)
        assertTrue(report, abs((max_x - min_x) - (max_y - min_y)) <= 2)
    }
}
