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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
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
import compose.icons.tablericons.Minus
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Strikethrough
import compose.icons.tablericons.Table
import compose.icons.tablericons.Typography
import compose.icons.tablericons.Underline
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterSwitch
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

data class signature_font_family(val label: String, val stack: String, val is_monospace: Boolean = false)

const val signature_default_font_family = "inherit"

val signature_font_families = listOf(
    signature_font_family("Arial", "Arial, Helvetica, sans-serif"),
    signature_font_family("Helvetica", "Helvetica, Arial, sans-serif"),
    signature_font_family("Verdana", "Verdana, Geneva, sans-serif"),
    signature_font_family("Tahoma", "Tahoma, Geneva, sans-serif"),
    signature_font_family("Trebuchet MS", "'Trebuchet MS', Helvetica, sans-serif"),
    signature_font_family("Georgia", "Georgia, serif"),
    signature_font_family("Times New Roman", "'Times New Roman', Times, serif"),
    signature_font_family("Palatino", "'Palatino Linotype', Palatino, 'Book Antiqua', serif"),
    signature_font_family("Garamond", "Garamond, 'EB Garamond', Georgia, serif"),
    signature_font_family(
        "Monospace",
        "ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', 'Courier New', monospace",
        is_monospace = true,
    ),
    signature_font_family("Courier New", "'Courier New', Courier, monospace"),
    signature_font_family("Lucida Console", "'Lucida Console', Monaco, 'Courier New', monospace"),
)

internal fun signature_font_family_allowed(stack: String): Boolean =
    stack == signature_default_font_family || signature_font_families.any { it.stack == stack }

const val signature_table_max_rows = 20

const val signature_table_max_columns = 8

val signature_border_actions = setOf("borders_on", "borders_off")

val signature_structure_actions = setOf(
    "row_above", "row_below", "column_before", "column_after", "delete_row", "delete_column",
)

val signature_table_actions = listOf(
    "row_above", "row_below", "column_before", "column_after", "delete_row", "delete_column", "delete_table",
)

data class signature_table_state(
    val in_table: Boolean,
    val borders: Boolean = false,
    val rows: Int = 0,
    val columns: Int = 0,
    val merged: Boolean = false,
)

internal fun parse_signature_table_state(raw: String?): signature_table_state {
    val decoded = runCatching { org.json.JSONTokener(raw ?: "").nextValue() as? String }.getOrNull()
        ?: return signature_table_state(false)
    val json = runCatching { org.json.JSONObject(decoded) }.getOrNull() ?: return signature_table_state(false)
    if (!json.optBoolean("in_table", false)) return signature_table_state(false)
    return signature_table_state(
        in_table = true,
        borders = json.optBoolean("borders", false),
        rows = json.optInt("rows", 0),
        columns = json.optInt("columns", 0),
        merged = json.optBoolean("merged", false),
    )
}

private val hex_color_pattern = Regex("^#[0-9a-fA-F]{6}$")

private val font_size_pattern = Regex("^[0-9]{1,3}px$")

internal fun plain_signature_to_html(text: String): String =
    text.replace("\r\n", "\n").split("\n").joinToString("<br>") { line ->
        org.astermail.android.ui.compose.preserve_plain_spaces(
            line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"),
        )
    }

private val signature_block_tags = setOf(
    "address", "article", "blockquote", "dd", "div", "dl", "dt", "figure", "footer",
    "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li", "ol", "p", "pre",
    "section", "table", "tbody", "tfoot", "thead", "tr", "ul",
)

private val signature_source_whitespace = Regex("[ \\t\\r\\n\\f]+")

private class signature_plain_lines {
    val lines = mutableListOf<String>()
    val current = StringBuilder()

    fun push_line() {
        lines.add(current.toString().trimEnd())
        current.setLength(0)
    }

    fun soft_break() {
        if (current.isNotEmpty()) push_line()
    }
}

private fun collect_signature_text(node: org.jsoup.nodes.Node, out: signature_plain_lines, in_pre: Boolean) {
    when (node) {
        is org.jsoup.nodes.TextNode -> {
            val raw = node.wholeText
            val text = if (in_pre) raw else signature_source_whitespace.replace(raw, " ")
            val parts = text.replace("\u00A0", " ").split("\n")
            parts.forEachIndexed { index, part ->
                if (index > 0) out.push_line()
                out.current.append(part)
            }
        }
        is org.jsoup.nodes.Element -> {
            val tag = node.normalName()
            if (tag == "br") {
                out.push_line()
                return
            }
            if (tag == "script" || tag == "style" || tag == "head" || tag == "title") return
            val is_block = tag in signature_block_tags
            if (is_block) out.soft_break()
            val pre = in_pre || tag == "pre"
            node.childNodes().forEach { collect_signature_text(it, out, pre) }
            if (tag == "td" || tag == "th") out.current.append(' ')
            if (is_block) out.soft_break()
        }
    }
}

