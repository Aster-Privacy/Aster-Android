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

package org.astermail.android.ui.mail

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TrackingPixelMarkerRenderTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val newsletter =
        """<div style="background-color:#ffffff;padding:16px;color:#111827">""" +
            """<h2 style="margin:0 0 8px">Weekly digest</h2>""" +
            """<p>Three things worth reading this week.<img src="https://open.mailmetrics.example/o/1.gif" width="1" height="1"></p>""" +
            """<p style="color:#6b7280;font-size:12px">You are receiving this because you subscribed.</p></div>""" +
            """<img src="https://hidden.example/open?id=1" width="1" height="1" style="display:none">"""

    private val plain =
        """<p>Hi, the notes from today are below.</p>""" +
            """<p>See you Thursday.<img src="https://t.beacon.example/open?id=9" width="1" height="1"></p>"""

    private fun save(name: String, bitmap: Bitmap?) {
        bitmap ?: return
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun device_screenshot(name: String) {
        save(name, runCatching { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() }.getOrNull())
    }

    private fun document(body: String, dark: Boolean): String {
        val sanitized = EmailHtmlSanitizer.sanitize(
            body,
            EmailHtmlSanitizer.SanitizeOptions(mark_tracking_pixels = true),
        )
        return build_email_html(
            body = EmailHtmlSanitizer.replace_blocked_images(sanitized, mark_tracking_pixels = true),
            is_dark = dark,
            fg_hex = if (dark) "#E8E8E8" else "#111827",
            link_hex = "#2563eb",
            forwarded_label = "Forwarded message",
            image_failed_label = "Image could not be loaded",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )
    }

    private fun render(name: String, body: String, dark: Boolean): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val loaded = CountDownLatch(1)
        val web_ref = arrayOfNulls<WebView>(1)
        val html = document(body, dark)
        compose_rule.setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (dark) Color(0xFF0A0A0A) else Color.White)
                    .padding(top = 24.dp),
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loaded.countDown()
                                }
                            }
                            loadDataWithBaseURL("https://mail-content.invalid/", html, "text/html", "UTF-8", null)
                            web_ref[0] = this
                        }
                    },
                )
            }
        }
        assertTrue("the email never finished loading", loaded.await(30, TimeUnit.SECONDS))
        Thread.sleep(1500)
        compose_rule.waitForIdle()
        device_screenshot(name)
        val result = arrayOf("")
        val measured = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web_ref[0]!!.evaluateJavascript(
                "(function(){" +
                    "var all=document.querySelectorAll('span[data-tracking-pixel-marker]');" +
                    "var m=all[0];var r=m.getBoundingClientRect();var g=getComputedStyle(m,'::before');" +
                    "var root=document.getElementById('m');var with_markers=root.getBoundingClientRect().height;" +
                    "var text=root.querySelector('p').getBoundingClientRect().width;" +
                    "for(var i=0;i<all.length;i++){all[i].style.setProperty('display','none','important');}" +
                    "var without=root.getBoundingClientRect().height;" +
                    "var text_without=root.querySelector('p').getBoundingClientRect().width;" +
                    "return JSON.stringify({count:all.length,width:r.width,height:r.height,glyph_w:g.width,glyph_h:g.height," +
                    "label:m.getAttribute('aria-label'),with_markers:with_markers,without:without,text:text,text_without:text_without});" +
                    "})()",
            ) { value ->
                result[0] = value
                measured.countDown()
            }
        }
        assertTrue(measured.await(10, TimeUnit.SECONDS))
        val json = result[0]
        return JSONObject(if (json.startsWith("\"")) JSONObject("{\"v\":$json}").getString("v") else json)
    }

    private fun assert_marker_takes_no_space(metrics: JSONObject) {
        assertEquals(1, metrics.getInt("count"))
        assertEquals(0.0, metrics.getDouble("width"), 0.01)
        assertEquals(0.0, metrics.getDouble("height"), 0.01)
        assertEquals("11px", metrics.getString("glyph_w"))
        assertEquals("11px", metrics.getString("glyph_h"))
        assertEquals("Tracking pixel blocked", metrics.getString("label"))
        assertEquals(metrics.getDouble("without"), metrics.getDouble("with_markers"), 0.01)
        assertEquals(metrics.getDouble("text_without"), metrics.getDouble("text"), 0.01)
    }

    @Test
    fun a_marker_on_a_light_newsletter_takes_no_space() {
        assert_marker_takes_no_space(render("tracking_marker_newsletter_light", newsletter, dark = false))
    }

    @Test
    fun a_marker_on_a_dark_plain_email_takes_no_space() {
        assert_marker_takes_no_space(render("tracking_marker_plain_dark", plain, dark = true))
    }

    @Test
    fun a_marker_on_a_light_plain_email_takes_no_space() {
        assert_marker_takes_no_space(render("tracking_marker_plain_light", plain, dark = false))
    }

    @Test
    fun tapping_the_banner_tracker_count_opens_the_tracker_dialog() {
        val report = EmailHtmlSanitizer.analyze_trackers(
            """<img src="https://open.mailmetrics.example/o/1.gif" width="1" height="1">""" +
                """<img src="https://open.mailmetrics.example/o/2.gif" width="1" height="1">""" +
                """<img src="https://t.beacon.example/open?id=9" width="1" height="1">""",
        )
        var opened = 0
        compose_rule.setContent {
            AsterTheme(use_dark_theme = false) {
                var show by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AsterMaterial.colors.bg_primary)
                        .padding(vertical = 24.dp),
                ) {
                    external_content_banner(
                        counts = ExternalContentCounts(image_count = 4, tracker_count = 3, font_count = 0, css_count = 0),
                        on_allow_once = {},
                        on_always_allow = {},
                        on_show_trackers = {
                            opened++
                            show = true
                        },
                    )
                    external_content_banner(
                        counts = ExternalContentCounts(image_count = 0, tracker_count = 1, font_count = 0, css_count = 0),
                        on_allow_once = {},
                        on_always_allow = null,
                        on_show_trackers = {},
                    )
                }
                if (show) tracker_details_dialog(report = report, on_close = { show = false })
            }
        }
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("4 images", useUnmergedTree = true).assertIsDisplayed()
        compose_rule.onNodeWithText("1 tracker", useUnmergedTree = true).assertIsDisplayed()
        device_screenshot("tracking_banner")

        compose_rule.onNodeWithText("3 trackers", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose_rule.waitForIdle()

        assertEquals(1, opened)
        compose_rule.onNodeWithText("open.mailmetrics.example").assertIsDisplayed()
        compose_rule.onNodeWithText("t.beacon.example").assertIsDisplayed()
        compose_rule.onNodeWithText("x2").assertIsDisplayed()
        Thread.sleep(500)
        device_screenshot("tracking_banner_dialog")
    }
}
