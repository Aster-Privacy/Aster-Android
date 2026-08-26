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

package org.astermail.android.ui.settings.mail_rules

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.AttachmentNameOp
import org.astermail.android.api.mail_rules.AuthResult
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.DateOp
import org.astermail.android.api.mail_rules.MatchMode
import org.astermail.android.api.mail_rules.NumericOp
import org.astermail.android.api.mail_rules.ReadState
import org.astermail.android.api.mail_rules.TextOp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.parse_hex_color_safe
import org.astermail.android.folders.flatten_folder_tree
import org.astermail.android.mail_rules.AliasDeliverySetting
import org.astermail.android.mail_rules.rule_alias_delivery_conflict
import org.astermail.android.mail_rules.rule_alias_label_conflict
import org.astermail.android.folders.folder_path
import org.astermail.android.folders.is_custom_folder
import org.astermail.android.folders.is_folder_protected
import org.astermail.android.folders.max_folder_depth
import org.astermail.android.ui.drawer.create_folder_dialog
import org.astermail.android.ui.drawer.create_label_dialog
import org.astermail.android.ui.drawer.folder_parent_option
import org.astermail.android.ui.drawer.resolve_label_icon
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.AsterTopBar
import org.astermail.android.mail_rules.MailRulesViewModel
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.settings.detail.skeleton_card_list
import org.astermail.android.ui.settings.detail.skeleton_hero_card
import org.astermail.android.ui.settings.detail.skeleton_section_label
import org.astermail.android.ui.settings.mail_rules.pickers.address_value_picker
import org.astermail.android.ui.settings.mail_rules.pickers.alias_option
import org.astermail.android.ui.settings.mail_rules.pickers.boolean_value_picker
import org.astermail.android.ui.settings.mail_rules.pickers.color_picker
import org.astermail.android.ui.settings.mail_rules.pickers.decimal_value_picker
import org.astermail.android.ui.settings.mail_rules.pickers.field_picker
import org.astermail.android.ui.settings.mail_rules.pickers.folder_picker
import org.astermail.android.ui.settings.mail_rules.pickers.header_value_picker
import org.astermail.android.ui.settings.mail_rules.pickers.label_multi_picker
import org.astermail.android.ui.settings.mail_rules.pickers.numeric_value_picker
import org.astermail.android.ui.settings.mail_rules.pickers.options_picker
import org.astermail.android.ui.settings.mail_rules.pickers.picker_item
import org.astermail.android.ui.settings.mail_rules.pickers.snooze_picker
import org.astermail.android.ui.settings.mail_rules.pickers.text_value_picker
import org.astermail.android.settings.shared_settings_view_model

private sealed class active_sheet {
    data object none : active_sheet()
    data object pick_field : active_sheet()
    data class pick_operator(val cond_index: Int) : active_sheet()
    data class pick_value(val cond_index: Int) : active_sheet()
    data class pick_action_kind(val action_index: Int) : active_sheet()
    data class pick_action_target(val action_index: Int) : active_sheet()
    data object pick_color : active_sheet()
    data object pick_match_mode : active_sheet()
}

