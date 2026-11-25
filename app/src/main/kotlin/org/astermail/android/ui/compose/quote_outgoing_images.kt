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

import org.jsoup.Jsoup

private const val PLACEHOLDER_PIXEL_DATA_URI =
    "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=="

private val UNSENDABLE_PREFIXES = listOf("cid:", "blob:", PLACEHOLDER_PIXEL_DATA_URI.lowercase())

private fun is_unsendable_source(src: String): Boolean {
    val value = src.trim().lowercase()
    return UNSENDABLE_PREFIXES.any { value.startsWith(it) }
}

internal fun remove_unsendable_quote_images(html: String): String {
    val has_image = html.contains("<img", ignoreCase = true)
    if (!has_image && !html.contains(PLACEHOLDER_PIXEL_DATA_URI)) return html
    var out = html
    if (has_image) {
        val doc = Jsoup.parseBodyFragment(html)
        doc.outputSettings().prettyPrint(false)
        val unsendable = doc.select("img").filter { is_unsendable_source(it.attr("src")) }
        if (unsendable.isNotEmpty()) {
            unsendable.forEach { it.remove() }
            out = doc.body().html()
        }
    }
    return out.replace(PLACEHOLDER_PIXEL_DATA_URI, "")
}
