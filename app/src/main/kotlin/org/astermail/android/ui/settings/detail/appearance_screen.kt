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

package org.astermail.android.ui.settings.detail

import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import androidx.compose.animation.animateContentSize
import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.astermail.android.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.ColorThemeId
import org.astermail.android.design.dynamic_color_supported
import org.astermail.android.design.ColorThemePalette
import org.astermail.android.design.AsterColorThemes
import org.astermail.android.design.MaterialThemeGenerator
import org.astermail.android.design.FONT_OPTIONS
import org.astermail.android.ui.theme.ThemeBackground
import org.astermail.android.ui.theme.no_theme_background
import org.astermail.android.ui.theme.theme_background_for
import org.astermail.android.design.preview_font_family_for
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.SquircleShape
import org.astermail.android.ui.mail.is_comfortable_density
import org.astermail.android.api.preferences.compose_font_size_labels
import org.astermail.android.api.preferences.effective_compose_font_color
import org.astermail.android.api.preferences.effective_compose_font_size
import org.astermail.android.api.preferences.normalize_compose_font_color
import org.astermail.android.api.preferences.normalize_compose_font_size
import org.astermail.android.api.preferences.effective_theme_values
import org.astermail.android.api.preferences.theme_sync_enabled
import org.astermail.android.api.preferences.with_theme_sync_enabled
import org.astermail.android.api.preferences.with_theme_values
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField

import org.astermail.android.settings.SaveStatus
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.storage.ThemeMode
import org.astermail.android.ui.settings.mail_rules.pickers.base_sheet
import org.astermail.android.ui.theme.ThemeViewModel
import org.astermail.android.settings.shared_settings_view_model

private val quick_seed_colors = listOf(
    "#3b82f6", "#a855f7", "#22c55e", "#f43f5e", "#f97316",
    "#14b88a", "#f5be0b", "#068fd4", "#84cc16", "#e0399d",
)

private val quick_compose_text_colors = listOf(
    "#1a73e8", "#0f172a", "#475569", "#b91c1c", "#c2410c",
    "#15803d", "#0f766e", "#7c3aed", "#be185d", "#a16207",
)

private fun parse_hex_color(hex: String): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Throwable) { Color.Gray }

private fun compose_font_size_label_res(label: String): Int = when (label) {
    "small" -> R.string.compose_size_small
    "large" -> R.string.compose_size_large
    "huge" -> R.string.compose_size_huge
    else -> R.string.compose_size_normal
}

private fun font_label_res(id: String): Int = when (id) {
    "default" -> R.string.font_option_default
    "system" -> R.string.font_option_system
    "inter" -> R.string.font_option_inter
    "roboto" -> R.string.font_option_roboto
    "nunito" -> R.string.font_option_nunito
    "merriweather" -> R.string.font_option_merriweather
    "lora" -> R.string.font_option_lora
    "system_mono" -> R.string.font_option_system_mono
    "jetbrains_mono" -> R.string.font_option_jetbrains_mono
    "poppins" -> R.string.font_option_poppins
    "montserrat" -> R.string.font_option_montserrat
    "work_sans" -> R.string.font_option_work_sans
    "ibm_plex_sans" -> R.string.font_option_ibm_plex_sans
    "ibm_plex_mono" -> R.string.font_option_ibm_plex_mono
    "space_mono" -> R.string.font_option_space_mono
    "playfair_display" -> R.string.font_option_playfair_display
    "libre_baskerville" -> R.string.font_option_libre_baskerville
    "pt_serif" -> R.string.font_option_pt_serif
    "raleway" -> R.string.font_option_raleway
    else -> R.string.font_option_default
}

