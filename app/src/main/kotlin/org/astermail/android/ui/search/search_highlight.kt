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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.Normalizer

internal const val SEARCH_HIT_CLASS = "aster_search_hit"
internal const val SEARCH_HIT_ID_PREFIX = "aster_hit_"
internal const val MAX_SEARCH_HIGHLIGHT_TERMS = 16
internal const val MAX_SEARCH_HITS_PER_BODY = 999

private val search_mark_regex = Regex("""\p{M}+""")
private val search_skip_tags = setOf(
    "script", "style", "textarea", "title", "noscript", "head", "svg", "math",
    "template", "select", "option", "iframe", "object", "embed", "mark",
)
private val search_sender_operator_keys = setOf("from", "contact")

internal data class search_highlight_terms(
    val body: List<String>,
    val subject: List<String>,
    val sender: List<String>,
) {
    val is_empty: Boolean get() = body.isEmpty() && subject.isEmpty() && sender.isEmpty()
}

internal data class search_hit_range(val start: Int, val end: Int)

internal class folded_search_text(
    val folded: String,
    private val starts: IntArray,
    private val ends: IntArray,
) {
    fun original_start(folded_index: Int): Int = starts[folded_index]
    fun original_end(folded_end: Int): Int = ends[folded_end - 1]
}

internal fun search_highlight_terms_for(parsed: ParsedQuery): search_highlight_terms {
    val positive = parsed.operators.filter { !it.negated }
    val text_values = positive.filter { it.key == TEXT_OPERATOR_KEY }.map { it.value }
    val base = parsed.terms + text_values
    return search_highlight_terms(
        body = clean_search_terms(base),
        subject = clean_search_terms(base + positive.filter { it.key == "subject" }.map { it.value }),
        sender = clean_search_terms(base + positive.filter { it.key in search_sender_operator_keys }.map { it.value }),
    )
}

internal fun clean_search_terms(raw: List<String>): List<String> =
    raw.asSequence()
        .map { fold_search_text(it.replace("\"", "").trim()).folded.trim() }
        .filter { term_is_highlightable(it) }
        .distinct()
        .sortedByDescending { it.length }
        .take(MAX_SEARCH_HIGHLIGHT_TERMS)
        .toList()

private fun term_is_highlightable(term: String): Boolean {
    if (term.isEmpty()) return false
    if (term.length >= 2) return true
    val code = term[0].code
    return code >= 0x2E80
}

internal fun fold_search_text(text: String): folded_search_text {
    val out = StringBuilder(text.length)
    val starts = IntArray(text.length * 2 + 4)
    val ends = IntArray(text.length * 2 + 4)
    var count = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val next = i + Character.charCount(cp)
        val piece = fold_code_point(cp)
        if (piece.isEmpty() && count > 0 && ends[count - 1] == i) ends[count - 1] = next
        for (ch in piece) {
            if (count == starts.size) break
            out.append(ch)
            starts[count] = i
            ends[count] = next
            count++
        }
        i = next
    }
    return folded_search_text(out.toString(), starts.copyOf(count), ends.copyOf(count))
}

private fun fold_code_point(cp: Int): String {
    if (cp < 128) return Character.toChars(Character.toLowerCase(cp)).concatToString()
    val raw = String(Character.toChars(cp))
    if (Character.getType(cp) == Character.NON_SPACING_MARK.toInt() ||
        Character.getType(cp) == Character.ENCLOSING_MARK.toInt() ||
        Character.getType(cp) == Character.COMBINING_SPACING_MARK.toInt()
    ) {
        return ""
    }
    val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
    return search_mark_regex.replace(decomposed, "").lowercase().replace('ς', 'σ')
}

internal fun find_search_hit_ranges(text: String, terms: List<String>): List<search_hit_range> {
    if (text.isEmpty() || terms.isEmpty()) return emptyList()
    val folded = fold_search_text(text)
    val haystack = folded.folded
    val raw = ArrayList<search_hit_range>()
    for (term in terms) {
        val needle = fold_search_text(term).folded
        if (needle.isEmpty()) continue
        var from = 0
        while (from <= haystack.length - needle.length) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) break
            raw.add(search_hit_range(folded.original_start(at), folded.original_end(at + needle.length)))
            from = at + needle.length
        }
    }
    return merge_search_hit_ranges(raw)
}

