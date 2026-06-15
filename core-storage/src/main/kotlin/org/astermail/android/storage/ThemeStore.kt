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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeMode { system, light, dark }

enum class TextSize(val scale: Float) {
    small(0.85f),
    default_size(1.0f),
    large(1.15f),
    extra_large(1.3f),
}

private val Context.theme_data_store by preferencesDataStore(name = "aster_theme_prefs")

class ThemeStore(context: Context) {

    private val app_context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val key_theme_mode = stringPreferencesKey("theme_mode")
    private val key_text_scale = floatPreferencesKey("text_scale")
    private val key_signature = stringPreferencesKey("signature_text")
    private val key_high_contrast = booleanPreferencesKey("high_contrast")
    private val key_reduce_transparency = booleanPreferencesKey("reduce_transparency")
    private val key_reduce_motion = booleanPreferencesKey("reduce_motion")
    private val key_compact_mode = booleanPreferencesKey("compact_mode")
    private val key_text_spacing = booleanPreferencesKey("text_spacing")
    private val key_underline_links = booleanPreferencesKey("underline_links")
    private val key_dyslexia_font = booleanPreferencesKey("dyslexia_font")
    private val key_onboarding_seen = booleanPreferencesKey("onboarding_seen")

    val theme_mode: StateFlow<ThemeMode> = app_context.theme_data_store.data
        .map { prefs -> parse_mode(prefs[key_theme_mode]) }
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.system)

    val text_size: StateFlow<TextSize> = app_context.theme_data_store.data
        .map { prefs -> parse_text_size(prefs[key_text_scale]) }
        .stateIn(scope, SharingStarted.Eagerly, TextSize.default_size)

    val signature_text: StateFlow<String> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_signature].orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, "")

    val high_contrast: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_high_contrast] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val reduce_transparency: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_reduce_transparency] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val reduce_motion: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_reduce_motion] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val compact_mode: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_compact_mode] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val text_spacing: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_text_spacing] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val underline_links: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_underline_links] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val dyslexia_font: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_dyslexia_font] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val onboarding_seen: StateFlow<Boolean> = app_context.theme_data_store.data
        .map { prefs -> prefs[key_onboarding_seen] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

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

    fun set_dyslexia_font(enabled: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_dyslexia_font] = enabled } }
    }

    fun set_onboarding_seen(seen: Boolean) {
        scope.launch { app_context.theme_data_store.edit { it[key_onboarding_seen] = seen } }
    }

    suspend fun clear() {
        app_context.theme_data_store.edit { it.clear() }
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
}