internal val signature_filler_char = 0x200B.toChar().toString()

internal fun strip_signature_fillers(html: String): String =
    html.replace(signature_filler_char, "").replace("&#8203;", "").replace("&#x200b;", "", ignoreCase = true)

internal fun signature_html_to_plain_text(html: String): String {
    val aligned = org.astermail.android.mail.align_simple_tables(strip_signature_fillers(html))
    val body = org.jsoup.Jsoup.parseBodyFragment(aligned).body()
    val out = signature_plain_lines()
    body.childNodes().forEach { collect_signature_text(it, out, false) }
    out.soft_break()
    val lines = out.lines.map { it.trimEnd() }
    val first = lines.indexOfFirst { it.isNotEmpty() }
    if (first < 0) return ""
    val last = lines.indexOfLast { it.isNotEmpty() }
    return lines.subList(first, last + 1).joinToString("\n")
}

internal const val signature_font_marker = "aster_font_marker"

internal fun strip_signature_font_markers(html: String): String {
    if (!html.contains(signature_font_marker, ignoreCase = true)) return html
    val body = org.jsoup.Jsoup.parseBodyFragment(html).apply { outputSettings().prettyPrint(false) }.body()
    body.select("font").forEach { font ->
        if (font.attr("face").equals(signature_font_marker, ignoreCase = true)) font.unwrap()
    }
    return body.html()
}

internal fun sanitize_signature_html(html: String): String =
    scrub_signature_styles(
        strip_signature_font_markers(
            EmailHtmlSanitizer.sanitize(
                strip_signature_fillers(html),
                EmailHtmlSanitizer.SanitizeOptions(
                    clean_tracking_links = false,
                    remove_tracking_pixels = false,
                    block_remote_fonts = true,
                    block_remote_css = true,
                ),
            ),
        ),
    )

internal fun signature_editor_request_allowed(scheme: String?): Boolean {
    val normalized = scheme?.lowercase() ?: return false
    return normalized == "data" || normalized == "about"
}

private fun blocked_signature_editor_response(): android.webkit.WebResourceResponse =
    android.webkit.WebResourceResponse(
        "text/plain",
        null,
        403,
        "Forbidden",
        mapOf("Cache-Control" to "no-store"),
        java.io.ByteArrayInputStream(ByteArray(0)),
    )

internal fun normalize_signature_link(raw: String): String? =
    org.astermail.android.ui.compose.normalize_link_url(raw)

private fun js_string(value: String): String = org.json.JSONObject.quote(value)

internal const val remove_highlight_script = "window.aster_unhighlight()"

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

    fun set_font_family(stack: String) {
        if (!signature_font_family_allowed(stack)) return
        run("window.aster_font_family(${js_string(stack)})")
    }

    fun insert_table(rows: Int, columns: Int, show_borders: Boolean) {
        val safe_rows = rows.coerceIn(1, signature_table_max_rows)
        val safe_columns = columns.coerceIn(1, signature_table_max_columns)
        run("window.aster_table_insert($safe_rows,$safe_columns,$show_borders)")
    }

    fun edit_table(action: String) {
        if (action !in signature_border_actions && action !in signature_table_actions) return
        run("window.aster_table_edit(${js_string(action)})")
    }

    fun request_table_state(on_result: (signature_table_state) -> Unit) {
        val view = editor_view
        if (view == null) {
            on_result(signature_table_state(false))
            return
        }
        view.post {
            view.evaluateJavascript("window.aster_table_state&&window.aster_table_state()") { raw ->
                on_result(parse_signature_table_state(raw))
            }
        }
    }

    fun set_font_color(hex: String) {
        if (!hex_color_pattern.matches(hex)) return
        run("window.aster_color('fore',${js_string(hex)})")
    }

    fun set_background_color(hex: String) {
        if (!hex_color_pattern.matches(hex)) return
        run("window.aster_color('back',${js_string(hex)})")
    }

    fun remove_background_color() = run(remove_highlight_script)

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

