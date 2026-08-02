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

package org.astermail.android.crypto

fun normalize_address_ignoring_dots(address: String): String {
    val lowered = address.lowercase().trim()
    val at = lowered.lastIndexOf('@')
    if (at == -1) return lowered.replace(".", "")
    return lowered.substring(0, at).replace(".", "") + lowered.substring(at)
}

fun same_address_ignoring_dots(left: String?, right: String?): Boolean {
    if (left.isNullOrBlank() || right.isNullOrBlank()) return false
    return normalize_address_ignoring_dots(left) == normalize_address_ignoring_dots(right)
}
