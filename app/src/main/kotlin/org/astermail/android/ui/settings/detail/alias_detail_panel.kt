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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import org.astermail.android.api.settings.AliasRun
import org.astermail.android.settings.AliasDetailState
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.is_alias_run_active

@Composable
internal fun alias_detail_panel(
    alias: org.astermail.android.api.settings.AliasInfo,
    detail: AliasDetailState,
    vm: SettingsViewModel,
    rule_delivery: AliasRuleDeliveryNote? = null,
    rule_label: AliasRuleLabelNote? = null,
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
        alias_delivery_section(alias, vm, detail, rule_delivery, rule_label)
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
        } else if (detail.load_failed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                panel_hint_text(stringResource(R.string.failed_to_load))
                Text(
                    text = stringResource(R.string.retry),
                    color = colors.accent_blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { vm.load_alias_detail(alias.id, force = true) },
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
        alias_websites_field(
            websites = alias.websites,
            on_add = { vm.add_alias_website(alias.id, it) },
            on_remove = { vm.remove_alias_website(alias.id, it) },
        )
    }
}

data class AliasRuleDeliveryNote(
    val rule_name: String,
    val folder_name: String,
    val matches_alias_delivery: Boolean,
)

data class AliasRuleLabelNote(
    val rule_name: String,
    val label_names: String,
    val matches_alias_label: Boolean,
)

@Composable
private fun alias_delivery_section(
    alias: org.astermail.android.api.settings.AliasInfo,
    vm: SettingsViewModel,
    detail: AliasDetailState,
    rule_delivery: AliasRuleDeliveryNote?,
    rule_label: AliasRuleLabelNote?,
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.load_labels(folder_type = "folder")
        vm.load_tags(force = false)
    }

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

    val tags = state.tags.filter { it.encrypted_name.isNotBlank() }
    val selected_tag = alias.delivery_label_token?.let { token ->
        tags.firstOrNull { it.tag_token == token }
    }
    val selected_label_name = when {
        selected_tag != null -> selected_tag.encrypted_name
        alias.delivery_label_token != null -> stringResource(R.string.alias_delivery_label_missing)
        else -> stringResource(R.string.alias_delivery_label_none)
    }

    val apply_unsupported = alias.delivery_folder_token?.let { token ->
        state.labels.firstOrNull { it.label_token == token }?.folder_type == "spam"
    } ?: false
    val nothing_to_apply = alias.delivery_folder_token == null &&
        !alias.never_inbox &&
        alias.delivery_label_token == null

    alias_delivery_picker(
        selected_label = selected_label,
        folders = folders,
        rule_delivery = rule_delivery,
        selected_label_name = selected_label_name,
        tags = tags,
        rule_label = rule_label,
        on_select_inbox = { vm.set_alias_delivery(alias.id, null, to_archive = false) },
        on_select_archive = { vm.set_alias_delivery(alias.id, null, to_archive = true) },
        on_select_folder = { token -> vm.set_alias_delivery(alias.id, token, to_archive = false) },
        on_select_delivery_label = { token -> vm.set_alias_delivery_label(alias.id, token) },
        apply_section = {
            alias_apply_existing_row(
                run = detail.apply_run,
                busy = detail.apply_busy,
                unsupported = apply_unsupported,
                nothing_to_apply = nothing_to_apply,
                on_apply = { vm.run_alias_on_existing(alias.id) },
                on_cancel = { vm.cancel_alias_run(alias.id) },
            )
        },
    )
}

@Composable
internal fun alias_delivery_rule_note(note: AliasRuleDeliveryNote, selected_label: String) {
    val colors = AsterMaterial.colors
    val text = if (note.matches_alias_delivery) {
        stringResource(R.string.alias_delivery_rule_note, note.rule_name, note.folder_name)
    } else {
        stringResource(
            R.string.alias_delivery_rule_conflict,
            note.rule_name,
            note.folder_name,
            selected_label,
        )
    }
    Text(
        text = text,
        color = if (note.matches_alias_delivery) colors.text_muted else colors.warning,
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alias_delivery_rule_note"),
    )
}

