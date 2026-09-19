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

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

const val RECOVERY_CODE_SET_SIZE = 10

private const val recovery_code_alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val recovery_code_segment_length = 4
private val recovery_code_segment_counts = listOf(4, 3)

fun canonicalize_recovery_code(code: String): String {
    val upper = code.uppercase(java.util.Locale.ROOT)
    val stripped = upper.filter { it in 'A'..'Z' || it in '0'..'9' }

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

    return upper.filter { it in 'A'..'Z' || it in '0'..'9' || it == '-' }
}

fun is_valid_recovery_code(code: String): Boolean {
    val segments = canonicalize_recovery_code(code).split("-")

    if (segments.firstOrNull() != "ASTER") return false
    if (!recovery_code_segment_counts.contains(segments.size - 1)) return false

    return segments.drop(1).all { segment ->
        segment.length == recovery_code_segment_length &&
            segment.all { it in 'A'..'Z' || it in '0'..'9' }
    }
}

fun generate_recovery_codes(count: Int = RECOVERY_CODE_SET_SIZE): List<String> {
    val random = SecureRandom()

    return (1..count).map {
        val segments = (1..4).map {
            (1..recovery_code_segment_length)
                .map { recovery_code_alphabet[random.nextInt(recovery_code_alphabet.length)] }
                .joinToString("")
        }

        "ASTER-" + segments.joinToString("-")
    }
}

fun hash_recovery_code(code: String): String {
    val canonical = canonicalize_recovery_code(code)
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))

    return Base64.encodeToString(digest, Base64.NO_WRAP)
}
