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

package org.astermail.android.ui.settings.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Blockquote
import compose.icons.tablericons.Bold
import compose.icons.tablericons.ClearFormatting
import compose.icons.tablericons.ColorPicker
import compose.icons.tablericons.ColorSwatch
import compose.icons.tablericons.Italic
import compose.icons.tablericons.LetterCase
import compose.icons.tablericons.Link
import compose.icons.tablericons.List
import compose.icons.tablericons.Strikethrough
import compose.icons.tablericons.Underline
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.ui.compose.numbered_list_icon
import org.astermail.android.ui.mail.EmailHtmlSanitizer

val signature_preset_colors = listOf(
    "#000000", "#434343", "#666666", "#999999", "#b7b7b7", "#cccccc", "#efefef", "#ffffff",
    "#980000", "#ff0000", "#ff9900", "#ffff00", "#00ff00", "#00ffff", "#0000ff", "#9900ff",
    "#e6b8af", "#f4cccc", "#fce5cd", "#fff2cc", "#d9ead3", "#d0e0e3", "#c9daf8", "#d9d2e9",
    "#dd7e6b", "#ea9999", "#f9cb9c", "#ffe599", "#b6d7a8", "#a2c4c9", "#6d9eeb", "#8e7cc3",
    "#cc4125", "#e06666", "#f6b26b", "#ffd966", "#93c47d", "#76a5af", "#6fa8dc", "#c27ba0",
)

val signature_font_sizes = listOf(
    "small" to "12px",
    "normal" to "14px",
    "large" to "18px",
    "huge" to "24px",
)

private val hex_color_pattern = Regex("^#[0-9a-fA-F]{6}$")

private val font_size_pattern = Regex("^[0-9]{1,3}px$")

internal fun plain_signature_to_html(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\r\n", "\n")
        .replace("\n", "<br>")

internal fun sanitize_signature_html(html: String): String =
    EmailHtmlSanitizer.sanitize(
        html,
        EmailHtmlSanitizer.SanitizeOptions(
            clean_tracking_links = false,
            remove_tracking_pixels = false,
            block_remote_fonts = false,
            block_remote_css = false,
        ),
    )

internal fun normalize_signature_link(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.length > 2048) return null
    if (trimmed.any { it.isISOControl() } || trimmed.contains(' ')) return null
    val lower = trimmed.lowercase()
    return when {
        lower.startsWith("https://") || lower.startsWith("http://") -> trimmed
        lower.startsWith("mailto:") -> trimmed
        trimmed.contains('@') -> "mailto:$trimmed"
        lower.contains(':') -> null
        else -> "https://$trimmed"
    }
}

private fun js_string(value: String): String = org.json.JSONObject.quote(value)

class signature_editor_controller {
    internal var editor_view: android.webkit.WebView? = null

    private fun run(script: String) {
        val view = editor_view ?: return
        view.post { view.evaluateJavascript(script, null) }
    }

    fun toggle(command: String) = run("window.aster_exec(${js_string(command)})")

    fun toggle_blockquote() = run("window.aster_block()")

    fun set_font_size(px: String) {
        if (!font_size_pattern.matches(px)) return
        run("window.aster_font_size(${js_string(px)})")
    }

    fun set_font_color(hex: String) {
        if (!hex_color_pattern.matches(hex)) return
        run("window.aster_color('fore',${js_string(hex)})")
    }

    fun set_background_color(hex: String) {
        if (!hex_color_pattern.matches(hex)) return
        run("window.aster_color('back',${js_string(hex)})")
    }

    fun insert_link(url: String) {
        val normalized = normalize_signature_link(url) ?: return
        run("window.aster_link(${js_string(normalized)})")
    }

    fun clear_formatting() = run("window.aster_clear()")

