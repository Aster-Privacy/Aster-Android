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

package org.astermail.android.mail

private val non_rendered_tags = setOf("style", "script", "head", "title", "noscript", "template")

private val script_like_tags = setOf("script", "iframe", "object", "applet")

private fun read_tag_name(html: String, start: Int): String {
    var i = start
    val sb = StringBuilder()
    while (i < html.length) {
        val c = html[i]
        if (c.isLetterOrDigit() || c == '-' || c == ':') {
            sb.append(c)
            i++
        } else {
            break
        }
    }
    return sb.toString().lowercase()
}

private fun index_of_tag_end(html: String, start: Int): Int {
    var i = start
    var quote = '\u0000'
    while (i < html.length) {
        val c = html[i]
        when {
            quote != '\u0000' -> if (c == quote) quote = '\u0000'
            c == '"' || c == '\'' -> quote = c
            c == '>' -> return i
        }
        i++
    }
    return -1
}

private fun index_of_closing_tag(html: String, name: String, from: Int): Int {
    var i = from
    while (i < html.length) {
        val lt = html.indexOf('<', i)
        if (lt < 0) return -1
        if (lt + 1 < html.length && html[lt + 1] == '/' && read_tag_name(html, lt + 2) == name) return lt
        i = lt + 1
    }
    return -1
}

private fun index_of_body_start(html: String, from: Int): Int {
    var i = from
    while (i < html.length) {
        val lt = html.indexOf('<', i)
        if (lt < 0) return -1
        if (lt + 1 < html.length && html[lt + 1] != '/' && read_tag_name(html, lt + 1) == "body") return lt
        i = lt + 1
    }
    return -1
}

fun strip_non_rendered_blocks(html: String): String = strip_element_blocks(html, non_rendered_tags)

fun strip_script_like_blocks(html: String): String = strip_element_blocks(html, script_like_tags)

private fun strip_element_blocks(html: String, names: Set<String>): String {
    if (html.isEmpty() || html.indexOf('<') < 0) return html

    val out = StringBuilder(html.length)
    var i = 0

    while (i < html.length) {
        val lt = html.indexOf('<', i)

        if (lt < 0) {
            out.append(html, i, html.length)
            break
        }

        out.append(html, i, lt)

        if (html.startsWith("<!--", lt)) {
            val end = html.indexOf("-->", lt + 4)
            if (end < 0) break
            out.append(html, lt, end + 3)
            i = end + 3
            continue
        }

        val is_close = lt + 1 < html.length && html[lt + 1] == '/'
        val name_start = if (is_close) lt + 2 else lt + 1
        val name = read_tag_name(html, name_start)
        val tag_end = index_of_tag_end(html, name_start)

        if (tag_end < 0) {
            if (name.isEmpty() || is_close || name !in names) out.append(html, lt, html.length)
            break
        }

        if (!is_close && name.isNotEmpty() && name in names) {
            out.append(' ')
            val close = index_of_closing_tag(html, name, tag_end + 1)

            if (close >= 0) {
                val close_end = index_of_tag_end(html, close + 2)
                i = if (close_end < 0) html.length else close_end + 1
                continue
            }

            val body = index_of_body_start(html, tag_end + 1)
            i = if (body >= 0) body else html.length
            continue
        }

        out.append(html, lt, tag_end + 1)
        i = tag_end + 1
    }

    return out.toString()
}

fun degraded_email_html(html: String): String {
    val text = html_to_plain_text(html)

    if (text.isBlank()) return ""

    val escaped = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    return escaped
        .split("\n\n")
        .filter { it.isNotBlank() }
        .joinToString("") { "<p>" + it.replace("\n", "<br>") + "</p>" }
}
