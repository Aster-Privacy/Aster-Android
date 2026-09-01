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

package org.astermail.android.auth

private const val recovery_code_segment_length = 4
private val recovery_code_segment_counts = listOf(4, 3)

fun canonicalize_recovery_code(code: String): String {
    val stripped = code.uppercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }

    if (stripped.startsWith("ASTER")) {
        val body = stripped.substring(5)

        for (segment_count in recovery_code_segment_counts) {
            if (body.length != segment_count * recovery_code_segment_length) continue

            val segments = (0 until segment_count).map {
                body.substring(
                    it * recovery_code_segment_length,
                    (it + 1) * recovery_code_segment_length,
                )
            }

            return "ASTER-" + segments.joinToString("-")
        }
    }

    return code.uppercase(java.util.Locale.ROOT).trim()
}

fun is_valid_recovery_code(code: String): Boolean {
    val segments = canonicalize_recovery_code(code).split("-")

    if (segments.firstOrNull() != "ASTER") return false
    if (!recovery_code_segment_counts.contains(segments.size - 1)) return false

    return segments.drop(1).all { segment ->
        segment.length == recovery_code_segment_length && segment.all { it.isLetterOrDigit() }
    }
}
