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

package org.astermail.android.design.components

fun adjusted_caret(previous: String, next: String, caret: Int): Int {
    val position = caret.coerceIn(0, previous.length)
    val max_affix = minOf(previous.length, next.length)

    var prefix = 0
    while (prefix < max_affix && previous[prefix] == next[prefix]) prefix++

    var suffix = 0
    while (
        suffix < max_affix - prefix &&
        previous[previous.length - 1 - suffix] == next[next.length - 1 - suffix]
    ) {
        suffix++
    }

    if (position <= prefix) return position

    if (position >= previous.length - suffix) {
        return (position - (previous.length - next.length)).coerceIn(0, next.length)
    }

    return minOf(position, next.length - suffix)
}
