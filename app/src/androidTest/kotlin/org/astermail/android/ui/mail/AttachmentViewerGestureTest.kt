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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import compose.icons.TablerIcons
import compose.icons.tablericons.Download
import org.astermail.android.R
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.common.vertical_scroll_bar
import org.astermail.android.ui.common.vertical_scroll_with_indicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AttachmentViewerGestureTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun sample_image(): ByteArray {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.rgb(30, 90, 200))
        canvas.drawCircle(400f, 300f, 200f, Paint().apply { color = AndroidColor.WHITE })
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun sample_pdf(pages: Int): ByteArray {
        val document = PdfDocument()
        val paint = Paint().apply { textSize = 48f }
        repeat(pages) { index ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
            page.canvas.drawText("Sheet ${index + 1}", 80f, 160f, paint)
            document.finishPage(page)
        }
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun SemanticsNodeInteraction.left_in_root(): Float =
        fetchSemanticsNode().layoutInfo.coordinates.localToRoot(Offset.Zero).x

    private fun SemanticsNodeInteraction.shown_width(): Float {
        val node = fetchSemanticsNode()
        val coordinates = node.layoutInfo.coordinates
        return coordinates.localToRoot(Offset(node.size.width.toFloat(), 0f)).x - coordinates.localToRoot(Offset.Zero).x
    }

    private fun wait_for_tag(tag: String) {
        compose_rule.waitUntil(10_000) { compose_rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun an_image_zooms_with_two_fingers_drags_and_resets_on_double_tap() {
        val bytes = sample_image()
        compose_rule.setContent {
            AsterTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    image_attachment_viewer(bytes, "sample")
                }
            }
        }
        wait_for_tag("attachment_image")
        val image = compose_rule.onNodeWithTag("attachment_image")
        val viewer = compose_rule.onNodeWithTag("attachment_image_viewer")
        val fitted_width = image.shown_width()
        assertEquals(0f, image.left_in_root(), 1f)

        viewer.performTouchInput {
            pinch(
                start0 = center - Offset(60f, 0f),
                end0 = center - Offset(260f, 0f),
                start1 = center + Offset(60f, 0f),
                end1 = center + Offset(260f, 0f),
            )
        }
        compose_rule.waitForIdle()
        val zoomed_width = image.shown_width()
        val zoomed_left = image.left_in_root()
        assertTrue("zoomed $zoomed_width from $fitted_width", zoomed_width > fitted_width * 2f)
        assertTrue("left $zoomed_left", zoomed_left < -100f)

        viewer.performTouchInput { swipe(center, center + Offset(180f, 0f), durationMillis = 400) }
        compose_rule.waitForIdle()
        val dragged_left = image.left_in_root()
        assertTrue("dragged $dragged_left from $zoomed_left", dragged_left > zoomed_left + 100f)
        assertTrue("stays inside $dragged_left", dragged_left <= 1f)

        viewer.performTouchInput { swipe(center, center + Offset(4000f, 0f), durationMillis = 400) }
        compose_rule.waitForIdle()
        assertEquals(0f, image.left_in_root(), 2f)

        viewer.performTouchInput { doubleClick(center) }
        compose_rule.waitForIdle()
        assertEquals(fitted_width, image.shown_width(), 2f)
        assertEquals(0f, image.left_in_root(), 2f)

        viewer.performTouchInput { doubleClick(center) }
        compose_rule.waitForIdle()
        assertTrue(image.shown_width() > fitted_width * 2f)
    }

    @Test
    fun a_pdf_shows_the_current_page_and_zooms() {
        val bytes = sample_pdf(3)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose_rule.setContent {
            AsterTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    pdf_attachment_viewer(bytes, "sample.pdf") { Text(it) }
                }
            }
        }
        wait_for_tag("pdf_page_indicator")
        val first_label = context.getString(R.string.pdf_page_of_total, 1, 3)
        val last_label = context.getString(R.string.pdf_page_of_total, 3, 3)
        compose_rule.onNodeWithTag("pdf_page_indicator").assertTextEquals(first_label)
        compose_rule.waitUntil(10_000) {
            compose_rule.onAllNodesWithContentDescription(first_label).fetchSemanticsNodes().isNotEmpty()
        }
        val page = compose_rule.onAllNodesWithContentDescription(first_label).onFirst()
        val fitted_width = page.shown_width()

        compose_rule.onNodeWithTag("pdf_pages").performTouchInput {
            pinch(
                start0 = center - Offset(60f, 0f),
                end0 = center - Offset(240f, 0f),
                start1 = center + Offset(60f, 0f),
                end1 = center + Offset(240f, 0f),
            )
        }
        compose_rule.waitForIdle()
        assertTrue(page.shown_width() > fitted_width * 1.8f)

        compose_rule.onNodeWithTag("pdf_pages").performTouchInput { doubleClick(center) }
        compose_rule.waitForIdle()
        repeat(6) {
            compose_rule.onNodeWithTag("pdf_pages").performTouchInput { swipeUp() }
            compose_rule.waitForIdle()
        }
        compose_rule.onNodeWithTag("pdf_page_indicator").assertTextEquals(last_label)
    }

    @Test
    fun a_text_file_scrolls_and_its_text_grows_with_two_fingers() {
        val bytes = (0 until 400).joinToString("\n") { "row number $it" }.toByteArray()
        compose_rule.setContent {
            AsterTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    text_attachment_viewer(bytes)
                }
            }
        }
        wait_for_tag("attachment_text_viewer")
        val first = compose_rule.onNodeWithText("row number 0")
        first.assertIsDisplayed()
        val height_before = first.fetchSemanticsNode().size.height

        compose_rule.onNodeWithTag("attachment_text_viewer").performTouchInput {
            pinch(
                start0 = center - Offset(60f, 0f),
                end0 = center - Offset(200f, 0f),
                start1 = center + Offset(60f, 0f),
                end1 = center + Offset(200f, 0f),
            )
        }
        compose_rule.waitForIdle()
        val height_after = compose_rule.onNodeWithText("row number 0").fetchSemanticsNode().size.height
        assertTrue("text height $height_after from $height_before", height_after > height_before)

        repeat(2) {
            compose_rule.onNodeWithTag("attachment_text_viewer").performTouchInput { swipeUp() }
            compose_rule.waitForIdle()
        }
        assertTrue(compose_rule.onAllNodesWithText("row number 0").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun a_viewer_button_is_a_full_size_touch_target() {
        var taps = 0
        compose_rule.setContent {
            AsterTheme {
                Box(Modifier.fillMaxSize()) {
                    attachment_viewer_button(TablerIcons.Download, "Download", "viewer_button") { taps++ }
                }
            }
        }
        val button = compose_rule.onNodeWithTag("viewer_button")
        button.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        button.performTouchInput {
            down(Offset(3f, height / 2f))
            up()
        }
        compose_rule.waitForIdle()
        button.performClick()
        compose_rule.waitForIdle()
        assertEquals(2, taps)
    }

    @Test
    fun dragging_the_list_scroll_bar_moves_through_the_list() {
        val state = LazyListState()
        compose_rule.setContent {
            AsterTheme {
                Box(Modifier.fillMaxSize().testTag("list_host")) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                        items(600, key = { it }) { Text("entry $it", Modifier.fillMaxWidth().height(56.dp)) }
                    }
                    vertical_scroll_bar(state = state, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
        }
        compose_rule.waitForIdle()
        compose_rule.mainClock.autoAdvance = false
        val host = compose_rule.onNodeWithTag("list_host")
        host.performTouchInput { swipe(center, center - Offset(0f, 60f), durationMillis = 120) }
        compose_rule.mainClock.advanceTimeBy(200)
        val before = state.firstVisibleItemIndex
        assertTrue("near the top, was $before", before < 5)
        host.performTouchInput {
            down(Offset(width - 6f, 40f))
            moveBy(Offset(0f, 40f))
        }
        compose_rule.mainClock.advanceTimeBy(50)
        host.performTouchInput { moveBy(Offset(0f, height * 0.4f)) }
        compose_rule.mainClock.advanceTimeBy(50)
        host.performTouchInput { up() }
        compose_rule.mainClock.advanceTimeBy(50)
        val after = state.firstVisibleItemIndex
        assertTrue("moved to $after", after > 150)
        assertTrue("moved to $after", abs(after - 264) < 120)
        compose_rule.mainClock.autoAdvance = true
    }

    @Test
    fun a_scrolling_page_with_an_indicator_still_scrolls() {
        compose_rule.setContent {
            AsterTheme {
                Column(Modifier.fillMaxSize().testTag("page").vertical_scroll_with_indicator()) {
                    repeat(80) { Text("setting $it", Modifier.fillMaxWidth().height(56.dp)) }
                }
            }
        }
        compose_rule.onNodeWithText("setting 0").assertIsDisplayed()
        repeat(2) {
            compose_rule.onNodeWithTag("page").performTouchInput { swipeUp() }
            compose_rule.waitForIdle()
        }
        compose_rule.onNodeWithText("setting 79").assertExists()
        assertTrue(compose_rule.onNodeWithText("setting 0").fetchSemanticsNode().boundsInRoot.bottom <= 0f)
    }
}
