//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

data class AutolinkMatch(
    val start: Int,
    val end: Int,
    val text: String,
    val href: String,
)

data class AutolinkSegment(
    val text: String,
    val href: String? = null,
)

object Autolink {
    private val candidate_pattern = Regex(
        "(?<![\\w@/.-])(?:(https?://|www\\.)[^\\s<>\"'`]+|([A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}))",
        RegexOption.IGNORE_CASE,
    )
    private val entity_tail = Regex("&#?[A-Za-z0-9]+;$")
    private val quick_check = Regex("://|www\\.|@", RegexOption.IGNORE_CASE)
    private val tld_seam = Regex("[a-z][A-Z]")
    private val trailing_punctuation = setOf('.', ',', ':', ';', '!', '?', '\'', '"', '*', '_', '~')
    private val bracket_pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
    private const val max_tld_length = 24

    fun trim_url_tail(candidate: String): String {
        var url = candidate
        while (url.isNotEmpty()) {
            val last = url.last()
            if (last == ';') {
                val entity = entity_tail.find(url)
                url = if (entity != null) url.substring(0, entity.range.first) else url.dropLast(1)
                continue
            }
            if (last in trailing_punctuation) {
                url = url.dropLast(1)
                continue
            }
            val opener = bracket_pairs[last]
            if (opener != null && url.count { it == last } > url.count { it == opener }) {
                url = url.dropLast(1)
                continue
            }
            break
        }
        return url
    }

    fun trim_email_tail(address: String): String {
        val at = address.lastIndexOf('@')
        if (at < 0) return address
        val domain = address.substring(at + 1)
        val dot = domain.lastIndexOf('.')
        if (dot < 0) return address
        var tld = domain.substring(dot + 1)
        val seam = tld_seam.find(tld)
        if (seam != null) tld = tld.substring(0, seam.range.first + 1)
        if (tld.length > max_tld_length) tld = tld.substring(0, max_tld_length)
        return address.substring(0, at + 1) + domain.substring(0, dot + 1) + tld
    }

    private fun has_host(url: String, scheme_length: Int): Boolean {
        val host = url.substring(scheme_length).split('/', '?', '#', limit = 2)[0]
        return host.isNotEmpty() &&
            !host.startsWith(".") &&
            !host.endsWith(".") &&
            !host.contains("..") &&
            host.any { it.isLetterOrDigit() }
    }

    fun find(text: String): List<AutolinkMatch> {
        val matches = mutableListOf<AutolinkMatch>()
        if (text.isEmpty() || !quick_check.containsMatchIn(text)) return matches
        var search_from = 0
        while (search_from < text.length) {
            val match = candidate_pattern.find(text, search_from) ?: break
            val raw = match.value
            val prefix = match.groups[1]?.value
            if (prefix != null) {
                val url = trim_url_tail(raw)
                val is_www = prefix.equals("www.", ignoreCase = true)
                if (url.length > prefix.length &&
                    has_host(url, prefix.length) &&
                    (!is_www || url.substring(prefix.length).contains('.'))
                ) {
                    matches.add(
                        AutolinkMatch(
                            start = match.range.first,
                            end = match.range.first + url.length,
                            text = url,
                            href = if (is_www) "http://$url" else url,
                        ),
                    )
                }
                search_from = match.range.first + maxOf(url.length, 1)
                continue
            }
            val address = trim_email_tail(match.groups[2]?.value ?: raw)
            matches.add(
                AutolinkMatch(
                    start = match.range.first,
                    end = match.range.first + address.length,
                    text = address,
                    href = "mailto:$address",
                ),
            )
            search_from = match.range.first + address.length
        }
        return matches
    }

    fun split(text: String): List<AutolinkSegment> {
        val segments = mutableListOf<AutolinkSegment>()
        var cursor = 0
        for (link in find(text)) {
            if (link.start > cursor) segments.add(AutolinkSegment(text.substring(cursor, link.start)))
            segments.add(AutolinkSegment(link.text, link.href))
            cursor = link.end
        }
        if (cursor < text.length) segments.add(AutolinkSegment(text.substring(cursor)))
        return segments
    }
}