@Composable
fun RuleEditorScreen(
    rule_id: String?,
    on_back: () -> Unit,
    on_saved: () -> Unit,
) {
    val vm: MailRulesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings_vm: SettingsViewModel = shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) {
        if (state.rules.isEmpty()) vm.load()
        settings_vm.load_labels()
        settings_vm.load_tags(force = false)
    }

    val editor_context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(settings_state.action_result) {
        val msg = settings_state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(editor_context, msg, android.widget.Toast.LENGTH_SHORT).show()
        settings_vm.clear_action_result()
    }

    val existing = remember(rule_id, state.rules) {
        rule_id?.let { id -> state.rules.firstOrNull { it.id == id } }
    }

    if (rule_id != null && existing == null && state.is_loading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg_primary)
                .systemBarsPadding()
                .testTag("rule_editor_loading"),
        ) {
            AsterTopBar(
                title = stringResource(R.string.mail_rules_edit_rule),
                on_back = on_back,
            )
            AsterDivider()
            Column(modifier = Modifier.fillMaxSize().padding(AsterSpacing.lg)) {
                skeleton_hero_card(lines = 2)
                Spacer(Modifier.height(AsterSpacing.lg))
                skeleton_section_label()
                skeleton_card_list(rows = 2)
                Spacer(Modifier.height(AsterSpacing.lg))
                skeleton_section_label()
                skeleton_card_list(rows = 2)
            }
        }
        return
    }

    if (rule_id != null && existing == null && state.error != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg_primary)
                .systemBarsPadding()
                .testTag("rule_editor_error"),
        ) {
            AsterTopBar(
                title = stringResource(R.string.mail_rules_edit_rule),
                on_back = on_back,
            )
            AsterDivider()
            Column(
                modifier = Modifier.fillMaxSize().padding(AsterSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(state.error!!),
                    color = colors.text_secondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AsterSpacing.lg))
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.retry),
                    onClick = { vm.load(force_refresh = true) },
                )
            }
        }
        return
    }

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var color_hex by remember(existing) { mutableStateOf(existing?.color ?: palette_colors.first()) }
    var match_mode by remember(existing) { mutableStateOf(existing?.match_mode ?: MatchMode.ALL) }
    val conditions = remember(existing) {
        mutableStateListOf<Condition>().apply { existing?.conditions?.let { addAll(it) } }
    }
    val actions = remember(existing) {
        mutableStateListOf<Action>().apply {
            existing?.actions?.let { addAll(strip_unavailable_actions(it)) }
        }
    }
    val preserved_actions = remember(existing) {
        existing?.actions.orEmpty().filter { action_of(it) in unavailable_actions }
    }

    var sheet: active_sheet by remember { mutableStateOf(active_sheet.none) }
    var next_sheet: active_sheet? by remember { mutableStateOf(null) }
    var create_target by remember { mutableStateOf<String?>(null) }
    var create_for_action by remember { mutableStateOf<Int?>(null) }
    var pending_field_index: Int? by remember { mutableStateOf(null) }
    var auto_advance by remember { mutableStateOf(false) }
    var is_saving by remember { mutableStateOf(false) }
    var save_error by remember { mutableStateOf<Int?>(null) }
    var duplicate_warning by remember { mutableStateOf<Int?>(null) }
    var skipped_duplicates by remember { mutableStateOf(0) }
    var switched_to_any by remember { mutableStateOf(false) }

    val folders = remember(settings_state.labels) {
        flatten_folder_tree(settings_state.labels)
            .filter { !it.label.encrypted_name.isNullOrBlank() }
            .map { node ->
                picker_item(
                    id = node.label.label_token,
                    label = node.label.encrypted_name.orEmpty(),
                    icon = if (is_folder_protected(node.label)) TablerIcons.Lock else TablerIcons.Folder,
                    icon_tint = node.label.encrypted_color
                        ?.takeIf { it.startsWith("#") }
                        ?.let { parse_hex_color_safe(it) },
                    depth = node.depth,
                )
            }
    }
    val labels = remember(settings_state.labels, settings_state.tags) {
        val from_tags = org.astermail.android.labels.tag_rows(settings_state.tags)
            .mapIndexed { idx, tag ->
                picker_item(
                    id = tag.tag_token,
                    label = tag.encrypted_name,
                    icon = resolve_label_icon(tag.encrypted_icon?.takeIf { it.isNotBlank() }),
                    icon_tint = tag.encrypted_color?.let { parse_hex_color_safe(it) }
                        ?: rules_label_palette[idx % rules_label_palette.size],
                )
            }
        val from_labels = org.astermail.android.labels.label_rows(settings_state.labels)
            .mapIndexed { idx, label ->
                picker_item(
                    id = label.label_token,
                    label = label.encrypted_name.orEmpty(),
                    icon = resolve_label_icon(label.encrypted_icon?.takeIf { it.isNotBlank() }),
                    icon_tint = label.encrypted_color
                        ?.takeIf { it.startsWith("#") }
                        ?.let { parse_hex_color_safe(it) }
                        ?: rules_label_palette[(from_tags.size + idx) % rules_label_palette.size],
                )
            }
        from_tags + from_labels
    }
    val alias_options = remember(settings_state.aliases) {
        settings_state.aliases
            .asSequence()
            .filter { !it.decryption_failed }
            .map { it.address to it.encrypted_display_name?.takeIf { name -> name.isNotBlank() } }
            .filter { (address, _) -> address.contains('@') && !address.startsWith('@') }
            .distinctBy { (address, _) -> address.lowercase() }
            .sortedBy { (address, _) -> address.lowercase() }
            .map { (address, display_name) -> alias_option(address = address, display_name = display_name) }
            .toList()
    }
    val folder_parent_options = remember(settings_state.labels) {
        flatten_folder_tree(settings_state.labels)
            .filter { it.depth < max_folder_depth }
            .mapNotNull { node ->
                val readable_name = node.label.encrypted_name?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                folder_parent_option(
                    token = node.label.label_token,
                    label = readable_name,
                    depth = node.depth,
                    path_label = folder_path(settings_state.labels, node.label.label_token)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                )
            }
    }

    val is_read_only = existing != null && rule_is_advanced(existing)

    val is_valid = name.isNotBlank() &&
        conditions.isNotEmpty() && conditions.all { is_condition_complete(it) } &&
        (actions.isNotEmpty() || preserved_actions.isNotEmpty()) && actions.all { is_action_complete(it) }

    val save_hint: Int? = when {
        is_read_only -> null
        name.isBlank() -> R.string.rules_hint_name
        conditions.isEmpty() -> R.string.rules_hint_add_condition
        conditions.any { !is_condition_complete(it) } -> R.string.rules_hint_finish_condition
        actions.isEmpty() && preserved_actions.isEmpty() -> R.string.rules_hint_add_action
        actions.any { !is_action_complete(it) } -> R.string.rules_hint_finish_action
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .testTag("rule_editor"),
    ) {
        AsterTopBar(
            title = if (existing == null) stringResource(R.string.mail_rules_new_rule) else stringResource(R.string.mail_rules_edit_rule),
            on_back = on_back,
        )
        AsterDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AsterSpacing.lg),
        ) {
            if (is_read_only) {
                Text(
                    text = stringResource(R.string.mail_rules_advanced_notice),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg_tertiary)
                        .border(1.dp, colors.border_secondary, RoundedCornerShape(10.dp))
                        .padding(AsterSpacing.md)
                        .testTag("rule_advanced_notice"),
                )
                Spacer(Modifier.height(AsterSpacing.md))
            }

            AsterTextField(
                value = name,
                onValueChange = { if (it.length <= 200) name = it },
                label = stringResource(R.string.mail_rules_name),
                placeholder = stringResource(R.string.mail_rules_name_placeholder),
                modifier = Modifier.testTag("rule_name"),
            )
            Spacer(Modifier.height(AsterSpacing.md))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !is_read_only) { sheet = active_sheet.pick_color }
                    .padding(vertical = AsterSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.mail_rules_color),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(parse_hex(color_hex), CircleShape),
                )
            }

            Spacer(Modifier.height(AsterSpacing.xl))
            Text(
                text = stringResource(R.string.mail_rules_when_section),
                color = colors.text_tertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AsterSpacing.sm))

            conditions.forEachIndexed { index, condition ->
                condition_chip(
                    condition = condition,
                    on_field = {
                        if (!is_read_only) {
                            pending_field_index = index
                            sheet = active_sheet.pick_field
                        }
                    },
                    on_operator = { if (!is_read_only) sheet = active_sheet.pick_operator(index) },
                    on_value = { if (!is_read_only) sheet = active_sheet.pick_value(index) },
                    on_remove = {
                        if (!is_read_only) {
                            duplicate_warning = null
                            skipped_duplicates = 0
                            switched_to_any = false
                            if (conditions.size == 1) {
                                conditions.removeAt(0)
                                sheet = active_sheet.pick_field
                            } else {
                                conditions.removeAt(index)
                            }
                        }
                    },
                    modifier = Modifier.testTag("cond_$index"),
                )
                if (index < conditions.lastIndex) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    and_or_pill(
                        label = if (match_mode == MatchMode.ALL) stringResource(R.string.mail_rules_and) else stringResource(R.string.mail_rules_or),
                        on_click = { if (!is_read_only) sheet = active_sheet.pick_match_mode },
                    )
                    Spacer(Modifier.height(AsterSpacing.sm))
                } else {
                    Spacer(Modifier.height(AsterSpacing.sm))
                }
            }
            if (!is_read_only) {
                add_chip_pill(
                    label = stringResource(R.string.mail_rules_add_condition),
                    on_click = {
                        auto_advance = true
                        sheet = active_sheet.pick_field
                    },
                    modifier = Modifier.testTag("add_condition"),
                )
            }

            val existing_duplicates = duplicate_condition_indices(conditions.toList())
            val duplicate_message = duplicate_warning
                ?: existing_duplicates.firstOrNull()?.let { index ->
                    if (condition_is_address_field(conditions[index])) {
                        R.string.mail_rules_duplicate_alias
                    } else {
                        R.string.mail_rules_duplicate_condition
                    }
                }
            if (!is_read_only && duplicate_message != null) {
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(duplicate_message),
                    color = colors.danger,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg_tertiary)
                        .border(1.dp, colors.border_secondary, RoundedCornerShape(10.dp))
                        .padding(AsterSpacing.md)
                        .testTag("rule_duplicate_warning"),
                )
            }

            if (!is_read_only && skipped_duplicates > 0) {
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = pluralStringResource(
                        R.plurals.mail_rules_aliases_skipped,
                        skipped_duplicates,
                        skipped_duplicates,
                    ),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg_tertiary)
                        .border(1.dp, colors.border_secondary, RoundedCornerShape(10.dp))
                        .padding(AsterSpacing.md)
                        .testTag("rule_skipped_warning"),
                )
            }

            if (!is_read_only && switched_to_any) {
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.mail_rules_switched_to_any),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg_tertiary)
                        .border(1.dp, colors.border_secondary, RoundedCornerShape(10.dp))
                        .padding(AsterSpacing.md)
                        .testTag("rule_switched_to_any"),
                )
            }

            if (!is_read_only && conditions_conflict_under_all(conditions.toList(), match_mode)) {
                Spacer(Modifier.height(AsterSpacing.md))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg_tertiary)
                        .border(1.dp, colors.border_secondary, RoundedCornerShape(10.dp))
                        .padding(AsterSpacing.md)
                        .testTag("rule_conflict_warning"),
                ) {
                    Text(
                        text = stringResource(R.string.mail_rules_conflict_warning),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.mail_rules_conflict_switch),
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { match_mode = MatchMode.ANY }
                            .padding(vertical = AsterSpacing.xs, horizontal = AsterSpacing.sm)
                            .testTag("rule_conflict_switch"),
                    )
                }
            }

            Spacer(Modifier.height(AsterSpacing.xl))
            Text(
                text = stringResource(R.string.mail_rules_then_section),
                color = colors.text_tertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AsterSpacing.sm))

            actions.forEachIndexed { index, action ->
                action_chip(
                    action = action,
                    folder_label = (action as? Action.MoveTo)?.folder_token?.let { token ->
                        folders.firstOrNull { it.id == token }?.label
                    },
                    on_kind = { if (!is_read_only) sheet = active_sheet.pick_action_kind(index) },
                    on_target = { if (!is_read_only) sheet = active_sheet.pick_action_target(index) },
                    on_remove = { if (!is_read_only) actions.removeAt(index) },
                    modifier = Modifier.testTag("action_$index"),
                )
                Spacer(Modifier.height(AsterSpacing.sm))
            }
            if (!is_read_only) {
                add_chip_pill(
                    label = stringResource(R.string.mail_rules_add_action),
                    on_click = {
                        actions.add(default_action_for(action_id.move_to))
                        sheet = active_sheet.pick_action_kind(actions.lastIndex)
                    },
                    modifier = Modifier.testTag("add_action"),
                )
            }

            val needs_alias_delivery = actions.any { it is Action.MoveTo || it is Action.ApplyLabels }
            LaunchedEffect(needs_alias_delivery) {
                if (needs_alias_delivery) settings_vm.load_aliases()
            }
            val alias_delivery = remember(settings_state.aliases) {
                settings_state.aliases.associate { alias ->
                    alias.address.lowercase() to AliasDeliverySetting(
                        delivery_folder_token = alias.delivery_folder_token,
                        delivery_label_token = alias.delivery_label_token,
                        never_inbox = alias.never_inbox,
                    )
                }
            }
            val delivery_conflict = rule_alias_delivery_conflict(
                conditions = conditions.toList(),
                actions = actions.toList(),
                alias_delivery = alias_delivery,
            )
            if (!is_read_only && delivery_conflict != null) {
                val archive_name = stringResource(R.string.folder_archive)
                val missing_name = stringResource(R.string.alias_delivery_folder_missing)
                val alias_target = delivery_conflict.alias_delivery.delivery_folder_token
                    ?.let { token -> folders.firstOrNull { it.id == token }?.label ?: missing_name }
                    ?: archive_name
                val rule_target = folders
                    .firstOrNull { it.id == delivery_conflict.rule_folder_token }
                    ?.label
                    ?: missing_name
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(
                        R.string.mail_rules_alias_delivery_conflict,
                        delivery_conflict.alias_address,
                        alias_target,
                        rule_target,
                    ),
                    color = colors.warning,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg_tertiary)
                        .border(1.dp, colors.border_secondary, RoundedCornerShape(10.dp))
                        .padding(AsterSpacing.md)
                        .testTag("rule_alias_delivery_warning"),
                )
            }

            val label_conflict = rule_alias_label_conflict(
                conditions = conditions.toList(),
                actions = actions.toList(),
                alias_delivery = alias_delivery,
            )
            if (!is_read_only && label_conflict != null) {
                val missing_label_name = stringResource(R.string.alias_delivery_label_missing)
                val alias_label = labels
                    .firstOrNull { it.id == label_conflict.alias_label_token }
                    ?.label
                    ?: missing_label_name
                val rule_labels = label_conflict.rule_label_tokens
                    .map { token -> labels.firstOrNull { it.id == token }?.label ?: missing_label_name }
                    .joinToString(", ")
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(
                        R.string.mail_rules_alias_label_conflict,
                        label_conflict.alias_address,
                        alias_label,
                        rule_labels,
                    ),
                    color = colors.warning,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bg_tertiary)
                        .border(1.dp, colors.border_secondary, RoundedCornerShape(10.dp))
                        .padding(AsterSpacing.md)
                        .testTag("rule_alias_label_warning"),
                )
            }

            Spacer(Modifier.height(AsterSpacing.xxl))
            save_error?.let { msg ->
                Text(
                    text = stringResource(msg),
                    color = colors.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = AsterSpacing.sm),
                )
            }
            if (save_hint != null) {
                Text(
                    text = stringResource(save_hint),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = AsterSpacing.sm),
                )
            }
            AsterButton(
                label = stringResource(R.string.mail_rules_save),
                onClick = {
                    if (is_saving || is_read_only) return@AsterButton
                    if (rule_id != null && existing == null) return@AsterButton
                    is_saving = true
                    save_error = null
                    if (existing == null) {
                        vm.create_rule(
                            name = name,
                            color = color_hex,
                            enabled = true,
                            match_mode = match_mode,
                            conditions = conditions.toList(),
                            actions = actions.toList() + preserved_actions,
                        ) { id ->
                            is_saving = false
                            if (id != null) on_saved() else save_error = state.error ?: R.string.rules_save_failed
                        }
                    } else {
                        vm.update_rule(
                            rule_id = existing.id,
                            name = name,
                            color = color_hex,
                            match_mode = match_mode,
                            conditions = conditions.toList(),
                            actions = actions.toList() + preserved_actions,
                        ) { ok ->
                            is_saving = false
                            if (ok) on_saved() else save_error = state.error ?: R.string.rules_save_failed
                        }
                    }
                },
                enabled = is_valid && !is_saving && !is_read_only && (rule_id == null || existing != null),
                modifier = Modifier.testTag("save_rule"),
            )
            Spacer(Modifier.height(AsterSpacing.xxl))
        }
    }

    when (val s = sheet) {
        active_sheet.none -> {}
        active_sheet.pick_field -> field_picker(
            on_dismiss = {
                sheet = next_sheet ?: active_sheet.none
                next_sheet = null
                pending_field_index = null
                auto_advance = false
            },
            on_pick = { picked ->
                val target_index = pending_field_index
                if (target_index != null) {
                    if (target_index in conditions.indices) {
                        conditions[target_index] = default_condition_for(picked)
                    }
                    pending_field_index = null
                } else {
                    conditions.add(default_condition_for(picked))
                    if (auto_advance && field_kind_of(picked) != field_kind.boolean) {
                        next_sheet = active_sheet.pick_operator(conditions.lastIndex)
                        auto_advance = false
                        return@field_picker
                    }
                    auto_advance = false
                }
            },
        )
        is active_sheet.pick_operator -> operator_picker(
            condition = conditions[s.cond_index],
            on_dismiss = {
                sheet = next_sheet ?: active_sheet.none
                next_sheet = null
            },
            on_pick = { updated ->
                conditions[s.cond_index] = updated
                if (auto_advance && needs_value(updated)) {
                    next_sheet = active_sheet.pick_value(s.cond_index)
                    auto_advance = false
                    return@operator_picker
                }
                auto_advance = false
            },
        )
        is active_sheet.pick_value -> {
            val target = conditions.getOrNull(s.cond_index)
            if (target == null) {
                sheet = active_sheet.none
            } else if (condition_is_address_field(target)) {
                LaunchedEffect(Unit) { settings_vm.load_aliases() }
                address_value_picker(
                    on_dismiss = { sheet = active_sheet.none },
                    title = stringResource(R.string.mail_rules_enter_value),
                    initial = value_display(target).orEmpty(),
                    case_sensitive = case_sensitive_of(target),
                    is_regex = uses_regex_op(target),
                    show_aliases = condition_offers_alias_picker(target),
                    aliases = alias_options,
                    aliases_loading = settings_state.is_loading && settings_state.aliases.isEmpty(),
                    on_confirm = { values, case ->
                        val result = insert_condition_values(
                            conditions = conditions.toList(),
                            index = s.cond_index,
                            template = target,
                            values = values,
                            case = case,
                            match_mode = match_mode,
                        )
                        conditions.clear()
                        conditions.addAll(result.conditions)
                        switched_to_any = result.match_mode != match_mode
                        match_mode = result.match_mode
                        if (normalize_address_values(values).size <= 1) {
                            skipped_duplicates = 0
                            duplicate_warning = if (result.skipped_duplicates > 0) {
                                R.string.mail_rules_duplicate_alias
                            } else {
                                null
                            }
                        } else {
                            duplicate_warning = null
                            skipped_duplicates = result.skipped_duplicates
                        }
                    },
                )
            } else {
                value_picker_for(
                    condition = target,
                    on_dismiss = { sheet = active_sheet.none },
                    on_set = { updated ->
                        if (duplicates_condition_at(conditions.toList(), s.cond_index, updated)) {
                            duplicate_warning = if (condition_is_address_field(updated)) {
                                R.string.mail_rules_duplicate_alias
                            } else {
                                R.string.mail_rules_duplicate_condition
                            }
                            if (!is_condition_complete(target)) conditions.removeAt(s.cond_index)
                        } else {
                            duplicate_warning = null
                            conditions[s.cond_index] = updated
                        }
                    },
                )
            }
        }
        is active_sheet.pick_action_kind -> options_picker(
            on_dismiss = {
                sheet = next_sheet ?: active_sheet.none
                next_sheet = null
            },
            title = stringResource(R.string.mail_rules_pick_action),
            items = selectable_actions().map { picker_item(it.name, action_label(it)) },
            selected_id = action_of(actions[s.action_index])?.name,
            on_pick = { id ->
                val picked = action_id.valueOf(id)
                actions[s.action_index] = default_action_for(picked)
                if (picked == action_id.move_to || picked == action_id.apply_labels ||
                    picked == action_id.categorize ||
                    picked == action_id.mark_as
                ) {
                    next_sheet = active_sheet.pick_action_target(s.action_index)
                    return@options_picker
                }
            },
        )
        is active_sheet.pick_action_target -> action_target_picker(
            action = actions[s.action_index],
            folders = folders,
            labels = labels,
            on_dismiss = { sheet = active_sheet.none },
            on_set = { updated -> actions[s.action_index] = updated },
            on_create_folder = {
                create_for_action = s.action_index
                create_target = "folder"
                sheet = active_sheet.none
            },
            on_create_label = {
                create_for_action = s.action_index
                create_target = "label"
                sheet = active_sheet.none
            },
        )
        active_sheet.pick_color -> color_picker(
            on_dismiss = { sheet = active_sheet.none },
            selected = color_hex,
            palette = palette_colors,
            on_pick = { color_hex = it },
        )
        active_sheet.pick_match_mode -> options_picker(
            on_dismiss = { sheet = active_sheet.none },
            title = stringResource(R.string.mail_rules_match_mode),
            items = listOf(
                picker_item("all", stringResource(R.string.mail_rules_match_all)),
                picker_item("any", stringResource(R.string.mail_rules_match_any)),
            ),
            selected_id = if (match_mode == MatchMode.ALL) "all" else "any",
            on_pick = { match_mode = if (it == "all") MatchMode.ALL else MatchMode.ANY },
        )
    }

    when (create_target) {
        "folder" -> create_folder_dialog(
            title = stringResource(R.string.create_folder),
            placeholder = stringResource(R.string.folder_name),
            parent_options = folder_parent_options,
            on_dismiss = { create_target = null; create_for_action = null },
            on_create = { folder_name, parent_token ->
                val sibling_count = settings_state.labels.count {
                    is_custom_folder(it) && it.parent_token.orEmpty() == parent_token.orEmpty()
                }
                val action_index = create_for_action
                settings_vm.create_folder(
                    name = folder_name,
                    sort_order = sibling_count,
                    parent_token = parent_token,
                    on_created = { token ->
                        if (action_index != null) {
                            val updated = apply_created_target(actions.getOrNull(action_index), token)
                            if (updated != null) {
                                actions[action_index] = updated
                                sheet = active_sheet.pick_action_target(action_index)
                            }
                        }
                        create_for_action = null
                    },
                )
                create_target = null
            },
        )
        "label" -> create_label_dialog(
            on_dismiss = { create_target = null; create_for_action = null },
            on_create = { label_name, label_color, label_icon ->
                val action_index = create_for_action
                settings_vm.create_tag(
                    name = label_name,
                    color = label_color,
                    icon = label_icon,
                    on_created = { token ->
                        if (action_index != null) {
                            val updated = apply_created_target(actions.getOrNull(action_index), token)
                            if (updated != null) {
                                actions[action_index] = updated
                                sheet = active_sheet.pick_action_target(action_index)
                            }
                        }
                        create_for_action = null
                    },
                )
                create_target = null
            },
        )
    }
}

