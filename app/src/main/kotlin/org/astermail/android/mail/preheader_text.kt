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

private val FILLER_RE = Regex(
    "[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\u2066-\\u2069\\uFEFF\\u00AD\\u034F" +
        "\\u00A0\\u115F\\u1160\\u17B4\\u17B5\\u180E\\u3164\\uFFA0\\uFFF9-\\uFFFC]",
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

private val STYLE_BLOCK_RE =
    Regex("<style\\b[^>]*>([\\s\\S]*?)</style>", RegexOption.IGNORE_CASE)

private val CSS_RULE_RE = Regex("([^{}]+)\\{([^{}]*)\\}")

private val CSS_CLASS_RE = Regex("\\.([A-Za-z0-9_-]+)")

private val CSS_ID_RE = Regex("#([A-Za-z0-9_-]+)")

private val ALPHANUMERIC_RE = Regex("[\\p{L}\\p{N}]")

private class HiddenSelectors(
    val classes: Set<String>,
    val ids: Set<String>,
)

fun strip_preview_filler(value: String): String =
    FILLER_RE.replace(value, "").replace(Regex("\\s+"), " ").trim()

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

private fun collect_hidden_selectors(html: String): HiddenSelectors {
    val classes = mutableSetOf<String>()
    val ids = mutableSetOf<String>()

    for (block in STYLE_BLOCK_RE.findAll(html)) {
        val css = drop_at_rule_groups(block.groupValues[1])

        for (rule in CSS_RULE_RE.findAll(css)) {
            val declarations = rule.groupValues[2]

            if (HIDDEN_STYLE_PATTERNS.none { it.containsMatchIn(declarations) }) continue

            for (selector in rule.groupValues[1].split(",")) {
                val target = selector.trim().split(Regex("[\\s>+~]+")).lastOrNull() ?: continue

                CSS_CLASS_RE.findAll(target).forEach { classes.add(it.groupValues[1].lowercase()) }
                CSS_ID_RE.findAll(target).forEach { ids.add(it.groupValues[1].lowercase()) }
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
            if (token.isNotEmpty() && selectors.classes.contains(token.lowercase())) return true
        }
    }

    if (id.isNotEmpty() && selectors.ids.contains(id.lowercase())) return true

    val style = element.attr("style")

    if (style.isEmpty()) return false

    return HIDDEN_STYLE_PATTERNS.any { it.containsMatchIn(style) }
}

private fun collect_leading_hidden_text(
    node: Element,
    selectors: HiddenSelectors,
    parts: MutableList<String>,
): Boolean {
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

        if (collect_leading_hidden_text(child, selectors, parts)) return true
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

        val text = strip_preview_filler(parts.joinToString(" "))

        if (text.length < PREHEADER_MIN_CHARS) return@runCatching ""
        if (!ALPHANUMERIC_RE.containsMatchIn(text)) return@runCatching ""

        text
    }.getOrDefault("")
}
