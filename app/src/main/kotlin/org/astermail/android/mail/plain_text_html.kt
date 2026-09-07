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

fun build_plain_text_html(body: String): String {
    val normalized = body.replace("\r\n", "\n").replace('\r', '\n')
    val unflowed = if (FormatFlowed.looks_flowed(normalized)) {
        FormatFlowed.unflow(normalized)
    } else {
        normalized
    }
    val linked = Autolink.split(unflowed).joinToString("") { segment ->
        val text = escape_plain_text(segment.text)
        val href = segment.href
        if (href == null) text else "<a href=\"${escape_plain_text(href)}\">$text</a>"
    }
    return "<div style=\"white-space:pre-wrap;overflow-wrap:break-word\">" +
        linked.replace("\n", "<br>") +
        "</div>"
}

private fun escape_plain_text(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