internal const val signature_editor_script = """
(function(){
  var ed=document.getElementById('aster_signature_editor');
  if(!ed){return;}
  try{document.execCommand('styleWithCSS',false,true);}catch(e){}
  function push(){ensure_cell_placeholders();try{aster_bridge.push_html(ed.innerHTML);}catch(e){}}
  window.aster_push=push;
  ed.addEventListener('input',push);
  ed.addEventListener('blur',push);
  ensure_cell_placeholders();
  window.aster_exec=function(command){ed.focus();try{document.execCommand(command,false,null);}catch(e){}push();};
  window.aster_block=function(){ed.focus();try{document.execCommand('formatBlock',false,'blockquote');}catch(e){}push();};
  window.aster_color=function(kind,hex){
    ed.focus();
    try{document.execCommand(kind==='fore'?'foreColor':'hiliteColor',false,hex);}catch(e){}
    push();
  };
  window.aster_font_size=function(px){
    ed.focus();
    try{document.execCommand('styleWithCSS',false,false);}catch(e){}
    try{document.execCommand('fontSize',false,'7');}catch(e){}
    try{document.execCommand('styleWithCSS',false,true);}catch(e){}
    var fonts=ed.querySelectorAll('font[size]');
    for(var index=0;index<fonts.length;index++){
      var node=fonts[index];
      var span=document.createElement('span');
      span.style.fontSize=px;
      while(node.firstChild){span.appendChild(node.firstChild);}
      node.parentNode.replaceChild(span,node);
      var inner=span.querySelectorAll('[style*="font-size"]');
      for(var k=0;k<inner.length;k++){inner[k].style.fontSize='';}
    }
    push();
  };
  var saved_range=null;
  document.addEventListener('selectionchange',function(){
    var sel=window.getSelection();
    if(!sel||sel.rangeCount===0){return;}
    var range=sel.getRangeAt(0);
    if(ed.contains(range.commonAncestorContainer)){saved_range=range.cloneRange();}
  });
  function restore(){
    ed.focus();
    if(!saved_range||!ed.contains(saved_range.commonAncestorContainer)){return;}
    var sel=window.getSelection();
    if(!sel){return;}
    sel.removeAllRanges();
    sel.addRange(saved_range);
  }
  var font_marker='aster_font_marker';
  window.aster_font_family=function(stack){
    restore();
    var caret_sel=window.getSelection();
    if(caret_sel&&caret_sel.rangeCount>0){
      var caret_range=caret_sel.getRangeAt(0);
      if(caret_range.collapsed&&ed.contains(caret_range.startContainer)){
        var filler=document.createTextNode(String.fromCharCode(8203));
        var holder=document.createElement('span');
        holder.style.fontFamily=stack==='inherit'?window.getComputedStyle(ed).fontFamily:stack;
        holder.appendChild(filler);
        caret_range.insertNode(holder);
        var after=document.createRange();
        after.setStart(filler,filler.length);
        after.collapse(true);
        caret_sel.removeAllRanges();
        caret_sel.addRange(after);
        push();
        return;
      }
    }
    try{document.execCommand('styleWithCSS',false,false);}catch(e){}
    try{document.execCommand('fontName',false,font_marker);}catch(e){}
    try{document.execCommand('styleWithCSS',false,true);}catch(e){}
    var fonts=ed.querySelectorAll('font[face="'+font_marker+'"]');
    for(var index=0;index<fonts.length;index++){
      var node=fonts[index];
      var inner=node.querySelectorAll('[style*="font-family"]');
      for(var k=0;k<inner.length;k++){
        inner[k].style.fontFamily='';
        if(!(inner[k].getAttribute('style')||'').trim()){inner[k].removeAttribute('style');}
      }
      if(stack==='inherit'){unwrap(node);continue;}
      var span=document.createElement('span');
      span.style.fontFamily=stack;
      while(node.firstChild){span.appendChild(node.firstChild);}
      node.parentNode.replaceChild(span,node);
    }
    push();
  };
  var max_rows=20;
  var max_columns=8;
  var table_border='1px solid #c4c4c4';
  var pending_attr='data-aster-table-pending';
  function clamp(value,max){value=Math.floor(Number(value));if(!isFinite(value)){return 1;}return Math.min(max,Math.max(1,value));}
  function cell_style(borders){return 'padding: 4px 8px; vertical-align: top; border: '+(borders?table_border:'none');}
  function table_html(rows,columns,borders){
    var cell='<td style="'+cell_style(borders)+'"><br></td>';
    var row='<tr>'+new Array(clamp(columns,max_columns)+1).join(cell)+'</tr>';
    return '<table '+pending_attr+'="1" border="'+(borders?1:0)+'" cellpadding="0" cellspacing="0" style="border-collapse: collapse; border-spacing: 0; border: '+(borders?table_border:'none')+'"><tbody>'+new Array(clamp(rows,max_rows)+1).join(row)+'</tbody></table>';
  }
  function take_pending(){
    var table=ed.querySelector('table['+pending_attr+']');
    if(table){table.removeAttribute(pending_attr);}
    return table;
  }
  function insert_html(html){try{return document.execCommand('insertHTML',false,html);}catch(e){return false;}}
  function select_node(node){
    var sel=window.getSelection();
    if(!sel){return false;}
    var range=document.createRange();
    range.selectNode(node);
    sel.removeAllRanges();
    sel.addRange(range);
    return true;
  }
  function ensure_cell_placeholders(){
    var cells=ed.querySelectorAll('td,th');
    var sel=window.getSelection();
    var anchor=sel&&sel.rangeCount>0?sel.getRangeAt(0).startContainer:null;
    for(var i=0;i<cells.length;i++){
      var cell=cells[i];
      if(cell.textContent!==''||cell.querySelector('br,img,table')){continue;}
      var holds=anchor&&cell.contains(anchor);
      cell.appendChild(document.createElement('br'));
      if(holds){caret_in(cell);}
    }
  }
  function caret_in(cell){
    var sel=window.getSelection();
    if(!sel){return;}
    var range=document.createRange();
    range.selectNodeContents(cell);
    range.collapse(true);
    sel.removeAllRanges();
    sel.addRange(range);
  }
  function current_cell(){
    var sel=window.getSelection();
    var range=sel&&sel.rangeCount>0?sel.getRangeAt(0):saved_range;
    if(!range){return null;}
    for(var node=range.startContainer;node&&node!==ed;node=node.parentNode){
      if(node.nodeType===1&&(node.tagName==='TD'||node.tagName==='TH')){
        var table=node.closest('table');
        if(!table||!ed.contains(table)||!node.parentElement){return null;}
        return {table:table,cell:node,row:node.parentElement.rowIndex,column:node.cellIndex};
      }
    }
    return null;
  }
  function dimensions(table){
    var columns=0;
    for(var i=0;i<table.rows.length;i++){columns=Math.max(columns,table.rows[i].cells.length);}
    return {rows:table.rows.length,columns:columns};
  }
  function shows_borders(table){
    var attr=table.getAttribute('border');
    if(attr!==null&&attr.trim()!==''){return attr.trim()!=='0';}
    var cell=table.querySelector('td,th');
    if(!cell){return false;}
    var kind=cell.style.borderTopStyle||cell.style.borderStyle;
    var width=(cell.style.borderTopWidth||cell.style.borderWidth||'').trim();
    if(!kind||kind==='none'||kind==='hidden'){return false;}
    return !/^0(?:px)?$/.test(width);
  }
  function set_borders(table,borders){
    var border=borders?table_border:'none';
    table.setAttribute('border',borders?'1':'0');
    table.style.borderCollapse='collapse';
    table.style.border=border;
    var cells=table.querySelectorAll('td,th');
    for(var i=0;i<cells.length;i++){cells[i].style.border=border;}
  }
  function empty_like(source){
    var cell=source.cloneNode(false);
    cell.removeAttribute('rowspan');
    cell.removeAttribute('colspan');
    cell.innerHTML='<br>';
    return cell;
  }
  function add_row(table,index,after){
    var reference=table.rows[index];
    if(!reference||table.rows.length>=max_rows){return false;}
    var row=reference.cloneNode(false);
    var columns=Math.max(dimensions(table).columns,1);
    for(var i=0;i<columns;i++){
      var source=reference.cells[i]||reference.cells[0];
      row.appendChild(source?empty_like(source):document.createElement('td'));
    }
    reference.parentNode.insertBefore(row,after?reference.nextSibling:reference);
    return true;
  }
  function add_column(table,index,after){
    if(dimensions(table).columns>=max_columns){return false;}
    for(var r=0;r<table.rows.length;r++){
      var row=table.rows[r];
      if(row.cells.length===0){continue;}
      var at=Math.min(index,row.cells.length-1);
      var reference=row.cells[at];
      var cell=empty_like(reference);
      if(after&&at===index){row.insertBefore(cell,reference.nextSibling);}
      else if(after){row.appendChild(cell);}
      else{row.insertBefore(cell,reference);}
    }
    return true;
  }
  function cell_at(table,row,column){
    if(table.rows.length===0){return null;}
    var target=table.rows[Math.min(Math.max(row,0),table.rows.length-1)];
    if(target.cells.length===0){return null;}
    return target.cells[Math.min(Math.max(column,0),target.cells.length-1)];
  }
  function remove_table(table){
    var removed=false;
    if(select_node(table)){try{removed=document.execCommand('delete',false,null);}catch(e){removed=false;}}
    if(!removed||table.isConnected){table.remove();}
  }
  function replace_table(table,next){
    next.setAttribute(pending_attr,'1');
    var placed=select_node(table)&&insert_html(next.outerHTML)?take_pending():null;
    if(placed){if(table.isConnected&&table!==placed){table.remove();}return placed;}
    next.removeAttribute(pending_attr);
    if(table.isConnected){table.parentNode.replaceChild(next,table);}
    return next;
  }
  window.aster_table_insert=function(rows,columns,borders){
    restore();
    var sel=window.getSelection();
    var range=sel&&sel.rangeCount>0?sel.getRangeAt(0):null;
    if(!range||!ed.contains(range.commonAncestorContainer)){
      var end=document.createRange();
      end.selectNodeContents(ed);
      end.collapse(false);
      if(sel){sel.removeAllRanges();sel.addRange(end);}
    }
    var html=table_html(rows,columns,!!borders)+'<div><br></div>';
    var table=insert_html(html)?take_pending():null;
    if(!table){
      var holder=document.createElement('div');
      holder.innerHTML=html;
      while(holder.firstChild){ed.appendChild(holder.firstChild);}
      table=take_pending();
    }
    if(table){var first=cell_at(table,0,0);if(first){caret_in(first);}}
    push();
  };
  window.aster_table_state=function(){
    var position=current_cell();
    if(!position){return JSON.stringify({in_table:false});}
    var size=dimensions(position.table);
    return JSON.stringify({in_table:true,borders:shows_borders(position.table),rows:size.rows,columns:size.columns,merged:has_merged(position.table)});
  };
  function has_merged(table){
    var all=table.querySelectorAll('td,th');
    for(var i=0;i<all.length;i++){if(all[i].closest('table')===table&&(all[i].colSpan>1||all[i].rowSpan>1)){return true;}}
    return false;
  }
  var structure_actions=['row_above','row_below','column_before','column_after','delete_row','delete_column'];
  window.aster_table_edit=function(action){
    restore();
    var position=current_cell();
    if(!position){return;}
    var table=position.table;
    if(structure_actions.indexOf(action)>=0&&has_merged(table)){return;}
    if(action==='delete_table'||(action==='delete_row'&&table.rows.length<=1)||(action==='delete_column'&&dimensions(table).columns<=1)){
      remove_table(table);
      push();
      return;
    }
    var next=table.cloneNode(true);
    var row=position.row;
    var column=position.column;
    var changed=true;
    if(action==='row_above'){changed=add_row(next,row,false);}
    else if(action==='row_below'){changed=add_row(next,row,true);row+=1;}
    else if(action==='column_before'){changed=add_column(next,column,false);}
    else if(action==='column_after'){changed=add_column(next,column,true);column+=1;}
    else if(action==='delete_row'){if(!next.rows[row]){return;}next.rows[row].remove();}
    else if(action==='delete_column'){
      for(var r=0;r<next.rows.length;r++){var cell=next.rows[r].cells[column];if(cell){cell.remove();}}
      for(var q=next.rows.length-1;q>=0;q--){if(next.rows[q].cells.length===0){next.rows[q].remove();}}
    }
    else if(action==='borders_on'||action==='borders_off'){set_borders(next,action==='borders_on');}
    else{return;}
    if(!changed){return;}
    var placed=replace_table(table,next);
    var target=cell_at(placed,row,column);
    if(target){caret_in(target);}
    push();
  };
  ed.addEventListener('keydown',function(event){
    if(event.key!=='Tab'||event.altKey||event.ctrlKey||event.metaKey){return;}
    var position=current_cell();
    if(!position){return;}
    event.preventDefault();
    var cells=[];
    var all=position.table.querySelectorAll('td,th');
    for(var i=0;i<all.length;i++){if(all[i].closest('table')===position.table){cells.push(all[i]);}}
    var index=cells.indexOf(position.cell)+(event.shiftKey?-1:1);
    if(index>=0&&index<cells.length){caret_in(cells[index]);return;}
    if(event.shiftKey||position.table.rows.length>=max_rows){return;}
    var last=position.table.rows.length-1;
    var next=position.table.cloneNode(true);
    if(!add_row(next,last,true)){return;}
    var placed=replace_table(position.table,next);
    var target=cell_at(placed,last+1,0);
    if(target){caret_in(target);}
    push();
  });
  window.aster_link=function(url){ed.focus();try{document.execCommand('createLink',false,url);}catch(e){}push();};
  window.aster_clear=function(){ed.focus();try{document.execCommand('removeFormat',false,null);}catch(e){}push();};
  var content_selector='img,br,hr,table,video';
  function is_lit(el){
    if(el.tagName==='MARK'){return true;}
    var s=el.style;
    if(!s){return false;}
    return s.getPropertyValue('background-color').trim()!==''||s.getPropertyValue('background').trim()!=='';
  }
  function unwrap(el){
    var p=el.parentNode;
    if(!p){return;}
    while(el.firstChild){p.insertBefore(el.firstChild,el);}
    p.removeChild(el);
  }
  function strip_one(el){
    if(el.tagName==='MARK'){unwrap(el);return;}
    el.style.removeProperty('background-color');
    el.style.removeProperty('background');
    if(!(el.getAttribute('style')||'').trim()){el.removeAttribute('style');}
    if(el.tagName==='SPAN'&&el.attributes.length===0){unwrap(el);}
  }
  function strip_all(node){
    var list=node.querySelectorAll('*');
    for(var i=list.length-1;i>=0;i--){if(is_lit(list[i])){strip_one(list[i]);}}
  }
  function has_content(node){
    if((node.textContent||'').length>0){return true;}
    if(node.nodeType===1&&node.matches(content_selector)){return true;}
    return !!(node.querySelector&&node.querySelector(content_selector));
  }
  function element_of(node){return node.nodeType===1?node:node.parentNode;}
  function lit_ancestors(node){
    var out=[];
    for(var el=element_of(node);el&&el!==ed&&ed.contains(el);el=el.parentNode){if(is_lit(el)){out.push(el);}}
    return out;
  }
  window.aster_unhighlight=function(){
    ed.focus();
    var sel=window.getSelection();
    if(!sel||sel.rangeCount===0){return;}
    var range=sel.getRangeAt(0);
    var common=range.commonAncestorContainer;
    if(common!==ed&&!ed.contains(common)){return;}
    if(range.collapsed){
      var around=lit_ancestors(range.startContainer);
      for(var a=0;a<around.length;a++){strip_one(around[a]);}
      push();
      return;
    }
    var lit=lit_ancestors(common);
    var top=lit.length?lit[lit.length-1]:null;
    var selected=range.extractContents();
    var output=selected;
    var first=null;
    var last=null;
    if(top&&top.parentNode){
      var after_range=document.createRange();
      after_range.setStart(range.startContainer,range.startOffset);
      after_range.setEnd(top,top.childNodes.length);
      var after=after_range.extractContents();
      var path=[];
      for(var el=element_of(common);el;el=el.parentNode){path.unshift(el);if(el===top){break;}}
      output=document.createDocumentFragment();
      var holder=output;
      for(var k=0;k<path.length;k++){var copy=path[k].cloneNode(false);holder.appendChild(copy);holder=copy;}
      holder.appendChild(selected);
      strip_all(output);
      var parent=top.parentNode;
      if(has_content(after)){var tail=top.cloneNode(false);tail.appendChild(after);parent.insertBefore(tail,top.nextSibling);}
      first=output.firstChild;
      last=output.lastChild;
      parent.insertBefore(output,top.nextSibling);
      if(!has_content(top)){parent.removeChild(top);}
    }else{
      strip_all(output);
      first=output.firstChild;
      last=output.lastChild;
      range.insertNode(output);
    }
    if(first){
      var next=document.createRange();
      next.setStartBefore(first);
      next.setEndAfter(last);
      sel.removeAllRanges();
      sel.addRange(next);
    }
    push();
  };
})();
"""

