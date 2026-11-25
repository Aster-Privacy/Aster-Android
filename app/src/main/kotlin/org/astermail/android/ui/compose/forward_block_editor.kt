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

package org.astermail.android.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.mail.EmailHtmlSanitizer
import org.astermail.android.ui.mail.intercept_forward_editor_image
import org.astermail.android.ui.mail.setting_blocks_remote_images

internal data class forward_header_labels(
    val forwarded_message: String,
    val from: String,
    val date: String,
    val subject: String,
    val to: String,
)

internal data class forward_header_values(
    val from: String,
    val date: String,
    val subject: String,
    val to: List<String>,
)

private const val forward_editor_min_height_dp = 120

private const val forward_editor_max_height_dp = 420

private fun escape_forward_text(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

internal fun build_forward_header_html(
    labels: forward_header_labels,
    values: forward_header_values,
): String {
    val lines = mutableListOf<String>()
    lines.add("---------- ${escape_forward_text(labels.forwarded_message)} ---------")
    lines.add("<b>${escape_forward_text(labels.from)}</b> ${escape_forward_text(values.from)}")
    if (values.date.isNotBlank()) {
        lines.add("<b>${escape_forward_text(labels.date)}</b> ${escape_forward_text(values.date)}")
    }
    if (values.subject.isNotBlank()) {
        lines.add("<b>${escape_forward_text(labels.subject)}</b> ${escape_forward_text(values.subject)}")
    }
    val to = values.to.map { it.trim() }.filter { it.isNotEmpty() }
    if (to.isNotEmpty()) {
        lines.add("<b>${escape_forward_text(labels.to)}</b> ${escape_forward_text(to.joinToString(", "))}")
    }
    return "<div class=\"aster_quote_attr gmail_attr\">" + lines.joinToString("<br>") + "</div><br>"
}

internal fun build_forward_inner_html(header_html: String, original_html: String): String =
    header_html + "<div class=\"aster_forward_body\">" + original_html + "</div>"

internal data class reply_quote_labels(
    val from: String,
    val date: String,
    val subject: String,
)

internal fun build_reply_quote_html(
    labels: reply_quote_labels,
    from: String,
    date: String,
    subject: String,
    original_html: String,
): String =
    "<div class=\"aster_quote_attr gmail_attr\" style=\"color:#555;font-size:13px\">" +
        "<div><b>${escape_forward_text(labels.from)}</b> ${escape_forward_text(from)}</div>" +
        "<div><b>${escape_forward_text(labels.date)}</b> ${escape_forward_text(date)}</div>" +
        "<div><b>${escape_forward_text(labels.subject)}</b> ${escape_forward_text(subject)}</div>" +
        "</div>" +
        "<blockquote class=\"gmail_quote\" style=\"margin:8px 0 0;padding-left:12px;border-left:2px solid #ccc;color:#555;font-size:13px\">" +
        original_html +
        "</blockquote>"

internal fun sanitize_quoted_original(html: String): String = sanitize_forward_seed(html)

internal fun wrap_forward_block(inner_html: String): String =
    "<br><br><div class=\"aster_quote gmail_quote\">" + inner_html + "</div>"

internal fun draft_html_with_forward(base_draft: String, base_is_html: Boolean, forward_block: String): String {
    if (forward_block.isBlank()) return base_draft
    val base_html = if (base_is_html) base_draft else escape_forward_text(base_draft).replace("\n", "<br>")
    return base_html + forward_block
}

internal fun sanitize_forward_seed(html: String): String =
    EmailHtmlSanitizer.sanitize(
        html,
        EmailHtmlSanitizer.SanitizeOptions(
            clean_tracking_links = false,
            remove_tracking_pixels = true,
            block_remote_fonts = true,
            block_remote_css = true,
        ),
    )

private class forward_editor_bridge(
    private val on_html: (String) -> Unit,
    private val on_height: (Int) -> Unit,
) {
    private val main_handler = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun push_html(html: String) {
        main_handler.post { on_html(html) }
    }

    @android.webkit.JavascriptInterface
    fun push_height(height: Int) {
        main_handler.post { on_height(height) }
    }
}

private const val forward_editor_script = """
(function(){
  var ed=document.getElementById('aster_forward_editor');
  if(!ed){return;}
  var timer=null;
  var height_queued=false;
  function height(){try{aster_forward.push_height(Math.ceil(ed.getBoundingClientRect().height));}catch(e){}}
  function queue_height(){if(height_queued){return;}height_queued=true;requestAnimationFrame(function(){height_queued=false;height();});}
  function push(){try{aster_forward.push_html(ed.innerHTML);}catch(e){}height();}
  function schedule(){if(timer){clearTimeout(timer);}timer=setTimeout(push,250);}
  ed.addEventListener('input',schedule);
  ed.addEventListener('blur',function(){if(timer){clearTimeout(timer);timer=null;push();}});
  if(window.ResizeObserver){new ResizeObserver(queue_height).observe(ed);}
  document.addEventListener('load',queue_height,true);
  document.addEventListener('error',queue_height,true);
  window.addEventListener('load',height);
  setTimeout(height,0);
})();
"""

internal fun forward_editor_image_sources(allow_external: Boolean): String =
    if (allow_external) "data: https: http:" else "data:"

private fun build_forward_editor_document(seed_html: String, nonce: String, allow_external: Boolean): String =
    "<!DOCTYPE html><html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; " +
        "img-src ${forward_editor_image_sources(allow_external)}; style-src 'unsafe-inline'; " +
        "script-src 'nonce-$nonce'\">" +
        "<style>html,body{margin:0;padding:0;background:#ffffff;}" +
        "#aster_forward_editor{padding:12px;outline:none;font-family:sans-serif;font-size:14px;" +
        "color:#222222;word-wrap:break-word;overflow-wrap:anywhere;}" +
        "#aster_forward_editor img{max-width:100%;height:auto;}" +
        "#aster_forward_editor table{max-width:100%;}</style>" +
        "</head><body>" +
        "<div id=\"aster_forward_editor\" contenteditable=\"true\">" + seed_html + "</div>" +
        "<script nonce=\"$nonce\">" + forward_editor_script + "</script>" +
        "</body></html>"

@Composable
internal fun forward_block_editor(
    seed_html: String,
    on_html_change: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!org.astermail.android.ui.common.remember_webview_usable()) {
        org.astermail.android.ui.common.webview_unavailable_notice(modifier)
        return
    }
    val latest_on_html by rememberUpdatedState(on_html_change)
    val settings_vm = shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val allow_external = !setting_blocks_remote_images(settings_state.preferences) &&
        !org.astermail.android.network.low_network_active()
    val allow_external_now = rememberUpdatedState(allow_external)
    val document by produceState<String?>(initialValue = null, seed_html, allow_external) {
        value = withContext(Dispatchers.Default) {
            val nonce = java.util.UUID.randomUUID().toString().replace("-", "")
            build_forward_editor_document(sanitize_forward_seed(seed_html), nonce, allow_external)
        }
    }
    var measured_height_dp by remember { mutableIntStateOf(0) }
    val reduce_motion = aster_reduce_motion()
    val content_alpha by animateFloatAsState(
        targetValue = if (measured_height_dp > 0) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reduce_motion) 0 else AsterDuration.short_4,
            easing = AsterEasing.standard_in_out,
        ),
        label = "forward_editor_alpha",
    )
    val screen_height_dp = LocalConfiguration.current.screenHeightDp
    val max_height_dp = minOf(forward_editor_max_height_dp, (screen_height_dp * 0.55f).toInt())
        .coerceAtLeast(forward_editor_min_height_dp)
    val target_height_dp = if (measured_height_dp > 0) {
        measured_height_dp.coerceIn(forward_editor_min_height_dp, max_height_dp)
    } else {
        forward_editor_min_height_dp
    }

    val show_scroll_bar = !org.astermail.android.ui.theme.local_accessibility.current.hide_scrollbars
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.blockNetworkImage = !allow_external_now.value
                settings.blockNetworkLoads = !allow_external_now.value
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean = true

                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                    ): android.webkit.WebResourceResponse? =
                        intercept_forward_editor_image(view?.context, request, allow_external_now.value)

                    override fun onRenderProcessGone(
                        view: android.webkit.WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?,
                    ): Boolean {
                        runCatching {
                            (view?.parent as? android.view.ViewGroup)?.removeView(view)
                            view?.destroy()
                        }
                        return true
                    }
                }
                addJavascriptInterface(
                    forward_editor_bridge(
                        on_html = { html -> latest_on_html(html) },
                        on_height = { height -> if (height > 0 && height != measured_height_dp) measured_height_dp = height },
                    ),
                    "aster_forward",
                )
                isVerticalScrollBarEnabled = show_scroll_bar
                isHorizontalScrollBarEnabled = false
                setOnTouchListener { view, _ ->
                    val scrollable = view.canScrollVertically(1) || view.canScrollVertically(-1)
                    view.parent?.requestDisallowInterceptTouchEvent(scrollable)
                    false
                }
                setBackgroundColor(android.graphics.Color.WHITE)
            }
        },
        update = { view ->
            val doc = document ?: return@AndroidView
            view.settings.blockNetworkImage = !allow_external
            view.settings.blockNetworkLoads = !allow_external
            if (view.tag != doc) {
                view.tag = doc
                view.loadDataWithBaseURL(null, doc, "text/html", "utf-8", null)
            }
        },
        onRelease = { view ->
            runCatching {
                view.stopLoading()
                view.removeJavascriptInterface("aster_forward")
                view.destroy()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(target_height_dp.dp)
            .alpha(content_alpha),
    )
}