    fun request_html(on_result: (String?) -> Unit) {
        val view = editor_view
        if (view == null) {
            on_result(null)
            return
        }
        view.post {
            view.evaluateJavascript(
                "document.getElementById('aster_signature_editor').innerHTML",
            ) { raw ->
                val decoded = runCatching {
                    org.json.JSONTokener(raw).nextValue() as? String
                }.getOrNull()
                on_result(decoded)
            }
        }
    }
}

private class signature_editor_bridge(private val on_html: (String) -> Unit) {
    private val main_handler = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun push_html(html: String) {
        main_handler.post { on_html(html) }
    }
}

private const val signature_editor_script = """
(function(){
  var ed=document.getElementById('aster_signature_editor');
  if(!ed){return;}
  try{document.execCommand('styleWithCSS',false,true);}catch(e){}
  function push(){try{aster_bridge.push_html(ed.innerHTML);}catch(e){}}
  window.aster_push=push;
  ed.addEventListener('input',push);
  ed.addEventListener('blur',push);
  window.aster_exec=function(command){ed.focus();try{document.execCommand(command,false,null);}catch(e){}push();};
  window.aster_block=function(){ed.focus();try{document.execCommand('formatBlock',false,'blockquote');}catch(e){}push();};
  window.aster_color=function(kind,hex){
    ed.focus();
    try{document.execCommand(kind==='fore'?'foreColor':'hiliteColor',false,hex);}catch(e){}
    push();
  };
  window.aster_font_size=function(px){
    ed.focus();
    try{document.execCommand('fontSize',false,'7');}catch(e){}
    var fonts=ed.querySelectorAll('font[size="7"]');
    for(var index=0;index<fonts.length;index++){
      var node=fonts[index];
      var span=document.createElement('span');
      span.style.fontSize=px;
      while(node.firstChild){span.appendChild(node.firstChild);}
      node.parentNode.replaceChild(span,node);
    }
    push();
  };
  window.aster_link=function(url){ed.focus();try{document.execCommand('createLink',false,url);}catch(e){}push();};
  window.aster_clear=function(){ed.focus();try{document.execCommand('removeFormat',false,null);}catch(e){}push();};
})();
"""

private fun build_signature_document(seed_html: String): String =
    "<!DOCTYPE html><html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>html,body{margin:0;padding:0;background:#ffffff;}" +
        "#aster_signature_editor{min-height:100%;padding:12px;outline:none;" +
        "font-family:sans-serif;font-size:14px;color:#222222;word-wrap:break-word;}" +
        "#aster_signature_editor img{max-width:100%;height:auto;}</style>" +
        "</head><body>" +
        "<div id=\"aster_signature_editor\" contenteditable=\"true\">" + seed_html + "</div>" +
        "<script>" + signature_editor_script + "</script>" +
        "</body></html>"

@Composable
fun signature_rich_editor(
    initial_html: String,
    controller: signature_editor_controller,
    on_html_change: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = remember { build_signature_document(sanitize_signature_html(initial_html)) }

    DisposableEffect(Unit) {
        onDispose { controller.editor_view = null }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean = true
                }
                addJavascriptInterface(signature_editor_bridge(on_html_change), "aster_bridge")
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.WHITE)
                loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
                controller.editor_view = this
            }
        },
        update = { view -> controller.editor_view = view },
        modifier = modifier,
    )
}

