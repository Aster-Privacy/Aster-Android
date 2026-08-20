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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.astermail.android.mail.build_plain_text_html
import org.junit.runner.RunWith

private const val PROBE_JS = """(function(){
  var btn=document.querySelector('.aster-quote-toggle');
  var quoted=document.querySelector('.aster-quoted-content');
  var visible=document.getElementById('m')?document.getElementById('m').innerText:document.body.innerText;
  if(quoted&&quoted.innerText)visible=visible.split(quoted.innerText).join('');
  return [btn?'1':'0',quoted?quoted.style.display:'',visible.replace(/\s+/g,' ').trim()].join('~');
})()"""

@RunWith(AndroidJUnit4::class)
class QuoteCollapseDetectionTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private data class Probe(
        val has_toggle: Boolean,
        val quoted_display: String,
        val visible_text: String,
    )

    private val linkified_attribution = buildString {
        append("<div dir=\"auto\">Well, since it is difficult to delete it, we wait for deletion.</div>")
        append("<div dir=\"auto\"><br></div>")
        append("<div dir=\"auto\">I also sent you this message from a browser.</div>")
        append("<div dir=\"auto\"><br></div>")
        append("<div dir=\"auto\">On Wed, Aug 12, 2026, 4:23 PM, Aster Team ")
        append("&lt;<a href=\"mailto:hello@astermail.org\">hello@astermail.org</a>&gt; wrote:Hi Lyria,")
        append("<br>Thanks for the screenshots, they made the problem obvious.</div>")
    }

    private val plain_text_reply = build_plain_text_html(
        buildString {
            append("Thanks, I have found it :-)\n\n")
            append("Secured by Aster Mail\n\n")
            append("On Wed, Aug 19, 2026, 12:26 PM, Aster Team <hello@astermail.org> wrote:\n\n")
            append("Hi,\n\n")
            append("The archive lives in the folder list under More.\n")
            append("Let us know if anything else looks off.\n")
        },
    )

    private val br_only_reply = buildString {
        append("<div dir=\"auto\">Thanks, I have found it :-)<br><br>")
        append("Secured by Aster Mail<br><br>")
        append("On Wed, Aug 19, 2026, 12:26 PM, Aster Team ")
        append("&lt;<a href=\"mailto:hello@astermail.org\">hello@astermail.org</a>&gt; wrote:<br><br>")
        append("Hi,<br>The archive lives in the folder list under More.</div>")
    }

    private val orphan_blockquote = buildString {
        append("<div>Short reply with nothing else in it.</div>")
        append("<blockquote class=\"gmail_quote\">the original message that must collapse</blockquote>")
    }

    private val no_quote = buildString {
        append("<div>Hi there, this is a normal message with no quoted history in it at all.</div>")
        append("<div>Everything here belongs to the reply and must stay visible.</div>")
    }

    private fun probe_all(bodies: List<String>): List<Probe> {
        val refs = bodies.map { AtomicReference<WebView?>(null) }
        compose_rule.setContent {
            Column(modifier = Modifier.fillMaxWidth()) {
                bodies.forEachIndexed { index, body ->
                    val html = build_email_html(
                        body = body,
                        is_dark = false,
                        fg_hex = "#111827",
                        link_hex = "#2563eb",
                        forwarded_label = "Forwarded message",
                        image_failed_label = "Image unavailable",
                        force_dark_emails = false,
                        dyslexia_font = false,
                        translate_mode = "off",
                        email_font_id = null,
                        text_zoom = 100,
                    )
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
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
                        modifier = Modifier.fillMaxWidth().height(400.dp),
                    )
                }
            }
        }

        compose_rule.waitForIdle()
        Thread.sleep(3000)
        compose_rule.waitForIdle()

        return refs.map { ref ->
            val latch = CountDownLatch(1)
            val raw = AtomicReference("")
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ref.get()?.evaluateJavascript(PROBE_JS) { value ->
                    raw.set(value?.trim()?.removeSurrounding("\"") ?: "")
                    latch.countDown()
                } ?: latch.countDown()
            }
            latch.await(10, TimeUnit.SECONDS)

            val parts = raw.get().split("~")
            assertTrue("probe failed [${raw.get()}]", parts.size >= 3)
            Probe(
                has_toggle = parts[0] == "1",
                quoted_display = parts[1],
                visible_text = parts.drop(2).joinToString("~").replace("\\n", " "),
            )
        }
    }

    @Test
    fun collapses_a_reply_whose_attribution_line_contains_a_linked_address() {
        val probe = probe_all(listOf(linkified_attribution)).first()

        assertTrue("quoted history was left expanded [${probe.visible_text}]", probe.has_toggle)
        assertEquals("none", probe.quoted_display)
        assertFalse(
            "the quoted original is still visible [${probe.visible_text}]",
            probe.visible_text.contains("Thanks for the screenshots"),
        )
        assertTrue(
            "the new reply text was collapsed too [${probe.visible_text}]",
            probe.visible_text.contains("from a browser"),
        )
    }

    @Test
    fun collapses_a_quoted_blockquote_that_has_no_wrapper() {
        val probe = probe_all(listOf(orphan_blockquote)).first()

        assertTrue("blockquote quote was not collapsible", probe.has_toggle)
        assertFalse(
            "the quoted original is still visible [${probe.visible_text}]",
            probe.visible_text.contains("original message that must collapse"),
        )
    }

    @Test
    fun collapses_a_plain_text_reply_that_only_has_line_breaks() {
        val probe = probe_all(listOf(plain_text_reply)).first()

        assertTrue("plain text quote was not collapsible [${probe.visible_text}]", probe.has_toggle)
        assertEquals("none", probe.quoted_display)
        assertFalse(
            "the quoted original is still visible [${probe.visible_text}]",
            probe.visible_text.contains("The archive lives in the folder list"),
        )
        assertTrue(
            "the new reply text was collapsed too [${probe.visible_text}]",
            probe.visible_text.contains("I have found it"),
        )
    }

    @Test
    fun collapses_an_html_reply_separated_only_by_br_tags() {
        val probe = probe_all(listOf(br_only_reply)).first()

        assertTrue("br separated quote was not collapsible [${probe.visible_text}]", probe.has_toggle)
        assertFalse(
            "the quoted original is still visible [${probe.visible_text}]",
            probe.visible_text.contains("The archive lives in the folder list"),
        )
        assertTrue(
            "the new reply text was collapsed too [${probe.visible_text}]",
            probe.visible_text.contains("I have found it"),
        )
    }

    @Test
    fun leaves_a_message_without_quoted_history_untouched() {
        val probe = probe_all(listOf(no_quote)).first()

        assertFalse("a message with no quote grew a toggle", probe.has_toggle)
        assertTrue(
            "body text went missing [${probe.visible_text}]",
            probe.visible_text.contains("must stay visible"),
        )
    }
}
