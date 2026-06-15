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

package org.astermail.android.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import org.astermail.android.storage.TextSize
import org.astermail.android.storage.ThemeMode
import org.astermail.android.storage.ThemeStore

data class AccessibilityState(
    val high_contrast: Boolean = false,
    val reduce_transparency: Boolean = false,
    val reduce_motion: Boolean = false,
    val compact_mode: Boolean = false,
    val text_spacing: Boolean = false,
    val underline_links: Boolean = false,
    val dyslexia_font: Boolean = false,
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val theme_store: ThemeStore,
) : ViewModel() {
    val theme_mode: StateFlow<ThemeMode> = theme_store.theme_mode
    val text_size: StateFlow<TextSize> = theme_store.text_size
    val signature: StateFlow<String> = theme_store.signature_text
    val high_contrast: StateFlow<Boolean> = theme_store.high_contrast
    val reduce_transparency: StateFlow<Boolean> = theme_store.reduce_transparency
    val reduce_motion: StateFlow<Boolean> = theme_store.reduce_motion
    val compact_mode: StateFlow<Boolean> = theme_store.compact_mode
    val text_spacing: StateFlow<Boolean> = theme_store.text_spacing
    val underline_links: StateFlow<Boolean> = theme_store.underline_links
    val dyslexia_font: StateFlow<Boolean> = theme_store.dyslexia_font
    val onboarding_seen: StateFlow<Boolean> = theme_store.onboarding_seen

    fun set_mode(mode: ThemeMode) = theme_store.set_theme_mode(mode)
    fun set_text_size(size: TextSize) = theme_store.set_text_size(size)
    fun set_text_size_from_key(key: String) = theme_store.set_text_size_from_key(key)
    fun set_signature(text: String) = theme_store.set_signature(text)
    fun set_high_contrast(v: Boolean) = theme_store.set_high_contrast(v)
    fun set_reduce_transparency(v: Boolean) = theme_store.set_reduce_transparency(v)
    fun set_reduce_motion(v: Boolean) = theme_store.set_reduce_motion(v)
    fun set_compact_mode(v: Boolean) = theme_store.set_compact_mode(v)
    fun set_text_spacing(v: Boolean) = theme_store.set_text_spacing(v)
    fun set_underline_links(v: Boolean) = theme_store.set_underline_links(v)
    fun set_dyslexia_font(v: Boolean) = theme_store.set_dyslexia_font(v)
    fun mark_onboarding_seen() = theme_store.set_onboarding_seen(true)
}

val local_text_scale = compositionLocalOf { 1.0f }
val local_accessibility = compositionLocalOf { AccessibilityState() }
