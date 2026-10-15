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

package org.astermail.android.ui.compose

private val doubled_link_scheme = Regex("^(?i)https?://(https?://)")

private val leading_link_scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:(?![0-9])")

private val web_link_scheme = Regex("^(?i)https?://")

internal const val max_link_url_length = 2048

internal fun collapse_doubled_link_scheme(raw: String): String {
    var current = raw
    while (true) {
        val match = doubled_link_scheme.find(current) ?: return current
        current = current.substring(match.groups[1]!!.range.first)
    }
}

internal fun normalize_link_url(raw: String): String? {
    val trimmed = collapse_doubled_link_scheme(raw.trim())
    if (trimmed.isEmpty() || trimmed.length > max_link_url_length) return null
    if (trimmed.any { it.isISOControl() || it.isWhitespace() }) return null
    val lower = trimmed.lowercase()
    return when {
        web_link_scheme.containsMatchIn(trimmed) ->
            trimmed.takeIf { web_link_scheme.replace(it, "").isNotEmpty() }
        lower.startsWith("mailto:") -> trimmed.takeIf { it.length > "mailto:".length }
        leading_link_scheme.containsMatchIn(trimmed) -> null
        trimmed.contains('@') -> "mailto:$trimmed"
        else -> "https://$trimmed"
    }
}
