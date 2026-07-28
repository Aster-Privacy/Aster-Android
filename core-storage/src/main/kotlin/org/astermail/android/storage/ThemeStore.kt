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

package org.astermail.android.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ThemeMode { system, light, dark }

enum class TextSize(val scale: Float) {
    small(0.85f),
    default_size(1.0f),
    large(1.15f),
    extra_large(1.3f),
}

private val Context.theme_data_store by preferencesDataStore(name = "aster_theme_prefs")

data class ThemeBootSnapshot(
    val theme_mode: ThemeMode,
    val color_theme: String,
    val custom_theme_seed: String,
    val custom_theme_overrides: Map<String, String>,
    val high_contrast: Boolean,
)

class ThemeStore(context: Context) {

    private val app_context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val boot_cache = app_context.getSharedPreferences(BOOT_CACHE_NAME, Context.MODE_PRIVATE)

    private fun cached_string(name: String, fallback: String): String =
        try {
            boot_cache.getString(name, fallback) ?: fallback
        } catch (_: Throwable) {
            fallback
        }

    private fun cached_bool(name: String, fallback: Boolean): Boolean =
        try {
            boot_cache.getBoolean(name, fallback)
        } catch (_: Throwable) {
            fallback
        }

    private fun cached_float(name: String, fallback: Float): Float =
        try {
            boot_cache.getFloat(name, fallback)
        } catch (_: Throwable) {
            fallback
        }

    private fun cache_string(name: String, value: String) {
        boot_cache.edit().putString(name, value).apply()
    }

    private fun cache_bool(name: String, value: Boolean) {
        boot_cache.edit().putBoolean(name, value).apply()
    }

    private fun cache_float(name: String, value: Float) {
        boot_cache.edit().putFloat(name, value).apply()
    }

    private val key_theme_mode = stringPreferencesKey("theme_mode")
    private val key_text_scale = floatPreferencesKey("text_scale")
    private val key_signature = stringPreferencesKey("signature_text")
    private val key_high_contrast = booleanPreferencesKey("high_contrast")
    private val key_reduce_transparency = booleanPreferencesKey("reduce_transparency")
    private val key_reduce_motion = booleanPreferencesKey("reduce_motion")
    private val key_compact_mode = booleanPreferencesKey("compact_mode")
    private val key_text_spacing = booleanPreferencesKey("text_spacing")
    private val key_underline_links = booleanPreferencesKey("underline_links")
    private val key_haptic_enabled = booleanPreferencesKey("haptic_enabled")
    private val key_dyslexia_font = booleanPreferencesKey("dyslexia_font")
    private val key_onboarding_seen = booleanPreferencesKey("onboarding_seen")
    private val key_color_theme = stringPreferencesKey("color_theme")
    private val key_custom_theme_seed = stringPreferencesKey("custom_theme_seed")
    private val key_custom_theme_overrides = stringPreferencesKey("custom_theme_overrides")
    private val key_font_choice = stringPreferencesKey("font_choice")

    val theme_mode: StateFlow<ThemeMode> = app_context.theme_data_store.data
        .map { prefs -> parse_mode(prefs[key_theme_mode]) }
        .onEach { cache_string("theme_mode", it.name) }
        .stateIn(scope, SharingStarted.Eagerly, parse_mode(cached_string("theme_mode", ThemeMode.system.name)))

    val text_size: StateFlow<TextSize> = app_context.theme_data_store.data
        .map { prefs -> parse_text_size(prefs[key_text_scale]) }
        .onEach { cache_float("text_scale", it.scale) }
        .stateIn(scope, SharingStarted.Eagerly, parse_text_size(cached_float("text_scale", TextSize.default_size.scale)))

