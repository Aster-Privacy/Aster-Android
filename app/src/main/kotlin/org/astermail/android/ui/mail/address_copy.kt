// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.mail

private val angle_addressed = Regex("<([^<>]+)>")

fun copyable_email_address(value: String): String {
    val trimmed = value.trim()
    val bracketed = angle_addressed.findAll(trimmed)
        .map { it.groupValues[1].trim() }
        .filter { it.contains('@') }
        .toList()
    if (bracketed.isNotEmpty()) return bracketed.joinToString(", ")
    val bare = trimmed.split(',')
        .map { it.trim().trim('"').trim() }
        .filter { it.contains('@') }
    if (bare.isNotEmpty()) return bare.joinToString(", ")
    return trimmed
}
