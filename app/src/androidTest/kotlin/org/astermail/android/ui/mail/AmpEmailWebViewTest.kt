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
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AmpEmailWebViewTest {

    private val amp_email = """<!doctype html><html ⚡4email data-css-strict><head><meta charset="utf-8">""" +
        """<style amp4email-boilerplate>body{visibility:hidden}</style>""" +
        """<script async src="https://cdn.ampproject.org/v0.js"></script>""" +
        """<style amp-custom>.es-wrapper{width:100%;background-color:#F7F7F7}h1{font-size:30px;color:#023047}""" +
        """.es-content-body p{color:#666666;font-size:14px}</style></head>""" +
        """<body><div class="es-wrapper-color"><table class="es-wrapper" width="100%"><tr><td>""" +
        """<table class="es-content-body" width="600" bgcolor="#ffffff"><tr><td align="center">""" +
        """<h1>Verify your email</h1><amp-img src="cid:pic1" alt="Banner" width="370" height="255" layout="responsive"></amp-img>""" +
        """<p>Enter this verification code in the app to finish signing up.</p>""" +
        """<p style="font-size:20px;line-height:40px"><strong id="code">482915</strong></p>""" +
        """</td></tr></table></td></tr></table></div></body></html>"""

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

    private fun rendered_document(): String {
        val sanitized = EmailHtmlSanitizer.sanitize(amp_email, EmailHtmlSanitizer.SanitizeOptions())
        val blocked = EmailHtmlSanitizer.replace_blocked_images(sanitized, "Image could not be loaded")
        return document_for(EmailHtmlSanitizer.neutralize_blocked_backgrounds(blocked))
    }

    private data class Probe(val visibility: String, val text: String, val code_height: Int)

    private fun probe(document: String, capture_name: String): Probe {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val loaded = CountDownLatch(1)
        var web_view: WebView? = null

        instrumentation.runOnMainSync {
            val view = WebView(context)
            web_view = view
            view.settings.javaScriptEnabled = true
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.countDown()
                }
            }
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 1080, 1600)
            view.loadDataWithBaseURL("https://mail-content.invalid/", document, "text/html", "UTF-8", null)
        }

        assertTrue("the email document never finished loading", loaded.await(30, TimeUnit.SECONDS))
        Thread.sleep(1500)

        val raw = arrayOf("")
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web_view?.evaluateJavascript(
                "(function(){var c=document.getElementById('code');" +
                    "return {v:getComputedStyle(document.body).visibility," +
                    "t:(document.body.innerText||'').split(String.fromCharCode(10)).join(' ')," +
                    "h:c?Math.round(c.getBoundingClientRect().height):-1}})()",
            ) { value ->
                raw[0] = value
                evaluated.countDown()
            }
        }
        assertTrue("the probe script never returned", evaluated.await(10, TimeUnit.SECONDS))

        instrumentation.runOnMainSync {
            val view = web_view ?: return@runOnMainSync
            val bitmap = Bitmap.createBitmap(1080, 1600, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = context.getExternalFilesDir(null)
            if (dir != null) {
                File(dir, "$capture_name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            bitmap.recycle()
            view.destroy()
        }

        val parsed = org.json.JSONObject(raw[0])
        return Probe(parsed.getString("v"), parsed.getString("t"), parsed.getInt("h"))
    }

    @Test
    fun the_unrepaired_amp_document_is_hidden_by_the_boilerplate() {
        val probe = probe(document_for(amp_email), "amp_email_unrepaired")
        assertEquals("the control must reproduce the blank render", "hidden", probe.visibility)
    }

    @Test
    fun an_amp_email_renders_visible_through_the_mail_pipeline() {
        val document = rendered_document()
        assertTrue("the boilerplate must be gone", !document.contains("visibility:hidden"))
        val probe = probe(document, "amp_email_repaired")
        assertEquals("the body must be visible", "visible", probe.visibility)
        assertTrue("the verification code must be readable: ${probe.text}", probe.text.contains("482915"))
        assertTrue("the heading must be readable", probe.text.contains("Verify your email"))
        assertTrue("the code must occupy space on the page", probe.code_height > 0)
    }
}
