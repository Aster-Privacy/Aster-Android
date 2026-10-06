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

package org.astermail.android.util

const val max_indent_depth = 6

fun indent_depth(depth: Int): Int = depth.coerceIn(0, max_indent_depth)

fun indent_trail(trail: List<Boolean>, depth: Int): List<Boolean> {
    val visible = indent_depth(depth)
    return if (trail.size > visible) trail.takeLast(visible) else trail
}
