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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.mail.cache_toolbar_actions
import org.astermail.android.ui.mail.load_toolbar_actions
import org.astermail.android.ui.mail.parse_toolbar_actions
import org.astermail.android.ui.mail.toolbar_action_by_id
import org.astermail.android.ui.mail.toolbar_action_catalog
import org.astermail.android.ui.mail.toolbar_slot_count

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomizeToolbarScreen(
    on_back: () -> Unit,
    settings_vm: SettingsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val server_prefs = settings_state.preferences

    LaunchedEffect(Unit) { settings_vm.load_preferences() }

    var slots by remember { mutableStateOf(load_toolbar_actions(context)) }
    var editing_slot by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(server_prefs?.toolbar_actions) {
        val raw = server_prefs?.toolbar_actions
        if (raw != null) {
            val parsed = parse_toolbar_actions(raw)
            if (parsed != slots) slots = parsed
            cache_toolbar_actions(context, parsed)
        }
    }

    fun set_slot(index: Int, id: String) {
        val current = slots.toMutableList()
        val existing = current.indexOf(id)
        if (existing >= 0 && existing != index) {
            current[existing] = current[index]
        }
        current[index] = id
        slots = current
        cache_toolbar_actions(context, current)
        val base = server_prefs ?: org.astermail.android.api.preferences.UserPreferences()
        settings_vm.save_preferences(base.copy(toolbar_actions = current.joinToString(",")))
    }

    detail_scaffold(
        title = stringResource(R.string.customize_toolbar),
        on_back = on_back,
    ) {
        Text(
            text = stringResource(R.string.customize_toolbar_subtitle),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = AsterSpacing.md),
        )
        section_label(stringResource(R.string.toolbar_slots))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until toolbar_slot_count) {
                val id = slots.getOrNull(i)
                val action = id?.let { toolbar_action_by_id(it) }
                slot_row(
                    index = i + 1,
                    action_label = action?.let { stringResource(it.label_res) }
                        ?: stringResource(R.string.unset),
                    action_icon = action?.icon,
                    on_click = { editing_slot = i },
                )
                if (i < toolbar_slot_count - 1) AsterDivider(modifier = Modifier)
            }
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.preview))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.md, vertical = 14.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slots.forEach { id ->
                    val action = toolbar_action_by_id(id)
                    if (action != null) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = colors.text_primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = null,
                    tint = colors.text_primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        v_gap(AsterSpacing.xxl)
    }

    val sheet_state = rememberModalBottomSheetState()
    val active_slot = editing_slot
    if (active_slot != null) {
        ModalBottomSheet(
            onDismissRequest = { editing_slot = null },
            sheetState = sheet_state,
            containerColor = colors.bg_secondary,
            dragHandle = { AsterDragHandle() },
        ) {
            Column(modifier = Modifier.padding(bottom = AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.choose_action),
                    color = colors.text_primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        start = AsterSpacing.lg,
                        end = AsterSpacing.lg,
                        top = AsterSpacing.xs,
                        bottom = AsterSpacing.sm,
                    ),
                )
                toolbar_action_catalog.forEachIndexed { i, action ->
                    val is_current = slots.getOrNull(active_slot) == action.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                set_slot(active_slot, action.id)
                                editing_slot = null
                            }
                            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = colors.text_secondary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(AsterSpacing.md))
                        Text(
                            text = stringResource(action.label_res),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (is_current) {
                            Box(
                                modifier = Modifier.size(18.dp).background(colors.accent_blue, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("âœ“", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (i < toolbar_action_catalog.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
    }
}

@Composable
private fun slot_row(
    index: Int,
    action_label: String,
    action_icon: androidx.compose.ui.graphics.vector.ImageVector?,
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
                .size(28.dp)
                .background(colors.bg_tertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                color = colors.text_secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        if (action_icon != null) {
            Icon(
                imageVector = action_icon,
                contentDescription = null,
                tint = colors.text_primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(AsterSpacing.sm))
        }
        Text(
            text = action_label,
            color = colors.text_primary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.change),
            color = colors.accent_blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