private val rules_label_palette = listOf(
    Color(0xFF3B82F6),
    Color(0xFF22C55E),
    Color(0xFFF59E0B),
    Color(0xFFA855F7),
    Color(0xFFEC4899),
    Color(0xFF14B8A6),
    Color(0xFFF97316),
    Color(0xFF6366F1),
)

private fun parse_hex(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Throwable) {
    Color(0xFF6366F1)
}

@Composable
private fun action_label(id: action_id?): String = stringResource(
    when (id) {
        null -> R.string.mail_rules_action_unsupported
        action_id.move_to -> R.string.mail_rules_action_move_to
        action_id.apply_labels -> R.string.mail_rules_action_apply_labels
        action_id.mark_as -> R.string.mail_rules_action_mark_as
        action_id.star -> R.string.mail_rules_action_star
        action_id.skip_inbox -> R.string.mail_rules_action_skip_inbox
        action_id.pin -> R.string.mail_rules_action_pin
        action_id.snooze -> R.string.mail_rules_action_snooze
        action_id.categorize -> R.string.mail_rules_action_categorize
        action_id.notify -> R.string.mail_rules_action_notify
        action_id.forward -> R.string.mail_rules_action_forward
        action_id.delete -> R.string.mail_rules_action_delete
        action_id.auto_reply -> R.string.mail_rules_action_auto_reply
    },
)