internal fun build_signature_document(seed_html: String, nonce: String): String =
    "<!DOCTYPE html><html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; " +
        "img-src data:; style-src 'unsafe-inline'; font-src data:; script-src 'nonce-$nonce'; " +
        "connect-src 'none'; base-uri 'none'; form-action 'none'; frame-src 'none'; object-src 'none'\">" +
        "<style>html,body{margin:0;padding:0;background:#ffffff;}" +
        "#aster_signature_editor{min-height:100%;padding:12px;outline:none;" +
        "font-family:sans-serif;font-size:14px;color:#222222;word-wrap:break-word;}" +
        "#aster_signature_editor img{max-width:100%;height:auto;}" +
        "#aster_signature_editor table{border-collapse:collapse;max-width:100%;}" +
        "#aster_signature_editor td,#aster_signature_editor th{min-width:48px;}" +
        "#aster_signature_editor table[border=\"0\"] td,#aster_signature_editor table[border=\"0\"] th" +
        "{outline:1px dashed #c4c4c4;outline-offset:-1px;}</style>" +
        "</head><body>" +
        "<div id=\"aster_signature_editor\" contenteditable=\"true\">" + seed_html + "</div>" +
        "<script nonce=\"$nonce\">" + signature_editor_script + "</script>" +
        "</body></html>"

