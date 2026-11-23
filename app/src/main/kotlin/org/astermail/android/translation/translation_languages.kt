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

package org.astermail.android.translation

import java.util.Locale

const val PIVOT_LANGUAGE = "en"

val translation_language_codes: List<String> = listOf(
    "ar", "de", "en", "es", "fr", "it", "ja", "ko", "nl", "pl", "pt", "ru", "tr", "zh",
)

fun normalize_language_code(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val base = value.trim().lowercase(Locale.US).split('-', '_').firstOrNull().orEmpty()
    return if (base.isNotBlank() && translation_language_codes.contains(base)) base else null
}

fun language_display_name(code: String): String {
    val locale = Locale.forLanguageTag(code)
    val name = locale.getDisplayLanguage(Locale.getDefault())
    return if (name.isBlank() || name == code) {
        code.uppercase(Locale.US)
    } else {
        name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

const val TRANSLATION_INACTIVITY_TIMEOUT_MS = 90_000L

val limited_quality_languages: Set<String> = setOf("ja", "ko", "zh")

fun translation_quality_limited(code: String?): Boolean =
    code != null && limited_quality_languages.contains(code)

data class translated_parts(val body: List<String>, val subject: String?)

fun split_subject_segment(segments: List<String>, subject_included: Boolean): translated_parts {
    if (!subject_included || segments.isEmpty()) return translated_parts(segments, null)
    val subject = segments.last().trim().ifEmpty { null }
    return translated_parts(segments.dropLast(1), subject)
}

private const val MIN_REPLY_DETECTION_CHARACTERS = 40

fun detection_text_of(text: String): String {
    val lines = text.lines().map { org.astermail.android.ui.mail.normalize_quote_line(it) }
    val quote = org.astermail.android.ui.mail.find_quote_start(lines)
        ?: org.astermail.android.ui.mail.find_trailing_quote(lines)
    val reply_lines = if (quote != null) lines.subList(0, quote.start) else lines
    val reply = reply_lines
        .filterNot { org.astermail.android.ui.mail.is_quoted_line(it) }
        .joinToString("\n")
        .trim()
    return if (reply.count { !it.isWhitespace() } >= MIN_REPLY_DETECTION_CHARACTERS) reply else text
}