internal fun color_theme_label_res(id: ColorThemeId): Int = when (id) {
    ColorThemeId.default -> R.string.color_theme_default
    ColorThemeId.custom -> R.string.color_theme_custom
    ColorThemeId.dynamic -> R.string.theme_dynamic
    ColorThemeId.purple -> R.string.color_theme_purple
    ColorThemeId.green -> R.string.color_theme_green
    ColorThemeId.rose -> R.string.color_theme_rose
    ColorThemeId.orange -> R.string.color_theme_orange
    ColorThemeId.teal -> R.string.color_theme_teal
    ColorThemeId.indigo -> R.string.color_theme_indigo
    ColorThemeId.amber -> R.string.color_theme_amber
    ColorThemeId.cyan -> R.string.color_theme_cyan
    ColorThemeId.slate -> R.string.color_theme_slate
    ColorThemeId.aster_blue -> R.string.color_theme_aster_blue
    ColorThemeId.lime -> R.string.color_theme_lime
    ColorThemeId.fuchsia -> R.string.color_theme_fuchsia
    ColorThemeId.emerald -> R.string.color_theme_emerald
    ColorThemeId.pink -> R.string.color_theme_pink
    ColorThemeId.black -> R.string.color_theme_black
}

private val preset_swatch_ids = listOf(
    ColorThemeId.purple, ColorThemeId.green, ColorThemeId.rose, ColorThemeId.orange,
    ColorThemeId.teal, ColorThemeId.indigo, ColorThemeId.amber, ColorThemeId.cyan,
    ColorThemeId.slate, ColorThemeId.lime, ColorThemeId.fuchsia,
    ColorThemeId.emerald, ColorThemeId.pink, ColorThemeId.black,
)

@OptIn(ExperimentalLayoutApi::class)
private data class ThemeOpacityLevel(val value: Float, val title: Int, val subtitle: Int)

private val theme_opacity_levels = listOf(
    ThemeOpacityLevel(0.38f, R.string.theme_opacity_clear, R.string.theme_opacity_clear_subtitle),
    ThemeOpacityLevel(0.62f, R.string.theme_opacity_glass, R.string.theme_opacity_glass_subtitle),
    ThemeOpacityLevel(0.82f, R.string.theme_opacity_frosted, R.string.theme_opacity_frosted_subtitle),
    ThemeOpacityLevel(1f, R.string.theme_opacity_solid, R.string.theme_opacity_solid_subtitle),
)

internal const val solid_theme_opacity = 1f
internal const val default_glass_opacity = 0.62f

private val glass_opacity_levels = theme_opacity_levels.filter { it.value < solid_theme_opacity }

private fun nearest_theme_opacity(value: Float): Float =
    theme_opacity_levels.minBy { kotlin.math.abs(it.value - value) }.value

