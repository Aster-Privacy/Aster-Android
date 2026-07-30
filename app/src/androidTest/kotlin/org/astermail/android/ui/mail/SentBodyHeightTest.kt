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

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val CONTENT_BOTTOM_JS = """(function(){
  var m=document.getElementById('m');
  if(!m)return 0;
  var sy=window.pageYOffset||document.documentElement.scrollTop||0;
  var max=0;
  var tw=document.createTreeWalker(m,NodeFilter.SHOW_TEXT,null);
  var rng=document.createRange();
  while(tw.nextNode()){
    var n=tw.currentNode;
    if(!n.nodeValue||!n.nodeValue.trim())continue;
    rng.selectNodeContents(n);
    var rs=rng.getClientRects();
    for(var i=0;i<rs.length;i++){
      var r=rs[i];
      if(r.height<=0||r.width<=0)continue;
      if(r.bottom+sy>max)max=r.bottom+sy;
    }
  }
  var im=m.querySelectorAll('img,hr');
  for(var j=0;j<im.length;j++){
    var ir=im[j].getBoundingClientRect();
    if(ir.height>2&&ir.width>2&&ir.bottom+sy>max)max=ir.bottom+sy;
  }
  var caps=m.querySelectorAll('.aster-quote-toggle,details.aster-forwarded-collapse>summary');
  var deepest='';
  for(var q=0;q<caps.length;q++){
    var cr=caps[q].getBoundingClientRect();
    if(cr.height>0&&cr.width>0&&cr.bottom+sy>max){max=cr.bottom+sy;deepest='toggle'}
  }
  var pb=parseFloat(window.getComputedStyle(document.body).paddingBottom)||0;
  var mb=parseFloat(window.getComputedStyle(m).marginBottom)||0;
  var top=m.getBoundingClientRect().top+sy;
  var fs=window.__aster_fit_scale||1;
  var wid='';
  var wr=0;
  var all=m.querySelectorAll('*');
  for(var k=0;k<all.length;k++){
    var e2=all[k];
    var c2=window.getComputedStyle(e2);
    if(c2.position==='fixed')continue;
    if(c2.display==='none'||c2.visibility==='hidden')continue;
    var b2=e2.getBoundingClientRect();
    if(b2.width<=0||b2.height<=0)continue;
    var cand=b2.right;
    var sw2=e2.scrollWidth||0;
    if(sw2>b2.width+1&&b2.left+sw2>cand)cand=b2.left+sw2;
    if(cand>wr){wr=cand;wid=e2.tagName+'.'+(e2.className||'')+'@'+Math.round(b2.left)+'w'+Math.round(b2.width)+'sw'+sw2;}
  }
  var pr=parseFloat(window.getComputedStyle(document.body).paddingRight)||0;
  return Math.ceil(max*fs)+'|'+m.offsetHeight+'|'+top+'|'+pb+'|'+mb+'|'+deepest+'|'+fs+'|'+
    window.innerWidth+'|'+document.documentElement.scrollWidth+'|'+Math.round(wr)+'|'+wid+'|pr'+pr;
})()"""

