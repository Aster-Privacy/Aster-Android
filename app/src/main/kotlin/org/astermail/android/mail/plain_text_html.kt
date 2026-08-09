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

private val url_pattern = Regex(
    "(https?://[^\\s<>\"']+|www\\.[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}(?:/[^\\s<>\"']*)?)",
)
private val trailing_punct_pattern = Regex("[.,;:!?)\\]\\}\"']+$")
private val email_pattern = Regex(
    "(?<![\\w@.-])([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})(?![\\w@.-])",
)

fun build_plain_text_html(body: String): String {
    val normalized = body.replace("\r\n", "\n").replace('\r', '\n')
    val unflowed = if (FormatFlowed.looks_flowed(normalized)) {
        FormatFlowed.unflow(normalized)
    } else {
        normalized
    }
    val escaped = unflowed
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    val linked = url_pattern.replace(escaped) { match ->
        val raw = match.value
        val trail_match = trailing_punct_pattern.find(raw)
        val (clean, trail) = if (trail_match != null) {
            raw.substring(0, trail_match.range.first) to raw.substring(trail_match.range.first)
        } else {
            raw to ""
        }
        val href = if (clean.startsWith("www.")) "http://$clean" else clean
        "<a href=\"$href\">$clean</a>$trail"
    }
    val linked_with_email = email_pattern.replace(linked) { match ->
        val address = trim_plain_text_email(match.value)
        "<a href=\"mailto:$address\">$address</a>" + match.value.substring(address.length)
    }
    return "<div style=\"white-space:pre-wrap;overflow-wrap:break-word\">" +
        linked_with_email.replace("\n", "<br>") +
        "</div>"
}

private fun trim_plain_text_email(address: String): String {
    val at = address.lastIndexOf('@')
    if (at < 0) return address
    val domain = address.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    if (dot < 0) return address
    var tld = domain.substring(dot + 1)
    val seam = Regex("[a-z][A-Z]").find(tld)
    if (seam != null) tld = tld.substring(0, seam.range.first + 1)
    if (tld.length > 24) tld = tld.substring(0, 24)
    return address.substring(0, at + 1) + domain.substring(0, dot + 1) + tld
}
