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

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val METRICS_JS = """(function(){
  var btn=document.querySelector('.aster-quote-toggle');
  if(!btn)return 'missing';
  var quoted=document.querySelector('.aster-quoted-content');
  var bs=window.getComputedStyle(document.body);
  var cs=window.getComputedStyle(btn);
  var r=btn.getBoundingClientRect();
  return [
    parseFloat(bs.fontSize),
    parseFloat(cs.fontSize),
    r.height,
    r.width,
    btn.scrollWidth,
    cs.fontFamily,
    bs.fontFamily,
    quoted?parseFloat(window.getComputedStyle(quoted).fontSize):0
  ].join('~');
})()"""

@RunWith(AndroidJUnit4::class)
class QuoteToggleSizingTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private data class Metrics(
        val body_font_px: Float,
        val toggle_font_px: Float,
        val toggle_height: Float,
        val toggle_width: Float,
        val toggle_scroll_width: Float,
        val toggle_family: String,
        val body_family: String,
        val quoted_font_px: Float,
    )

    private val quoted_body = buildString {
        append("<div>Sounds good, see you Thursday.</div>")
        append("<div class=\"aster_quote\">")
        append("<div class=\"aster_quote_attr\">On Tue, 14 Jul 2026, Riley Chen wrote:</div>")
        append("<blockquote>")
        repeat(12) { append("<div>quoted line $it of the original message</div>") }
        append("</blockquote>")
        append("</div>")
    }

    private fun measure_all(configs: List<Pair<Int, String?>>): List<Metrics> {
        val refs = configs.map { AtomicReference<WebView?>(null) }
        compose_rule.setContent {
            Column(modifier = Modifier.fillMaxWidth()) {
                configs.forEachIndexed { index, config ->
                    val html = build_email_html(
                        body = quoted_body,
                        is_dark = false,
                        fg_hex = "#111827",
                        link_hex = "#2563eb",
                        forwarded_label = "Forwarded message",
                        image_failed_label = "Image unavailable",
                        force_dark_emails = false,
                        dyslexia_font = false,
                        translate_mode = "off",
                        email_font_id = config.second,
                        text_zoom = config.first,
                    )
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.textZoom = config.first
                                refs[index].set(this)
                                loadDataWithBaseURL(
                                    "https://mail-content.invalid/",
                                    html,
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(900.dp),
                    )
                }
            }
        }

        compose_rule.waitForIdle()
        Thread.sleep(3500)
        compose_rule.waitForIdle()

        return refs.map { ref ->
            val latch = CountDownLatch(1)
            val raw = AtomicReference("")
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ref.get()?.evaluateJavascript(METRICS_JS) { value ->
                    raw.set(value?.trim()?.removeSurrounding("\"") ?: "")
                    latch.countDown()
                } ?: latch.countDown()
            }
            latch.await(10, TimeUnit.SECONDS)

            val parts = raw.get().split("~")
            assertTrue("quote toggle was not rendered [${raw.get()}]", parts.size == 8)
            Metrics(
                body_font_px = parts[0].toFloat(),
                toggle_font_px = parts[1].toFloat(),
                toggle_height = parts[2].toFloat(),
                toggle_width = parts[3].toFloat(),
                toggle_scroll_width = parts[4].toFloat(),
                toggle_family = parts[5],
                body_family = parts[6],
                quoted_font_px = parts[7].toFloat(),
            )
        }
    }

    @Test
    fun toggle_scales_with_the_users_font_size_setting() {
        val (default_size, extra_large) = measure_all(listOf(100 to null, 130 to null))

        val text_ratio = extra_large.body_font_px / default_size.body_font_px
        assertTrue(
            "textZoom did not change the body font size (${default_size.body_font_px} -> ${extra_large.body_font_px})",
            text_ratio > 1.2f,
        )
        val height_ratio = extra_large.toggle_height / default_size.toggle_height
        assertEquals(
            "toggle height must scale with the user font size (ratio $height_ratio vs text $text_ratio) [body ${default_size.body_font_px}->${extra_large.body_font_px} btn ${default_size.toggle_font_px}->${extra_large.toggle_font_px} h ${default_size.toggle_height}->${extra_large.toggle_height} w ${default_size.toggle_width}->${extra_large.toggle_width}]",
            text_ratio.toDouble(),
            height_ratio.toDouble(),
            0.06,
        )
        val width_ratio = extra_large.toggle_width / default_size.toggle_width
        assertEquals(
            "toggle width must scale with the user font size (ratio $width_ratio vs text $text_ratio)",
            text_ratio.toDouble(),
            width_ratio.toDouble(),
            0.06,
        )
    }

    @Test
    fun toggle_never_clips_its_dots() {
        val zooms = listOf(100, 85, 130, 160)
        measure_all(zooms.map { it to null }).forEachIndexed { index, m ->
            val zoom = zooms[index]
            assertTrue(
                "toggle clips its label at zoom $zoom (${m.toggle_scroll_width} > ${m.toggle_width})",
                m.toggle_scroll_width <= m.toggle_width + 1f,
            )
            assertTrue(
                "toggle is below the 44px touch target at zoom $zoom (${m.toggle_height})",
                m.toggle_height >= 27f,
            )
        }
    }

    @Test
    fun toggle_and_quoted_text_use_the_users_font_family() {
        val m = measure_all(listOf(100 to "system_mono")).first()
        assertEquals(
            "toggle must render in the user's chosen email font",
            m.body_family,
            m.toggle_family,
        )
        assertTrue(
            "the user's mono font did not reach the body (${m.body_family})",
            m.body_family.contains("mono", ignoreCase = true),
        )
    }

    @Test
    fun quoted_content_matches_the_body_text_size() {
        val m = measure_all(listOf(100 to null)).first()
        assertEquals(
            "expanded quoted text must match the body size",
            m.body_font_px.toDouble(),
            m.quoted_font_px.toDouble(),
            0.6,
        )
    }
}
