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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.settings.SettingsViewModel

private data class SwipeActionOption(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
fun SwipeActionsScreen(on_back: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences

    LaunchedEffect(Unit) { vm.load_preferences() }

    var swipe_right by remember { mutableStateOf("archive") }
    var swipe_left by remember { mutableStateOf("trash") }
    var save_trigger by remember { mutableIntStateOf(0) }
    var prefs_loaded by remember { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        if (prefs != null && !prefs_loaded) {
            prefs_loaded = true
            swipe_right = prefs.swipe_right_action
            swipe_left = prefs.swipe_left_action
        }
    }

    fun save() {
        val base = prefs ?: return
        vm.save_preferences(
            base.copy(
                swipe_right_action = swipe_right,
                swipe_left_action = swipe_left,
            ),
        )
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (!prefs_loaded || prefs == null) return@LaunchedEffect
        delay(500)
        save()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (save_trigger > 0 && prefs != null && prefs_loaded) {
                save()
            }
        }
    }

    val action_options = listOf(
        SwipeActionOption("archive", stringResource(R.string.swipe_archive), Icons.Filled.Archive, colors.accent_blue),
        SwipeActionOption("trash", stringResource(R.string.swipe_delete), Icons.Filled.Delete, colors.danger),
        SwipeActionOption("mark_read", stringResource(R.string.swipe_mark_as_read), Icons.Filled.MarkEmailRead, colors.success),
        SwipeActionOption("mark_unread", stringResource(R.string.swipe_mark_as_unread), Icons.Filled.MarkEmailUnread, colors.warning),
        SwipeActionOption("star", stringResource(R.string.swipe_star), Icons.Filled.Star, colors.warning),
        SwipeActionOption("spam", stringResource(R.string.swipe_report_spam), Icons.Filled.Block, colors.danger),
        SwipeActionOption("move_to_inbox", stringResource(R.string.swipe_move_to_inbox), Icons.Filled.Inbox, colors.accent_blue),
    )

    detail_scaffold(
        title = stringResource(R.string.swipe_actions),
        on_back = on_back,
    ) {
        if (state.is_loading && prefs == null) {
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
                        on_click = { swipe_right = option.id; save_trigger++ },
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
                        on_click = { swipe_left = option.id; save_trigger++ },
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
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            tint = option.color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = option.label,
            color = colors.text_primary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier.size(18.dp).background(colors.accent_blue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("âœ“", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