@Composable
private fun condition_chip(
    condition: Condition,
    on_field: () -> Unit,
    on_operator: () -> Unit,
    on_value: () -> Unit,
    on_remove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val field_label = field_display(field_of(condition))
    val op_label = operator_display(condition)
    val value_label = value_display(condition)
    val segments = buildList {
        add(chip_segment_spec(field_label, on_field, is_active = true))
        if (op_label != null) add(chip_segment_spec(op_label, on_operator))
        if (value_label != null) add(
            chip_segment_spec(
                label = value_label.ifBlank { stringResource(R.string.rules_value_placeholder_ellipsis) },
                on_click = on_value,
                is_placeholder = value_label.isBlank(),
            ),
        )
    }
    chip_pill_row(segments = segments, on_remove = on_remove, modifier = modifier)
}

@Composable
private fun action_chip(
    action: Action,
    folder_label: String?,
    on_kind: () -> Unit,
    on_target: () -> Unit,
    on_remove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = action_of(action)
    val kind_label = action_label(kind)
    val target = action_target_display(action, folder_label)
    val segments = buildList {
        add(chip_segment_spec(kind_label, on_kind, is_active = true))
        if (target != null) add(
            chip_segment_spec(
                label = target.ifBlank { stringResource(R.string.rules_value_placeholder_ellipsis) },
                on_click = on_target,
                is_placeholder = target.isBlank(),
            ),
        )
    }
    chip_pill_row(segments = segments, on_remove = on_remove, modifier = modifier)
}

