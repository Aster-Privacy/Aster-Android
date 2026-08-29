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

package org.astermail.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.design.AsterThemeMode
import org.astermail.android.design.ColorThemeId
import org.astermail.android.storage.ThemeMode
import org.astermail.android.ui.theme.AccessibilityState
import org.astermail.android.ui.theme.ThemeViewModel
import org.astermail.android.ui.theme.local_accessibility
import org.astermail.android.ui.theme.local_text_scale

@Composable
fun aster_theme_root(content: @Composable () -> Unit) {
    val theme_vm: ThemeViewModel = hiltViewModel()
    val mode_state by theme_vm.theme_mode.collectAsStateWithLifecycle()
    val text_size_state by theme_vm.text_size.collectAsStateWithLifecycle()
    val high_contrast by theme_vm.high_contrast.collectAsStateWithLifecycle()
    val reduce_transparency by theme_vm.reduce_transparency.collectAsStateWithLifecycle()
    val reduce_motion by theme_vm.reduce_motion.collectAsStateWithLifecycle()
    val compact_mode by theme_vm.compact_mode.collectAsStateWithLifecycle()
    val text_spacing by theme_vm.text_spacing.collectAsStateWithLifecycle()
    val underline_links by theme_vm.underline_links.collectAsStateWithLifecycle()
    val dyslexia_font by theme_vm.dyslexia_font.collectAsStateWithLifecycle()
    val haptic_enabled by theme_vm.haptic_enabled.collectAsStateWithLifecycle()
    val color_theme by theme_vm.color_theme.collectAsStateWithLifecycle()
    val custom_theme_seed by theme_vm.custom_theme_seed.collectAsStateWithLifecycle()
    val custom_theme_overrides by theme_vm.custom_theme_overrides.collectAsStateWithLifecycle()
    val font_choice by theme_vm.font_choice.collectAsStateWithLifecycle()
    val resolved_mode = when (mode_state) {
        ThemeMode.system -> AsterThemeMode.system
        ThemeMode.light -> AsterThemeMode.light
        ThemeMode.dark -> AsterThemeMode.dark
    }
    val a11y = remember(
        high_contrast,
        reduce_transparency,
        reduce_motion,
        compact_mode,
        text_spacing,
        underline_links,
        dyslexia_font,
        haptic_enabled,
    ) {
        AccessibilityState(
            high_contrast = high_contrast,
            reduce_transparency = reduce_transparency,
            reduce_motion = reduce_motion,
            compact_mode = compact_mode,
            text_spacing = text_spacing,
            underline_links = underline_links,
            dyslexia_font = dyslexia_font,
            haptic_enabled = haptic_enabled,
        )
    }
    val dyslexia_family = remember(dyslexia_font) {
        if (dyslexia_font) {
            FontFamily(Font(R.font.opendyslexic_regular, FontWeight.Normal))
        } else null
    }

    AsterTheme(
        theme_mode = resolved_mode,
        high_contrast = high_contrast,
        reduce_transparency = reduce_transparency,
        dyslexia_font = dyslexia_family,
        text_spacing = text_spacing,
        color_theme_id = ColorThemeId.from_key(color_theme),
        custom_theme_seed = custom_theme_seed,
        custom_theme_overrides = custom_theme_overrides,
        font_choice = font_choice,
    ) {
        val base_density = LocalDensity.current
        val compact_factor = if (compact_mode) 0.9f else 1f
        val scaled_density = remember(base_density, compact_factor, text_size_state.scale) {
            Density(
                density = base_density.density * compact_factor,
                fontScale = base_density.fontScale * text_size_state.scale,
            )
        }
        CompositionLocalProvider(
            LocalDensity provides scaled_density,
            local_text_scale provides text_size_state.scale,
            local_accessibility provides a11y,
            org.astermail.android.design.local_reduce_motion provides a11y.reduce_motion,
        ) {
            val colors = AsterMaterial.colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bg_primary),
            ) {
                content()
                app_toast_host()
            }
        }
    }
}
