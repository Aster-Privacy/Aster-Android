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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
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
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.storage.ThemeMode
import org.astermail.android.ui.settings.mail_rules.pickers.options_picker
import org.astermail.android.ui.settings.mail_rules.pickers.picker_item
import org.astermail.android.ui.theme.ThemeViewModel

private val quick_seed_colors = listOf(
    "#3b82f6", "#a855f7", "#22c55e", "#f43f5e", "#f97316",
    "#14b88a", "#f5be0b", "#068fd4", "#84cc16", "#e0399d",
)

private fun parse_hex_color(hex: String): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Throwable) { Color.Gray }

private fun font_label_res(id: String): Int = when (id) {
    "default" -> R.string.font_option_default
    "system" -> R.string.font_option_system
    "inter" -> R.string.font_option_inter
    "roboto" -> R.string.font_option_roboto
    "nunito" -> R.string.font_option_nunito
    "merriweather" -> R.string.font_option_merriweather
    "lora" -> R.string.font_option_lora
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

private fun color_theme_label_res(id: ColorThemeId): Int = when (id) {
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

@Composable
fun AppearanceScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: ThemeViewModel = hiltViewModel()
    val settings_vm: SettingsViewModel = hiltViewModel()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val mode by vm.theme_mode.collectAsStateWithLifecycle()
    val color_theme_key by vm.color_theme.collectAsStateWithLifecycle()
    val custom_theme_seed by vm.custom_theme_seed.collectAsStateWithLifecycle()
    val custom_theme_overrides by vm.custom_theme_overrides.collectAsStateWithLifecycle()
    val font_choice by vm.font_choice.collectAsStateWithLifecycle()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val prefs = settings_state.preferences
    val colors = AsterMaterial.colors
    val color_theme = ColorThemeId.from_key(color_theme_key)
    val plan_limits = plan_state.limits
    val is_paid_plan = plan_limits != null && plan_limits.plan_code != "free"

    var show_font_picker by remember { mutableStateOf(false) }
    var show_custom_theme_upgrade by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { settings_vm.load_preferences() }

    val prefs_authoritative = prefs != null && settings_state.preferences_authoritative
    var remote_prefs_adopted by remember { mutableStateOf(false) }
    val mode_derived_from_color_theme = AsterColorThemes.is_dark_only(color_theme)

    LaunchedEffect(prefs_authoritative, remote_prefs_adopted) {
        if (!prefs_authoritative || remote_prefs_adopted) return@LaunchedEffect
        val remote = prefs ?: return@LaunchedEffect
        remote_prefs_adopted = true
        if (!mode_derived_from_color_theme) {
            when (remote.theme) {
                "light" -> if (mode != ThemeMode.light) vm.set_mode(ThemeMode.light)
                "dark" -> if (mode != ThemeMode.dark) vm.set_mode(ThemeMode.dark)
                "system" -> if (mode != ThemeMode.system) vm.set_mode(ThemeMode.system)
                else -> {}
            }
        }
        remote.color_theme?.let { if (it != color_theme_key) vm.set_color_theme(it) }
        remote.custom_theme_seed?.let { if (it != custom_theme_seed) vm.set_custom_theme_seed(it) }
        remote.custom_theme_overrides?.let { if (it != custom_theme_overrides) vm.set_custom_theme_overrides(it) }
        remote.font_choice?.let { if (it != font_choice) vm.set_font_choice(it) }
    }

    LaunchedEffect(plan_limits, color_theme) {
        if (plan_limits == null || is_paid_plan) return@LaunchedEffect
        if (color_theme != ColorThemeId.custom) return@LaunchedEffect
        vm.set_color_theme(ColorThemeId.default.name)
        vm.set_custom_theme_overrides(emptyMap())
        val base = prefs ?: return@LaunchedEffect
        settings_vm.save_preferences(
            base.copy(color_theme = ColorThemeId.default.name, custom_theme_overrides = emptyMap()),
        )
    }

    fun apply(theme_mode: ThemeMode, theme_key: String) {
        remote_prefs_adopted = true
        vm.set_mode(theme_mode)
        vm.set_color_theme(ColorThemeId.default.name)
        val base = prefs ?: return
        if (base.theme != theme_key || base.color_theme != ColorThemeId.default.name) {
            settings_vm.save_preferences(base.copy(theme = theme_key, color_theme = ColorThemeId.default.name))
        }
    }

    fun apply_color_theme(id: ColorThemeId) {
        remote_prefs_adopted = true
        vm.set_color_theme(id.name)
        val forced_dark = AsterColorThemes.is_dark_only(id)
        if (forced_dark) vm.set_mode(ThemeMode.dark)
        val base = prefs ?: return
        val next_theme = if (forced_dark) "dark" else base.theme
        if (base.color_theme != id.name || base.theme != next_theme) {
            settings_vm.save_preferences(base.copy(color_theme = id.name, theme = next_theme))
        }
    }

    fun apply_custom_seed(hex: String) {
        remote_prefs_adopted = true
        vm.set_custom_theme_seed(hex)
        val base = prefs ?: return
        if (base.custom_theme_seed != hex) {
            settings_vm.save_preferences(base.copy(custom_theme_seed = hex))
        }
    }

    fun apply_font(id: String) {
        remote_prefs_adopted = true
        vm.set_font_choice(id)
        val base = prefs ?: return
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

    detail_scaffold(title = stringResource(R.string.settings_appearance), on_back = on_back) {
        section_label(stringResource(R.string.theme))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            theme_option_row(
                stringResource(R.string.theme_system),
                stringResource(R.string.theme_system_subtitle),
                mode == ThemeMode.system && color_theme == ColorThemeId.default,
            ) { apply(ThemeMode.system, "system") }
            AsterDivider(modifier = Modifier)
            theme_option_row(
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_light_subtitle),
                mode == ThemeMode.light && color_theme == ColorThemeId.default,
            ) { apply(ThemeMode.light, "light") }
            AsterDivider(modifier = Modifier)
            theme_option_row(
                stringResource(R.string.theme_dark),
                stringResource(R.string.theme_dark_subtitle),
                mode == ThemeMode.dark && color_theme == ColorThemeId.default,
            ) { apply(ThemeMode.dark, "dark") }
            AsterDivider(modifier = Modifier)
            theme_option_row(
                stringResource(R.string.color_theme_aster_blue),
                stringResource(R.string.theme_aster_blue_subtitle),
                color_theme == ColorThemeId.aster_blue,
            ) { apply_color_theme(ColorThemeId.aster_blue) }
            if (dynamic_color_supported) {
                AsterDivider(modifier = Modifier)
                theme_option_row(
                    stringResource(R.string.theme_dynamic),
                    stringResource(R.string.theme_dynamic_subtitle),
                    color_theme == ColorThemeId.dynamic,
                ) { apply_color_theme(ColorThemeId.dynamic) }
            }
        }

        v_gap(AsterSpacing.xxl)
        section_label(stringResource(R.string.color_theme))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                (preset_swatch_ids + ColorThemeId.custom).chunked(3).forEachIndexed { row_index, row ->
                    if (row_index > 0) v_gap(AsterSpacing.lg)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
                    ) {
                        row.forEach { id ->
                            val is_locked = id == ColorThemeId.custom && !is_paid_plan
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
                                        show_custom_theme_upgrade = true
                                    } else {
                                        apply_color_theme(id)
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("swatch_${id.name}"),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (show_custom_theme_upgrade && !is_paid_plan) {
            v_gap(AsterSpacing.xxl)
            UpgradeGate(
                title = stringResource(R.string.custom_theme_upgrade_title),
                description = stringResource(R.string.custom_theme_upgrade_description),
                plan_name = "Star",
                on_upgrade = { on_open("billing") },
                requires_label = stringResource(R.string.requires_plan, "Star"),
                button_label = stringResource(R.string.upgrade),
            )
        }

        if (color_theme == ColorThemeId.custom && is_paid_plan) {
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
                            onValueChange = { value ->
                                hex_input = value
                                if (MaterialThemeGenerator.is_valid_hex_color(value)) apply_custom_seed(value)
                            },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        quick_seed_colors.forEach { hex ->
                            val is_selected = hex.equals(custom_theme_seed, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
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
        section_label(stringResource(R.string.font))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.font),
                subtitle = stringResource(font_label_res(font_choice)),
                on_click = { show_font_picker = true },
            )
        }
        v_gap(AsterSpacing.xxl)
    }

    if (show_font_picker) {
        options_picker(
            on_dismiss = { show_font_picker = false },
            title = stringResource(R.string.pick_font),
            items = FONT_OPTIONS.map { picker_item(id = it.id, label = stringResource(font_label_res(it.id))) },
            selected_id = font_choice,
            on_pick = { id -> apply_font(id) },
        )
    }
}

@Composable
private fun theme_swatch(
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
                    .background(palette.bg_primary)
                    .border(1.dp, palette.border_secondary, CircleShape),
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
            if (selected || locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(23.dp)
                        .background(if (selected) colors.accent_blue else colors.bg_card, CircleShape)
                        .border(2.dp, colors.bg_card, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (selected) TablerIcons.Check else TablerIcons.Lock,
                        contentDescription = null,
                        tint = if (selected) Color.White else colors.text_secondary,
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
            Text(text = subtitle, color = colors.text_tertiary, fontSize = 13.sp)
        }
    }
}