@Composable
private fun field_display(field: field_id?): String = stringResource(
    when (field) {
        null -> R.string.mail_rules_field_advanced
        field_id.from -> R.string.mail_rules_field_from
        field_id.reply_to -> R.string.mail_rules_field_reply_to
        field_id.to -> R.string.mail_rules_field_to
        field_id.cc -> R.string.mail_rules_field_cc
        field_id.bcc -> R.string.mail_rules_field_bcc
        field_id.any_recipient -> R.string.mail_rules_field_any_recipient
        field_id.subject -> R.string.mail_rules_field_subject
        field_id.body -> R.string.mail_rules_field_body
        field_id.header -> R.string.mail_rules_field_header
        field_id.list_id -> R.string.mail_rules_field_list_id
        field_id.attachment_name -> R.string.mail_rules_field_attachment_name
        field_id.has_attachment -> R.string.mail_rules_field_has_attachment
        field_id.is_reply -> R.string.mail_rules_field_is_reply
        field_id.is_forward -> R.string.mail_rules_field_is_forward
        field_id.is_auto_submitted -> R.string.mail_rules_field_is_auto_submitted
        field_id.has_calendar_invite -> R.string.mail_rules_field_has_calendar_invite
        field_id.has_list_id -> R.string.mail_rules_field_has_list_id
        field_id.attachment_size -> R.string.mail_rules_field_attachment_size
        field_id.total_size -> R.string.mail_rules_field_total_size
        field_id.recipient_count -> R.string.mail_rules_field_recipient_count
        field_id.spam_score -> R.string.mail_rules_field_spam_score
        field_id.date_received -> R.string.mail_rules_field_date_received
        field_id.dkim_result -> R.string.mail_rules_field_dkim
        field_id.spf_result -> R.string.mail_rules_field_spf
        field_id.dmarc_result -> R.string.mail_rules_field_dmarc
    },
)

