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

package org.astermail.android.ui.compose

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

internal data class draft_quote_split(
    val body_html: String,
    val quote_block: String,
    val preview_html: String,
    val is_forward: Boolean,
)

private const val DRAFT_QUOTE_SELECTOR = "div.aster_quote"

private const val PREVIEW_HIDDEN_CLASS_SELECTOR =
    ".aster_quote, .gmail_quote, .aster_quote_attr, .gmail_attr, .protonmail_quote, .yahoo_quoted, .moz-cite-prefix"

private val TRAILING_BREAKS_RE = Regex("(?i)(?:\\s|<br\\s*/?>)+$")

internal fun split_draft_quote(html: String): draft_quote_split? {
    if (!html.contains("aster_quote", ignoreCase = true)) return null
    val doc = Jsoup.parseBodyFragment(html)
    doc.outputSettings().prettyPrint(false)
    val block = doc.body().select(DRAFT_QUOTE_SELECTOR)
        .firstOrNull { it.parents().none { parent -> parent.`is`(DRAFT_QUOTE_SELECTOR) } }
        ?: return null
    val quote_block = "<br><br>" + block.outerHtml()
    val is_forward = block.selectFirst(".aster_forward_body") != null
    val preview = block.clone()
    preview.select(PREVIEW_HIDDEN_CLASS_SELECTOR).forEach { it.removeAttr("class") }
    remove_leading_breaks(block)
    block.remove()
    return draft_quote_split(
        body_html = TRAILING_BREAKS_RE.replace(doc.body().html(), ""),
        quote_block = quote_block,
        preview_html = preview.html(),
        is_forward = is_forward,
    )
}

private fun remove_leading_breaks(block: Element) {
    while (true) {
        val previous = block.previousSibling() ?: return
        val is_break = (previous is Element && previous.tagName() == "br") ||
            (previous is TextNode && previous.isBlank)
        if (!is_break) return
        previous.remove()
    }
}
