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

package org.astermail.android.api.preferences

const val EMOJI_SKIN_TONE_DEFAULT = "default"

val emoji_skin_tones = listOf(
    EMOJI_SKIN_TONE_DEFAULT,
    "light",
    "medium_light",
    "medium",
    "medium_dark",
    "dark",
)

private val skin_tone_modifiers = mapOf(
    "light" to 0x1F3FB,
    "medium_light" to 0x1F3FC,
    "medium" to 0x1F3FD,
    "medium_dark" to 0x1F3FE,
    "dark" to 0x1F3FF,
)

private val modifier_base_ranges = listOf(
    0x261D..0x261D, 0x26F9..0x26F9, 0x270A..0x270D, 0x1F385..0x1F385,
    0x1F3C2..0x1F3C4, 0x1F3C7..0x1F3C7, 0x1F3CA..0x1F3CC, 0x1F442..0x1F443,
    0x1F446..0x1F450, 0x1F466..0x1F469, 0x1F46B..0x1F46E, 0x1F470..0x1F478,
    0x1F47C..0x1F47C, 0x1F481..0x1F483, 0x1F485..0x1F487, 0x1F48F..0x1F48F,
    0x1F491..0x1F491, 0x1F4AA..0x1F4AA, 0x1F574..0x1F575, 0x1F57A..0x1F57A,
    0x1F590..0x1F590, 0x1F595..0x1F596, 0x1F645..0x1F647, 0x1F64B..0x1F64F,
    0x1F6A3..0x1F6A3, 0x1F6B4..0x1F6B6, 0x1F6C0..0x1F6C0, 0x1F6CC..0x1F6CC,
    0x1F90C..0x1F90C, 0x1F90F..0x1F90F, 0x1F918..0x1F91F, 0x1F926..0x1F926,
    0x1F930..0x1F939, 0x1F93D..0x1F93E, 0x1F977..0x1F977, 0x1F9B5..0x1F9B6,
    0x1F9B8..0x1F9B9, 0x1F9BB..0x1F9BB, 0x1F9CD..0x1F9CF, 0x1F9D1..0x1F9DD,
    0x1FAC3..0x1FAC5, 0x1FAF0..0x1FAF8,
)

private const val ZERO_WIDTH_JOINER = 0x200D
private const val VARIATION_SELECTOR = 0xFE0F
private const val HANDSHAKE = 0x1F91D
private const val HEAVY_HEART = 0x2764

fun is_emoji_skin_tone(value: String?): Boolean = value != null && value in emoji_skin_tones

fun normalize_emoji_skin_tone(value: String?): String =
    if (is_emoji_skin_tone(value)) value!! else EMOJI_SKIN_TONE_DEFAULT

private fun is_modifier_base(code_point: Int): Boolean =
    modifier_base_ranges.any { code_point in it }

private fun is_skin_tone_modifier(code_point: Int): Boolean = code_point in 0x1F3FB..0x1F3FF

private fun code_points(value: String): List<Int> {
    val result = ArrayList<Int>(value.length)
    var index = 0
    while (index < value.length) {
        val point = value.codePointAt(index)
        result.add(point)
        index += Character.charCount(point)
    }
    return result
}

private fun split_segments(points: List<Int>): List<List<Int>> {
    val segments = mutableListOf<MutableList<Int>>(mutableListOf())
    for (point in points) {
        if (point == ZERO_WIDTH_JOINER) segments.add(mutableListOf()) else segments.last().add(point)
    }
    return segments
}

private fun tone_targets(segments: List<List<Int>>): List<Int> {
    val bases = segments.indices.filter { index ->
        segments[index].firstOrNull()?.let(::is_modifier_base) == true
    }
    if (segments.size == 1) return bases
    val people = bases.filter { segments[it][0] != HANDSHAKE }
    val pairs_people = segments.any { it.firstOrNull() == HANDSHAKE || it.firstOrNull() == HEAVY_HEART }
    if (people.size == 1) return people
    if (people.size == 2 && pairs_people) return people
    return emptyList()
}

fun is_tone_capable_emoji(emoji: String): Boolean {
    if (emoji.isEmpty()) return false
    val segments = split_segments(code_points(emoji))
    if (segments.any { segment -> segment.any(::is_skin_tone_modifier) }) return false
    return tone_targets(segments).isNotEmpty()
}

fun apply_emoji_skin_tone(emoji: String, tone: String?): String {
    val modifier = skin_tone_modifiers[normalize_emoji_skin_tone(tone)] ?: return emoji
    if (!is_tone_capable_emoji(emoji)) return emoji
    val segments = split_segments(code_points(emoji))
    val targets = tone_targets(segments).toSet()
    val builder = StringBuilder()
    segments.forEachIndexed { index, segment ->
        if (index > 0) builder.appendCodePoint(ZERO_WIDTH_JOINER)
        if (index in targets) {
            builder.appendCodePoint(segment[0])
            builder.appendCodePoint(modifier)
            val rest = segment.drop(1)
            val trimmed = if (rest.firstOrNull() == VARIATION_SELECTOR) rest.drop(1) else rest
            trimmed.forEach { builder.appendCodePoint(it) }
        } else {
            segment.forEach { builder.appendCodePoint(it) }
        }
    }
    return builder.toString()
}
