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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarGoldenVectorsTest {

    private data class GoldenAvatar(
        val input: String,
        val hash: Int,
        val index: Int,
        val background: Long,
        val text: Long,
    )

    private fun golden(input: String, hash: Int, index: Int, background: Long, text: Long) =
        GoldenAvatar(input, hash, index, background, text)

    private val color_vectors = listOf(
        golden("", 0, 0, 0xFF1E88E5, 0xFFFFFFFF),
        golden("?", 63, 15, 0xFFFF6F00, 0xFFFFFFFF),
        golden(" ", 32, 0, 0xFF1E88E5, 0xFFFFFFFF),
        golden("a", 97, 1, 0xFFE53935, 0xFFFFFFFF),
        golden("A", 65, 1, 0xFFE53935, 0xFFFFFFFF),
        golden("z", 122, 10, 0xFF3949AB, 0xFFFFFFFF),
        golden("0", 48, 0, 0xFF1E88E5, 0xFFFFFFFF),
        golden("12345", 46792755, 3, 0xFFFB8C00, 0xFFFFFFFF),
        golden("alice@example.com", 2145772861, 13, 0xFF039BE5, 0xFFFFFFFF),
        golden("Alice@Example.com", -837563139, 3, 0xFFFB8C00, 0xFFFFFFFF),
        golden("ALICE@EXAMPLE.COM", 1900625117, 13, 0xFF039BE5, 0xFFFFFFFF),
        golden(" alice@example.com ", 841153187, 3, 0xFFFB8C00, 0xFFFFFFFF),
        golden("alice@example.com\u000a", 2094449261, 13, 0xFF039BE5, 0xFFFFFFFF),
        golden("\u0009alice@astermail.org", -289676941, 13, 0xFF039BE5, 0xFFFFFFFF),
        golden("bob@astermail.org", 1066401425, 1, 0xFFE53935, 0xFFFFFFFF),
        golden("qa@astermail.org", -942582868, 4, 0xFF8E24AA, 0xFFFFFFFF),
        golden("zoe@example.com", 1408675469, 13, 0xFF039BE5, 0xFFFFFFFF),
        golden("sher@aster.cx", -930719534, 14, 0xFF7CB342, 0xFFFFFFFF),
        golden("noreply@astermail.org", 321799077, 5, 0xFFD81B60, 0xFFFFFFFF),
        golden("john.doe+newsletter@sub.example.co.uk", -1272308205, 13, 0xFF039BE5, 0xFFFFFFFF),
        golden("user_name-99@domain.io", -1834786306, 2, 0xFF43A047, 0xFFFFFFFF),
        golden("Maya Chen", -1658687022, 14, 0xFF7CB342, 0xFFFFFFFF),
        golden("maya chen", 633265618, 2, 0xFF43A047, 0xFFFFFFFF),
        golden("Mary Jane Watson", -1377707019, 11, 0xFFC0CA33, 0xFFFFFFFF),
        golden("\u674e\u96f7", 858473, 9, 0xFF00897B, 0xFFFFFFFF),
        golden("\u674e\u96f7@\u4f8b\u5b50.\u4e2d\u56fd", 674168386, 2, 0xFF43A047, 0xFFFFFFFF),
        golden("Jos\u00e9 \u00c1lvarez", 343570700, 12, 0xFF6D4C41, 0xFFFFFFFF),
        golden("Jose\u0301", 71761770, 10, 0xFF3949AB, 0xFFFFFFFF),
        golden("\u00d8degaard", 35618688, 0, 0xFF1E88E5, 0xFFFFFFFF),
        golden("\u0395\u03bb\u03ad\u03bd\u03b7", 876254081, 1, 0xFFE53935, 0xFFFFFFFF),
        golden("\u039f\u0394\u039f\u03a3", 28526201, 9, 0xFF00897B, 0xFFFFFFFF),
        golden("\u0130stanbul", -363956677, 5, 0xFFD81B60, 0xFFFFFFFF),
        golden("Stra\u00dfe", -1808122922, 10, 0xFF3949AB, 0xFFFFFFFF),
        golden("\u0418\u0432\u0430\u043d \u041f\u0435\u0442\u0440\u043e\u0432", 1788695809, 1, 0xFFE53935, 0xFFFFFFFF),
        golden("\u0645\u062d\u0645\u062f", 49385234, 2, 0xFF43A047, 0xFFFFFFFF),
        golden("\u05e9\u05dc\u05d5\u05dd", 46563067, 11, 0xFFC0CA33, 0xFFFFFFFF),
        golden("\ud83c\udf89", 1773261, 13, 0xFF039BE5, 0xFFFFFFFF),
        golden("\ud83d\udc69\u200d\ud83d\udc69\u200d\ud83d\udc67\u200d\ud83d\udc66 family", -54225448, 8, 0xFFF4511E, 0xFFFFFFFF),
        golden("\ud83c\uddef\ud83c\uddf5", 1705482668, 12, 0xFF6D4C41, 0xFFFFFFFF),
        golden("a\u0000b", 93315, 3, 0xFFFB8C00, 0xFFFFFFFF),
        golden("\u200bzero@width.com", 311888524, 12, 0xFF6D4C41, 0xFFFFFFFF),
        golden("\ufeffbom@example.com", -974377058, 2, 0xFF43A047, 0xFFFFFFFF),
        golden("tab\u0009inside", 955343888, 0, 0xFF1E88E5, 0xFFFFFFFF),
        golden("polygenelubricants", Int.MIN_VALUE, 0, 0xFF1E88E5, 0xFFFFFFFF),
        golden("GydZG_", Int.MIN_VALUE, 0, 0xFF1E88E5, 0xFFFFFFFF),
        golden("DESIGNING WORKHOUSES", Int.MIN_VALUE, 0, 0xFF1E88E5, 0xFFFFFFFF),
        golden("x".repeat(1000), -1715418112, 0, 0xFF1E88E5, 0xFFFFFFFF),
        golden("abcdefghij".repeat(257), -28796507, 11, 0xFFC0CA33, 0xFFFFFFFF),
        golden("\ud83d\ude00".repeat(300), 268022404, 4, 0xFF8E24AA, 0xFFFFFFFF),
        golden("\u00e9".repeat(4096), 2080964608, 0, 0xFF1E88E5, 0xFFFFFFFF),
    )

    private val key_vectors = listOf(
        Triple("alice@example.com", "Alice", "alice@example.com"),
        Triple("", "Alice", "Alice"),
        Triple("", "", "?"),
        Triple(" ", "Alice", " "),
        Triple("Alice@Example.com", "", "Alice@Example.com"),
        Triple("", " ", " "),
        Triple("", "\u674e\u96f7", "\u674e\u96f7"),
        Triple("x@y.z", "\ud83c\udf89", "x@y.z"),
    )

    private val contrast_vectors = listOf(
        "#1e88e5" to 0xFFFFFFFF,
        "#e53935" to 0xFFFFFFFF,
        "#43a047" to 0xFFFFFFFF,
        "#fb8c00" to 0xFFFFFFFF,
        "#8e24aa" to 0xFFFFFFFF,
        "#d81b60" to 0xFFFFFFFF,
        "#00acc1" to 0xFFFFFFFF,
        "#5e35b1" to 0xFFFFFFFF,
        "#f4511e" to 0xFFFFFFFF,
        "#00897b" to 0xFFFFFFFF,
        "#3949ab" to 0xFFFFFFFF,
        "#c0ca33" to 0xFFFFFFFF,
        "#6d4c41" to 0xFFFFFFFF,
        "#039be5" to 0xFFFFFFFF,
        "#7cb342" to 0xFFFFFFFF,
        "#ff6f00" to 0xFFFFFFFF,
        "#3b82f6" to 0xFFFFFFFF,
        "#8b5cf6" to 0xFFFFFFFF,
        "#ec4899" to 0xFFFFFFFF,
        "#ef4444" to 0xFFFFFFFF,
        "#f97316" to 0xFFFFFFFF,
        "#22c55e" to 0xFFFFFFFF,
        "#14b8a6" to 0xFFFFFFFF,
        "#6b7280" to 0xFFFFFFFF,
        "#ffffff" to 0xFF111827,
        "#fff" to 0xFF111827,
        "#FFF" to 0xFF111827,
        "#000000" to 0xFFFFFFFF,
        "#000" to 0xFFFFFFFF,
        "#fde047" to 0xFF111827,
        "#FDE047" to 0xFF111827,
        "#1e3a5f" to 0xFFFFFFFF,
        "#e5e7eb" to 0xFF111827,
        "#9ca3af" to 0xFFFFFFFF,
        "3b82f6" to 0xFFFFFFFF,
        "fde047" to 0xFF111827,
        "#abc" to 0xFFFFFFFF,
        "#c8cb04" to 0xFFFFFFFF,
        "#75dc0e" to 0xFF111827,
    )

    @Test
    fun palette_matches_the_shared_sixteen_colors() {
        assertEquals(16, avatar_palette_hex.size)
    }

    @Test
    fun hash_matches_utf16_signed_wrap() {
        for (vector in color_vectors) {
            assertEquals("hash for ${vector.input.take(24)}", vector.hash, avatar_hash(vector.input))
        }
    }

    @Test
    fun index_and_colors_match_every_vector() {
        for (vector in color_vectors) {
            val label = vector.input.take(24)
            assertEquals("index for $label", vector.index, avatar_color_index(vector.input))
            val (background, text) = avatar_colors_for(vector.input)
            assertEquals("background for $label", Color(vector.background), background)
            assertEquals("text for $label", Color(vector.text), text)
        }
    }

    @Test
    fun key_prefers_email_then_name_then_placeholder() {
        for ((email, name, key) in key_vectors) {
            assertEquals(key, avatar_key_for(email, name))
        }
    }

    @Test
    fun contrast_text_matches_every_vector() {
        for ((hex, text) in contrast_vectors) {
            assertEquals("text for $hex", Color(text), contrast_text_for_hex(hex))
        }
    }
}
