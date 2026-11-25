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

private val UNSUBSCRIBE_LINK_PATTERNS = listOf(
    Regex("""href=["']([^"']*unsubscribe[^"']*)["']""", RegexOption.IGNORE_CASE),
    Regex("""href=["']([^"']*opt-?out[^"']*)["']""", RegexOption.IGNORE_CASE),
    Regex("""href=["']([^"']*remove[^"']*list[^"']*)["']""", RegexOption.IGNORE_CASE),
    Regex("""href=["']([^"']*manage[^"']*preferences[^"']*)["']""", RegexOption.IGNORE_CASE),
    Regex("""href=["']([^"']*email[^"']*preferences[^"']*)["']""", RegexOption.IGNORE_CASE),
    Regex("""href=["']([^"']*subscription[^"']*settings[^"']*)["']""", RegexOption.IGNORE_CASE),
)

private val ANCHOR_UNSUBSCRIBE = Regex(
    """<a[^>]*href=["']([^"']+)["'][^>]*>[^<]*(?:unsubscribe|opt[\s-]?out)[^<]*</a>""",
    RegexOption.IGNORE_CASE,
)

private val HREF_EXTRACT = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

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
        val anchor_match = ANCHOR_UNSUBSCRIBE.find(html_content)
        if (anchor_match != null) {
            val href_match = HREF_EXTRACT.find(anchor_match.value)
            if (href_match != null) {
                val url = decode_html_entities(href_match.groupValues[1])
                if (is_valid_url(url)) return url
            }
        }

        for (pattern in UNSUBSCRIBE_LINK_PATTERNS) {
            val match = pattern.find(html_content)
            if (match != null) {
                val url = decode_html_entities(match.groupValues[1])
                if (is_valid_url(url)) return url
            }
        }
    }

    if (text_content != null) {
        val url_pattern = Regex("""https?://\S+(?:unsubscribe|opt-?out)\S*""", RegexOption.IGNORE_CASE)
        val match = url_pattern.find(text_content)
        if (match != null) {
            val url = decode_html_entities(match.value)
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