    val signature_text: StateFlow<String> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_signature].orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, "")

    val high_contrast: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_high_contrast] ?: false }
        .onEach { cache_bool("high_contrast", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_bool("high_contrast", false))

    val reduce_transparency: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_reduce_transparency] ?: false }
        .onEach { cache_bool("reduce_transparency", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_bool("reduce_transparency", false))

    val reduce_motion: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_reduce_motion] ?: false }
        .onEach { cache_bool("reduce_motion", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_bool("reduce_motion", false))

    val compact_mode: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_compact_mode] ?: false }
        .onEach { cache_bool("compact_mode", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_bool("compact_mode", false))

    val text_spacing: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_text_spacing] ?: false }
        .onEach { cache_bool("text_spacing", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_bool("text_spacing", false))

    val underline_links: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_underline_links] ?: false }
        .onEach { cache_bool("underline_links", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_bool("underline_links", false))

    val haptic_enabled: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_haptic_enabled] ?: true }
        .onEach { cache_bool("haptic_enabled", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_bool("haptic_enabled", true))

    val dyslexia_font: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_dyslexia_font] ?: false }
        .onEach { cache_bool("dyslexia_font", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_bool("dyslexia_font", false))

    val onboarding_seen: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_onboarding_seen] ?: false }
        .onEach { cache_bool("onboarding_seen", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_bool("onboarding_seen", false))

    val color_theme: StateFlow<String> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_color_theme] ?: "default" }
        .onEach { cache_string("color_theme", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_string("color_theme", "default"))

    val custom_theme_seed: StateFlow<String> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_custom_theme_seed] ?: "#3b82f6" }
        .onEach { cache_string("custom_theme_seed", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_string("custom_theme_seed", "#3b82f6"))

    val custom_theme_overrides: StateFlow<Map<String, String>> = app_context.theme_data_store.data
        .map { prefs -> parse_overrides(prefs[key_custom_theme_overrides]) }
        .onEach { cache_string("custom_theme_overrides", Json.encodeToString(it)) }
        .stateIn(scope, SharingStarted.Eagerly, parse_overrides(cached_string("custom_theme_overrides", "")))

    val font_choice: StateFlow<String> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_font_choice] ?: "default" }
        .onEach { cache_string("font_choice", it) }
        .stateIn(scope, SharingStarted.Eagerly, cached_string("font_choice", "default"))

    fun set_theme_mode(mode: ThemeMode) {
        scope.launch {
            app_context.theme_data_store.edit { it[key_theme_mode] = mode.name }
        }
    }

    fun set_text_size(size: TextSize) {
        scope.launch {
            app_context.theme_data_store.edit { it[key_text_scale] = size.scale }
        }
    }

    fun set_signature(text: String) {
        scope.launch {
            app_context.theme_data_store.edit { it[key_signature] = text }
        }
    }

    fun set_high_contrast(enabled: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_high_contrast] = enabled } }
    }

    fun set_reduce_transparency(enabled: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_reduce_transparency] = enabled } }
    }

    fun set_reduce_motion(enabled: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_reduce_motion] = enabled } }
    }

    fun set_compact_mode(enabled: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_compact_mode] = enabled } }
    }

    fun set_text_spacing(enabled: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_text_spacing] = enabled } }
    }

    fun set_underline_links(enabled: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_underline_links] = enabled } }
    }

    fun set_haptic_enabled(enabled: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_haptic_enabled] = enabled } }
    }

    fun set_dyslexia_font(enabled: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_dyslexia_font] = enabled } }
    }

    fun set_onboarding_seen(seen: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_onboarding_seen] = seen } }
    }

    fun set_color_theme(id: String) {
        scope.launch { app_context.theme_data_store.edit { it[key_color_theme] = id } }
    }

    fun set_custom_theme_seed(hex: String) {
        scope.launch { app_context.theme_data_store.edit { it[key_custom_theme_seed] = hex } }
    }

    fun set_custom_theme_overrides(overrides: Map<String, String>) {
        scope.launch {
            app_context.theme_data_store.edit {
                it[key_custom_theme_overrides] = Json.encodeToString(overrides)
            }
        }
    }

    fun set_font_choice(id: String) {
        scope.launch { app_context.theme_data_store.edit { it[key_font_choice] = id } }
    }

    suspend fun clear() {
        app_context.theme_data_store.edit { it.clear() }
        boot_cache.edit().clear().apply()
    }

    private fun parse_mode(raw: String?): ThemeMode = when (raw) {
        ThemeMode.light.name -> ThemeMode.light
        ThemeMode.dark.name -> ThemeMode.dark
        else -> ThemeMode.system
    }

    fun set_text_size_from_key(key: String) {
        val size = when (key) {
            "small" -> TextSize.small
            "large" -> TextSize.large
            "extra_large" -> TextSize.extra_large
            else -> TextSize.default_size
        }
        set_text_size(size)
    }

    private fun parse_text_size(raw: Float?): TextSize = when {
        raw == null -> TextSize.default_size
        raw <= 0.9f -> TextSize.small
        raw >= 1.25f -> TextSize.extra_large
        raw >= 1.1f -> TextSize.large
        else -> TextSize.default_size
    }

    private fun parse_overrides(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            Json.decodeFromString(raw)
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    companion object {
        private const val BOOT_CACHE_NAME = "aster_theme_boot"

        fun boot_snapshot(context: Context): ThemeBootSnapshot {
            val prefs = try {
                context.applicationContext.getSharedPreferences(BOOT_CACHE_NAME, Context.MODE_PRIVATE)
            } catch (_: Throwable) {
                null
            }
            val mode = when (prefs?.getString("theme_mode", null)) {
                ThemeMode.light.name -> ThemeMode.light
                ThemeMode.dark.name -> ThemeMode.dark
                else -> ThemeMode.system
            }
            val overrides = try {
                val raw = prefs?.getString("custom_theme_overrides", null)
                if (raw.isNullOrBlank()) emptyMap() else Json.decodeFromString<Map<String, String>>(raw)
            } catch (_: Throwable) {
                emptyMap()
            }
            return ThemeBootSnapshot(
                theme_mode = mode,
                color_theme = prefs?.getString("color_theme", null) ?: "default",
                custom_theme_seed = prefs?.getString("custom_theme_seed", null) ?: "#3b82f6",
                custom_theme_overrides = overrides,
                high_contrast = prefs?.getBoolean("high_contrast", false) ?: false,
            )
        }
    }
}
