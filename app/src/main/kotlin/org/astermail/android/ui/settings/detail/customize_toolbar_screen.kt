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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
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
import org.astermail.android.ui.mail.ToolbarAction
import org.astermail.android.ui.mail.cache_selection_toolbar_actions
import org.astermail.android.ui.mail.cache_toolbar_actions
import org.astermail.android.ui.mail.load_selection_toolbar_actions
import org.astermail.android.ui.mail.load_toolbar_actions
import org.astermail.android.ui.mail.parse_selection_toolbar_actions
import org.astermail.android.ui.mail.parse_toolbar_actions
import org.astermail.android.ui.mail.selection_toolbar_action_by_id
import org.astermail.android.ui.mail.selection_toolbar_action_catalog
import org.astermail.android.ui.mail.selection_toolbar_slot_count
import org.astermail.android.ui.mail.toolbar_action_by_id
import org.astermail.android.ui.mail.toolbar_action_catalog
import org.astermail.android.ui.mail.toolbar_slot_count
import org.astermail.android.settings.shared_settings_view_model

private data class editing_target(val kind: String, val index: Int)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomizeToolbarScreen(
    on_back: () -> Unit,
    settings_vm: SettingsViewModel = shared_settings_view_model(),
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val server_prefs = settings_state.preferences
    val prefs_authoritative = server_prefs != null && settings_state.preferences_authoritative

    LaunchedEffect(Unit) { settings_vm.load_preferences() }

    var reading_slots by remember { mutableStateOf(load_toolbar_actions(context)) }
    var selection_slots by remember { mutableStateOf(load_selection_toolbar_actions(context)) }
    var editing by remember { mutableStateOf<editing_target?>(null) }

    LaunchedEffect(server_prefs?.toolbar_actions) {
        val raw = server_prefs?.toolbar_actions
        if (raw != null) {
            val parsed = parse_toolbar_actions(raw)
            if (parsed != reading_slots) reading_slots = parsed
            cache_toolbar_actions(context, parsed)
        }
    }

    LaunchedEffect(server_prefs?.selection_toolbar_actions) {
        val raw = server_prefs?.selection_toolbar_actions
        if (raw != null) {
            val parsed = parse_selection_toolbar_actions(raw)
            if (parsed != selection_slots) selection_slots = parsed
            cache_selection_toolbar_actions(context, parsed)
        }
    }

    fun save_prefs() {
        val base = server_prefs
        if (base == null || !settings_state.preferences_authoritative) return
        settings_vm.save_preferences(
            base.copy(
                toolbar_actions = reading_slots.joinToString(","),
                selection_toolbar_actions = selection_slots.joinToString(","),
            ),
        )
    }

    fun set_slot(target: editing_target, id: String) {
        if (target.kind == "reading") {
            val current = reading_slots.toMutableList()
            val existing = current.indexOf(id)
            if (existing >= 0 && existing != target.index) {
                current[existing] = current[target.index]
            }
            current[target.index] = id
            reading_slots = current
            cache_toolbar_actions(context, current)
        } else {
            val current = selection_slots.toMutableList()
            val existing = current.indexOf(id)
            if (existing >= 0 && existing != target.index) {
                current[existing] = current[target.index]
            }
            current[target.index] = id
            selection_slots = current
            cache_selection_toolbar_actions(context, current)
        }
        save_prefs()
    }

    detail_scaffold(
        title = stringResource(R.string.customize_toolbar),
        on_back = on_back,
    ) {
        preferences_save_error_banner()
        Text(
            text = stringResource(R.string.customize_toolbar_subtitle),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = AsterSpacing.md),
        )
        if (!prefs_authoritative && !settings_state.is_loading) {
            Text(
                text = stringResource(R.string.failed_to_load),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = AsterSpacing.md),
            )
        }
        toolbar_section(
            kind = "selection",
            title = stringResource(R.string.selection_toolbar),
            slots = selection_slots,
            slot_count = selection_toolbar_slot_count,
            action_lookup = ::selection_toolbar_action_by_id,
            on_edit = { editing = it },
            enabled = prefs_authoritative,
        )
        v_gap(AsterSpacing.lg)
        toolbar_section(
            kind = "reading",
            title = stringResource(R.string.reading_toolbar),
            slots = reading_slots,
            slot_count = toolbar_slot_count,
            action_lookup = ::toolbar_action_by_id,
            on_edit = { editing = it },
            enabled = prefs_authoritative,
        )
        v_gap(AsterSpacing.xxl)
    }

    val sheet_state = rememberModalBottomSheetState()
    val active = editing
    if (active != null) {
        val catalog = if (active.kind == "reading") toolbar_action_catalog else selection_toolbar_action_catalog
        val slots = if (active.kind == "reading") reading_slots else selection_slots
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheet_state,
            containerColor = colors.bg_secondary,
            dragHandle = { AsterDragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = AsterSpacing.lg),
            ) {
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
                catalog.forEachIndexed { i, action ->
                    val is_current = slots.getOrNull(active.index) == action.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                set_slot(active, action.id)
                                editing = null
                            }
                            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)
                            .testTag("toolbar_choice_${action.id}"),
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
                            Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = colors.accent_blue,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (i < catalog.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
    }
}

@Composable
private fun toolbar_section(
    kind: String,
    title: String,
    slots: List<String>,
    slot_count: Int,
    action_lookup: (String) -> ToolbarAction?,
    on_edit: (editing_target) -> Unit,
    enabled: Boolean,
) {
    val colors = AsterMaterial.colors
    section_label(title)
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        for (i in 0 until slot_count) {
            val id = slots.getOrNull(i)
            val action = id?.let { action_lookup(it) }
            slot_row(
                index = i + 1,
                action_label = action?.let { stringResource(it.label_res) }
                    ?: stringResource(R.string.unset),
                action_icon = action?.icon,
                on_click = { on_edit(editing_target(kind, i)) },
                test_tag = "${kind}_slot_${i + 1}",
                enabled = enabled,
            )
            if (i < slot_count - 1) AsterDivider(modifier = Modifier)
        }
    }
    v_gap(AsterSpacing.sm)
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
                val action = action_lookup(id)
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
                imageVector = TablerIcons.Dots,
                contentDescription = null,
                tint = colors.text_primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun slot_row(
    index: Int,
    action_label: String,
    action_icon: androidx.compose.ui.graphics.vector.ImageVector?,
    on_click: () -> Unit,
    test_tag: String,
    enabled: Boolean,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)
            .testTag(test_tag),
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
            color = if (enabled) colors.accent_blue else colors.text_muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