@Composable
fun AppearanceScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: ThemeViewModel = hiltViewModel()
    val settings_vm: SettingsViewModel = shared_settings_view_model()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val mode by vm.theme_mode.collectAsStateWithLifecycle()
    val color_theme_key by vm.color_theme.collectAsStateWithLifecycle()
    val custom_theme_seed by vm.custom_theme_seed.collectAsStateWithLifecycle()
    val custom_theme_overrides by vm.custom_theme_overrides.collectAsStateWithLifecycle()
    val font_choice by vm.font_choice.collectAsStateWithLifecycle()
    val background_image by vm.background_image.collectAsStateWithLifecycle()
    val background_opacity by vm.background_opacity.collectAsStateWithLifecycle()
    val animate_background by vm.animate_background.collectAsStateWithLifecycle()
    val appearance_custom_meta by org.astermail.android.ui.theme.custom_theme_image.meta.collectAsState()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val prefs = settings_state.preferences
    val colors = AsterMaterial.colors
    val color_theme = ColorThemeId.from_key(color_theme_key)
    val plan_limits = plan_state.limits
    val plan_loaded = plan_limits != null
    val is_paid_plan = plan_limits != null && plan_limits.plan_code != "free"
    val custom_theme_unlocked = is_paid_plan || org.astermail.android.BuildConfig.DEBUG
    val custom_theme_locked = plan_loaded && !custom_theme_unlocked

    var show_font_picker by remember { mutableStateOf(false) }
    var show_custom_theme_upsell by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { settings_vm.load_preferences() }

    val prefs_authoritative = prefs != null && settings_state.preferences_authoritative
    var remote_prefs_adopted by remember { mutableStateOf(false) }

    LaunchedEffect(settings_state.save_status) {
        if (settings_state.save_status == SaveStatus.ERROR) remote_prefs_adopted = false
    }
    val mode_derived_from_color_theme = AsterColorThemes.is_dark_only(color_theme)

    LaunchedEffect(prefs_authoritative, remote_prefs_adopted) {
        if (!prefs_authoritative || remote_prefs_adopted) return@LaunchedEffect
        val remote = prefs ?: return@LaunchedEffect
        remote_prefs_adopted = true
        val remote_theme = effective_theme_values(remote)
        if (!mode_derived_from_color_theme) {
            when (remote_theme.theme) {
                "light" -> if (mode != ThemeMode.light) vm.set_mode(ThemeMode.light)
                "dark" -> if (mode != ThemeMode.dark) vm.set_mode(ThemeMode.dark)
                "system" -> if (mode != ThemeMode.system) vm.set_mode(ThemeMode.system)
                else -> {}
            }
        }
        if (remote_theme.color_theme != color_theme_key) {
            val active_background = org.astermail.android.ui.theme.theme_background_for(background_image)
            if (active_background != null && active_background.color_theme.name != remote_theme.color_theme) {
                vm.set_background_image(no_theme_background)
            }
            vm.set_color_theme(remote_theme.color_theme)
        }
        if (remote_theme.custom_theme_seed != custom_theme_seed) {
            vm.set_custom_theme_seed(remote_theme.custom_theme_seed)
        }
        remote.custom_theme_overrides.let { if (it != custom_theme_overrides) vm.set_custom_theme_overrides(it) }
        remote.font_choice.let { if (it != font_choice) vm.set_font_choice(it) }
    }

    LaunchedEffect(plan_limits, color_theme) {
        if (plan_limits == null || custom_theme_unlocked) return@LaunchedEffect
        if (color_theme != ColorThemeId.custom) return@LaunchedEffect
        vm.set_color_theme(ColorThemeId.default.name)
        vm.set_custom_theme_overrides(emptyMap())
        val base = prefs ?: return@LaunchedEffect
        settings_vm.save_preferences(
            with_theme_values(base, color_theme = ColorThemeId.default.name)
                .copy(custom_theme_overrides = emptyMap()),
        )
    }

    fun apply(theme_mode: ThemeMode, theme_key: String) {
        val base = prefs ?: return
        remote_prefs_adopted = true
        vm.set_background_image(no_theme_background)
        vm.set_mode(theme_mode)
        vm.set_color_theme(ColorThemeId.default.name)
        val next = with_theme_values(base, theme = theme_key, color_theme = ColorThemeId.default.name)
        if (next != base) settings_vm.save_preferences(next)
    }

    fun apply_color_theme(id: ColorThemeId, clear_background: Boolean = false) {
        val base = prefs ?: return
        remote_prefs_adopted = true
        if (clear_background) vm.set_background_image(no_theme_background)
        vm.set_color_theme(id.name)
        val forced_dark = AsterColorThemes.is_dark_only(id)
        if (forced_dark) vm.set_mode(ThemeMode.dark)
        val next = with_theme_values(
            base,
            theme = if (forced_dark) "dark" else null,
            color_theme = id.name,
        )
        if (next != base) settings_vm.save_preferences(next)
    }

    if (show_custom_theme_upsell) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_custom_theme_upsell = false },
            title = stringResource(R.string.custom_theme_upgrade_title),
            message = stringResource(R.string.custom_theme_upgrade_description),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { show_custom_theme_upsell = false },
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.upgrade),
                    onClick = {
                        show_custom_theme_upsell = false
                        on_open("billing")
                    },
                )
            },
        )
    }

    var library_open by rememberSaveable { mutableStateOf(false) }

    fun apply_image_theme(background: ThemeBackground?, color: ColorThemeId) {
        if (background == null) {
            apply_color_theme(color, clear_background = true)
            return
        }
        val base = prefs ?: return
        remote_prefs_adopted = true
        vm.set_color_theme(color.name)
        vm.set_mode(ThemeMode.dark)
        vm.set_background_image(background.id)
        val next = with_theme_values(base, theme = "dark", color_theme = color.name)
        if (next != base) settings_vm.save_preferences(next)
    }

    fun apply_custom_seed(hex: String) {
        val base = prefs ?: return
        remote_prefs_adopted = true
        vm.set_custom_theme_seed(hex)
        val next = with_theme_values(base, custom_theme_seed = hex)
        if (next != base) settings_vm.save_preferences(next)
    }

    fun apply_theme_sync(enabled: Boolean) {
        val base = prefs ?: return
        remote_prefs_adopted = true
        val next = with_theme_sync_enabled(base, enabled)
        settings_vm.save_preferences(next)
        val values = effective_theme_values(next)
        val next_mode = when (values.theme) {
            "light" -> ThemeMode.light
            "dark" -> ThemeMode.dark
            else -> ThemeMode.system
        }
        if (mode != next_mode) vm.set_mode(next_mode)
        if (color_theme_key != values.color_theme) vm.set_color_theme(values.color_theme)
        if (custom_theme_seed != values.custom_theme_seed) vm.set_custom_theme_seed(values.custom_theme_seed)
    }

    val device_uses_24h = android.text.format.DateFormat.is24HourFormat(
        androidx.compose.ui.platform.LocalContext.current,
    )
    val effective_24h = when (prefs?.time_format) {
        "24h" -> true
        "12h" -> false
        else -> device_uses_24h
    }

    fun apply_time_format(value: String) {
        val base = prefs ?: return
        if (base.time_format != value) {
            settings_vm.save_preferences(base.copy(time_format = value))
        }
    }

    fun apply_density(value: String) {
        val base = prefs ?: return
        if (base.mail_list_density != value) {
            settings_vm.save_preferences(base.copy(mail_list_density = value))
        }
    }

    val compose_font_size = effective_compose_font_size(prefs)
    val compose_font_color = effective_compose_font_color(prefs)

    fun apply_compose_font_size(label: String) {
        val base = prefs ?: return
        val next = normalize_compose_font_size(label)
        if (base.compose_font_size != next) {
            settings_vm.save_preferences(base.copy(compose_font_size = next))
        }
    }

    fun apply_compose_font_color(hex: String) {
        val base = prefs ?: return
        val next = normalize_compose_font_color(hex)
        if (base.compose_font_color != next) {
            settings_vm.save_preferences(base.copy(compose_font_color = next))
        }
    }

    fun apply_font(id: String) {
        val base = prefs ?: return
        remote_prefs_adopted = true
        vm.set_font_choice(id)
        if (base.font_choice != id) {
            settings_vm.save_preferences(base.copy(font_choice = id))
        }
    }

    val custom_preview_seed = custom_theme_seed.takeIf { MaterialThemeGenerator.is_valid_hex_color(it) } ?: "#3b82f6"
    val custom_preview_palette = remember(custom_preview_seed, custom_theme_overrides) {
        MaterialThemeGenerator.to_palette(
            MaterialThemeGenerator.compute_custom_theme_vars(custom_preview_seed, true, custom_theme_overrides),
        )
    }

    val page_scroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    var page_loaded_once by rememberSaveable { mutableStateOf(false) }
    SideEffect { if (prefs_authoritative && !page_loaded_once) page_loaded_once = true }

    detail_scaffold(
        title = stringResource(R.string.settings_appearance),
        on_back = on_back,
        scroll_state = page_scroll,
    ) {
        preferences_save_error_banner()
        if (prefs == null || (!settings_state.preferences_authoritative && !page_loaded_once)) {
            preferences_load_placeholder()
            return@detail_scaffold
        }
        section_label(stringResource(R.string.theme))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            theme_option_row(
                stringResource(R.string.theme_system),
                stringResource(R.string.theme_system_subtitle),
                mode == ThemeMode.system && color_theme == ColorThemeId.default,
            ) { apply(ThemeMode.system, "system") }
            settings_row_gap(modifier = Modifier)
            theme_option_row(
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_light_subtitle),
                mode == ThemeMode.light && color_theme == ColorThemeId.default,
            ) { apply(ThemeMode.light, "light") }
            settings_row_gap(modifier = Modifier)
            theme_option_row(
                stringResource(R.string.theme_dark),
                stringResource(R.string.theme_dark_subtitle),
                mode == ThemeMode.dark && color_theme == ColorThemeId.default,
            ) { apply(ThemeMode.dark, "dark") }
            settings_row_gap(modifier = Modifier)
            theme_option_row(
                stringResource(R.string.color_theme_aster_blue),
                stringResource(R.string.theme_aster_blue_subtitle),
                color_theme == ColorThemeId.aster_blue,
            ) { apply_color_theme(ColorThemeId.aster_blue, clear_background = true) }
            if (dynamic_color_supported) {
                settings_row_gap(modifier = Modifier)
                theme_option_row(
                    stringResource(R.string.theme_dynamic),
                    stringResource(R.string.theme_dynamic_subtitle),
                    color_theme == ColorThemeId.dynamic,
                ) { apply_color_theme(ColorThemeId.dynamic, clear_background = true) }
            }
        }


        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.color_theme))
        val swatch_order = remember { listOf(ColorThemeId.custom) + preset_swatch_ids }
        var colors_expanded by rememberSaveable { mutableStateOf(false) }
        val collapsed_swatches = remember(swatch_order) { swatch_order.take(6) }
        val shown_swatches = if (colors_expanded || color_theme !in collapsed_swatches) {
            swatch_order
        } else {
            collapsed_swatches
        }
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg).animateContentSize()) {
                shown_swatches.chunked(3).forEachIndexed { row_index, row ->
                    if (row_index > 0) v_gap(AsterSpacing.lg)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
                    ) {
                        row.forEach { id ->
                            val is_locked = id == ColorThemeId.custom && custom_theme_locked
                            val palette = if (id == ColorThemeId.custom) {
                                custom_preview_palette
                            } else {
                                AsterColorThemes.palette_for(id) ?: custom_preview_palette
                            }
                            theme_swatch(
                                label = stringResource(color_theme_label_res(id)),
                                palette = palette,
                                selected = color_theme == id,
                                locked = is_locked,
                                on_click = {
                                    if (is_locked) {
                                        show_custom_theme_upsell = true
                                    } else {
                                        apply_color_theme(id, clear_background = true)
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("swatch_${id.name}"),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (shown_swatches.size < swatch_order.size || colors_expanded) {
                    v_gap(AsterSpacing.lg)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(AsterRadius.md))
                            .clickable { colors_expanded = !colors_expanded }
                            .padding(vertical = AsterSpacing.xs),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                if (colors_expanded) R.string.show_less else R.string.color_theme_show_all,
                            ),
                            color = colors.accent_blue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(AsterSpacing.xs))
                        Icon(
                            imageVector = if (colors_expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
                            contentDescription = null,
                            tint = colors.accent_blue,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (color_theme == ColorThemeId.custom && !custom_theme_locked) {
            v_gap(AsterSpacing.xxl)
            section_label(stringResource(R.string.custom_theme_base_color))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.custom_theme_base_color_subtitle),
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                    )
                    v_gap(AsterSpacing.sm)
                    var hex_input by remember(custom_theme_seed) { mutableStateOf(custom_theme_seed) }
                    val is_valid = MaterialThemeGenerator.is_valid_hex_color(hex_input)
                    LaunchedEffect(hex_input) {
                        if (hex_input == custom_theme_seed) return@LaunchedEffect
                        if (!MaterialThemeGenerator.is_valid_hex_color(hex_input)) return@LaunchedEffect
                        delay(450)
                        apply_custom_seed(hex_input)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (is_valid) parse_hex_color(hex_input) else Color.Gray, CircleShape)
                                .border(1.dp, colors.border_primary, CircleShape),
                        )
                        Spacer(Modifier.width(AsterSpacing.md))
                        AsterTextField(
                            value = hex_input,
                            onValueChange = { value -> hex_input = value },
                            placeholder = stringResource(R.string.custom_theme_hex_placeholder),
                            modifier = Modifier.weight(1f).testTag("custom_hex_input"),
                        )
                    }
                    if (!is_valid && hex_input.isNotBlank()) {
                        v_gap(AsterSpacing.xs)
                        Text(
                            text = stringResource(R.string.custom_theme_hex_invalid),
                            color = colors.danger,
                            fontSize = 12.sp,
                        )
                    }
                    v_gap(AsterSpacing.md)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        quick_seed_colors.forEach { hex ->
                            val is_selected = hex.equals(custom_theme_seed, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(parse_hex_color(hex), CircleShape)
                                    .border(if (is_selected) 2.dp else 0.dp, colors.text_primary, CircleShape)
                                    .clickable {
                                        hex_input = hex
                                        apply_custom_seed(hex)
                                    },
                            )
                        }
                    }
                }
            }
        }
        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.image_themes))
        image_theme_entry_card(
            active = remember(background_image, appearance_custom_meta) { theme_background_for(background_image) },
            on_click = { library_open = true },
        )

        if (
            background_image == org.astermail.android.ui.theme.custom_theme_background &&
            appearance_custom_meta?.animated == true
        ) {
            v_gap(AsterSpacing.xxl)
            section_label(stringResource(R.string.image_theme_animation))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                settings_toggle_row(
                    title = stringResource(R.string.image_theme_animation),
                    subtitle = stringResource(R.string.image_theme_animation_subtitle),
                    checked = animate_background,
                    test_tag = "animate_background_toggle",
                    on_change = { on -> vm.set_animate_background(on) },
                )
            }
        }

        if (background_image != no_theme_background) {
            v_gap(AsterSpacing.xxl)
            section_label(stringResource(R.string.glass_mode))
            val glass_on = background_opacity < solid_theme_opacity - 0.01f
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                settings_toggle_row(
                    title = stringResource(R.string.glass_mode),
                    subtitle = stringResource(R.string.glass_mode_subtitle),
                    checked = glass_on,
                    test_tag = "glass_mode_toggle",
                    on_change = { on ->
                        vm.set_background_opacity(if (on) default_glass_opacity else solid_theme_opacity)
                    },
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = glass_on,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    v_gap(AsterSpacing.xxl)
                    section_label(stringResource(R.string.theme_transparency))
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        glass_opacity_levels.forEachIndexed { index, level ->
                            theme_option_row(
                                stringResource(level.title),
                                stringResource(level.subtitle),
                                nearest_theme_opacity(background_opacity) == level.value,
                            ) { vm.set_background_opacity(level.value) }
                            if (index < glass_opacity_levels.size - 1) settings_row_gap(modifier = Modifier)
                        }
                    }
                }
            }
        }

        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.font))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.font),
                subtitle = stringResource(font_label_res(font_choice)),
                on_click = { show_font_picker = true },
            )
        }

        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.time_format))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            theme_option_row(
                stringResource(R.string.time_format_12h),
                "",
                !effective_24h,
            ) { apply_time_format("12h") }
            settings_row_gap(modifier = Modifier)
            theme_option_row(
                stringResource(R.string.time_format_24h),
                "",
                effective_24h,
            ) { apply_time_format("24h") }
        }

        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.mail_list_density))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            theme_option_row(
                stringResource(R.string.density_compact),
                stringResource(R.string.density_compact_subtitle),
                !is_comfortable_density(prefs?.mail_list_density),
            ) { apply_density("compact") }
            settings_row_gap(modifier = Modifier)
            theme_option_row(
                stringResource(R.string.density_comfortable),
                stringResource(R.string.density_comfortable_subtitle),
                is_comfortable_density(prefs?.mail_list_density),
            ) { apply_density("comfortable") }
        }

        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.inbox_tools))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            val tools_context = androidx.compose.ui.platform.LocalContext.current
            settings_toggle_row(
                title = stringResource(R.string.inbox_tools),
                subtitle = stringResource(R.string.inbox_tools_subtitle),
                checked = org.astermail.android.ui.mail.show_inbox_tools(),
                test_tag = "inbox_tools_toggle",
                on_change = { on ->
                    org.astermail.android.ui.mail.inbox_tools_pref.set(tools_context, on)
                },
            )
        }

        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.section_compose_text))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.compose_text_size),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 2.dp),
            )
            Text(
                text = stringResource(R.string.compose_text_size_subtitle),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = AsterSpacing.lg, end = AsterSpacing.lg, bottom = 4.dp),
            )
            compose_font_size_labels.forEachIndexed { index, label ->
                compose_choice_row(
                    label = stringResource(compose_font_size_label_res(label)),
                    selected = compose_font_size == label,
                    test_tag = "compose_size_$label",
                    on_click = { apply_compose_font_size(label) },
                )
                if (index < compose_font_size_labels.size - 1) settings_row_gap(modifier = Modifier)
            }
        }

        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.compose_text_color))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.compose_text_color_subtitle),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
                v_gap(AsterSpacing.sm)
                var compose_color_input by remember(compose_font_color) { mutableStateOf(compose_font_color) }
                val compose_color_valid = normalize_compose_font_color(compose_color_input).isNotEmpty()
                LaunchedEffect(compose_color_input) {
                    if (compose_color_input == compose_font_color) return@LaunchedEffect
                    if (!compose_color_valid) return@LaunchedEffect
                    delay(450)
                    apply_compose_font_color(compose_color_input)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (compose_color_valid) parse_hex_color(compose_color_input) else colors.text_primary,
                                CircleShape,
                            )
                            .border(1.dp, colors.border_primary, CircleShape),
                    )
                    Spacer(Modifier.width(AsterSpacing.md))
                    AsterTextField(
                        value = compose_color_input,
                        onValueChange = { value ->
                            compose_color_input = value
                        },
                        placeholder = stringResource(R.string.compose_text_color_placeholder),
                        modifier = Modifier.weight(1f).testTag("compose_color_input"),
                    )
                }
                if (!compose_color_valid && compose_color_input.isNotBlank()) {
                    v_gap(AsterSpacing.xs)
                    Text(
                        text = stringResource(R.string.compose_text_color_invalid),
                        color = colors.danger,
                        fontSize = 12.sp,
                    )
                }
                v_gap(AsterSpacing.md)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    quick_compose_text_colors.forEach { hex ->
                        val is_selected = hex == compose_font_color
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(
                                    width = if (is_selected) 2.5.dp else 1.dp,
                                    color = if (is_selected) colors.text_primary else colors.border_primary,
                                    shape = CircleShape,
                                )
                                .padding(if (is_selected) 3.5.dp else 0.dp)
                                .background(parse_hex_color(hex), CircleShape)
                                .clickable {
                                    compose_color_input = hex
                                    apply_compose_font_color(hex)
                                },
                        )
                    }
                }
            }
            settings_row_gap(modifier = Modifier)
            compose_choice_row(
                label = stringResource(R.string.compose_text_color_theme_default),
                selected = compose_font_color.isEmpty(),
                test_tag = "compose_color_theme_default",
                on_click = { apply_compose_font_color("") },
            )
        }
        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.sync))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            val sync_enabled = prefs?.let { theme_sync_enabled(it) } ?: true
            detail_row(
                title = stringResource(R.string.theme_sync_across_devices),
                subtitle = stringResource(R.string.theme_sync_across_devices_subtitle),
                trailing = {
                    AsterSwitch(
                        checked = sync_enabled,
                        onCheckedChange = { apply_theme_sync(it) },
                        modifier = Modifier.testTag("theme_sync_switch"),
                    )
                },
            )
        }
        v_gap(AsterSpacing.xxl)
    }

    if (library_open) {
        image_theme_library(
            active_id = background_image,
            active_color = color_theme,
            on_dismiss = { library_open = false },
            on_apply = { chosen, color -> apply_image_theme(chosen, color) },
        )
    }

    if (show_font_picker) {
        base_sheet(
            on_dismiss = { show_font_picker = false },
            title = stringResource(R.string.pick_font),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FONT_OPTIONS.forEach { option ->
                    font_option_row(
                        label = stringResource(font_label_res(option.id)),
                        family = preview_font_family_for(option.id),
                        selected = option.id == font_choice,
                        on_click = {
                            apply_font(option.id)
                            show_font_picker = false
                        },
                        test_tag = "opt_${option.id}",
                    )
                }
            }
        }
    }
}