@Composable
fun signature_format_toolbar(
    controller: signature_editor_controller,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    var size_open by remember { mutableStateOf(false) }
    var text_color_open by remember { mutableStateOf(false) }
    var highlight_open by remember { mutableStateOf(false) }
    var link_open by remember { mutableStateOf(false) }
    var link_value by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bg_secondary, SquircleShape(18.dp))
            .padding(horizontal = AsterSpacing.xs, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        signature_format_button(TablerIcons.Bold, stringResource(R.string.bold)) {
            controller.toggle("bold")
        }
        signature_format_button(TablerIcons.Italic, stringResource(R.string.italic)) {
            controller.toggle("italic")
        }
        signature_format_button(TablerIcons.Underline, stringResource(R.string.underline)) {
            controller.toggle("underline")
        }
        signature_format_button(TablerIcons.Strikethrough, stringResource(R.string.strikethrough)) {
            controller.toggle("strikeThrough")
        }
        signature_format_divider()
        signature_format_button(TablerIcons.LetterCase, stringResource(R.string.font_size)) {
            size_open = true
        }
        signature_format_button(TablerIcons.ColorPicker, stringResource(R.string.text_color)) {
            text_color_open = true
        }
        signature_format_button(TablerIcons.ColorSwatch, stringResource(R.string.highlight_color)) {
            highlight_open = true
        }
        signature_format_divider()
        signature_format_button(TablerIcons.List, stringResource(R.string.bullet_list)) {
            controller.toggle("insertUnorderedList")
        }
        signature_format_button(numbered_list_icon, stringResource(R.string.numbered_list)) {
            controller.toggle("insertOrderedList")
        }
        signature_format_button(TablerIcons.Blockquote, stringResource(R.string.blockquote)) {
            controller.toggle_blockquote()
        }
        signature_format_divider()
        signature_format_button(TablerIcons.Link, stringResource(R.string.insert_link)) {
            link_value = ""
            link_open = true
        }
        signature_format_button(
            TablerIcons.ClearFormatting,
            stringResource(R.string.clear_formatting),
        ) {
            controller.clear_formatting()
        }
    }

    if (size_open) {
        AsterDialog(
            on_dismiss = { size_open = false },
            title = stringResource(R.string.font_size),
            body = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    signature_font_sizes.forEach { (label, px) ->
                        val text = when (label) {
                            "small" -> stringResource(R.string.font_small)
                            "large" -> stringResource(R.string.font_large)
                            "huge" -> stringResource(R.string.font_extra_large)
                            else -> stringResource(R.string.font_default)
                        }
                        Text(
                            text = text,
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    controller.set_font_size(px)
                                    size_open = false
                                }
                                .padding(vertical = AsterSpacing.md),
                        )
                    }
                }
            },
            footer = {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { size_open = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }

    if (text_color_open) {
        signature_color_dialog(
            title = stringResource(R.string.text_color),
            on_dismiss = { text_color_open = false },
            on_pick = { hex ->
                controller.set_font_color(hex)
                text_color_open = false
            },
        )
    }

    if (highlight_open) {
        signature_color_dialog(
            title = stringResource(R.string.highlight_color),
            on_dismiss = { highlight_open = false },
            on_pick = { hex ->
                controller.set_background_color(hex)
                highlight_open = false
            },
        )
    }

    if (link_open) {
        AsterDialog(
            on_dismiss = { link_open = false },
            title = stringResource(R.string.insert_link),
            body = {
                AsterTextField(
                    value = link_value,
                    onValueChange = { link_value = it },
                    label = stringResource(R.string.link_url_label),
                    placeholder = stringResource(R.string.link_url_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            footer = {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { link_open = false },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                AsterDialogPrimaryButton(
                    label = stringResource(R.string.insert),
                    onClick = {
                        controller.insert_link(link_value)
                        link_open = false
                    },
                    modifier = Modifier.weight(1f),
                )
            },
        )
    }
}

@Composable
private fun signature_color_dialog(
    title: String,
    on_dismiss: () -> Unit,
    on_pick: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    AsterDialog(
        on_dismiss = on_dismiss,
        title = title,
        body = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                signature_preset_colors.chunked(8).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                    ) {
                        row.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        1.dp,
                                        colors.border_secondary,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { on_pick(hex) },
                            )
                        }
                    }
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun signature_format_divider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .width(1.dp)
            .height(20.dp)
            .background(AsterMaterial.colors.border_secondary),
    )
}

@Composable
private fun signature_format_button(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = on_click),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.text_secondary,
            modifier = Modifier.size(18.dp),
        )
    }
}
