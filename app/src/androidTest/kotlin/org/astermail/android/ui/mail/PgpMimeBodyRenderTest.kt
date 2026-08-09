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
import org.astermail.android.mail.MimeParser
import org.astermail.android.mail.build_plain_text_html
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PgpMimeBodyRenderTest {

    private val phone_width_px = 1080
    private val phone_height_px = 2100

    private val decrypted_pgp_payload =
        "Content-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Type: text/plain; charset=UTF-8; format=flowed\r\n" +
            "\r\n" +
            "This is a test email sent from sourcehut to confirm that PGP is working as =\r\n" +
            "you \r\n" +
            "expect. This email is signed with this key:\r\n" +
            "\r\n" +
            "447B 69E4 B34B E90B C829 A0E9 6597 04D1 A38A 93AE\r\n" +
            "\r\n" +
            "You may update your PGP settings here:\r\n" +
            "\r\n" +
            "https://meta.sr.ht/privacy\r\n" +
            "\r\n" +
            "--\r\n" +
            "Drew DeVault\r\n" +
            "sourcehut\r\n"

    private fun rendered_text(raw_body: String): String {
        val body = if (MimeParser.looks_like_mime(raw_body)) {
            MimeParser.parse(raw_body).text ?: raw_body
        } else {
            raw_body
        }
        val document = build_email_html(
            body = build_plain_text_html(body),
            is_dark = false,
            fg_hex = "#111827",
            link_hex = "#2563eb",
            forwarded_label = "Forwarded message",
            image_failed_label = "Image could not be loaded",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )
        return load_and_read(document)
    }

    private fun load_and_read(document: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var web_view: WebView? = null
        val loaded = CountDownLatch(1)

        instrumentation.runOnMainSync {
            val view = WebView(context)
            web_view = view
            view.settings.javaScriptEnabled = true
            view.settings.loadWithOverviewMode = true
            view.settings.useWideViewPort = true
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
        Thread.sleep(1500)

        var raw = ""
        val settled = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web_view?.evaluateJavascript("document.body.innerText") { value ->
                raw = value
                settled.countDown()
            }
        }
        assertTrue("the body text was never read back", settled.await(30, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { web_view?.destroy() }

        return raw
            .trim('"')
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\u003C", "<")
    }

    @Test
    fun pgp_mime_body_renders_as_readable_text() {
        val text = rendered_text(decrypted_pgp_payload)

        assertFalse("raw MIME headers reached the viewer", text.contains("Content-Transfer-Encoding"))
        assertFalse("raw MIME headers reached the viewer", text.contains("Content-Type:"))
        assertFalse("quoted-printable soft breaks were not decoded", text.contains(" =\n"))
        assertTrue(
            "the decoded sentence is missing",
            text.contains("This is a test email sent from sourcehut to confirm that PGP is working as you"),
        )
        assertTrue(text.contains("447B 69E4 B34B E90B C829 A0E9 6597 04D1 A38A 93AE"))
        assertTrue(text.contains("https://meta.sr.ht/privacy"))
        assertTrue(text.contains("Drew DeVault"))
        assertFalse("the body picked up blank-line padding", text.contains("\n\n\n"))
    }

    @Test
    fun ordinary_plain_text_body_is_unchanged() {
        val text = rendered_text("Hi there,\n\nSee https://astermail.org for details.\n\nThanks")

        assertTrue(text.contains("Hi there,"))
        assertTrue(text.contains("See https://astermail.org for details."))
        assertTrue(text.contains("Thanks"))
    }
}
