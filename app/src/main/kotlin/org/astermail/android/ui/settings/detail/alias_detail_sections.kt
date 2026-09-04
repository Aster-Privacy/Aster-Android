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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import org.astermail.android.R
import org.astermail.android.api.aliases.AliasRule
import org.astermail.android.api.aliases.SENDER_PIN_MODE_ALLOWLIST
import org.astermail.android.api.aliases.SENDER_PIN_MODE_LOCK_FIRST
import org.astermail.android.api.aliases.SENDER_PIN_MODE_OFF
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.keep_visible_above_keyboard
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.settings.AliasDetailState
import org.astermail.android.settings.SettingsViewModel

@Composable
internal fun alias_inline_field(
    label: String,
    placeholder: String,
    value: String,
    test_tag: String,
    on_save: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        AsterTextField(
            value = draft,
            onValueChange = { if (it.length <= 500 || it.length < draft.length) draft = it },
            label = label,
            placeholder = placeholder,
            modifier = Modifier
                .keep_visible_above_keyboard()
                .testTag(test_tag),
        )
        if (draft.trim() != value.trim()) {
            Text(
                text = stringResource(R.string.save),
                color = AsterMaterial.colors.accent_blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { on_save(draft) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("${test_tag}_save"),
            )
        }
    }
}

@Composable
internal fun alias_add_row(
    placeholder: String,
    button_label: String,
    test_tag: String,
    enabled: Boolean,
    on_add: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsterTextField(
            value = draft,
            onValueChange = { draft = it.take(320) },
            placeholder = placeholder,
            modifier = Modifier
                .weight(1f)
                .testTag(test_tag),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        val can_add = enabled && draft.isNotBlank()
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (can_add) {
                        AsterMaterial.colors.accent_blue.copy(alpha = 0.16f)
                    } else {
                        AsterMaterial.colors.bg_secondary
                    },
                )
                .clickable(enabled = can_add) {
                    on_add(draft)
                    draft = ""
                }
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .testTag("${test_tag}_button"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.Plus,
                contentDescription = null,
                tint = if (can_add) AsterMaterial.colors.accent_blue else AsterMaterial.colors.text_muted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = button_label,
                color = if (can_add) AsterMaterial.colors.accent_blue else AsterMaterial.colors.text_muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun alias_sender_pinning_section(
    alias_id: String,
    detail: AliasDetailState,
    vm: SettingsViewModel,
) {
    if (detail.pins_locked) {
        panel_locked_section(stringResource(R.string.alias_sender_pinning_title))
        return
    }
    val colors = AsterMaterial.colors
    var menu_open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
        panel_section_title(stringResource(R.string.alias_sender_pinning_title))
        panel_hint_text(stringResource(R.string.alias_sender_pinning_description))
        Box {
            Row(
                modifier = Modifier
                    .clip(SquircleShape(12.dp))
                    .background(colors.input_bg, SquircleShape(12.dp))
                    .border(1.dp, colors.input_border, SquircleShape(12.dp))
                    .clickable { menu_open = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("alias_pin_mode_selector"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(pin_mode_label(detail.pin_mode)),
                    color = colors.text_primary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = TablerIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(16.dp),
                )
            }
            aster_dropdown_menu(expanded = menu_open, on_dismiss = { menu_open = false }) {
                listOf(SENDER_PIN_MODE_OFF, SENDER_PIN_MODE_LOCK_FIRST, SENDER_PIN_MODE_ALLOWLIST).forEach { mode ->
                    aster_dropdown_item(
                        label = stringResource(pin_mode_label(mode)),
                        selected = mode == detail.pin_mode,
                        on_click = {
                            menu_open = false
                            vm.set_alias_pin_mode(alias_id, mode)
                        },
                    )
                }
            }
        }
        if (detail.pins.isEmpty()) {
            panel_hint_text(stringResource(R.string.alias_pins_empty))
        } else {
            detail.pins.forEach { pin ->
                alias_entry_row(
                    label = pin.sender,
                    blocked = pin.is_blocked,
                    on_delete = { vm.delete_alias_pin(alias_id, pin.id) },
                )
            }
        }
        alias_add_row(
            placeholder = stringResource(R.string.alias_pin_placeholder),
            button_label = stringResource(R.string.alias_pin_add),
            test_tag = "alias_pin_input",
            enabled = !detail.busy,
            on_add = { vm.add_alias_pin(alias_id, it) },
        )
    }
}

private fun pin_mode_label(mode: Int): Int = when (mode) {
    SENDER_PIN_MODE_LOCK_FIRST -> R.string.alias_pin_mode_lock_first
    SENDER_PIN_MODE_ALLOWLIST -> R.string.alias_pin_mode_allowlist
    else -> R.string.alias_pin_mode_off
}

@Composable
internal fun alias_entry_row(
    label: String,
    blocked: Boolean,
    on_delete: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg_secondary)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.text_primary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (blocked) {
            panel_row_chip(stringResource(R.string.alias_pin_blocked))
            Spacer(Modifier.width(4.dp))
        }
        trailing?.invoke()
        AsterIconButton(
            icon = TablerIcons.Trash,
            content_description = stringResource(R.string.delete),
            onClick = on_delete,
            tint = colors.danger,
        )
    }
}