@Composable
private fun operator_display(c: Condition): String? = when (c) {
    is Condition.From -> address_op_label(c.op)
    is Condition.ReplyTo -> address_op_label(c.op)
    is Condition.To -> address_op_label(c.op)
    is Condition.Cc -> address_op_label(c.op)
    is Condition.Bcc -> address_op_label(c.op)
    is Condition.AnyRecipient -> address_op_label(c.op)
    is Condition.Subject -> text_op_label(c.op)
    is Condition.Body -> text_op_label(c.op)
    is Condition.ListId -> text_op_label(c.op)
    is Condition.Header -> text_op_label(c.op)
    is Condition.AttachmentName -> attachment_op_label(c.op)
    is Condition.AttachmentSize -> numeric_op_label(c.op)
    is Condition.TotalSize -> numeric_op_label(c.op)
    is Condition.RecipientCount -> numeric_op_label(c.op)
    is Condition.SpamScore -> numeric_op_label(c.op)
    is Condition.DateReceived -> date_op_label(c.op)
    else -> null
}

@Composable
private fun address_op_label(op: AddressOp): String = stringResource(
    when (op) {
        AddressOp.IS -> R.string.rules_op_is
        AddressOp.CONTAINS -> R.string.rules_op_contains
        AddressOp.IS_NOT -> R.string.rules_op_is_not
        AddressOp.MATCHES_DOMAIN -> R.string.rules_op_matches_domain
        AddressOp.MATCHES_REGEX -> R.string.rules_op_matches_regex
    },
)

@Composable
private fun text_op_label(op: TextOp): String = stringResource(
    when (op) {
        TextOp.CONTAINS -> R.string.rules_op_contains
        TextOp.DOES_NOT_CONTAIN -> R.string.rules_op_does_not_contain
        TextOp.IS -> R.string.rules_op_is
        TextOp.STARTS_WITH -> R.string.rules_op_starts_with
        TextOp.ENDS_WITH -> R.string.rules_op_ends_with
        TextOp.IS_EMPTY -> R.string.rules_op_is_empty
        TextOp.MATCHES_REGEX -> R.string.rules_op_matches_regex
    },
)

@Composable
private fun attachment_op_label(op: AttachmentNameOp): String = stringResource(
    when (op) {
        AttachmentNameOp.CONTAINS -> R.string.rules_op_contains
        AttachmentNameOp.ENDS_WITH -> R.string.rules_op_ends_with
        AttachmentNameOp.MATCHES_REGEX -> R.string.rules_op_matches_regex
    },
)

@Composable
private fun numeric_op_label(op: NumericOp): String = stringResource(
    when (op) {
        NumericOp.GREATER_THAN -> R.string.rules_op_greater_than
        NumericOp.LESS_THAN -> R.string.rules_op_less_than
        NumericOp.EQUALS -> R.string.rules_op_equals
    },
)

@Composable
private fun date_op_label(op: DateOp): String = stringResource(
    when (op) {
        DateOp.OLDER_THAN_DAYS -> R.string.rules_op_older_than
        DateOp.NEWER_THAN_DAYS -> R.string.rules_op_newer_than
    },
)

@Composable
private fun value_display(c: Condition): String? {
    val yes = stringResource(R.string.rules_value_yes)
    val no = stringResource(R.string.rules_value_no)
    return when (c) {
        is Condition.From -> c.value
        is Condition.ReplyTo -> c.value
        is Condition.To -> c.value
        is Condition.Cc -> c.value
        is Condition.Bcc -> c.value
        is Condition.AnyRecipient -> c.value
        is Condition.Subject -> if (c.op == TextOp.IS_EMPTY) "" else c.value
        is Condition.Body -> if (c.op == TextOp.IS_EMPTY) "" else c.value
        is Condition.ListId -> if (c.op == TextOp.IS_EMPTY) "" else c.value
        is Condition.Header -> "${c.name}: ${c.value}"
        is Condition.AttachmentName -> c.value
        is Condition.HasAttachment -> if (c.`is`) yes else no
        is Condition.IsReply -> if (c.`is`) yes else no
        is Condition.IsForward -> if (c.`is`) yes else no
        is Condition.IsAutoSubmitted -> if (c.`is`) yes else no
        is Condition.HasCalendarInvite -> if (c.`is`) yes else no
        is Condition.HasListId -> if (c.`is`) yes else no
        is Condition.AttachmentSize -> format_size(c.value)
        is Condition.TotalSize -> format_size(c.value)
        is Condition.RecipientCount -> c.value.toString()
        is Condition.SpamScore -> "%.1f".format(java.util.Locale.getDefault(), c.value)
        is Condition.DateReceived -> pluralStringResource(R.plurals.rules_value_days, c.value.toInt(), c.value.toInt())
        is Condition.DkimResult -> c.value.name.lowercase()
        is Condition.SpfResult -> c.value.name.lowercase()
        is Condition.DmarcResult -> c.value.name.lowercase()
        else -> null
    }
}

private fun format_size(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

private fun format_snooze_until(value: String): String = runCatching {
    java.time.OffsetDateTime.parse(value)
        .atZoneSameInstant(org.astermail.android.ui.mail.AsterTimePreferences.account_zone_id())
        .format(
            java.time.format.DateTimeFormatter
                .ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT),
        )
}.getOrDefault(value)

@Composable
private fun action_target_display(action: Action, folder_label: String?): String? = when (action) {
    is Action.MoveTo -> folder_label ?: ""
    is Action.ApplyLabels -> if (action.label_tokens.isEmpty()) "" else pluralStringResource(R.plurals.rules_value_label_count, action.label_tokens.size, action.label_tokens.size)
    is Action.MarkAs -> if (action.value == ReadState.READ) stringResource(R.string.rules_value_read) else stringResource(R.string.rules_value_unread)
    is Action.Forward -> action.to
    is Action.Snooze -> format_snooze_until(action.until_iso8601)
    is Action.Categorize -> category_label(action.category)
    is Action.Notify -> if (action.enabled) stringResource(R.string.rules_value_on) else stringResource(R.string.rules_value_off)
    is Action.AutoReply -> action.template_id
    else -> null
}

