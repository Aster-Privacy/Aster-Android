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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import org.astermail.android.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.storage.ThemeMode
import org.astermail.android.ui.theme.ThemeViewModel

@Composable
fun AppearanceScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: ThemeViewModel = hiltViewModel()
    val settings_vm: SettingsViewModel = hiltViewModel()
    val mode by vm.theme_mode.collectAsStateWithLifecycle()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val prefs = settings_state.preferences

    LaunchedEffect(Unit) { settings_vm.load_preferences() }

    LaunchedEffect(prefs?.theme) {
        when (prefs?.theme) {
            "light" -> if (mode != ThemeMode.light) vm.set_mode(ThemeMode.light)
            "dark" -> if (mode != ThemeMode.dark) vm.set_mode(ThemeMode.dark)
            "system" -> if (mode != ThemeMode.system) vm.set_mode(ThemeMode.system)
            else -> {}
        }
    }

    fun apply(theme_mode: ThemeMode, theme_key: String) {
        vm.set_mode(theme_mode)
        val base = prefs ?: return
        if (base.theme != theme_key) {
            settings_vm.save_preferences(base.copy(theme = theme_key))
        }
    }

    detail_scaffold(title = stringResource(R.string.settings_appearance), on_back = on_back) {
        section_label(stringResource(R.string.theme))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            theme_option_row(stringResource(R.string.theme_system), stringResource(R.string.theme_system_subtitle), mode == ThemeMode.system) { apply(ThemeMode.system, "system") }
            AsterDivider(modifier = Modifier)
            theme_option_row(stringResource(R.string.theme_light), stringResource(R.string.theme_light_subtitle), mode == ThemeMode.light) { apply(ThemeMode.light, "light") }
            AsterDivider(modifier = Modifier)
            theme_option_row(stringResource(R.string.theme_dark), stringResource(R.string.theme_dark_subtitle), mode == ThemeMode.dark) { apply(ThemeMode.dark, "dark") }
        }
        v_gap(AsterSpacing.xxl)
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
