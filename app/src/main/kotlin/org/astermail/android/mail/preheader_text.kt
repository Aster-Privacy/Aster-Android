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

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

const val PREHEADER_HTML_SCAN_CAP = 65536

private const val PREHEADER_MIN_CHARS = 4

private const val PREHEADER_MAX_CHARS = 600

private const val PREHEADER_MAX_DEPTH = 64

private val FILLER_RE = Regex(
    "[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\u2066-\\u2069\\uFEFF\\u00AD\\u034F" +
        "\\u115F\\u1160\\u17B4\\u17B5\\u180E\\u3164\\uFFA0\\uFFF9-\\uFFFC]",
)

private val HIDDEN_STYLE_PATTERNS = listOf(
    Regex("display\\s*:\\s*none", RegexOption.IGNORE_CASE),
    Regex("visibility\\s*:\\s*hidden", RegexOption.IGNORE_CASE),
    Regex("mso-hide\\s*:\\s*all", RegexOption.IGNORE_CASE),
    Regex("opacity\\s*:\\s*0(?!\\.[1-9]|[1-9])", RegexOption.IGNORE_CASE),
    Regex("font-size\\s*:\\s*0(?!\\.[1-9]|[1-9])", RegexOption.IGNORE_CASE),
    Regex("line-height\\s*:\\s*0(?!\\.[1-9]|[1-9])", RegexOption.IGNORE_CASE),
    Regex("max-height\\s*:\\s*0(?!\\.[1-9]|[1-9])", RegexOption.IGNORE_CASE),
    Regex("max-width\\s*:\\s*0(?!\\.[1-9]|[1-9])", RegexOption.IGNORE_CASE),
    Regex("(?:^|[;{\\s])height\\s*:\\s*0(?!\\.[1-9]|[1-9])", RegexOption.IGNORE_CASE),
)

private val HIDDEN_NAME_RE =
    Regex("(^|[\\s_-])(preheader|preview[-_]?text)([\\s_-]|$)", RegexOption.IGNORE_CASE)

private val CSS_CLASS_RE = Regex("\\.([A-Za-z0-9_-]+)")

private val CSS_ID_RE = Regex("#([A-Za-z0-9_-]+)")

private val ALPHANUMERIC_RE = Regex("[\\p{L}\\p{N}]")

private class HiddenSelectors(
    val classes: Set<String>,
    val ids: Set<String>,
)

fun strip_preview_filler(value: String): String =
    FILLER_RE.replace(value, "").replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

private fun drop_at_rule_groups(css: String): String {
    val result = StringBuilder()
    var index = 0

    while (index < css.length) {
        val at = css.indexOf('@', index)

        if (at == -1) {
            result.append(css, index, css.length)
            break
        }

        val open = css.indexOf('{', at)

        if (open == -1) {
            result.append(css, index, at)
            break
        }

        result.append(css, index, at)

        var depth = 0
        var cursor = open

        while (cursor < css.length) {
            if (css[cursor] == '{') {
                depth += 1
            } else if (css[cursor] == '}') {
                depth -= 1
                if (depth == 0) break
            }
            cursor += 1
        }

        index = if (cursor >= css.length) css.length else cursor + 1
    }

    return result.toString()
}

private fun style_block_contents(html: String): List<String> {
    val blocks = mutableListOf<String>()
    var from = 0
    while (true) {
        val open = html.indexOf("<style", from, ignoreCase = true)
        if (open < 0) return blocks
        val name_end = open + 6
        if (name_end < html.length && (html[name_end].isLetterOrDigit() || html[name_end] == '_')) {
            from = open + 1
            continue
        }
        val tag_end = html.indexOf('>', name_end)
        if (tag_end < 0) return blocks
        val close = html.indexOf("</style>", tag_end + 1, ignoreCase = true)
        if (close < 0) return blocks
        blocks.add(html.substring(tag_end + 1, close))
        from = close + 8
    }
}

private fun index_of_brace(css: String, from: Int): Int {
    for (index in from until css.length) if (css[index] == '{' || css[index] == '}') return index
    return -1
}

