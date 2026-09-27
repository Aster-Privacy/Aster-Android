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

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

data class language_option(
    val code: String,
    val native_name: String,
    val web_label: String,
)

object app_language {
    private const val PREFS_NAME = "aster_app_language"
    private const val KEY_CODE = "code"
    private const val KEY_EXPLICIT = "explicit"
    private const val KEY_LEGACY_RESET = "legacy_reset_done"
    private const val KEY_PLATFORM_MIGRATED = "platform_migrated"

    val supported: List<language_option> = listOf(
        language_option("en", "English", "English"),
        language_option("es", "Español", "Español"),
        language_option("fr", "Français", "Français"),
        language_option("de", "Deutsch", "Deutsch"),
        language_option("it", "Italiano", "Italiano"),
        language_option("pt", "Português (Portugal)", "Português (Portugal)"),
        language_option("pt-BR", "Português (Brasil)", "Português (Brazil)"),
        language_option("nl", "Nederlands", "Nederlands"),
        language_option("pl", "Polski", "Polski"),
        language_option("tr", "Türkçe", "Türkçe"),
        language_option("ru", "Русский", "Русский"),
        language_option("zh-CN", "简体中文", "简体中文 (Simplified)"),
        language_option("ja", "日本語", "日本語"),
        language_option("ko", "한국어", "한국어"),
        language_option("ar", "العربية", "العربية"),
        language_option("hi", "हिन्दी", "हिन्दी"),
    )

    val platform_managed: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun locale_manager(context: Context): LocaleManager? =
        if (platform_managed) context.getSystemService(LocaleManager::class.java) else null

    fun stored_code(context: Context): String? {
        reset_legacy_pin(context)

        if (platform_managed && prefs(context).getBoolean(KEY_PLATFORM_MIGRATED, false)) {
            val manager = locale_manager(context)

            if (manager != null) {
                val locales = manager.applicationLocales

                return if (locales.isEmpty) null else normalize_code(locales[0]?.toLanguageTag())
            }
        }

        return normalize_code(prefs(context).getString(KEY_CODE, null))
    }

    fun store_code(context: Context, code: String?): Boolean {
        val editor = prefs(context).edit()

        if (code == null) {
            editor.remove(KEY_CODE)
            editor.remove(KEY_EXPLICIT)
        } else {
            editor.putString(KEY_CODE, code)
            editor.putBoolean(KEY_EXPLICIT, true)
        }

        editor.putBoolean(KEY_LEGACY_RESET, true)

        val saved = editor.commit()

        if (platform_managed) {
            locale_manager(context)?.let { manager ->
                manager.applicationLocales = locale_list(code)
                prefs(context).edit().putBoolean(KEY_PLATFORM_MIGRATED, true).apply()
            }
        } else {
            apply_to_application(context)
        }

        return saved
    }

    fun migrate_to_platform(context: Context) {
        if (!platform_managed) return

        val store = prefs(context)

        if (store.getBoolean(KEY_PLATFORM_MIGRATED, false)) return

        val manager = locale_manager(context) ?: return
        val code = normalize_code(store.getString(KEY_CODE, null))

        if (code != null && manager.applicationLocales.isEmpty) {
            manager.applicationLocales = locale_list(code)
        }

        store.edit().putBoolean(KEY_PLATFORM_MIGRATED, true).apply()
    }

    private fun locale_list(code: String?): LocaleList =
        if (code == null) LocaleList.getEmptyLocaleList() else LocaleList(to_locale(code))

    fun system_code(context: Context): String? {
        val locales = context.resources.configuration.locales

        for (index in 0 until locales.size()) {
            val candidate = normalize_code(locales[index].toLanguageTag())

            if (candidate != null) return candidate
        }

        return null
    }

    private fun reset_legacy_pin(context: Context) {
        val store = prefs(context)

        if (store.getBoolean(KEY_LEGACY_RESET, false)) return

        val editor = store.edit().putBoolean(KEY_LEGACY_RESET, true)

        if (!store.getBoolean(KEY_EXPLICIT, false)) editor.remove(KEY_CODE)

        editor.apply()
    }

    fun synced_code_to_store(language: String?, explicit: Boolean, stored: String?): String? {
        val candidate = normalize_code(language) ?: return null

        if (!explicit && candidate == "en") return null

        if (candidate == normalize_code(stored)) return null

        return candidate
    }

    fun is_supported(code: String?): Boolean = supported.any { it.code == code }

    fun option_for(code: String?): language_option? = supported.firstOrNull { it.code == code }

    fun server_value(code: String?): String = option_for(code)?.web_label.orEmpty()

    fun normalize_code(value: String?): String? {
        val trimmed = value?.trim().orEmpty()

        if (trimmed.isEmpty()) return null

        supported.firstOrNull { it.web_label.equals(trimmed, ignoreCase = true) }?.let { return it.code }
        supported.firstOrNull { it.native_name.equals(trimmed, ignoreCase = true) }?.let { return it.code }

        val tag = trimmed.replace('_', '-')

        supported.firstOrNull { it.code.equals(tag, ignoreCase = true) }?.let { return it.code }

        val lowered = tag.lowercase(Locale.ROOT)
        val base = lowered.substringBefore('-')

        if (base == "pt") {
            val region = lowered.split('-').drop(1)

            return if ("br" in region) "pt-BR" else "pt"
        }

        if (base == "zh") return "zh-CN"

        supported.firstOrNull { it.code == base }?.let { return it.code }

        val label_base = lowered.substringBefore(" (")

        return supported.firstOrNull { it.native_name.substringBefore(" (").lowercase(Locale.ROOT) == label_base }?.code
    }

    fun to_locale(code: String): Locale = Locale.forLanguageTag(code.replace('_', '-'))

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

    @Suppress("DEPRECATION")
    fun apply_to_application(context: Context) {
        if (platform_managed) return

        val application = context.applicationContext ?: context
        val resources = application.resources
        val configuration = Configuration(resources.configuration)
        val code = stored_code(application)

        if (code != null && is_supported(code)) {
            val locale = to_locale(code)

            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            Locale.setDefault(locale)
        } else {
            val system_locales = Resources.getSystem().configuration.locales

            configuration.setLocales(system_locales)
            if (!system_locales.isEmpty) {
                configuration.setLayoutDirection(system_locales[0])
                Locale.setDefault(system_locales[0])
            }
        }

        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
}
