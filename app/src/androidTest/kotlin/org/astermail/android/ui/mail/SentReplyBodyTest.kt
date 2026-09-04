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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PROBE_JS = """(function(){
  var out=[];
  var aq=document.querySelector('div.aster_quote');
  out.push('aster_quote_present='+(aq?'1':'0'));
  out.push('aster_quote_has_proton='+((aq&&aq.querySelector('div.protonmail_quote'))?'1':'0'));
  out.push('aster_quote_display='+(aq?getComputedStyle(aq).display:'-'));
  var det=document.querySelector('details.aster-forwarded-collapse');
  out.push('details_present='+(det?'1':'0'));
  out.push('details_open='+(det&&det.open?'1':'0'));
  out.push('details_parent='+(det&&det.parentNode?det.parentNode.nodeName+'.'+(det.parentNode.className||''):'-'));
  out.push('quote_toggle='+(document.querySelector('.aster-quote-toggle')?'1':'0'));
  var qc=document.querySelector('.aster-quoted-content');
  out.push('quoted_content='+(qc?getComputedStyle(qc).display:'-'));
  var root=document.getElementById('m')||document.body;
  var vis='';
  (function walk(n){
    if(n.nodeType===3){vis+=n.nodeValue;return}
    if(n.nodeType!==1)return;
    var cs=getComputedStyle(n);
    if(cs.display==='none'||cs.visibility==='hidden')return;
    if(n.nodeName==='DETAILS'&&!n.open){
      var sum=n.querySelector('summary');
      if(sum)walk(sum);
      return;
    }
    for(var i=0;i<n.childNodes.length;i++)walk(n.childNodes[i]);
  })(root);
  out.push('VISIBLE='+vis.replace(/\s+/g,' ').trim());
  return out.join(' | ');
})()"""

@RunWith(AndroidJUnit4::class)
class SentReplyBodyTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val my_reply_text = "Thanks for getting back to me, I will take a look at the help section."

    private val their_proton_reply = buildString {
        append("<div>Hey, all great questions. Im not in the office right now, I&#39;ll get back ")
        append("to you tomorrow. Some of your questions get answered already in our ")
        append("<a href=\"https://www.privacynotes.app/help\">https://www.privacynotes.app/help</a> ")
        append("section. Please check it out for, the AI prompt is also genuinely useful.</div>")
        append("<div><br></div>")
        append("<div class=\"protonmail_signature_block\">")
        append("<div class=\"protonmail_signature_block-user\">Sent from Proton Mail for iOS.</div>")
        append("</div>")
        append("<div><br></div>")
        append("<div class=\"protonmail_quote\">")
        append("On Thursday, September 4th, 2026 at 5:58 PM, MR. JARVIS wrote:")
        append("<blockquote class=\"protonmail_quote\">")
        append("Hello Developers, I am reaching out. I just learned about your product.")
        append("</blockquote></div>")
    }

    private val sent_reply_body = buildString {
        append("<div>").append(my_reply_text).append("</div>")
        append("<br><div class=\"aster_quote gmail_quote\">")
        append("<div class=\"aster_quote_attr gmail_attr\" style=\"color:#555;font-size:13px\">")
        append("<div><b>From:</b> LifetimeLabs</div>")
        append("<div><b>Date:</b> Sep 4, 2026</div>")
        append("<div><b>Subject:</b> Re: Questions</div>")
        append("</div>")
        append("<blockquote class=\"gmail_quote\" style=\"margin:8px 0 0;padding-left:12px\">")
        append(their_proton_reply)
        append("</blockquote></div>")
    }

    private fun visible_text_of(body: String): String {
        val ref = AtomicReference<WebView?>(null)
        compose_rule.setContent {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            ref.set(this)
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
        compose_rule.waitForIdle()
        Thread.sleep(3000)
        compose_rule.waitForIdle()

        val latch = CountDownLatch(1)
        val raw = AtomicReference("")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ref.get()?.evaluateJavascript(PROBE_JS) { value ->
                raw.set(value?.trim()?.removeSurrounding("\"") ?: "")
                latch.countDown()
            } ?: latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)
        return raw.get().replace("\n", " ").replace("\\\"", "\"")
    }

    @Test
    fun a_sent_reply_shows_my_own_text_and_not_the_quoted_original() {
        val visible = visible_text_of(sent_reply_body)

        assertTrue(
            "my own reply text is missing from the sent copy [" + visible + "]",
            visible.contains("VISIBLE=Thanks for getting back to me, I will take a look at the help section"),
        )
        assertTrue(
            "the quoted original was moved out of my own quote [" + visible + "]",
            visible.contains("aster_quote_has_proton=1"),
        )
        assertFalse(
            "my own quote was turned into a forwarded block [" + visible + "]",
            visible.contains("details_present=1"),
        )
        assertTrue(
            "my own quote has no expand toggle [" + visible + "]",
            visible.contains("quote_toggle=1"),
        )
        assertFalse(
            "the quoted original is shown as my reply body [" + visible + "]",
            visible.contains("Im not in the office right now"),
        )
        assertFalse(
            "their signature is shown as my reply body [" + visible + "]",
            visible.contains("Sent from Proton Mail"),
        )
    }

    @Test
    fun a_received_third_party_quote_outside_our_wrapper_still_collapses() {
        val visible = visible_text_of(their_proton_reply)

        assertTrue(
            "a third party quote no longer collapses [" + visible + "]",
            visible.contains("details_present=1"),
        )
        assertFalse(
            "the third party quote is expanded by default [" + visible + "]",
            visible.contains("details_open=1"),
        )
        assertTrue(
            "the sender own new text is missing [" + visible + "]",
            visible.contains("all great questions"),
        )
    }
}