internal fun merge_search_hit_ranges(ranges: List<search_hit_range>): List<search_hit_range> {
    if (ranges.size < 2) return ranges
    val sorted = ranges.sortedWith(compareBy({ it.start }, { -it.end }))
    val merged = ArrayList<search_hit_range>(sorted.size)
    var current = sorted[0]
    for (index in 1 until sorted.size) {
        val next = sorted[index]
        current = if (next.start <= current.end) {
            search_hit_range(current.start, maxOf(current.end, next.end))
        } else {
            merged.add(current)
            next
        }
    }
    merged.add(current)
    return merged
}

internal fun count_search_hits(text: String, terms: List<String>): Int =
    find_search_hit_ranges(text, terms).size

internal fun highlight_annotated(
    text: AnnotatedString,
    terms: List<String>,
    style: SpanStyle,
    current_style: SpanStyle? = null,
    current_index: Int = -1,
): AnnotatedString {
    val ranges = find_search_hit_ranges(text.text, terms)
    if (ranges.isEmpty()) return text
    return buildAnnotatedString {
        append(text)
        ranges.forEachIndexed { index, range ->
            val chosen = if (index == current_index && current_style != null) current_style else style
            addStyle(chosen, range.start, range.end)
        }
    }
}

internal data class search_snippet(val text: String, val shifted: Boolean)

internal fun search_snippet_window(
    text: String,
    terms: List<String>,
    lead_chars: Int = 28,
    trigger_chars: Int = 40,
): search_snippet {
    val first = find_search_hit_ranges(text, terms).firstOrNull() ?: return search_snippet(text, false)
    if (first.start <= trigger_chars) return search_snippet(text, false)
    var start = (first.start - lead_chars).coerceAtLeast(0)
    while (start < first.start && !text[start].isWhitespace()) start++
    if (start >= first.start) start = (first.start - lead_chars).coerceAtLeast(0)
    while (start < first.start && text[start].isWhitespace()) start++
    if (start <= 0) return search_snippet(text, false)
    return search_snippet("…" + text.substring(start), true)
}

internal class marked_search_body(
    val html: String,
    val hits: Int,
    val hit_fractions: FloatArray = FloatArray(0),
    val opened_quotes: Int = 0,
) {
    fun fraction_of(local: Int): Float =
        if (local in hit_fractions.indices) hit_fractions[local] else 0f
}

internal fun mark_search_hits(
    document_html: String,
    terms: List<String>,
    accent_argb: Int,
    dark: Boolean,
    reduce_motion: Boolean,
): marked_search_body {
    if (terms.isEmpty() || document_html.isEmpty()) return marked_search_body(document_html, 0)
    val doc = Jsoup.parse(document_html, "https://mail-content.invalid/")
    doc.outputSettings().prettyPrint(false)
    val root: Element = doc.getElementById("m") ?: doc.body()
    val targets = ArrayList<TextNode>()
    collect_search_text_nodes(root, targets)
    val total_chars = targets.sumOf { it.wholeText.length }.coerceAtLeast(1)
    val fractions = ArrayList<Float>()
    var chars_before = 0
    var hits = 0
    for (node in targets) {
        val text = node.wholeText
        val node_start = chars_before
        chars_before += text.length
        if (hits >= MAX_SEARCH_HITS_PER_BODY) continue
        val ranges = find_search_hit_ranges(text, terms)
        if (ranges.isEmpty()) continue
        var cursor = 0
        for (range in ranges) {
            if (hits >= MAX_SEARCH_HITS_PER_BODY) break
            if (range.start > cursor) node.before(TextNode(text.substring(cursor, range.start)))
            val mark = Element("mark")
                .addClass(SEARCH_HIT_CLASS)
                .attr("id", SEARCH_HIT_ID_PREFIX + hits)
            mark.appendChild(TextNode(text.substring(range.start, range.end)))
            node.before(mark)
            fractions.add(((node_start + range.start).toFloat() / total_chars).coerceIn(0f, 1f))
            hits++
            cursor = range.end
        }
        if (cursor < text.length) node.before(TextNode(text.substring(cursor)))
        node.remove()
    }
    if (hits == 0) return marked_search_body(document_html, 0)
    var opened = 0
    for (details in root.select("details")) {
        if (!details.hasAttr("open") && details.selectFirst("mark.$SEARCH_HIT_CLASS") != null) {
            details.attr("open", "")
            opened++
        }
    }
    val page_dark = dark && doc.selectFirst("html")?.hasAttr("data-white") != true
    doc.head().appendElement("style").appendChild(DataNode(search_hit_css(search_hit_palette_for(accent_argb, page_dark), reduce_motion)))
    return marked_search_body(doc.outerHtml(), hits, fractions.toFloatArray(), opened)
}

