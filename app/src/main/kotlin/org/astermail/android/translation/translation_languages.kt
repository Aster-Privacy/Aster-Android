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
