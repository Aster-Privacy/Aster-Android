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

package org.astermail.android.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object app_language {
    private const val PREFS_NAME = "aster_app_language"
    private const val KEY_CODE = "code"

    val supported: List<Pair<String, String>> = listOf(
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "it" to "Italiano",
        "pt" to "Português",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "tr" to "Türkçe",
        "ru" to "Русский",
        "zh" to "简体中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "ar" to "العربية",
        "hi" to "हिन्दी",
    )

    fun stored_code(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CODE, null)

    fun store_code(context: Context, code: String?): Boolean {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()

        if (code == null) editor.remove(KEY_CODE) else editor.putString(KEY_CODE, code)

        return editor.commit()
    }

    fun is_supported(code: String?): Boolean = supported.any { it.first == code }

    fun normalize_code(value: String?): String? {
        val trimmed = value?.trim().orEmpty()

        if (trimmed.isEmpty()) return null

        supported.firstOrNull { it.second.equals(trimmed, ignoreCase = true) }?.let { return it.first }
        supported.firstOrNull { it.first.equals(trimmed, ignoreCase = true) }?.let { return it.first }

        val base = trimmed.lowercase(java.util.Locale.ROOT).substringBefore('-').substringBefore('_')

        return supported.firstOrNull { it.first == base }?.first
    }

    fun to_locale(code: String): Locale = when (code) {
        "zh" -> Locale.forLanguageTag("zh-CN")
        else -> Locale.forLanguageTag(code.replace('_', '-'))
    }

    fun wrap(context: Context): Context {
        val code = stored_code(context) ?: return context

        if (!is_supported(code)) return context

        val locale = to_locale(code)

        if (locale.language.isEmpty()) return context

        val configuration = Configuration(context.resources.configuration)

        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    fun apply(context: Context): Context {
        val code = stored_code(context) ?: return context

        if (!is_supported(code)) return context

        val locale = to_locale(code)

        if (locale.language.isEmpty()) return context

        Locale.setDefault(locale)

        return wrap(context)
    }
}