@Composable
internal fun alias_blocked_log_section(detail: AliasDetailState) {
    if (detail.blocked_locked) {
        panel_locked_section(stringResource(R.string.alias_blocked_log_title))
        return
    }
    val colors = AsterMaterial.colors
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
        panel_section_title(stringResource(R.string.alias_blocked_log_title))
        if (detail.blocked_events.isEmpty()) {
            panel_hint_text(stringResource(R.string.alias_blocked_log_empty))
        } else {
            detail.blocked_events.take(20).forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg_secondary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = event.blocked_reason,
                        color = colors.text_primary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = format_panel_date(event.created_at),
                        color = colors.text_muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun alias_contacts_section(
    alias_id: String,
    detail: AliasDetailState,
    vm: SettingsViewModel,
) {
    if (detail.contacts_locked) {
        panel_locked_section(stringResource(R.string.alias_contacts_title))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
        panel_section_title(stringResource(R.string.alias_contacts_title))
        panel_hint_text(stringResource(R.string.alias_contacts_description))
        if (detail.contacts.isEmpty()) {
            panel_hint_text(stringResource(R.string.alias_contacts_empty))
        } else {
            detail.contacts.forEach { contact ->
                alias_entry_row(
                    label = contact.contact,
                    blocked = contact.is_blocked,
                    on_delete = { vm.delete_alias_contact(alias_id, contact.id) },
                    trailing = {
                        Text(
                            text = if (contact.is_blocked) {
                                stringResource(R.string.alias_contact_unblock)
                            } else {
                                stringResource(R.string.alias_contact_block)
                            },
                            color = AsterMaterial.colors.accent_blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    vm.set_alias_contact_blocked(alias_id, contact.id, !contact.is_blocked)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    },
                )
            }
        }
        alias_add_row(
            placeholder = stringResource(R.string.alias_contact_placeholder),
            button_label = stringResource(R.string.alias_contact_add),
            test_tag = "alias_contact_input",
            enabled = !detail.busy,
            on_add = { vm.add_alias_contact(alias_id, it) },
        )
    }
}

@Composable
internal fun alias_rules_section(
    alias_id: String,
    detail: AliasDetailState,
    vm: SettingsViewModel,
) {
    if (detail.rules_locked) {
        panel_locked_section(stringResource(R.string.alias_rules_title))
        return
    }
    var editor_open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                panel_section_title(stringResource(R.string.alias_rules_title))
                panel_hint_text(stringResource(R.string.alias_rules_description))
            }
            Text(
                text = stringResource(R.string.alias_rule_add),
                color = AsterMaterial.colors.accent_blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { editor_open = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("alias_rule_add"),
            )
        }
        if (detail.rules.isEmpty()) {
            panel_hint_text(stringResource(R.string.alias_rules_empty))
        } else {
            detail.rules.forEach { rule ->
                alias_rule_row(alias_id, rule, vm)
            }
        }
    }
    if (editor_open) {
        alias_rule_editor_dialog(
            on_dismiss = { editor_open = false },
            on_save = { field, operator, value, actions ->
                vm.create_alias_rule(alias_id, field, operator, value, actions)
                editor_open = false
            },
        )
    }
}

@Composable
private fun alias_rule_row(alias_id: String, rule: AliasRule, vm: SettingsViewModel) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg_secondary)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = describe_rule_conditions(rule),
                color = colors.text_primary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val actions = describe_rule_actions(rule)
            if (actions.isNotBlank()) {
                Text(
                    text = actions,
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AsterSwitch(
            checked = rule.is_enabled,
            onCheckedChange = { vm.set_alias_rule_enabled(alias_id, rule.id, it) },
        )
        AsterIconButton(
            icon = TablerIcons.Trash,
            content_description = stringResource(R.string.delete),
            onClick = { vm.delete_alias_rule(alias_id, rule.id) },
            tint = colors.danger,
        )
    }
}

@Composable
private fun describe_rule_conditions(rule: AliasRule): String {
    if (rule.conditions.isEmpty()) return stringResource(R.string.alias_rule_field_all)
    val parts = rule.conditions.map { condition ->
        if (condition.field == "all") {
            stringResource(R.string.alias_rule_field_all)
        } else {
            val field = stringResource(rule_field_label(condition.field))
            val operator = stringResource(rule_operator_label(condition.operator))
            "$field $operator \"${condition.value}\""
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun describe_rule_actions(rule: AliasRule): String {
    val parts = mutableListOf<String>()
    if (rule.actions.block == true) parts.add(stringResource(R.string.alias_rule_action_block))
    if (rule.actions.to_trash == true) parts.add(stringResource(R.string.alias_rule_action_to_trash))
    val label = rule.actions.label
    if (!label.isNullOrBlank()) {
        parts.add("${stringResource(R.string.alias_rule_action_label)}: $label")
    }
    return parts.joinToString(", ")
}

internal fun rule_field_label(field: String): Int = when (field) {
    "from" -> R.string.alias_rule_field_from
    "to" -> R.string.alias_rule_field_to
    "subject" -> R.string.alias_rule_field_subject
    else -> R.string.alias_rule_field_all
}

internal fun rule_operator_label(operator: String): Int = when (operator) {
    "equals" -> R.string.alias_rule_operator_equals
    "starts_with" -> R.string.alias_rule_operator_starts_with
    "ends_with" -> R.string.alias_rule_operator_ends_with
    "matches_regex" -> R.string.alias_rule_operator_matches_regex
    else -> R.string.alias_rule_operator_contains
}
