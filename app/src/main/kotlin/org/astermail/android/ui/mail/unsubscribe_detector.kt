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
    val method: String = "none",
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

private fun is_valid_url(url: String): Boolean {
    return try {
        val lower = url.lowercase()
        lower.startsWith("http://") || lower.startsWith("https://")
    } catch (_: Throwable) {
        false
    }
}

fun detect_unsubscribe_info(
    html_content: String? = null,
    text_content: String? = null,
): UnsubscribeInfo {
    if (html_content != null) {
        for (pattern in UNSUBSCRIBE_LINK_PATTERNS) {
            val match = pattern.find(html_content)
            if (match != null) {
                val url = match.groupValues[1]
                if (is_valid_url(url)) {
                    return UnsubscribeInfo(
                        has_unsubscribe = true,
                        unsubscribe_link = url,
                        method = "link",
                    )
                }
            }
        }

        val anchor_match = ANCHOR_UNSUBSCRIBE.find(html_content)
        if (anchor_match != null) {
            val href_match = HREF_EXTRACT.find(anchor_match.value)
            if (href_match != null && is_valid_url(href_match.groupValues[1])) {
                return UnsubscribeInfo(
                    has_unsubscribe = true,
                    unsubscribe_link = href_match.groupValues[1],
                    method = "link",
                )
            }
        }
    }

    if (text_content != null) {
        val url_pattern = Regex("""https?://\S+(?:unsubscribe|opt-?out)\S*""", RegexOption.IGNORE_CASE)
        val match = url_pattern.find(text_content)
        if (match != null && is_valid_url(match.value)) {
            return UnsubscribeInfo(
                has_unsubscribe = true,
                unsubscribe_link = match.value,
                method = "link",
            )
        }
    }

    return UnsubscribeInfo()
}
