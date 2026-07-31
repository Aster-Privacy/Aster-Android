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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class InlineImageWebViewTest {

    private val image_width = 2400
    private val image_height = 1600

    private fun high_resolution_png(): ByteArray {
        val pixels = IntArray(image_width * image_height)
        var state = 0x9e3779b9L
        for (index in pixels.indices) {
            state = (state * 6364136223846793005L + 1442695040888963407L) ushr 1
            pixels[index] = 0xff000000.toInt() or (state.toInt() and 0x00ffffff)
        }
        val bitmap = android.graphics.Bitmap.createBitmap(
            pixels,
            image_width,
            image_height,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun document_for(src: String): String = build_email_html(
        body = """<p>Here is the photo you asked for.</p><img src="$src">""",
        is_dark = false,
        fg_hex = "#111827",
        link_hex = "#2563eb",
        forwarded_label = "Forwarded message",
        image_failed_label = "Image could not be loaded",
        force_dark_emails = false,
        dyslexia_font = false,
        translate_mode = "off",
    )

    private fun natural_width_of_first_image(document: String): Int {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val result = arrayOf(-1)
        val loaded = CountDownLatch(1)
        val measured = CountDownLatch(1)
        var web_view: WebView? = null

        instrumentation.runOnMainSync {
            val view = WebView(context)
            web_view = view
            view.settings.javaScriptEnabled = true
            view.webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                ): android.webkit.WebResourceResponse? {
                    val uri = request?.url ?: return null
                    if (uri.host != org.astermail.android.translation.TranslationAssets.CONTENT_HOST) return null
                    return inline_image_response(uri.path)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.countDown()
                }
            }
            view.loadDataWithBaseURL(
                "https://${org.astermail.android.translation.TranslationAssets.CONTENT_HOST}/",
                document,
                "text/html",
                "UTF-8",
                null,
            )
        }

        assertTrue("the email document never finished loading", loaded.await(30, TimeUnit.SECONDS))

        for (attempt in 1..40) {
            val settled = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web_view?.evaluateJavascript(
                    "(function(){var i=document.images[0];return i?i.naturalWidth:-1})()",
                ) { value ->
                    result[0] = value.trim('"').toIntOrNull() ?: -1
                    settled.countDown()
                }
            }
            settled.await(5, TimeUnit.SECONDS)
            if (result[0] > 1) break
            Thread.sleep(250)
        }
        measured.countDown()
        instrumentation.runOnMainSync { web_view?.destroy() }
        return result[0]
    }

    @Test
    fun a_high_resolution_inline_image_renders_instead_of_the_failed_placeholder() {
        val bytes = high_resolution_png()
        assertTrue("the fixture must exceed the old four megabyte cap", bytes.size > 4 * 1024 * 1024)
        val key = InlineImageStore.content_key("photo@gmail", bytes)
        InlineImageStore.put(key, "image/png", bytes)
        val width = natural_width_of_first_image(document_for(InlineImageStore.url_for(key)))
        assertEquals("the inline image must decode at full resolution", image_width, width)
    }

    @Test
    fun an_unresolvable_inline_reference_still_falls_back_to_the_placeholder() {
        val document = document_for(InlineImageStore.url_for("missing_key"))
        assertEquals("an unknown key must not render an image", -1, natural_width_of_first_image(document))
    }
}
