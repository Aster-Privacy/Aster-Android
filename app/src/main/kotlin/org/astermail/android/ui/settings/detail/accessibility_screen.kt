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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.theme.ThemeViewModel

@Composable
private fun access_option_row(label: String, selected: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = colors.text_primary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) {
            Box(
                modifier = Modifier.size(20.dp).background(colors.accent_blue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun access_toggle_row(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    info_title: String? = null,
    info_description: String? = null,
    on_change: (Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (info_title != null && info_description != null) {
                    Spacer(Modifier.width(5.dp))
                    info_dialog_button(info_title, info_description)
                }
            }
            if (subtitle != null) Text(subtitle, color = colors.text_tertiary, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = on_change,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent_blue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.text_muted.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
fun AccessibilityScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = hiltViewModel()
    val theme_vm: ThemeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences

    LaunchedEffect(Unit) { vm.load_preferences() }

    var font_size by remember { mutableStateOf("default") }
    var high_contrast by remember { mutableStateOf(false) }
    var reduce_transparency by remember { mutableStateOf(false) }
    var underline_links by remember { mutableStateOf(false) }
    var dyslexia by remember { mutableStateOf(false) }
    var text_spacing by remember { mutableStateOf(false) }
    var reduce_motion by remember { mutableStateOf(false) }
    var compact by remember { mutableStateOf(false) }
    var low_network by remember { mutableStateOf(false) }
    var save_trigger by remember { mutableIntStateOf(0) }
    var prefs_loaded by remember { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        if (prefs != null && !prefs_loaded) {
            prefs_loaded = true
            font_size = prefs.font_size_scale
            high_contrast = prefs.high_contrast
            reduce_transparency = prefs.reduce_transparency
            underline_links = prefs.underline_links
            dyslexia = prefs.dyslexia_font
            text_spacing = prefs.text_spacing
            reduce_motion = prefs.reduce_motion
            compact = prefs.compact_mode
            low_network = prefs.low_network_mode
            theme_vm.set_text_size_from_key(prefs.font_size_scale)
            theme_vm.set_high_contrast(prefs.high_contrast)
            theme_vm.set_reduce_transparency(prefs.reduce_transparency)
            theme_vm.set_reduce_motion(prefs.reduce_motion)
            theme_vm.set_compact_mode(prefs.compact_mode)
            theme_vm.set_text_spacing(prefs.text_spacing)
            theme_vm.set_underline_links(prefs.underline_links)
            theme_vm.set_dyslexia_font(prefs.dyslexia_font)
        }
    }

    fun save() {
        val base = prefs ?: return
        vm.save_preferences(
            base.copy(
                font_size_scale = font_size,
                high_contrast = high_contrast,
                reduce_transparency = reduce_transparency,
                underline_links = underline_links,
                dyslexia_font = dyslexia,
                text_spacing = text_spacing,
                reduce_motion = reduce_motion,
                compact_mode = compact,
                low_network_mode = low_network,
            ),
        )
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (!prefs_loaded || prefs == null) return@LaunchedEffect
        delay(400)
        save()
    }

    DisposableEffect(Unit) {
        onDispose { if (save_trigger > 0 && prefs != null && prefs_loaded) save() }
    }

    detail_scaffold(title = stringResource(R.string.settings_accessibility), on_back = on_back) {
        if (state.is_loading && prefs == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {

            // ── Font Size ──────────────────────────────────────────────────────
            section_label(stringResource(R.string.font_size))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "small" to stringResource(R.string.font_small),
                    "default" to stringResource(R.string.font_default),
                    "large" to stringResource(R.string.font_large),
                    "extra_large" to stringResource(R.string.font_extra_large),
                ).forEachIndexed { i, (id, label) ->
                    access_option_row(label, font_size == id) {
                        font_size = id
                        theme_vm.set_text_size_from_key(id)
                        save_trigger++
                    }
                    if (i < 3) AsterDivider(modifier = Modifier)
                }
            }

            v_gap(AsterSpacing.lg)

            // ── Vision ─────────────────────────────────────────────────────────
            section_label(stringResource(R.string.vision))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                access_toggle_row(
                    title = stringResource(R.string.high_contrast),
                    subtitle = stringResource(R.string.high_contrast_subtitle),
                    checked = high_contrast,
                    info_title = stringResource(R.string.high_contrast_info_title),
                    info_description = stringResource(R.string.high_contrast_info_desc),
                ) { high_contrast = it; theme_vm.set_high_contrast(it); save_trigger++ }
                AsterDivider(modifier = Modifier)
                access_toggle_row(
                    title = stringResource(R.string.reduce_transparency),
                    subtitle = stringResource(R.string.reduce_transparency_subtitle),
                    checked = reduce_transparency,
                    info_title = stringResource(R.string.reduce_transparency_info_title),
                    info_description = stringResource(R.string.reduce_transparency_info_desc),
                ) { reduce_transparency = it; theme_vm.set_reduce_transparency(it); save_trigger++ }
                AsterDivider(modifier = Modifier)
                access_toggle_row(
                    title = stringResource(R.string.underline_links),
                    subtitle = stringResource(R.string.underline_links_subtitle),
                    checked = underline_links,
                    info_title = stringResource(R.string.underline_links_info_title),
                    info_description = stringResource(R.string.underline_links_info_desc),
                ) { underline_links = it; theme_vm.set_underline_links(it); save_trigger++ }
            }

            v_gap(AsterSpacing.lg)

            // ── Reading ────────────────────────────────────────────────────────
            section_label(stringResource(R.string.reading))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                access_toggle_row(
                    title = stringResource(R.string.dyslexia_font),
                    subtitle = stringResource(R.string.dyslexia_font_subtitle),
                    checked = dyslexia,
                    info_title = stringResource(R.string.dyslexia_font_info_title),
                    info_description = stringResource(R.string.dyslexia_font_info_desc),
                ) { dyslexia = it; theme_vm.set_dyslexia_font(it); save_trigger++ }
                AsterDivider(modifier = Modifier)
                access_toggle_row(
                    title = stringResource(R.string.text_spacing),
                    subtitle = stringResource(R.string.text_spacing_subtitle),
                    checked = text_spacing,
                    info_title = stringResource(R.string.text_spacing_info_title),
                    info_description = stringResource(R.string.text_spacing_info_desc),
                ) { text_spacing = it; theme_vm.set_text_spacing(it); save_trigger++ }
            }

            v_gap(AsterSpacing.lg)

            // ── Motion & Layout ────────────────────────────────────────────────
            section_label(stringResource(R.string.motion_layout))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                access_toggle_row(
                    title = stringResource(R.string.reduce_motion),
                    subtitle = stringResource(R.string.reduce_motion_subtitle),
                    checked = reduce_motion,
                    info_title = stringResource(R.string.reduce_motion_info_title),
                    info_description = stringResource(R.string.reduce_motion_info_desc),
                ) { reduce_motion = it; theme_vm.set_reduce_motion(it); save_trigger++ }
                AsterDivider(modifier = Modifier)
                access_toggle_row(
                    title = stringResource(R.string.compact_mode),
                    subtitle = stringResource(R.string.compact_mode_subtitle),
                    checked = compact,
                    info_title = stringResource(R.string.compact_mode_info_title),
                    info_description = stringResource(R.string.compact_mode_info_desc),
                ) { compact = it; theme_vm.set_compact_mode(it); save_trigger++ }
            }

            v_gap(AsterSpacing.lg)

            // ── Network ────────────────────────────────────────────────────────
            section_label(stringResource(R.string.network))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                access_toggle_row(
                    title = stringResource(R.string.low_network_mode),
                    subtitle = stringResource(R.string.low_network_mode_subtitle),
                    checked = low_network,
                    info_title = stringResource(R.string.low_network_mode_info_title),
                    info_description = stringResource(R.string.low_network_mode_info_desc),
                ) { low_network = it; save_trigger++ }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}