@Composable
fun signature_rich_editor(
    initial_html: String,
    controller: signature_editor_controller,
    on_html_change: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!org.astermail.android.ui.common.remember_webview_usable()) {
        org.astermail.android.ui.common.webview_unavailable_notice(modifier)
        return
    }
    val document = remember {
        val nonce = java.util.UUID.randomUUID().toString().replace("-", "")
        build_signature_document(sanitize_signature_html(initial_html), nonce)
    }

    DisposableEffect(Unit) {
        onDispose { controller.editor_view = null }
    }

    val show_scroll_bar = !org.astermail.android.ui.theme.local_accessibility.current.hide_scrollbars
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.blockNetworkImage = true
                settings.blockNetworkLoads = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean = true

                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                    ): android.webkit.WebResourceResponse? {
                        val uri = request?.url ?: return null
                        if (request.isForMainFrame) return null
                        if (signature_editor_request_allowed(uri.scheme)) return null
                        return blocked_signature_editor_response()
                    }

                    override fun onRenderProcessGone(
                        view: android.webkit.WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?,
                    ): Boolean {
                        runCatching {
                            controller.editor_view = null
                            (view?.parent as? android.view.ViewGroup)?.removeView(view)
                            view?.destroy()
                        }
                        return true
                    }
                }
                addJavascriptInterface(signature_editor_bridge(on_html_change), "aster_bridge")
                isVerticalScrollBarEnabled = show_scroll_bar
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.WHITE)
                loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
                controller.editor_view = this
            }
        },
        update = { view -> controller.editor_view = view },
        onRelease = { view ->
            runCatching {
                view.stopLoading()
                view.removeJavascriptInterface("aster_bridge")
                view.destroy()
            }
        },
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
    var family_open by remember { mutableStateOf(false) }
    var table_insert_open by remember { mutableStateOf(false) }
    var table_state by remember { mutableStateOf<signature_table_state?>(null) }
    var text_color_open by remember { mutableStateOf(false) }
    var highlight_open by remember { mutableStateOf(false) }
    var link_open by remember { mutableStateOf(false) }
    var link_value by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AsterShapes.control)
            .background(field_surface_color(colors), AsterShapes.control)
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
        signature_format_button(TablerIcons.Typography, stringResource(R.string.signature_font_family)) {
            family_open = true
        }
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
        signature_format_button(TablerIcons.Table, stringResource(R.string.table_insert)) {
            controller.request_table_state { state ->
                if (state.in_table) table_state = state else table_insert_open = true
            }
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

    if (family_open) {
        signature_font_family_dialog(
            on_dismiss = { family_open = false },
            on_pick = { stack ->
                controller.set_font_family(stack)
                family_open = false
            },
        )
    }

    if (table_insert_open) {
        signature_table_insert_dialog(
            on_dismiss = { table_insert_open = false },
            on_insert = { rows, columns, borders ->
                controller.insert_table(rows, columns, borders)
                table_insert_open = false
            },
        )
    }

    table_state?.let { state ->
        signature_table_options_dialog(
            state = state,
            on_dismiss = { table_state = null },
            on_action = { action ->
                controller.edit_table(action)
                table_state = null
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
            remove_label = stringResource(R.string.remove_highlight),
            on_remove = {
                controller.remove_background_color()
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
private fun signature_font_family_dialog(on_dismiss: () -> Unit, on_pick: (String) -> Unit) {
    val colors = AsterMaterial.colors
    val options = listOf(
        signature_font_family(stringResource(R.string.signature_font_family_default), signature_default_font_family),
    ) + signature_font_families
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.signature_font_family),
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    val label = if (option.is_monospace) {
                        stringResource(R.string.signature_font_family_monospace)
                    } else {
                        option.label
                    }
                    Text(
                        text = label,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontFamily = signature_preview_family(option.stack),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { on_pick(option.stack) }
                            .padding(vertical = AsterSpacing.md),
                    )
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

private fun signature_preview_family(stack: String): androidx.compose.ui.text.font.FontFamily? = when {
    stack.endsWith("monospace") -> androidx.compose.ui.text.font.FontFamily.Monospace
    stack.endsWith(", serif") || stack == "Georgia, serif" -> androidx.compose.ui.text.font.FontFamily.Serif
    stack.endsWith("sans-serif") -> androidx.compose.ui.text.font.FontFamily.SansSerif
    else -> null
}

@Composable
private fun signature_table_stepper(
    label: String,
    decrease_label: String,
    increase_label: String,
    value: Int,
    max: Int,
    on_change: (Int) -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = colors.text_primary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        signature_format_button(TablerIcons.Minus, decrease_label, enabled = value > 1) {
            on_change((value - 1).coerceAtLeast(1))
        }
        Text(
            text = value.toString(),
            color = colors.text_primary,
            fontSize = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        signature_format_button(TablerIcons.Plus, increase_label, enabled = value < max) {
            on_change((value + 1).coerceAtMost(max))
        }
    }
}

@Composable
private fun signature_borders_row(checked: Boolean, on_change: (Boolean) -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { on_change(!checked) }
            .padding(vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.table_show_borders),
            color = colors.text_primary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        AsterSwitch(checked = checked, onCheckedChange = on_change)
    }
}

@Composable
private fun signature_table_insert_dialog(on_dismiss: () -> Unit, on_insert: (Int, Int, Boolean) -> Unit) {
    var rows by remember { mutableStateOf(2) }
    var columns by remember { mutableStateOf(2) }
    var borders by remember { mutableStateOf(true) }
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.table_insert),
        body = {
            Column(modifier = Modifier.fillMaxWidth()) {
                signature_table_stepper(
                    stringResource(R.string.table_rows),
                    stringResource(R.string.table_remove_row),
                    stringResource(R.string.table_add_row),
                    rows,
                    signature_table_max_rows,
                ) { rows = it }
                signature_table_stepper(
                    stringResource(R.string.table_columns),
                    stringResource(R.string.table_remove_column),
                    stringResource(R.string.table_add_column),
                    columns,
                    signature_table_max_columns,
                ) {
                    columns = it
                }
                signature_borders_row(borders) { borders = it }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            AsterDialogPrimaryButton(
                label = stringResource(R.string.insert),
                onClick = { on_insert(rows, columns, borders) },
                modifier = Modifier.weight(1f),
            )
        },
    )
}

@Composable
private fun signature_table_options_dialog(
    state: signature_table_state,
    on_dismiss: () -> Unit,
    on_action: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val labels = mapOf(
        "row_above" to stringResource(R.string.table_insert_row_above),
        "row_below" to stringResource(R.string.table_insert_row_below),
        "column_before" to stringResource(R.string.table_insert_column_before),
        "column_after" to stringResource(R.string.table_insert_column_after),
        "delete_row" to stringResource(R.string.table_delete_row),
        "delete_column" to stringResource(R.string.table_delete_column),
        "delete_table" to stringResource(R.string.table_delete),
    )
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.table_options),
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                signature_borders_row(state.borders) { checked ->
                    on_action(if (checked) "borders_on" else "borders_off")
                }
                signature_table_actions.forEach { action ->
                    val enabled = when {
                        state.merged && action in signature_structure_actions -> false
                        action == "row_above" || action == "row_below" -> state.rows < signature_table_max_rows
                        action == "column_before" || action == "column_after" ->
                            state.columns < signature_table_max_columns
                        else -> true
                    }
                    val destructive = action.startsWith("delete")
                    Text(
                        text = labels.getValue(action),
                        color = when {
                            !enabled -> colors.text_muted
                            destructive -> colors.danger
                            else -> colors.text_primary
                        },
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { on_action(action) }
                            .padding(vertical = AsterSpacing.md),
                    )
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
private fun signature_color_dialog(
    title: String,
    on_dismiss: () -> Unit,
    on_pick: (String) -> Unit,
    remove_label: String? = null,
    on_remove: (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val swatch_ring_color = if (colors.is_dark) {
        Color.White.copy(alpha = 0.28f)
    } else {
        Color.Black.copy(alpha = 0.16f)
    }
    AsterDialog(
        on_dismiss = on_dismiss,
        title = title,
        body = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                if (remove_label != null && on_remove != null) {
                    Text(
                        text = remove_label,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AsterShapes.control)
                            .border(1.dp, swatch_ring_color, AsterShapes.control)
                            .clickable(onClick = on_remove)
                            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                    )
                }
                signature_preset_colors.chunked(8).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                    ) {
                        row.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(1.5.dp, swatch_ring_color, CircleShape)
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
    enabled: Boolean = true,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = on_click)
            .alpha(if (enabled) 1f else 0.4f),
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