private fun css_rules(css: String): List<Pair<String, String>> {
    val rules = mutableListOf<Pair<String, String>>()
    var pos = 0
    while (pos < css.length) {
        val open = index_of_brace(css, pos)
        if (open < 0) break
        if (css[open] == '}' || open == pos) {
            pos = open + 1
            continue
        }
        val close = index_of_brace(css, open + 1)
        if (close < 0) break
        if (css[close] == '{') {
            pos = open + 1
            continue
        }
        rules.add(css.substring(pos, open) to css.substring(open + 1, close))
        pos = close + 1
    }
    return rules
}

private fun collect_hidden_selectors(html: String): HiddenSelectors {
    val classes = mutableSetOf<String>()
    val ids = mutableSetOf<String>()

    for (block in style_block_contents(html)) {
        val css = drop_at_rule_groups(block)

        for ((rule_selectors, declarations) in css_rules(css)) {
            if (HIDDEN_STYLE_PATTERNS.none { it.containsMatchIn(declarations) }) continue

            for (selector in rule_selectors.split(",")) {
                val target = selector.trim().split(Regex("[\\s>+~]+")).lastOrNull() ?: continue

                CSS_CLASS_RE.findAll(target).forEach { classes.add(it.groupValues[1].lowercase(java.util.Locale.ROOT)) }
                CSS_ID_RE.findAll(target).forEach { ids.add(it.groupValues[1].lowercase(java.util.Locale.ROOT)) }
            }
        }
    }

    return HiddenSelectors(classes, ids)
}

private fun is_hidden_element(element: Element, selectors: HiddenSelectors): Boolean {
    if (element.hasAttr("hidden")) return true

    val class_name = element.attr("class")
    val id = element.attr("id")

    if (HIDDEN_NAME_RE.containsMatchIn(class_name)) return true
    if (HIDDEN_NAME_RE.containsMatchIn(id)) return true

    if (selectors.classes.isNotEmpty()) {
        for (token in class_name.split(Regex("\\s+"))) {
            if (token.isNotEmpty() && selectors.classes.contains(token.lowercase(java.util.Locale.ROOT))) return true
        }
    }

    if (id.isNotEmpty() && selectors.ids.contains(id.lowercase(java.util.Locale.ROOT))) return true

    val style = element.attr("style")

    if (style.isEmpty()) return false

    return HIDDEN_STYLE_PATTERNS.any { it.containsMatchIn(style) }
}

private fun collect_leading_hidden_text(
    node: Element,
    selectors: HiddenSelectors,
    parts: MutableList<String>,
    depth: Int = 0,
): Boolean {
    if (depth >= PREHEADER_MAX_DEPTH) return true

    for (child in node.childNodes()) {
        if (child is TextNode) {
            if (strip_preview_filler(child.text()).isNotEmpty()) return true
            continue
        }

        if (child !is Element) continue

        val text = strip_preview_filler(child.text())

        if (text.isEmpty()) continue

        if (is_hidden_element(child, selectors)) {
            parts.add(text)
            if (parts.joinToString(" ").length >= PREHEADER_MAX_CHARS) return true
            continue
        }

        if (collect_leading_hidden_text(child, selectors, parts, depth + 1)) return true
    }

    return false
}

fun extract_preheader_text(html: String): String {
    if (html.isEmpty()) return ""

    return runCatching {
        val scanned = if (html.length > PREHEADER_HTML_SCAN_CAP) {
            html.substring(0, PREHEADER_HTML_SCAN_CAP)
        } else {
            html
        }
        val selectors = collect_hidden_selectors(scanned)
        val document = Jsoup.parse(scanned)

        document.select("script, style, noscript, template").remove()

        val body = document.body() ?: return@runCatching ""
        val parts = mutableListOf<String>()

        collect_leading_hidden_text(body, selectors, parts)

        val text = strip_preview_filler(decode_html_entities_for_preview(parts.joinToString(" ")))

        if (text.length < PREHEADER_MIN_CHARS) return@runCatching ""
        if (!ALPHANUMERIC_RE.containsMatchIn(text)) return@runCatching ""

        text
    }.getOrDefault("")
}