@Composable
private fun category_label(category: String): String = when (category) {
    "primary" -> stringResource(R.string.rules_category_primary)
    "important" -> stringResource(R.string.rules_category_important)
    "promotions" -> stringResource(R.string.rules_category_promotions)
    "social" -> stringResource(R.string.rules_category_social)
    "updates" -> stringResource(R.string.rules_category_updates)
    "forums" -> stringResource(R.string.rules_category_forums)
    else -> category
}

private fun needs_value(c: Condition): Boolean = when (c) {
    is Condition.HasAttachment, is Condition.IsReply, is Condition.IsForward,
    is Condition.IsAutoSubmitted, is Condition.HasCalendarInvite, is Condition.HasListId,
    is Condition.DkimResult, is Condition.SpfResult, is Condition.DmarcResult -> false
    is Condition.Subject -> c.op != TextOp.IS_EMPTY
    is Condition.Body -> c.op != TextOp.IS_EMPTY
    is Condition.ListId -> c.op != TextOp.IS_EMPTY
    else -> true
}

@Composable
private fun operator_picker(
    condition: Condition,
    on_dismiss: () -> Unit,
    on_pick: (Condition) -> Unit,
) {
    val items = address_or_text_operators(condition)
    options_picker(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.mail_rules_pick_operator),
        items = items,
        selected_id = current_operator_id(condition),
        on_pick = { id -> on_pick(apply_operator(condition, id)) },
    )
}

private fun current_operator_id(c: Condition): String? = when (c) {
    is Condition.From -> c.op.name
    is Condition.ReplyTo -> c.op.name
    is Condition.To -> c.op.name
    is Condition.Cc -> c.op.name
    is Condition.Bcc -> c.op.name
    is Condition.AnyRecipient -> c.op.name
    is Condition.Subject -> c.op.name
    is Condition.Body -> c.op.name
    is Condition.ListId -> c.op.name
    is Condition.Header -> c.op.name
    is Condition.AttachmentName -> c.op.name
    is Condition.AttachmentSize -> c.op.name
    is Condition.TotalSize -> c.op.name
    is Condition.RecipientCount -> c.op.name
    is Condition.SpamScore -> c.op.name
    is Condition.DateReceived -> c.op.name
    else -> null
}

@Composable
private fun address_or_text_operators(c: Condition): List<picker_item> = when (c) {
    is Condition.From, is Condition.ReplyTo, is Condition.To, is Condition.Cc, is Condition.Bcc, is Condition.AnyRecipient ->
        AddressOp.values().map { picker_item(it.name, address_op_label(it)) }
    is Condition.Subject, is Condition.Body, is Condition.ListId, is Condition.Header ->
        TextOp.values().map { picker_item(it.name, text_op_label(it)) }
    is Condition.AttachmentName -> AttachmentNameOp.values().map { picker_item(it.name, attachment_op_label(it)) }
    is Condition.AttachmentSize, is Condition.TotalSize, is Condition.RecipientCount, is Condition.SpamScore ->
        NumericOp.values().map { picker_item(it.name, numeric_op_label(it)) }
    is Condition.DateReceived ->
        DateOp.values().map { picker_item(it.name, date_op_label(it)) }
    else -> emptyList()
}

private fun apply_operator(c: Condition, op_id: String): Condition = when (c) {
    is Condition.From -> c.copy(op = AddressOp.valueOf(op_id))
    is Condition.ReplyTo -> c.copy(op = AddressOp.valueOf(op_id))
    is Condition.To -> c.copy(op = AddressOp.valueOf(op_id))
    is Condition.Cc -> c.copy(op = AddressOp.valueOf(op_id))
    is Condition.Bcc -> c.copy(op = AddressOp.valueOf(op_id))
    is Condition.AnyRecipient -> c.copy(op = AddressOp.valueOf(op_id))
    is Condition.Subject -> c.copy(op = TextOp.valueOf(op_id))
    is Condition.Body -> c.copy(op = TextOp.valueOf(op_id))
    is Condition.ListId -> c.copy(op = TextOp.valueOf(op_id))
    is Condition.Header -> c.copy(op = TextOp.valueOf(op_id))
    is Condition.AttachmentName -> c.copy(op = AttachmentNameOp.valueOf(op_id))
    is Condition.AttachmentSize -> c.copy(op = NumericOp.valueOf(op_id))
    is Condition.TotalSize -> c.copy(op = NumericOp.valueOf(op_id))
    is Condition.RecipientCount -> c.copy(op = NumericOp.valueOf(op_id))
    is Condition.SpamScore -> c.copy(op = NumericOp.valueOf(op_id))
    is Condition.DateReceived -> c.copy(op = DateOp.valueOf(op_id))
    else -> c
}

private fun uses_regex_op(c: Condition): Boolean = when (c) {
    is Condition.From -> c.op == AddressOp.MATCHES_REGEX
    is Condition.ReplyTo -> c.op == AddressOp.MATCHES_REGEX
    is Condition.To -> c.op == AddressOp.MATCHES_REGEX
    is Condition.Cc -> c.op == AddressOp.MATCHES_REGEX
    is Condition.Bcc -> c.op == AddressOp.MATCHES_REGEX
    is Condition.AnyRecipient -> c.op == AddressOp.MATCHES_REGEX
    is Condition.Subject -> c.op == TextOp.MATCHES_REGEX
    is Condition.Body -> c.op == TextOp.MATCHES_REGEX
    is Condition.ListId -> c.op == TextOp.MATCHES_REGEX
    is Condition.Header -> c.op == TextOp.MATCHES_REGEX
    is Condition.AttachmentName -> c.op == AttachmentNameOp.MATCHES_REGEX
    else -> false
}

