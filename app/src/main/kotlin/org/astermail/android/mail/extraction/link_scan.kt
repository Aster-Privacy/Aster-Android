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
package org.astermail.android.mail.extraction

import java.util.Locale

internal const val link_scan_max_length = 200_000
internal const val link_scan_max_url_length = 2048

private val url_token_regex = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)
private val link_image_path = Regex("\\.(?:png|jpe?g|gif|svg|webp|bmp|ico)(?:[?#].*)?$", RegexOption.IGNORE_CASE)

internal fun url_tokens(text: String): Sequence<String> =
    url_token_regex.findAll(text.take(link_scan_max_length))
        .map { it.value }
        .filter { it.length <= link_scan_max_url_length }

internal fun contains_after_scheme(url: String, keyword: String): Boolean {
    val scheme_end = url.indexOf("://")
    if (scheme_end < 0) return false
    val start = scheme_end + 4
    return start <= url.length && url.indexOf(keyword, start, ignoreCase = true) >= 0
}

internal fun is_web_link(url: String): Boolean {
    val lower = url.lowercase(Locale.ROOT)
    return lower.startsWith("https://") || lower.startsWith("http://")
}

internal fun is_image_link(url: String): Boolean {
    val path = runCatching { java.net.URI(url).path }.getOrNull() ?: url.substringBefore('?').substringBefore('#')
    return link_image_path.containsMatchIn(path)
}

internal fun anchor_hrefs(html: String): List<String> {
    val document = runCatching { org.jsoup.Jsoup.parse(html.take(link_scan_max_length)) }.getOrNull()
        ?: return emptyList()
    return document.select("a[href]")
        .map { it.attr("href").trim() }
        .filter { it.length <= link_scan_max_url_length && is_web_link(it) }
}
