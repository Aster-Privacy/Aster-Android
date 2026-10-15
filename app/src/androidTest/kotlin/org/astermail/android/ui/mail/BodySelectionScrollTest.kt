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

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.MainActivity
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BodySelectionScrollTest {

    @get:Rule
    val activity_rule = ActivityScenarioRule(MainActivity::class.java)

    private val host = AtomicReference<ComposeView?>(null)
    private val list_state = AtomicReference<LazyListState?>(null)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun find_body(view: View?): mail_body_web_view? {
        if (view is mail_body_web_view) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                find_body(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun long_body(): String = buildString {
        append("<html><body>")
        for (line in 1..PARAGRAPH_COUNT) {
            append("<p>Paragraph $line of the follow-up with enough words to fill the whole line of text.</p>")
        }
        append("</body></html>")
    }

    private fun show_body() {
        val body = long_body()
        activity_rule.scenario.onActivity { activity ->
            val view = ComposeView(activity)
            host.set(view)
            view.setContent {
                AsterTheme(use_dark_theme = true) {
                    val state = rememberLazyListState()
                    list_state.set(state)
                    LazyColumn(
                        state = state,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AsterMaterial.colors.bg_primary),
                    ) {
                        item(key = "header") { Box(modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT_DP.dp)) }
                        item(key = "body") { email_html_view(html = body, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
            activity.setContentView(view)
        }
        wait_until("body taller than the screen") {
            val web = find_body(host.get())
            web != null && web.height > web.resources.displayMetrics.heightPixels
        }
        Thread.sleep(SETTLE_MS)
    }

    private fun wait_until(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MS
        var met = false
        while (!met && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { met = condition() }
            if (!met) Thread.sleep(POLL_MS)
        }
        assertTrue("timed out waiting for $label", met)
    }

    private fun screen_size(): Pair<Int, Int> {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun long_press_body() {
        val (width, height) = screen_size()
        val x = width * 0.3f
        val y = height * 0.5f
        val down_time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(down_time, down_time, MotionEvent.ACTION_DOWN, x, y, 0)
        instrumentation.sendPointerSync(down)
        Thread.sleep(LONG_PRESS_MS)
        val up = MotionEvent.obtain(down_time, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        instrumentation.sendPointerSync(up)
        down.recycle()
        up.recycle()
    }

    private fun select_all_with_keyboard() {
        val now = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON),
        )
        instrumentation.sendKeySync(
            KeyEvent(now, now + 30, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON),
        )
        Thread.sleep(SETTLE_MS)
    }

    private fun drag_up(x_fraction: Float = EDGE_X_FRACTION) {
        val (width, height) = screen_size()
        val x = width * x_fraction
        val start_y = height * 0.8f
        val end_y = height * 0.3f
        val down_time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(down_time, down_time, MotionEvent.ACTION_DOWN, x, start_y, 0)
        instrumentation.sendPointerSync(down)
        down.recycle()
        for (step in 1..DRAG_STEPS) {
            Thread.sleep(DRAG_STEP_MS)
            val y = start_y + (end_y - start_y) * step / DRAG_STEPS
            val move = MotionEvent.obtain(down_time, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y, 0)
            instrumentation.sendPointerSync(move)
            move.recycle()
        }
        Thread.sleep(DRAG_STEP_MS)
        val up = MotionEvent.obtain(down_time, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, end_y, 0)
        instrumentation.sendPointerSync(up)
        up.recycle()
        Thread.sleep(SETTLE_MS)
    }

    private fun scrolled_px(): Int {
        var total = 0
        instrumentation.runOnMainSync {
            val state = list_state.get() ?: return@runOnMainSync
            total = state.firstVisibleItemIndex * INDEX_WEIGHT_PX + state.firstVisibleItemScrollOffset
        }
        return total
    }

    private fun body_scroll_y(): Int {
        var offset = 0
        instrumentation.runOnMainSync { offset = find_body(host.get())?.scrollY ?: 0 }
        return offset
    }

    private fun selection_active(): Boolean {
        var active = false
        instrumentation.runOnMainSync { active = find_body(host.get())?.selection_active == true }
        return active
    }

    @Test
    fun the_message_scrolls_with_no_selection() {
        show_body()
        val before = scrolled_px()
        drag_up()
        val after = scrolled_px()
        Log.i(TAG, "no selection: $before -> $after")
        assertTrue("list did not scroll: $before -> $after", after > before)
    }

    @Test
    fun the_message_still_scrolls_after_a_long_press_selection() {
        show_body()
        long_press_body()
        wait_until("selection to start") { find_body(host.get())?.selection_active == true }
        Thread.sleep(SETTLE_MS)
        val before = scrolled_px()
        drag_up()
        val middle = scrolled_px()
        Log.i(TAG, "selection: $before -> $middle active=${selection_active()}")
        assertTrue("list did not scroll with a selection: $before -> $middle", middle > before)
        assertTrue("selection was dropped by scrolling", selection_active())
        drag_up()
        val after = scrolled_px()
        assertTrue("list stopped scrolling on the second drag: $middle -> $after", after > middle)
    }

    @Test
    fun the_message_still_scrolls_after_select_all() {
        show_body()
        long_press_body()
        wait_until("selection to start") { find_body(host.get())?.selection_active == true }
        select_all_with_keyboard()
        assertTrue("selection ended before the drag", selection_active())
        val before = scrolled_px()
        drag_up()
        val middle = scrolled_px()
        Log.i(TAG, "select all: $before -> $middle active=${selection_active()}")
        assertTrue("list did not scroll after select all: $before -> $middle", middle > before)
        drag_up()
        val after = scrolled_px()
        assertTrue("list stopped scrolling on the second drag: $middle -> $after", after > middle)
    }

    @Test
    fun the_body_never_scrolls_inside_itself_with_a_selection() {
        show_body()
        long_press_body()
        wait_until("selection to start") { find_body(host.get())?.selection_active == true }
        Thread.sleep(SETTLE_MS)
        val before = scrolled_px()
        drag_up(TEXT_X_FRACTION)
        val after = scrolled_px()
        val inner = body_scroll_y()
        Log.i(TAG, "drag over text: $before -> $after inner=$inner")
        assertTrue("list did not scroll from a drag over the text: $before -> $after", after > before)
        assertEquals("message body scrolled inside its own frame", 0, inner)
        assertTrue("selection was dropped by scrolling", selection_active())
    }

    private companion object {
        const val TAG = "BodySelectionScroll"
        const val PARAGRAPH_COUNT = 240
        const val HEADER_HEIGHT_DP = 120
        const val WAIT_TIMEOUT_MS = 15_000L
        const val POLL_MS = 150L
        const val SETTLE_MS = 800L
        const val LONG_PRESS_MS = 900L
        const val DRAG_STEPS = 14
        const val DRAG_STEP_MS = 40L
        const val INDEX_WEIGHT_PX = 1_000_000
        const val EDGE_X_FRACTION = 0.92f
        const val TEXT_X_FRACTION = 0.4f
    }
}
