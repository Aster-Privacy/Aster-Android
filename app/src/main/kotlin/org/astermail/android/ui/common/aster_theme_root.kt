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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import org.astermail.android.ui.theme.local_background_image
import org.astermail.android.ui.theme.local_text_scale
import org.astermail.android.ui.theme.draw_theme_animation
import org.astermail.android.ui.theme.draw_theme_background
import org.astermail.android.ui.theme.draw_theme_veil
import org.astermail.android.ui.theme.draw_theme_window_slice
import org.astermail.android.ui.theme.remember_active_theme_bitmap
import org.astermail.android.ui.theme.remember_theme_animation

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
    val background_image by theme_vm.background_image.collectAsStateWithLifecycle()
    val background_opacity by theme_vm.background_opacity.collectAsStateWithLifecycle()
    val animate_background by theme_vm.animate_background.collectAsStateWithLifecycle()
    val app_context = LocalContext.current.applicationContext
    LaunchedEffect(mode_state, color_theme) {
        apply_app_night_mode(app_context, mode_state, color_theme)
    }
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

    val custom_image_meta by org.astermail.android.ui.theme.custom_theme_image.meta.collectAsState()
    val manifest_catalog by org.astermail.android.ui.theme.theme_manifest.catalog.collectAsState()
    val manifest_context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(background_image, manifest_catalog.size) {
        val needs_manifest = background_image != org.astermail.android.ui.theme.no_theme_background &&
            background_image != org.astermail.android.ui.theme.custom_theme_background &&
            org.astermail.android.ui.theme.theme_background_for(background_image) == null
        if (needs_manifest) {
            runCatching { org.astermail.android.ui.theme.theme_manifest.load(manifest_context) }
        }
    }
    val active_backdrop = remember(background_image, custom_image_meta, manifest_catalog) {
        org.astermail.android.ui.theme.theme_background_for(background_image)
    }?.takeIf { !reduce_transparency }
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
        glass = active_backdrop != null,
        glass_tint = active_backdrop?.tint ?: androidx.compose.ui.graphics.Color.Unspecified,
        glass_opacity = background_opacity,
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
            local_background_image provides background_image,
            org.astermail.android.ui.theme.local_background_opacity provides background_opacity,
            org.astermail.android.design.local_reduce_motion provides a11y.reduce_motion,
        ) {
            val colors = AsterMaterial.colors
            val backdrop = if (colors.is_glass) {
                remember_active_theme_bitmap()
            } else null
            val blur = if (colors.is_glass) {
                org.astermail.android.ui.theme.remember_active_theme_blur()
            } else null
            val animation = if (backdrop != null) {
                remember_theme_animation(animate_background && !a11y.reduce_motion)
            } else null
            val container = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
            val veil_ink = colors.bg_primary
            val acrylic_source = remember(blur, container, veil_ink) {
                blur?.let { bitmap ->
                    org.astermail.android.design.AcrylicSource { origin ->
                        val window = androidx.compose.ui.geometry.Size(
                            container.width.toFloat(),
                            container.height.toFloat(),
                        )
                        draw_theme_window_slice(bitmap, window, origin)
                        drawRect(color = veil_ink.copy(alpha = org.astermail.android.ui.theme.theme_veil_alpha))
                    }
                }
            }
            CompositionLocalProvider(
                org.astermail.android.design.local_acrylic provides acrylic_source,
                androidx.compose.foundation.LocalOverscrollFactory provides
                    if (colors.is_translucent) null else androidx.compose.foundation.LocalOverscrollFactory.current,
            ) {
                Box(modifier = Modifier.fillMaxSize().background(colors.bg_primary)) {
                    if (backdrop != null) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer()
                                .drawBehind {
                                    if (animation != null) {
                                        draw_theme_animation(animation)
                                    } else {
                                        draw_theme_background(backdrop)
                                    }
                                    draw_theme_veil(colors.bg_primary)
                                },
                        )
                    }
                    deferred_clipboard_provider(content)
                    app_toast_host()
                }
            }
        }
    }
}
