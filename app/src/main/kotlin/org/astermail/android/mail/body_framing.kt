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

fun is_body_framing_char(char: Char): Boolean = char.isWhitespace() ||
    char.code <= 0x1f ||
    char.code == 0x7f ||
    char.code == 0xa0 ||
    char.code == 0xad ||
    char.code == 0x34f ||
    char.code == 0x61c ||
    char.code == 0x1680 ||
    char.code in 0x2000..0x200f ||
    char.code in 0x2028..0x202f ||
    char.code in 0x205f..0x2060 ||
    char.code == 0x2066 ||
    char.code == 0x2067 ||
    char.code == 0x2068 ||
    char.code == 0x2069 ||
    char.code == 0x3000 ||
    char.code == 0x3164 ||
    char.code == 0xfeff ||
    char.code == 0xffa0

fun strip_body_framing(body: String): String {
    var start = 0
    while (start < body.length && is_body_framing_char(body[start])) start += 1
    return if (start == 0) body else body.substring(start)
}

fun is_body_framing_only(text: String): Boolean = text.all { is_body_framing_char(it) }

fun body_starts_with(body: String, prefix: String, ignore_case: Boolean = false): Boolean =
    strip_body_framing(body).startsWith(prefix, ignoreCase = ignore_case)
