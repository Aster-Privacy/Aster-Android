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

data class UnsubscribeInfo(
    val has_unsubscribe: Boolean = false,
    val unsubscribe_link: String? = null,
    val unsubscribe_mailto: String? = null,
    val unsubscribe_page_url: String? = null,
    val method: String = "none",
    val list_unsubscribe_header: String? = null,
    val list_unsubscribe_post: String? = null,
)

private const val QUOTES = "\"'"
private const val PATTERN_SPACE = " \t\n\u000B\u000C\r"

private fun contains_in_order(value: String, parts: List<String>): Boolean {
    var from = 0
    for (part in parts) {
        val at = value.indexOf(part, from, ignoreCase = true)
        if (at < 0) return false
        from = at + part.length
    }
    return true
}

private fun contains_opt_out(value: String, spaced: Boolean): Boolean {
    var from = 0
    while (true) {
        val at = value.indexOf("opt", from, ignoreCase = true)
        if (at < 0) return false
        val next = at + 3
        if (value.startsWith("out", next, ignoreCase = true)) return true
        if (next < value.length) {
            val gap = value[next]
            if ((gap == '-' || (spaced && gap in PATTERN_SPACE)) &&
                value.startsWith("out", next + 1, ignoreCase = true)
            ) {
                return true
            }
        }
        from = at + 1
    }
}

private val UNSUBSCRIBE_LINK_CHECKS: List<(String) -> Boolean> = listOf(
    { it.contains("unsubscribe", ignoreCase = true) },
    { contains_opt_out(it, spaced = false) },
    { contains_in_order(it, listOf("remove", "list")) },
    { contains_in_order(it, listOf("manage", "preferences")) },
    { contains_in_order(it, listOf("email", "preferences")) },
    { contains_in_order(it, listOf("subscription", "settings")) },
)

private fun quoted_href_values(markup: String, from: Int, to: Int): Sequence<String> = sequence {
    var cursor = from
    while (true) {
        val at = markup.indexOf("href=", cursor, ignoreCase = true)
        if (at < 0 || at + 5 >= to) return@sequence
        cursor = at + 1
        if (markup[at + 5] !in QUOTES) continue
        var end = at + 6
        while (end < to && markup[end] !in QUOTES) end++
        if (end >= to) continue
        yield(markup.substring(at + 6, end))
    }
}

private fun find_anchor_unsubscribe_href(markup: String): String? {
    var from = 0
    while (true) {
        val open = markup.indexOf("<a", from, ignoreCase = true)
        if (open < 0) return null
        val tag_end = markup.indexOf('>', open + 2)
        if (tag_end < 0) return null
        from = tag_end + 1
        val href = quoted_href_values(markup, open + 2, tag_end).firstOrNull { it.isNotEmpty() } ?: continue
        val text_end = markup.indexOf('<', tag_end + 1)
        if (text_end < 0 || !markup.startsWith("</a>", text_end, ignoreCase = true)) continue
        val text = markup.substring(tag_end + 1, text_end)
        if (text.contains("unsubscribe", ignoreCase = true) || contains_opt_out(text, spaced = true)) return href
    }
}

private fun find_url_with_unsubscribe(text: String): String? {
    var run_start = 0
    while (run_start < text.length) {
        while (run_start < text.length && text[run_start] in PATTERN_SPACE) run_start++
        var run_end = run_start
        while (run_end < text.length && text[run_end] !in PATTERN_SPACE) run_end++
        val run = text.substring(run_start, run_end)
        val last_keyword = maxOf(
            run.lastIndexOf("unsubscribe", ignoreCase = true),
            run.lastIndexOf("optout", ignoreCase = true),
            run.lastIndexOf("opt-out", ignoreCase = true),
        )
        var cursor = 0
        while (last_keyword > 0) {
            val at = run.indexOf("http", cursor, ignoreCase = true)
            if (at < 0) break
            cursor = at + 1
            val scheme_end = when {
                run.startsWith("https://", at, ignoreCase = true) -> at + 8
                run.startsWith("http://", at, ignoreCase = true) -> at + 7
                else -> continue
            }
            if (scheme_end < last_keyword) return run.substring(at)
            break
        }
        run_start = run_end
    }
    return null
}