@Composable
private fun font_option_row(
    label: String,
    family: FontFamily,
    selected: Boolean,
    on_click: () -> Unit,
    test_tag: String,
) {
    choice_option_row(
        label = label,
        selected = selected,
        on_click = on_click,
        subtitle = stringResource(R.string.font_preview_sample),
        label_font_family = family,
        test_tag = test_tag,
    )
}

@Composable
internal fun theme_swatch(
    label: String,
    palette: ColorThemePalette,
    selected: Boolean,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier.clickable(onClick = on_click),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) colors.accent_blue else Color.Transparent,
                    shape = CircleShape,
                )
                .padding(5.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(palette.bg_primary),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(palette.bg_secondary),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(palette.accent_color),
                )
            }
            if (locked && !selected) {
                plan_pill(
                    text = stringResource(R.string.plan_badge_star),
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 7.dp),
                )
            } else if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(23.dp)
                        .background(colors.accent_blue, CircleShape)
                        .border(2.dp, colors.bg_card, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = null,
                        tint = colors.on_accent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = if (selected) colors.text_primary else colors.text_secondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
internal fun plan_pill(text: String, modifier: Modifier = Modifier) {
    org.astermail.android.design.components.AsterPlanTag(
        text = text,
        modifier = modifier,
    )
}

@Composable
private fun image_theme_entry_card(
    active: ThemeBackground?,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val catalog_categories by org.astermail.android.ui.theme.theme_manifest.categories.collectAsState()
    val featured = remember(active?.id, catalog_categories) {
        listOfNotNull(active) + catalog_categories
            .mapNotNull { it.second.firstOrNull() }
            .filter { it.id != active?.id }
    }.take(3)
    AsterCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .testTag("image_theme_browse"),
    ) {
        Row(
            modifier = Modifier.padding(AsterSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(width = 92.dp, height = 84.dp)) {
                featured.reversed().forEachIndexed { reversed_index, background ->
                    val index = featured.size - 1 - reversed_index
                    image_theme_thumbnail(
                        background = background,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = (index * 22).dp)
                            .size(width = 46.dp, height = 82.dp)
                            .graphicsLayer {
                                rotationZ = (index - 1) * 6f
                                scaleX = 1f - index * 0.06f
                                scaleY = 1f - index * 0.06f
                            }
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .border(1.5.dp, colors.bg_card.copy(alpha = 1f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                    )
                }
            }
            Spacer(Modifier.width(AsterSpacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.image_theme_browse),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = active?.let { stringResource(it.category.label_res) } ?: stringResource(R.string.image_themes_subtitle),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun compose_choice_row(
    label: String,
    selected: Boolean,
    test_tag: String,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .testTag(test_tag)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) colors.accent_blue else colors.border_primary,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(colors.accent_blue, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = label,
            color = colors.text_primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun theme_option_row(
    title: String,
    subtitle: String,
    selected: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) colors.accent_blue else colors.border_primary,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(colors.accent_blue, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, color = colors.text_tertiary, fontSize = 13.sp)
            }
        }
    }
}
