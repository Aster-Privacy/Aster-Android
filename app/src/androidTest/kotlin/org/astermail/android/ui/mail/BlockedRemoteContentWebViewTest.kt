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

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockedRemoteContentWebViewTest {

    private val remote_origin = "https://remote-probe.invalid"

    private val payload = """
        <html><head><style>
        .escaped { background-image: \75 rl($remote_origin/css_escape.png); }
        .quoted { background-image: url("$remote_origin/css_quoted.png" ); }
        </style></head><body>
        <div class="escaped" style="width:40px;height:40px">a</div>
        <div class="quoted" style="width:40px;height:40px">b</div>
        <img src="$remote_origin/img_control.png" width="40" height="40">
        </body></html>
    """.trimIndent()

    private fun mail_document(body: String): String = build_email_html(
        body = body,
        is_dark = false,
        fg_hex = "#111827",
        link_hex = "#2563eb",
        forwarded_label = "Forwarded message",
        image_failed_label = "Image could not be loaded",
        force_dark_emails = false,
        dyslexia_font = false,
        translate_mode = "auto",
    )

    private fun external_requests(document: String, allow_external: Boolean): List<String> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val finished = CountDownLatch(1)
        val holder = arrayOfNulls<WebView>(1)
        instrumentation.runOnMainSync {
            val view = WebView(instrumentation.targetContext)
            configure_mail_body_web_view(view, 100, allow_external)
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    if (request.isForMainFrame) return null
                    if (uri.host == MAIL_CONTENT_HOST) return null
                    seen.add(uri.toString())
                    return WebResourceResponse(
                        "text/plain",
                        null,
                        403,
                        "Forbidden",
                        emptyMap(),
                        ByteArrayInputStream(ByteArray(0)),
                    )
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    finished.countDown()
                }
            }
            view.layout(0, 0, 1080, 1920)
            view.loadDataWithBaseURL("https://$MAIL_CONTENT_HOST/", document, "text/html", "UTF-8", null)
            holder[0] = view
        }
        assertTrue("the body document must finish loading", finished.await(20, TimeUnit.SECONDS))
        Thread.sleep(2000)
        instrumentation.runOnMainSync { holder[0]?.destroy() }
        return seen.toList()
    }

    @Test
    fun the_unsanitized_payload_reaches_the_network_in_this_web_view() {
        val requests = external_requests(payload, allow_external = true)
        assertTrue("escaped url() must fire unsanitized: $requests", requests.any { it.contains("css_escape.png") })
        assertTrue("quoted url() must fire unsanitized: $requests", requests.any { it.contains("css_quoted.png") })
        assertTrue("the plain image must fire unsanitized: $requests", requests.any { it.contains("img_control.png") })
    }

    @Test
    fun blocked_remote_content_requests_nothing() {
        val sanitized = EmailHtmlSanitizer.sanitize(payload)
        val blocked = EmailHtmlSanitizer.neutralize_blocked_backgrounds(
            EmailHtmlSanitizer.replace_blocked_images(sanitized),
        )
        val requests = external_requests(mail_document(blocked), allow_external = false)
        assertEquals("blocked remote content must request nothing", emptyList<String>(), requests)
    }

    @Test
    fun allowed_remote_content_requests_the_image_through_the_proxy_only() {
        val sanitized = EmailHtmlSanitizer.sanitize(payload)
        val proxied = proxy_external_urls(sanitized, REMOTE_IMAGE_PROXY_BASE)
        val requests = external_requests(mail_document(proxied), allow_external = true)
        assertTrue(
            "the plain image must load through the proxy: $requests",
            requests.any { it.startsWith(REMOTE_IMAGE_PROXY_BASE) && it.contains("img_control.png") },
        )
        assertTrue(
            "nothing may be requested from the remote origin directly: $requests",
            requests.none { it.startsWith(remote_origin) },
        )
    }
}
