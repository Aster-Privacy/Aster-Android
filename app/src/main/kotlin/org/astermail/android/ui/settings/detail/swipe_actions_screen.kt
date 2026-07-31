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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.mail.DEFAULT_SWIPE_LEFT_ACTION
import org.astermail.android.mail.DEFAULT_SWIPE_RIGHT_ACTION
import org.astermail.android.mail.SWIPE_ACTION_ARCHIVE
import org.astermail.android.mail.SWIPE_ACTION_DELETE
import org.astermail.android.mail.SWIPE_ACTION_NONE
import org.astermail.android.mail.SWIPE_ACTION_SNOOZE
import org.astermail.android.mail.SWIPE_ACTION_SPAM
import org.astermail.android.mail.SWIPE_ACTION_STAR
import org.astermail.android.mail.SWIPE_ACTION_TOGGLE_READ
import org.astermail.android.mail.normalize_swipe_action
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

private data class SwipeActionOption(
    val id: String,
    val label: String,
    val icon: ImageVector?,
    val color: Color?,
)

@Composable
fun SwipeActionsScreen(on_back: () -> Unit) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences

    LaunchedEffect(Unit) { vm.load_preferences() }

    var swipe_right by remember { mutableStateOf(DEFAULT_SWIPE_RIGHT_ACTION) }
    var swipe_left by remember { mutableStateOf(DEFAULT_SWIPE_LEFT_ACTION) }
    var prefs_loaded by remember { mutableStateOf(false) }

    LaunchedEffect(prefs, state.preferences_authoritative) {
        if (prefs != null && state.preferences_authoritative && !prefs_loaded) {
            prefs_loaded = true
            swipe_right = normalize_swipe_action(prefs.swipe_right_action, DEFAULT_SWIPE_RIGHT_ACTION)
            swipe_left = normalize_swipe_action(prefs.swipe_left_action, DEFAULT_SWIPE_LEFT_ACTION)
        }
    }

    val action_options = listOf(
        SwipeActionOption(SWIPE_ACTION_ARCHIVE, stringResource(R.string.swipe_archive), TablerIcons.Archive, colors.accent_blue),
        SwipeActionOption(SWIPE_ACTION_DELETE, stringResource(R.string.swipe_delete), TablerIcons.Trash, colors.danger),
        SwipeActionOption(SWIPE_ACTION_TOGGLE_READ, stringResource(R.string.swipe_mark_as_read), TablerIcons.MailOpened, colors.success),
        SwipeActionOption(SWIPE_ACTION_SNOOZE, stringResource(R.string.snooze), TablerIcons.Clock, colors.warning),
        SwipeActionOption(SWIPE_ACTION_STAR, stringResource(R.string.swipe_star), TablerIcons.Star, colors.star),
        SwipeActionOption(SWIPE_ACTION_SPAM, stringResource(R.string.swipe_report_spam), TablerIcons.Ban, colors.danger),
        SwipeActionOption(SWIPE_ACTION_NONE, stringResource(R.string.swipe_none), TablerIcons.CircleOff, colors.text_muted),
    )

    detail_scaffold(
        title = stringResource(R.string.swipe_actions),
        on_back = on_back,
    ) {
        if (prefs == null || !state.preferences_authoritative) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            section_label(stringResource(R.string.swipe_right))
            Text(
                text = stringResource(R.string.swipe_right_subtitle),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = AsterSpacing.xs),
            )
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                action_options.forEachIndexed { i, option ->
                    swipe_action_option(
                        option = option,
                        selected = swipe_right == option.id,
                        on_click = {
                            prefs_loaded = true
                            swipe_right = option.id
                            prefs?.let { base ->
                                vm.save_preferences(base.copy(swipe_right_action = option.id, swipe_left_action = swipe_left))
                            }
                        },
                    )
                    if (i < action_options.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
            v_gap(AsterSpacing.xl)
            section_label(stringResource(R.string.swipe_left))
            Text(
                text = stringResource(R.string.swipe_left_subtitle),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = AsterSpacing.xs),
            )
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                action_options.forEachIndexed { i, option ->
                    swipe_action_option(
                        option = option,
                        selected = swipe_left == option.id,
                        on_click = {
                            prefs_loaded = true
                            swipe_left = option.id
                            prefs?.let { base ->
                                vm.save_preferences(base.copy(swipe_right_action = swipe_right, swipe_left_action = option.id))
                            }
                        },
                    )
                    if (i < action_options.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun swipe_action_option(
    option: SwipeActionOption,
    selected: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .background(if (selected) colors.accent_blue.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.icon != null && option.color != null) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = option.color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(AsterSpacing.md))
        }
        Text(
            text = option.label,
            color = colors.text_primary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) colors.accent_blue else Color.Transparent)
                .border(
                    width = if (selected) 0.dp else 1.5.dp,
                    color = if (selected) Color.Transparent else colors.border_secondary,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