@RunWith(AndroidJUnit4::class)
class SentBodyHeightTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private data class Measurement(val reported: Int, val content_bottom: Int, val diag: String) {
        val fit_scale: Float get() = diag.split('|').getOrNull(6)?.toFloatOrNull() ?: 1f
    }

    private fun measure(body: String): Measurement {
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
        )

        val reported = AtomicInteger(0)
        val web_ref = AtomicReference<WebView?>(null)

        compose_rule.setContent {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.setSupportZoom(true)
                        settings.loadsImagesAutomatically = true
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                                val text = msg.message()
                                val prefix = "ASTER_HEIGHT_EXACT:"
                                if (text.startsWith(prefix)) {
                                    text.substring(prefix.length).toIntOrNull()?.let { reported.set(it) }
                                }
                                return true
                            }
                        }
                        web_ref.set(this)
                        loadDataWithBaseURL(
                            "https://mail-content.invalid/",
                            html,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(4000.dp),
            )
        }

        compose_rule.waitForIdle()
        Thread.sleep(3500)
        compose_rule.waitForIdle()

        val latch = CountDownLatch(1)
        val diag = AtomicReference("")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            web_ref.get()?.evaluateJavascript(CONTENT_BOTTOM_JS) { value ->
                diag.set(value?.trim()?.removeSurrounding("\"") ?: "")
                latch.countDown()
            } ?: latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)

        val raw = diag.get()
        val bottom = raw.substringBefore('|').toFloatOrNull()?.toInt() ?: 0
        return Measurement(reported.get(), bottom, raw)
    }

    private fun assert_covers(label: String, m: Measurement) {
        assertTrue("$label: nothing was measured", m.reported > 0)
        assertTrue("$label: no content found", m.content_bottom > 0)
        assertTrue(
            "$label: reported ${m.reported}px cuts off content ending at ${m.content_bottom}px [${m.diag}]",
            m.reported >= m.content_bottom,
        )
    }

    @Test
    fun plain_text_sent_reply_is_not_cut_off() {
        val body = buildString {
            append("Hey, that works for me. Let's do Thursday at 10.\n\n")
            append("Thanks again for putting the deck together, it reads really well.\n\n")
            append("Best,\nSam\n\n")
            append("Sam Ellis\nProduct\nAster\n\n")
            append("Secured by Aster Mail\n\n")
            append("On Tue, 14 Jul 2026 at 09:12, Riley Chen <riley@example.com> wrote:\n")
            repeat(30) { append("> line $it of the quoted original message goes here\n") }
        }
        val m = measure(body)
        assert_covers("plain sent reply", m)
        assert_no_spurious_zoom("plain sent reply", m)
    }

    private fun assert_no_spurious_zoom(label: String, m: Measurement) {
        assertTrue(
            "$label: content fits the viewport but was zoomed out to ${m.fit_scale} [${m.diag}]",
            m.fit_scale >= 0.999f,
        )
    }

    @Test
    fun html_sent_reply_is_not_cut_off() {
        val quoted = buildString {
            repeat(30) { append("<div>quoted line $it of the original message</div>") }
        }
        val body = buildString {
            append("<div>Hey, that works for me. Let's do Thursday at 10.</div>")
            append("<div><br></div>")
            append("<div>Thanks again for putting the deck together.</div>")
            append("<div><br></div>")
            append("<div>Best,<br>Sam</div>")
            append("<div><br></div>")
            append("<div>Secured by Aster Mail</div>")
            append("<div class=\"aster_quote\">")
            append("<div class=\"aster_quote_attr\">On Tue, 14 Jul 2026, Riley Chen wrote:</div>")
            append("<blockquote>").append(quoted).append("</blockquote>")
            append("</div>")
        }
        val m = measure(body)
        assert_covers("html sent reply", m)
        assert_no_spurious_zoom("html sent reply", m)
    }

    @Test
    fun wide_html_sent_email_is_not_cut_off() {
        val body = buildString {
            append("<div style=\"width:900px\">")
            append("<div>Here is the summary you asked for.</div>")
            repeat(20) { append("<div>row $it with a fairly long sentence that keeps the table wide</div>") }
            append("<div>Regards,<br>Sam</div>")
            append("<div>Secured by Aster Mail</div>")
            append("</div>")
        }
        val m = measure(body)
        assert_covers("wide sent email", m)
        assertTrue(
            "wide sent email should be zoomed out to fit, was ${m.fit_scale} [${m.diag}]",
            m.fit_scale < 1f,
        )
        assertTrue(
            "wide sent email zoomed out far more than the overflow needs: ${m.fit_scale} [${m.diag}]",
            m.fit_scale > 0.95f,
        )
    }
}