private fun collect_search_text_nodes(element: Element, out: MutableList<TextNode>) {
    if (element.normalName() in search_skip_tags) return
    for (child: Node in element.childNodes()) {
        when (child) {
            is TextNode -> if (!child.isBlank) out.add(child)
            is Element -> collect_search_text_nodes(child, out)
        }
    }
}

internal data class search_hit_palette(
    val background: Int,
    val foreground: Int,
    val current_background: Int,
    val current_foreground: Int,
    val ring: Int,
)

private fun blend_rgb(top: Int, bottom: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val a = (top shr shift) and 0xFF
        val b = (bottom shr shift) and 0xFF
        return (a * t + b * (1f - t) + 0.5f).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

internal fun search_hit_palette_for(accent_argb: Int, dark: Boolean): search_hit_palette =
    if (dark) {
        search_hit_palette(
            background = blend_rgb(accent_argb, 0xFF151515.toInt(), 0.42f),
            foreground = 0xFFFFFFFF.toInt(),
            current_background = blend_rgb(accent_argb, 0xFF151515.toInt(), 0.72f),
            current_foreground = 0xFFFFFFFF.toInt(),
            ring = blend_rgb(accent_argb, 0xFFFFFFFF.toInt(), 0.75f),
        )
    } else {
        search_hit_palette(
            background = blend_rgb(accent_argb, 0xFFFFFFFF.toInt(), 0.22f),
            foreground = 0xFF111827.toInt(),
            current_background = blend_rgb(accent_argb, 0xFFFFFFFF.toInt(), 0.45f),
            current_foreground = 0xFF0B1220.toInt(),
            ring = accent_argb or (0xFF shl 24),
        )
    }

private fun css_hex(argb: Int): String = String.format(java.util.Locale.US, "#%06X", argb and 0xFFFFFF)

internal fun search_hit_css(palette: search_hit_palette, reduce_motion: Boolean): String {
    val transition = if (reduce_motion) "" else "transition:background-color .18s ease;"
    val hit = "mark.$SEARCH_HIT_CLASS.$SEARCH_HIT_CLASS"
    return "html body $hit,html body #m $hit{background:${css_hex(palette.background)}!important;" +
        "color:${css_hex(palette.foreground)}!important;-webkit-text-fill-color:${css_hex(palette.foreground)}!important;" +
        "border-radius:3px;padding:0 1px;margin:0 -1px;-webkit-box-decoration-break:clone;box-decoration-break:clone;$transition}"
}

internal fun plain_text_for_search(body: String, body_html: String?): String {
    if (body.isNotBlank()) return body
    val html = body_html ?: return ""
    if (html.isBlank()) return ""
    return runCatching { Jsoup.parse(html).text() }.getOrDefault("")
}

internal object search_highlight_handoff {
    private const val handoff_ttl_ms = 15_000L
    private var pending: Triple<String, search_highlight_terms, Long>? = null

    @Synchronized
    fun offer(email_id: String, terms: search_highlight_terms, now_ms: Long = System.currentTimeMillis()) {
        pending = if (terms.is_empty) null else Triple(email_id, terms, now_ms)
    }

    @Synchronized
    fun take(email_id: String, now_ms: Long = System.currentTimeMillis()): search_highlight_terms? {
        val held = pending ?: return null
        if (now_ms - held.third > handoff_ttl_ms) {
            pending = null
            return null
        }
        if (held.first != email_id) return null
        pending = null
        return held.second
    }
}