private val HTML_ENTITIES = mapOf(
    "&amp;" to "&",
    "&#38;" to "&",
    "&#x26;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#39;" to "'",
    "&#x27;" to "'",
    "&nbsp;" to " ",
)

private val HTML_ENTITY_PATTERN = Regex(
    """&(?:amp|#38|#x26|lt|gt|quot|#39|#x27|nbsp);""",
    RegexOption.IGNORE_CASE,
)

private fun decode_html_entities(url: String): String {
    return HTML_ENTITY_PATTERN.replace(url) { match ->
        HTML_ENTITIES[match.value.lowercase()] ?: match.value
    }.trim()
}

private fun is_valid_url(url: String): Boolean {
    return try {
        val lower = url.lowercase()
        lower.startsWith("http://") || lower.startsWith("https://")
    } catch (_: Throwable) {
        false
    }
}

private val HEADER_BRACKETED = Regex("""<([^<>]*)>""")
private val HEADER_WHITESPACE = Regex("""\s+""")
private const val MAX_HEADER_TARGET_LENGTH = 2048

data class ListUnsubscribeTargets(
    val web_links: List<String> = emptyList(),
    val mailto_addresses: List<String> = emptyList(),
) {
    val https_link: String? get() = web_links.firstOrNull { is_https_url(it) }
    val preferred_web_link: String? get() = https_link ?: web_links.firstOrNull()
    val mailto: String? get() = mailto_addresses.firstOrNull()
}

private fun header_candidates(header: String): List<String> {
    val bracketed = HEADER_BRACKETED.findAll(header).map { it.groupValues[1] }.toList()
    val raw = if (bracketed.isNotEmpty()) bracketed else header.split(',')
    return raw.map { HEADER_WHITESPACE.replace(it, "") }.filter { it.isNotEmpty() }
}

private fun web_target(candidate: String): String? {
    val scheme = candidate.substringBefore(':', "").lowercase(java.util.Locale.ROOT)
    if (scheme != "https" && scheme != "http") return null
    val rest = candidate.substring(scheme.length)
    if (!rest.startsWith("://")) return null
    val host = rest.removePrefix("://").substringBefore('/').substringBefore('?').substringBefore('#')
    if (host.isBlank() || host.contains('@')) return null
    return candidate
}

private fun mailto_target(candidate: String): String? {
    if (!candidate.startsWith("mailto:", ignoreCase = true)) return null
    val address = candidate.substring("mailto:".length)
    val recipient = address.substringBefore('?').replace("%40", "@", ignoreCase = true)
    if (recipient.isBlank() || !recipient.contains('@') || recipient.startsWith('@') || recipient.endsWith('@')) {
        return null
    }
    return address
}

fun parse_list_unsubscribe(header: String?): ListUnsubscribeTargets {
    if (header.isNullOrBlank()) return ListUnsubscribeTargets()
    val web = mutableListOf<String>()
    val mail = mutableListOf<String>()
    for (candidate in header_candidates(header)) {
        if (candidate.length > MAX_HEADER_TARGET_LENGTH) continue
        web_target(candidate)?.let { if (it !in web) web += it }
        mailto_target(candidate)?.let { if (it !in mail) mail += it }
    }
    return ListUnsubscribeTargets(web, mail)
}

const val ONE_CLICK_POST_VALUE = "List-Unsubscribe=One-Click"

fun is_one_click_post_header(value: String?): Boolean {
    return value?.trim().equals(ONE_CLICK_POST_VALUE, ignoreCase = true)
}

private fun is_https_url(url: String): Boolean {
    return url.startsWith("https://", ignoreCase = true)
}

private fun is_dkim_acceptable(dkim_result: String?): Boolean {
    val normalized = dkim_result?.trim()
    return normalized.isNullOrEmpty() || normalized.equals("pass", ignoreCase = true)
}