@Composable
private fun value_picker_for(
    condition: Condition,
    on_dismiss: () -> Unit,
    on_set: (Condition) -> Unit,
) {
    when (condition) {
        is Condition.Subject, is Condition.Body, is Condition.ListId, is Condition.AttachmentName -> {
            val current = value_display(condition).orEmpty()
            val case = case_sensitive_of(condition)
            text_value_picker(
                on_dismiss = on_dismiss,
                title = stringResource(R.string.mail_rules_enter_value),
                initial = current,
                case_sensitive = case,
                show_case_toggle = true,
                is_regex = uses_regex_op(condition),
                on_confirm = { v, c -> on_set(set_condition_value(condition, v, c)) },
            )
        }
        is Condition.Header -> header_value_picker(
            on_dismiss = on_dismiss,
            initial_name = condition.name,
            initial_value = condition.value,
            case_sensitive = condition.case_sensitive == true,
            is_regex = condition.op == TextOp.MATCHES_REGEX,
            on_confirm = { n, v, c -> on_set(condition.copy(name = n, value = v, case_sensitive = c)) },
        )
        is Condition.HasAttachment -> boolean_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_has_attachment),
            initial = condition.`is`,
            on_confirm = { on_set(condition.copy(`is` = it)) },
        )
        is Condition.IsReply -> boolean_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_is_reply),
            initial = condition.`is`,
            on_confirm = { on_set(condition.copy(`is` = it)) },
        )
        is Condition.IsForward -> boolean_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_is_forward),
            initial = condition.`is`,
            on_confirm = { on_set(condition.copy(`is` = it)) },
        )
        is Condition.IsAutoSubmitted -> boolean_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_is_auto_submitted),
            initial = condition.`is`,
            on_confirm = { on_set(condition.copy(`is` = it)) },
        )
        is Condition.HasCalendarInvite -> boolean_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_has_calendar_invite),
            initial = condition.`is`,
            on_confirm = { on_set(condition.copy(`is` = it)) },
        )
        is Condition.HasListId -> boolean_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_has_list_id),
            initial = condition.`is`,
            on_confirm = { on_set(condition.copy(`is` = it)) },
        )
        is Condition.AttachmentSize -> numeric_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_attachment_size),
            initial = condition.value,
            is_size = true,
            on_confirm = { on_set(condition.copy(value = it)) },
        )
        is Condition.TotalSize -> numeric_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_total_size),
            initial = condition.value,
            is_size = true,
            on_confirm = { on_set(condition.copy(value = it)) },
        )
        is Condition.RecipientCount -> numeric_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_recipient_count),
            initial = condition.value,
            is_size = false,
            on_confirm = { on_set(condition.copy(value = it)) },
        )
        is Condition.SpamScore -> decimal_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_spam_score),
            initial = condition.value,
            on_confirm = { on_set(condition.copy(value = it)) },
        )
        is Condition.DateReceived -> numeric_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_date_received),
            initial = condition.value,
            is_size = false,
            on_confirm = { on_set(condition.copy(value = it)) },
        )
        is Condition.DkimResult -> options_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_dkim),
            items = AuthResult.values().map { picker_item(it.name, it.name.lowercase()) },
            selected_id = condition.value.name,
            on_pick = { on_set(condition.copy(value = AuthResult.valueOf(it))) },
        )
        is Condition.SpfResult -> options_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_spf),
            items = AuthResult.values().map { picker_item(it.name, it.name.lowercase()) },
            selected_id = condition.value.name,
            on_pick = { on_set(condition.copy(value = AuthResult.valueOf(it))) },
        )
        is Condition.DmarcResult -> options_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_field_dmarc),
            items = AuthResult.values().map { picker_item(it.name, it.name.lowercase()) },
            selected_id = condition.value.name,
            on_pick = { on_set(condition.copy(value = AuthResult.valueOf(it))) },
        )
        else -> on_dismiss()
    }
}

private fun case_sensitive_of(c: Condition): Boolean = when (c) {
    is Condition.From -> c.case_sensitive == true
    is Condition.ReplyTo -> c.case_sensitive == true
    is Condition.To -> c.case_sensitive == true
    is Condition.Cc -> c.case_sensitive == true
    is Condition.Bcc -> c.case_sensitive == true
    is Condition.AnyRecipient -> c.case_sensitive == true
    is Condition.Subject -> c.case_sensitive == true
    is Condition.Body -> c.case_sensitive == true
    is Condition.ListId -> c.case_sensitive == true
    is Condition.AttachmentName -> c.case_sensitive == true
    else -> false
}

@Composable
private fun action_target_picker(
    action: Action,
    folders: List<picker_item>,
    labels: List<picker_item>,
    on_dismiss: () -> Unit,
    on_set: (Action) -> Unit,
    on_create_folder: (() -> Unit)? = null,
    on_create_label: (() -> Unit)? = null,
) {
    when (action) {
        is Action.MoveTo -> folder_picker(
            on_dismiss = on_dismiss,
            folders = folders,
            selected_token = action.folder_token.takeIf { it.isNotBlank() },
            on_pick = { id, _ -> on_set(action.copy(folder_token = id)) },
            on_create = on_create_folder,
        )
        is Action.ApplyLabels -> label_multi_picker(
            on_dismiss = on_dismiss,
            labels = labels,
            selected_tokens = action.label_tokens,
            on_confirm = { on_set(action.copy(label_tokens = it)) },
            on_create = on_create_label,
        )
        is Action.MarkAs -> options_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_action_mark_as),
            items = listOf(
                picker_item(ReadState.READ.name, stringResource(R.string.rules_value_read)),
                picker_item(ReadState.UNREAD.name, stringResource(R.string.rules_value_unread)),
            ),
            selected_id = action.value.name,
            on_pick = { on_set(action.copy(value = ReadState.valueOf(it))) },
        )
        is Action.Forward -> text_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_action_forward),
            initial = action.to,
            case_sensitive = false,
            show_case_toggle = false,
            on_confirm = { v, _ -> on_set(action.copy(to = v)) },
        )
        is Action.Categorize -> options_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_action_categorize),
            items = listOf("primary", "important", "promotions", "social", "updates", "forums")
                .map { picker_item(it, category_label(it)) },
            selected_id = action.category,
            on_pick = { on_set(action.copy(category = it)) },
        )
        is Action.Notify -> boolean_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_action_notify),
            initial = action.enabled,
            on_confirm = { on_set(action.copy(enabled = it)) },
        )
        is Action.Snooze -> snooze_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_action_snooze),
            current = action.until_iso8601,
            on_pick = { on_set(action.copy(until_iso8601 = it)) },
        )
        is Action.AutoReply -> text_value_picker(
            on_dismiss = on_dismiss,
            title = stringResource(R.string.mail_rules_action_auto_reply),
            initial = action.template_id,
            case_sensitive = false,
            show_case_toggle = false,
            on_confirm = { v, _ -> on_set(action.copy(template_id = v)) },
        )
        else -> on_dismiss()
    }
}
