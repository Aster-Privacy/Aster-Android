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

fun signature_below_quote(signature_placement: Int?, preference: String?): Boolean {
    if (signature_placement == 1) return false
    if (signature_placement == 0) return true
    return preference != "above"
}

fun split_trailing_signature(body: String, signature: String): Pair<String, String>? {
    if (signature.isBlank()) return null
    val trimmed = body.trimEnd('\n', ' ')
    if (!trimmed.endsWith(signature)) return null
    val before = trimmed.substring(0, trimmed.length - signature.length).trimEnd('\n', ' ')
    return before to signature
}

fun plain_signature_with_separator(content: String, preference: Boolean?): String =
    if (content.isBlank() || preference == false) content else "--\n" + content

fun html_signature_with_separator(html: String, preference: Boolean?): String =
    if (html.isBlank() || preference == false) html else "--<br>" + html
