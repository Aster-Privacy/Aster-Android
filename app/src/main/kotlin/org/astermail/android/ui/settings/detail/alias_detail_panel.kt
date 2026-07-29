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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.settings.AliasDetailState
import org.astermail.android.settings.SettingsViewModel

@Composable
internal fun alias_detail_panel(
    alias: org.astermail.android.api.settings.AliasInfo,
    detail: AliasDetailState,
    vm: SettingsViewModel,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AsterSpacing.md)
            .testTag("alias_detail_panel"),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.lg),
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_secondary),
        )
        alias_details_section(alias, vm)
        alias_delivery_section(alias, vm)
        if (detail.loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent_blue,
                )
            }
        } else {
            alias_stats_section(detail)
            alias_sender_pinning_section(alias.id, detail, vm)
            alias_rules_section(alias.id, detail, vm)
            alias_blocked_log_section(detail)
            alias_contacts_section(alias.id, detail, vm)
        }
    }
}

@Composable
internal fun panel_section_title(text: String) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_secondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun panel_hint_text(text: String) {
    Text(text = text, color = AsterMaterial.colors.text_muted, fontSize = 11.sp)
}

@Composable
internal fun panel_locked_section(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        panel_section_title(title)
        panel_hint_text(stringResource(R.string.alias_feature_locked))
    }
}

@Composable
internal fun panel_row_chip(label: String) {
    val colors = AsterMaterial.colors
    Text(
        text = label,
        color = colors.danger,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.danger.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun alias_details_section(
    alias: org.astermail.android.api.settings.AliasInfo,
    vm: SettingsViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
        panel_section_title(stringResource(R.string.alias_panel_details_title))
        alias_inline_field(
            label = stringResource(R.string.alias_panel_display_name),
            placeholder = stringResource(R.string.alias_panel_display_name_placeholder),
            value = alias.encrypted_display_name.orEmpty(),
            test_tag = "alias_field_display_name",
            on_save = { vm.update_alias_display_name(alias.id, it) },
        )
        alias_inline_field(
            label = stringResource(R.string.alias_note_title),
            placeholder = stringResource(R.string.alias_note_placeholder),
            value = alias.encrypted_note.orEmpty(),
            test_tag = "alias_field_note",
            on_save = { vm.update_alias_note(alias.id, it) },
        )
        alias_inline_field(
            label = stringResource(R.string.alias_panel_websites),
            placeholder = stringResource(R.string.alias_panel_websites_placeholder),
            value = alias.encrypted_websites.orEmpty(),
            test_tag = "alias_field_websites",
            on_save = { vm.update_alias_websites(alias.id, it) },
        )
    }
}

@Composable
private fun alias_delivery_section(
    alias: org.astermail.android.api.settings.AliasInfo,
    vm: SettingsViewModel,
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load_labels(folder_type = "folder") }

    val folders = state.labels.filter {
        (it.folder_type == "folder" || it.folder_type == "custom") &&
            !it.encrypted_name.isNullOrBlank()
    }
    val selected_folder = alias.delivery_folder_token?.let { token ->
        folders.firstOrNull { it.label_token == token }
    }
    val selected_label = when {
        selected_folder != null -> selected_folder.encrypted_name.orEmpty()
        alias.delivery_folder_token != null -> stringResource(R.string.alias_delivery_folder_missing)
        alias.never_inbox -> stringResource(R.string.folder_archive)
        else -> stringResource(R.string.folder_inbox)
    }

    alias_delivery_picker(
        selected_label = selected_label,
        folders = folders,
        on_select_inbox = { vm.set_alias_delivery(alias.id, null, to_archive = false) },
        on_select_archive = { vm.set_alias_delivery(alias.id, null, to_archive = true) },
        on_select_folder = { token -> vm.set_alias_delivery(alias.id, token, to_archive = false) },
    )
}

@Composable
internal fun alias_delivery_picker(
    selected_label: String,
    folders: List<org.astermail.android.api.labels.LabelItem>,
    on_select_inbox: () -> Unit,
    on_select_archive: () -> Unit,
    on_select_folder: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var menu_open by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
        panel_section_title(stringResource(R.string.alias_delivery_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.alias_delivery_folder_title),
                    color = colors.text_primary,
                    fontSize = 13.sp,
                )
                panel_hint_text(stringResource(R.string.alias_delivery_folder_subtitle))
            }
            Spacer(Modifier.width(AsterSpacing.sm))
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bg_secondary)
                        .clickable { menu_open = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("alias_delivery_folder_selector"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected_label,
                        color = colors.text_primary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = TablerIcons.ChevronDown,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(expanded = menu_open, onDismissRequest = { menu_open = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_inbox)) },
                        onClick = {
                            menu_open = false
                            on_select_inbox()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_archive)) },
                        onClick = {
                            menu_open = false
                            on_select_archive()
                        },
                    )
                    folders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder.encrypted_name.orEmpty()) },
                            onClick = {
                                menu_open = false
                                on_select_folder(folder.label_token)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun alias_stats_section(detail: AliasDetailState) {
    if (detail.stats_locked) {
        panel_locked_section(stringResource(R.string.alias_stats_title))
        return
    }
    val stats = detail.stats ?: return
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
        panel_section_title(stringResource(R.string.alias_stats_title))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            alias_stat_cell(stringResource(R.string.alias_stats_received), stats.received, Modifier.weight(1f))
            alias_stat_cell(stringResource(R.string.alias_stats_forwarded), stats.forwarded, Modifier.weight(1f))
            alias_stat_cell(stringResource(R.string.alias_stats_blocked), stats.blocked, Modifier.weight(1f))
            alias_stat_cell(stringResource(R.string.alias_stats_replied), stats.replied, Modifier.weight(1f))
        }
        val created = format_panel_date(stats.created_at)
        if (created.isNotBlank()) {
            panel_hint_text(stringResource(R.string.alias_stats_created, created))
        }
    }
}

@Composable
private fun alias_stat_cell(label: String, value: Long, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg_secondary)
            .padding(vertical = AsterSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            color = colors.text_primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = label, color = colors.text_muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun format_panel_date(value: String): String {
    if (value.isBlank()) return ""
    return value.substringBefore('T')
}
