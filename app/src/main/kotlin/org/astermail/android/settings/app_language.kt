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
    private const val KEY_EXPLICIT = "explicit"
    private const val KEY_LEGACY_RESET = "legacy_reset_done"

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

    fun stored_code(context: Context): String? {
        reset_legacy_pin(context)

        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_CODE, null)
    }

    fun store_code(context: Context, code: String?): Boolean {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()

        if (code == null) {
            editor.remove(KEY_CODE)
            editor.remove(KEY_EXPLICIT)
        } else {
            editor.putString(KEY_CODE, code)
            editor.putBoolean(KEY_EXPLICIT, true)
        }

        editor.putBoolean(KEY_LEGACY_RESET, true)

        return editor.commit()
    }

    fun system_code(context: Context): String? {
        val locales = context.resources.configuration.locales

        for (index in 0 until locales.size()) {
            val candidate = normalize_code(locales[index].toLanguageTag())

            if (candidate != null) return candidate
        }

        return null
    }

    private fun reset_legacy_pin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (prefs.getBoolean(KEY_LEGACY_RESET, false)) return

        val editor = prefs.edit().putBoolean(KEY_LEGACY_RESET, true)

        if (!prefs.getBoolean(KEY_EXPLICIT, false)) editor.remove(KEY_CODE)

        editor.apply()
    }

    fun synced_code_to_store(language: String?, explicit: Boolean, stored: String?): String? {
        val candidate = normalize_code(language) ?: return null

        if (!explicit && candidate == "en") return null

        if (candidate == stored) return null

        return candidate
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

        return language_context(context, context.createConfigurationContext(configuration))
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
