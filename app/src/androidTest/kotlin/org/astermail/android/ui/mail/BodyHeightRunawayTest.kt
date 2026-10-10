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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.MainActivity
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BodyHeightRunawayTest {

    @get:Rule
    val activity_rule = ActivityScenarioRule(MainActivity::class.java)

    private data class Sample(
        val view_px: Int,
        val content_css: Int,
        val scale: Float,
        val density: Float,
        val scroll_range: Int,
        val scroll_extent: Int,
    ) {
        val view_dp: Float get() = view_px / density
        val page_scale: Float get() = scale / density
        val overflows: Boolean get() = body_overflows_sideways(scroll_range, scroll_extent)
    }

    private fun find_web_view(view: View?): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                find_web_view(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private val host = AtomicReference<ComposeView?>(null)

    private fun tap_quote_toggle() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val point = FloatArray(2)
        instrumentation.runOnMainSync {
            val web = find_web_view(host.get()) ?: return@runOnMainSync
            val location = IntArray(2)
            web.getLocationOnScreen(location)
            val density = web.resources.displayMetrics.density
            point[0] = location[0] + TOGGLE_INSET_X_DP * density
            point[1] = location[1] + web.height - TOGGLE_INSET_BOTTOM_DP * density
        }
        if (point[1] <= 0f) return
        val down_time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(down_time, down_time, MotionEvent.ACTION_DOWN, point[0], point[1], 0)
        val up = MotionEvent.obtain(down_time, down_time + 60, MotionEvent.ACTION_UP, point[0], point[1], 0)
        instrumentation.sendPointerSync(down)
        instrumentation.sendPointerSync(up)
        down.recycle()
        up.recycle()
        Log.i(TAG, "tapped quote toggle at ${point[0]},${point[1]}")
    }

    private fun show_body(body: String) {
        activity_rule.scenario.onActivity { activity ->
            val view = ComposeView(activity)
            host.set(view)
            view.setContent {
                AsterTheme(use_dark_theme = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AsterMaterial.colors.bg_primary)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        email_html_view(html = body, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            activity.setContentView(view)
        }
    }

    private fun sample_heights(body: String, label: String): List<Sample> {
        show_body(body)
        return collect_samples(label, SAMPLE_COUNT)
    }

    private fun collect_samples(label: String, count: Int): List<Sample> {
        val samples = mutableListOf<Sample>()
        repeat(count) { index ->
            Thread.sleep(SAMPLE_INTERVAL_MS)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val web = find_web_view(host.get()) ?: return@runOnMainSync
                @Suppress("DEPRECATION")
                val sample = Sample(
                    view_px = web.height,
                    content_css = web.contentHeight,
                    scale = web.scale,
                    density = web.resources.displayMetrics.density,
                    scroll_range = (web as? mail_body_web_view)?.horizontal_scroll_range() ?: 0,
                    scroll_extent = (web as? mail_body_web_view)?.horizontal_scroll_extent() ?: 0,
                )
                samples.add(sample)
                Log.i(
                    TAG,
                    "$label #$index view=${sample.view_dp}dp content=${sample.content_css} " +
                        "scale=${sample.page_scale} range=${sample.scroll_range}/${sample.scroll_extent}",
                )
            }
        }
        return samples
    }

    private fun assert_bounded(label: String, samples: List<Sample>, limit_dp: Float) {
        assertTrue("$label: the body web view never appeared", samples.isNotEmpty())
        val trace = samples.joinToString(" ") { "${it.view_dp.toInt()}/${it.content_css}@${it.page_scale}" }
        val tallest = samples.maxOf { it.view_dp }
        assertTrue("$label: body grew to ${tallest}dp, limit ${limit_dp}dp [$trace]", tallest <= limit_dp)
        val settled = samples.takeLast(SETTLED_TAIL)
        assertTrue(
            "$label: body height still changing at the end [$trace]",
            settled.maxOf { it.view_dp } - settled.minOf { it.view_dp } < 2f,
        )
        val last = settled.last()
        assertTrue("$label: body collapsed to nothing [$trace]", last.view_dp >= 40f)
        val painted_dp = last.content_css * last.page_scale
        if (!last.overflows) {
            assertTrue(
                "$label: body is ${last.view_dp}dp but the page paints ${painted_dp}dp [$trace]",
                kotlin.math.abs(last.view_dp - painted_dp) <= HEIGHT_TOLERANCE_DP,
            )
        }
    }

    private fun assert_fits_width(label: String, samples: List<Sample>) {
        val last = samples.last()
        assertTrue(
            "$label: page is wider than the screen (${last.scroll_range} of ${last.scroll_extent})",
            !last.overflows,
        )
    }

    @Test
    fun quoted_reply_with_fixed_width_blocks_stays_bounded() {
        val samples = sample_heights(FIXED_WIDTH_QUOTED_REPLY, "fixed")
        assert_bounded("fixed width quote", samples, 600f)
        assert_fits_width("fixed width quote", samples)
    }

    @Test
    fun content_that_cannot_shrink_stays_bounded() {
        val body = buildString {
            append("<div>Here is the reference you asked for.</div>")
            append("<div style=\"min-width:470px;background-color:rgb(18,18,18);color:rgb(250,250,250)\">")
            append("<p style=\"margin:0;font-size:14px;line-height:20px\">That name is not available.</p>")
            append("</div>")
            append("<div style=\"white-space:nowrap\">")
            append("A single line that is far too long to fit on a phone screen without wrapping at all.")
            append("</div>")
            append("<p>Thanks for looking into it.</p>")
        }
        assert_bounded("rigid block", sample_heights(body, "rigid"), 400f)
    }

    @Test
    fun wide_fixed_block_outside_a_quote_stays_bounded() {
        val body = buildString {
            append("<div>Here is what I see on the page.</div>")
            append("<div style=\"width:640px;background-color:rgb(18,18,18);color:rgb(250,250,250)\">")
            append("<p style=\"margin:0;font-size:14px;line-height:20px\">That name is not available.</p>")
            append("<input type=\"text\" value=\"sample.name\" style=\"width:640px;height:40px;display:flex\">")
            append("</div>")
            append("<p>Thanks for looking into it.</p>")
        }
        assert_bounded("wide block", sample_heights(body, "wide"), 600f)
    }

    @Test
    fun device_supplied_body_stays_bounded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = context.getExternalFilesDir(null)
            ?.listFiles { file -> file.isFile && file.name.startsWith(DEVICE_BODY_PREFIX) }
            ?.sortedBy { it.name }
            .orEmpty()
        assumeTrue("no device supplied body", files.isNotEmpty())
        for (file in files) {
            val collapsed = sample_heights(file.readText(), "${file.name} collapsed")
            assert_bounded("${file.name} collapsed", collapsed, DEVICE_LIMIT_DP)
            tap_quote_toggle()
            val expanded = collect_samples("${file.name} expanded", SAMPLE_COUNT)
            assert_bounded("${file.name} expanded", expanded, DEVICE_LIMIT_DP)
            assert_fits_width("${file.name} expanded", expanded)
        }
    }

    private companion object {
        const val TAG = "BodyHeightRunaway"
        const val SAMPLE_COUNT = 40
        const val SAMPLE_INTERVAL_MS = 250L
        const val SETTLED_TAIL = 6
        const val HEIGHT_TOLERANCE_DP = 24f
        const val DEVICE_BODY_PREFIX = "body_height_repro"
        const val DEVICE_LIMIT_DP = 1500f
        const val TOGGLE_INSET_X_DP = 38f
        const val TOGGLE_INSET_BOTTOM_DP = 18f

        const val FONT_STACK = "&quot;Google Sans Flex&quot;,-apple-system,BlinkMacSystemFont," +
            "&quot;Segoe UI&quot;,Roboto,sans-serif"
        const val RESET = "box-sizing:border-box;border-width:0px;border-style:solid"
        const val QUOTE_STYLE = "margin:0px 0px 0px 0.8ex;border-left:1px solid rgb(204,204,204);padding-left:1ex"

        val FIXED_WIDTH_QUOTED_REPLY = buildString {
            append("<div dir=\"ltr\"><div class=\"gmail_quote gmail_quote_container\">")
            append("<blockquote class=\"gmail_quote\" style=\"$QUOTE_STYLE\"><div dir=\"ltr\">")
            append("<div class=\"gmail_quote\"><blockquote class=\"gmail_quote\" style=\"$QUOTE_STYLE\">")
            append("<div style=\"$RESET;margin:0px 0px 16px;padding:0px;width:368px;font-family:$FONT_STACK;")
            append("color:rgb(250,250,250);font-size:medium;background-color:rgb(18,18,18);opacity:1\">")
            append("<p style=\"$RESET;margin:0px;padding:0px;text-align:center;font-size:14px;")
            append("line-height:20px;color:rgb(248,113,113)\">That name is not available right now. ")
            append("If you think this is a mistake, <a href=\"https://example.com/help\">the support team</a>")
            append(" can help.</p>")
            append("<p style=\"$RESET;margin:4px 0px 0px;padding:0px;text-align:center;font-size:12px;")
            append("line-height:16px;color:rgb(252,165,165)\">Reference: 00000000</p></div>")
            append("<div style=\"$RESET;margin:0px;padding:0px;width:368px;font-family:$FONT_STACK;")
            append("color:rgb(250,250,250);font-size:medium;background-color:rgb(18,18,18)\"><br>")
            append("<a class=\"gmail_plusreply\" id=\"plusReplyChip-1\">@</a>")
            append("<input type=\"text\" maxlength=\"55\" value=\"sample.name\" style=\"box-sizing:border-box;")
            append("border-width:1px;border-style:solid;border-color:rgb(239,68,68);padding:0px 14px;")
            append("outline:none;font-weight:400;font-size:14px;line-height:1.5;font-family:$FONT_STACK;")
            append("color:rgb(245,245,245);opacity:1;background:rgba(255,255,255,0.04);border-radius:16px;")
            append("width:368px;height:40px;min-height:40px;display:flex\"></div>")
            append("<p style=\"$RESET;margin:20px 0px 0px;padding:0px;line-height:16px;color:rgb(144,144,144);")
            append("font-size:12px;font-family:$FONT_STACK;background-color:rgb(18,18,18)\">")
            append("By continuing, you agree to the <a href=\"https://example.com/terms\" ")
            append("style=\"color:inherit\">Terms of Service</a> and ")
            append("<a href=\"https://example.com/privacy\" style=\"color:inherit\">Privacy Policy</a>.</p>")
            append("</blockquote></div></div></blockquote></div></div>")
        }
    }
}