@Composable
internal fun alias_delivery_label_rule_note(note: AliasRuleLabelNote, selected_label_name: String) {
    val colors = AsterMaterial.colors
    val text = if (note.matches_alias_label) {
        stringResource(R.string.alias_delivery_label_rule_note, note.rule_name, note.label_names)
    } else {
        stringResource(
            R.string.alias_delivery_label_rule_conflict,
            note.rule_name,
            note.label_names,
            selected_label_name,
        )
    }
    Text(
        text = text,
        color = if (note.matches_alias_label) colors.text_muted else colors.warning,
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alias_delivery_label_rule_note"),
    )
}

@Composable
internal fun alias_delivery_picker(
    selected_label: String,
    folders: List<org.astermail.android.api.labels.LabelItem>,
    rule_delivery: AliasRuleDeliveryNote? = null,
    selected_label_name: String = "",
    tags: List<org.astermail.android.api.tags.TagItem> = emptyList(),
    rule_label: AliasRuleLabelNote? = null,
    on_select_inbox: () -> Unit,
    on_select_archive: () -> Unit,
    on_select_folder: (String) -> Unit,
    on_select_delivery_label: (String?) -> Unit = {},
    apply_section: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    var menu_open by remember { mutableStateOf(false) }
    var label_menu_open by remember { mutableStateOf(false) }

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
        rule_delivery?.let { alias_delivery_rule_note(it, selected_label) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.alias_delivery_label_title),
                    color = colors.text_primary,
                    fontSize = 13.sp,
                )
                panel_hint_text(stringResource(R.string.alias_delivery_label_subtitle))
            }
            Spacer(Modifier.width(AsterSpacing.sm))
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.bg_secondary)
                        .clickable { label_menu_open = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("alias_delivery_label_selector"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected_label_name,
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
                DropdownMenu(
                    expanded = label_menu_open,
                    onDismissRequest = { label_menu_open = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.alias_delivery_label_none)) },
                        onClick = {
                            label_menu_open = false
                            on_select_delivery_label(null)
                        },
                    )
                    tags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag.encrypted_name) },
                            onClick = {
                                label_menu_open = false
                                on_select_delivery_label(tag.tag_token)
                            },
                        )
                    }
                }
            }
        }
        rule_label?.let { alias_delivery_label_rule_note(it, selected_label_name) }
        apply_section?.invoke()
    }
}

@Composable
private fun alias_apply_existing_status(run: AliasRun?, unsupported: Boolean): String? {
    if (unsupported) return stringResource(R.string.alias_apply_existing_unavailable)
    if (run == null) return null
    val total = run.total_estimate
    return when (run.status) {
        "pending" -> stringResource(R.string.alias_apply_existing_queued)
        "running" -> if (total != null && total > 0) {
            stringResource(R.string.alias_apply_existing_progress_total, run.scanned, total, run.applied)
        } else {
            stringResource(R.string.alias_apply_existing_progress, run.scanned, run.applied)
        }
        "completed" -> stringResource(R.string.alias_apply_existing_done, run.scanned, run.applied)
        "canceled" -> stringResource(R.string.alias_apply_existing_canceled, run.applied)
        "failed" -> stringResource(R.string.alias_apply_existing_error)
        else -> null
    }
}

@Composable
internal fun alias_apply_existing_row(
    run: AliasRun?,
    busy: Boolean,
    unsupported: Boolean,
    nothing_to_apply: Boolean,
    on_apply: () -> Unit,
    on_cancel: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val active = is_alias_run_active(run)
    val enabled = !busy && !unsupported && (active || !nothing_to_apply)
    val disabled_interaction = remember { MutableInteractionSource() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.alias_apply_existing),
                color = colors.text_primary,
                fontSize = 13.sp,
            )
            panel_hint_text(stringResource(R.string.alias_apply_existing_desc))
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bg_secondary)
                .then(
                    if (enabled) {
                        Modifier.clickable { if (active) on_cancel() else on_apply() }
                    } else {
                        Modifier.clickable(
                            interactionSource = disabled_interaction,
                            indication = null,
                            onClick = {},
                        )
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("alias_apply_existing_button"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = colors.text_muted,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = stringResource(
                    if (active) {
                        R.string.alias_apply_existing_cancel
                    } else {
                        R.string.alias_apply_existing_action
                    },
                ),
                color = if (enabled) colors.text_primary else colors.text_muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    alias_apply_existing_status(run, unsupported)?.let { status ->
        Text(
            text = status,
            color = colors.text_muted,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("alias_apply_existing_status"),
        )
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
    return absolute_date_label(value)
}
