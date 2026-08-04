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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EmailBodyFitWidthTest {

    private val phone_width_px = 1080
    private val phone_height_px = 2100

    private fun document_for(body: String): String = build_email_html(
        body = body,
        is_dark = false,
        fg_hex = "#111827",
        link_hex = "#2563eb",
        forwarded_label = "Forwarded message",
        image_failed_label = "Image could not be loaded",
        force_dark_emails = false,
        dyslexia_font = false,
        translate_mode = "off",
    )

    private class FitReport(
        val viewport_width: Int,
        val sideways_scroll: Int,
        val content_right: Int,
        val scale: Double,
        val reported_height: Int,
        val layout_height: Int,
    )

    private fun measure_fit(document: String): FitReport {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var web_view: WebView? = null
        val loaded = CountDownLatch(1)
        val reported_height = intArrayOf(0)

        instrumentation.runOnMainSync {
            val view = WebView(context)
            web_view = view
            view.settings.javaScriptEnabled = true
            view.settings.loadWithOverviewMode = true
            view.settings.useWideViewPort = true
            view.settings.builtInZoomControls = true
            view.settings.displayZoomControls = false
            view.settings.setSupportZoom(true)
            view.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                    val text = message?.message() ?: return false
                    if (text.startsWith("ASTER_HEIGHT_EXACT:")) {
                        text.substring("ASTER_HEIGHT_EXACT:".length).toIntOrNull()?.let {
                            reported_height[0] = it
                        }
                        return true
                    }
                    return false
                }
            }
            view.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.countDown()
                }
            }
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(phone_width_px, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(phone_height_px, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, phone_width_px, phone_height_px)
            view.loadDataWithBaseURL(
                "https://${org.astermail.android.translation.TranslationAssets.CONTENT_HOST}/",
                document,
                "text/html",
                "UTF-8",
                null,
            )
        }

        assertTrue("the email document never finished loading", loaded.await(30, TimeUnit.SECONDS))
        Thread.sleep(3000)

        val probe = """
            (function(){
              var m=document.getElementById('m');
              if(!m)return '';
              document.documentElement.scrollLeft=9999;
              document.body.scrollLeft=9999;
              var sideways=Math.max(document.documentElement.scrollLeft,document.body.scrollLeft);
              document.documentElement.scrollLeft=0;
              document.body.scrollLeft=0;
              var saved=m.style.getPropertyValue('transform');
              if(saved)m.style.setProperty('transform','none','important');
              var layout_bottom=Math.ceil(m.getBoundingClientRect().bottom);
              if(saved)m.style.setProperty('transform',saved,'important');
              return [
                window.innerWidth,
                sideways,
                Math.ceil(m.getBoundingClientRect().right),
                (window.__aster_fit_scale||1),
                layout_bottom
              ].join('|');
            })()
        """.trimIndent()

        var raw = ""
        for (attempt in 1..20) {
            val settled = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web_view?.evaluateJavascript(probe) { value ->
                    raw = value.trim('"').replace("\\\"", "\"")
                    settled.countDown()
                }
            }
            settled.await(5, TimeUnit.SECONDS)
            if (raw.contains("|")) break
            Thread.sleep(250)
        }
        instrumentation.runOnMainSync { web_view?.destroy() }

        val parts = raw.split("|")
        assertTrue("the fit probe returned nothing usable: $raw", parts.size >= 5)

        return FitReport(
            viewport_width = parts[0].toDouble().toInt(),
            sideways_scroll = parts[1].toDouble().toInt(),
            content_right = parts[2].toDouble().toInt(),
            scale = parts[3].toDouble(),
            reported_height = reported_height[0],
            layout_height = parts[4].toDouble().toInt(),
        )
    }

    @Test
    fun a_fixed_width_block_wider_than_the_screen_is_scaled_down_to_fit() {
        val body = """
            <div style="width:900px">
              <h1 style="font-size:28px">Your statement is ready</h1>
              <p style="width:900px">Account ending 4417 has a new statement available for download.</p>
            </div>
        """.trimIndent()
        val report = measure_fit(document_for(body))

        assertTrue("the viewport was never laid out", report.viewport_width > 100)
        assertTrue(
            "wide content must be scaled down, got scale ${report.scale}",
            report.scale < 0.95,
        )
        assertTrue(
            "content still runs past the screen: right=${report.content_right} viewport=${report.viewport_width}",
            report.content_right <= report.viewport_width + 2,
        )
        assertTrue(
            "the page still scrolls sideways by ${report.sideways_scroll}px",
            report.sideways_scroll == 0,
        )
    }

    @Test
    fun a_newsletter_table_with_unbreakable_cells_is_scaled_down_to_fit() {
        val body = """
            <table width="600" bgcolor="#ffffff" cellpadding="0" cellspacing="0">
              <tr><td>
                <div style="width:1180px;font-size:20px">
                  Transaction reference 4417-9930-2288-1174 posted on 02 August 2026
                </div>
              </td></tr>
              <tr><td><div style="width:1180px">
                Statement period 01 July 2026 through 31 July 2026 for the card ending 4417
              </div></td></tr>
              <tr><td>Thank you for banking with us.</td></tr>
            </table>
        """.trimIndent()
        val report = measure_fit(document_for(body))

        assertTrue("the viewport was never laid out", report.viewport_width > 100)
        assertTrue(
            "wide newsletter content must be scaled down, got scale ${report.scale}",
            report.scale < 0.95,
        )
        assertTrue(
            "content still runs past the screen: right=${report.content_right} viewport=${report.viewport_width}",
            report.content_right <= report.viewport_width + 2,
        )
        assertTrue(
            "the page still scrolls sideways by ${report.sideways_scroll}px",
            report.sideways_scroll == 0,
        )
    }

    @Test
    fun the_reported_height_matches_what_the_screen_actually_shows() {
        val body = """
            <div style="width:900px">
              <h1 style="font-size:28px">Your statement is ready</h1>
              <p style="width:900px">Account ending 4417 has a new statement available for download.</p>
              <p style="width:900px">A second paragraph so the body is comfortably taller than one line.</p>
            </div>
        """.trimIndent()
        val report = measure_fit(document_for(body))

        assertTrue("no height was ever reported", report.reported_height > 0)
        assertTrue("wide content must be scaled down, got scale ${report.scale}", report.scale < 0.95)

        val expected = report.layout_height * report.scale
        assertTrue(
            "reported height ${report.reported_height} does not follow the scaled layout height $expected",
            report.reported_height >= expected * 0.75 && report.reported_height <= expected * 1.25,
        )
    }

    @Test
    fun an_ordinary_narrow_email_is_left_at_full_size() {
        val body = "<p>Hi, are we still on for Thursday?</p>"
        val report = measure_fit(document_for(body))

        assertTrue(
            "a narrow email must not be zoomed out, got scale ${report.scale}",
            report.scale >= 0.99,
        )
    }
}