private fun find_body_unsubscribe_link(
    html_content: String?,
    text_content: String?,
): String? {
    if (html_content != null) {
        val anchor_href = find_anchor_unsubscribe_href(html_content)
        if (anchor_href != null) {
            val url = decode_html_entities(anchor_href)
            if (is_valid_url(url)) return url
        }

        val hrefs = quoted_href_values(html_content, 0, html_content.length).toList()
        for (check in UNSUBSCRIBE_LINK_CHECKS) {
            val match = hrefs.firstOrNull(check)
            if (match != null) {
                val url = decode_html_entities(match)
                if (is_valid_url(url)) return url
            }
        }
    }

    if (text_content != null) {
        val match = find_url_with_unsubscribe(text_content)
        if (match != null) {
            val url = decode_html_entities(match)
            if (is_valid_url(url)) return url
        }
    }

    return null
}

fun detect_unsubscribe_info(
    html_content: String? = null,
    text_content: String? = null,
    list_unsubscribe: String? = null,
    list_unsubscribe_post: String? = null,
    dkim_result: String? = null,
): UnsubscribeInfo {
    val body_link = find_body_unsubscribe_link(html_content, text_content)
    val post_declared = is_one_click_post_header(list_unsubscribe_post)
    val declared_post = if (post_declared) ONE_CLICK_POST_VALUE else null

    if (!list_unsubscribe.isNullOrBlank()) {
        val targets = parse_list_unsubscribe(list_unsubscribe)
        val mailto = targets.mailto
        val https_link = targets.https_link
        val plain_link = targets.preferred_web_link
        when {
            post_declared && https_link != null && is_dkim_acceptable(dkim_result) ->
                return UnsubscribeInfo(
                    has_unsubscribe = true,
                    unsubscribe_link = https_link,
                    unsubscribe_mailto = mailto,
                    unsubscribe_page_url = body_link,
                    method = "one-click",
                    list_unsubscribe_header = list_unsubscribe,
                    list_unsubscribe_post = declared_post,
                )
            mailto != null ->
                return UnsubscribeInfo(
                    has_unsubscribe = true,
                    unsubscribe_mailto = mailto,
                    unsubscribe_page_url = plain_link?.takeIf { !post_declared } ?: body_link,
                    method = "mailto",
                    list_unsubscribe_header = list_unsubscribe,
                    list_unsubscribe_post = declared_post,
                )
            plain_link != null && !post_declared ->
                return UnsubscribeInfo(
                    has_unsubscribe = true,
                    unsubscribe_link = plain_link,
                    unsubscribe_page_url = plain_link,
                    method = "link",
                    list_unsubscribe_header = list_unsubscribe,
                )
        }
    }

    if (body_link != null) {
        return UnsubscribeInfo(
            has_unsubscribe = true,
            unsubscribe_link = body_link,
            unsubscribe_page_url = body_link,
            method = "link",
            list_unsubscribe_header = list_unsubscribe,
            list_unsubscribe_post = declared_post,
        )
    }

    return UnsubscribeInfo(
        list_unsubscribe_header = list_unsubscribe,
        list_unsubscribe_post = declared_post,
    )
}

fun is_one_click_only(info: UnsubscribeInfo): Boolean {
    return info.method == "one-click" || !info.list_unsubscribe_post.isNullOrBlank()
}

fun get_manual_unsubscribe_url(info: UnsubscribeInfo): String? {
    info.unsubscribe_page_url?.let { if (is_valid_url(it)) return it }

    val one_click_only = is_one_click_only(info)
    if (!one_click_only) {
        info.unsubscribe_link?.let { if (is_valid_url(it)) return it }
    }

    info.unsubscribe_mailto?.let { return to_mailto_url(it) }

    val header = info.list_unsubscribe_header ?: return null
    val targets = parse_list_unsubscribe(header)
    if (!one_click_only) {
        targets.preferred_web_link?.let { return it }
    }
    val mailto = targets.mailto ?: return null
    return to_mailto_url(mailto)
}

private fun to_mailto_url(address: String): String {
    return if (address.startsWith("mailto:", ignoreCase = true)) address else "mailto:$address"
}
