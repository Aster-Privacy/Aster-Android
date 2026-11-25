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

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.At
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Tag
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.AddressOp
import org.astermail.android.api.mail_rules.AttachmentNameOp
import org.astermail.android.api.mail_rules.AuthResult
import org.astermail.android.api.mail_rules.Condition
import org.astermail.android.api.mail_rules.DateOp
import org.astermail.android.api.mail_rules.MailRule
import org.astermail.android.api.mail_rules.MatchMode
import org.astermail.android.api.mail_rules.NumericOp
import org.astermail.android.api.mail_rules.ReadState
import org.astermail.android.api.mail_rules.TextOp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlert
import org.astermail.android.design.components.AsterAlertSeverity
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCompactGhostButton
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogDestructiveButton
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu_section_label
import org.astermail.android.design.components.aster_menu_toggle_item
import org.astermail.android.design.parse_hex_color_safe
import org.astermail.android.folders.flatten_folder_tree
import org.astermail.android.folders.folder_path
import org.astermail.android.folders.is_folder_protected
import org.astermail.android.folders.max_folder_depth
import org.astermail.android.mail_rules.AliasDeliverySetting
import org.astermail.android.mail_rules.MailRulesViewModel
import org.astermail.android.mail_rules.rule_alias_delivery_conflict
import org.astermail.android.mail_rules.rule_alias_label_conflict
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.drawer.create_folder_dialog
import org.astermail.android.ui.drawer.create_label_dialog
import org.astermail.android.ui.drawer.folder_parent_option
import org.astermail.android.ui.drawer.resolve_label_icon
import org.astermail.android.ui.settings.detail.detail_row
import org.astermail.android.ui.settings.detail.detail_scaffold
import org.astermail.android.ui.settings.detail.load_failed_card
import org.astermail.android.ui.settings.detail.settings_group
import org.astermail.android.ui.settings.detail.settings_row_gap
import org.astermail.android.ui.settings.detail.settings_toggle_row
import org.astermail.android.ui.settings.detail.skeleton_card_list
import org.astermail.android.ui.settings.detail.skeleton_hero_card
import org.astermail.android.ui.settings.detail.skeleton_section_label
import org.astermail.android.ui.settings.detail.v_gap
import org.astermail.android.ui.settings.mail_rules.pickers.address_value_picker
import org.astermail.android.ui.settings.mail_rules.pickers.alias_option
import org.astermail.android.ui.settings.mail_rules.pickers.color_picker
import org.astermail.android.ui.settings.mail_rules.pickers.picker_item

private const val rule_name_max_length = 200

private val field_sections: List<Pair<Int, List<field_id>>> = listOf(
    R.string.rules_section_sender to listOf(field_id.from, field_id.reply_to),
    R.string.rules_section_recipients to listOf(field_id.to, field_id.cc, field_id.bcc, field_id.any_recipient),
    R.string.rules_section_content to listOf(field_id.subject, field_id.body, field_id.header, field_id.list_id),
    R.string.rules_section_attachments_flags to listOf(
        field_id.has_attachment,
        field_id.attachment_name,
        field_id.attachment_size,
        field_id.is_reply,
        field_id.is_forward,
        field_id.is_auto_submitted,
        field_id.has_calendar_invite,
        field_id.has_list_id,
    ),
    R.string.rules_section_numeric_date_auth to listOf(
        field_id.recipient_count,
        field_id.total_size,
        field_id.spam_score,
        field_id.date_received,
        field_id.dkim_result,
        field_id.spf_result,
        field_id.dmarc_result,
    ),
)

private val snooze_choices: List<Pair<Int, java.time.Duration>> = listOf(
    R.string.rules_snooze_1_hour to java.time.Duration.ofHours(1),
    R.string.rules_snooze_1_day to java.time.Duration.ofDays(1),
    R.string.rules_snooze_3_days to java.time.Duration.ofDays(3),
    R.string.rules_snooze_1_week to java.time.Duration.ofDays(7),
)

private val actions_with_target = setOf(
    action_id.move_to,
    action_id.apply_labels,
    action_id.mark_as,
    action_id.snooze,
    action_id.categorize,
    action_id.notify,
)

private const val create_target_folder = "folder"
private const val create_target_label = "label"

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

    LaunchedEffect(settings_state.action_result) {
        val msg = settings_state.action_result ?: return@LaunchedEffect
        org.astermail.android.ui.common.app_toast.show(msg.toString())
        settings_vm.clear_action_result()
    }

    val existing = remember(rule_id, state.rules) {
        rule_id?.let { id -> state.rules.firstOrNull { it.id == id } }
    }
    val last_rule = remember(rule_id) { arrayOfNulls<MailRule>(1) }
    if (existing != null) last_rule[0] = existing
    val held_rule = existing ?: last_rule[0]

    val base_draft = remember(held_rule) {
        rule_draft_of(held_rule, org.astermail.android.ui.common.default_custom_color)
    }
    val current_base by rememberUpdatedState(base_draft)
    var draft_json by rememberSaveable(rule_id) { mutableStateOf<String?>(null) }
    var initial_json by rememberSaveable(rule_id) { mutableStateOf<String?>(null) }
    val draft = remember(draft_json, base_draft) { decode_rule_draft(draft_json) ?: base_draft }

    fun edit(transform: (rule_draft) -> rule_draft) {
        val current = decode_rule_draft(draft_json) ?: current_base
        if (initial_json == null) initial_json = encode_rule_draft(current_base)
        draft_json = encode_rule_draft(transform(current))
    }

    val preserved = remember(held_rule) { preserved_actions_of(held_rule) }
    val is_read_only = held_rule != null && rule_is_advanced(held_rule)
    val editable = !is_read_only
    val dirty = is_dirty(initial_json, draft_json)

    var open_menu by rememberSaveable { mutableStateOf<String?>(null) }
    var create_target by rememberSaveable { mutableStateOf<String?>(null) }
    var create_for_action by rememberSaveable { mutableStateOf<Int?>(null) }
    var alias_picker_index by rememberSaveable { mutableStateOf<Int?>(null) }
    var show_color by rememberSaveable { mutableStateOf(false) }
    var confirm_delete by rememberSaveable { mutableStateOf(false) }
    var show_discard by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var save_error by remember { mutableStateOf<Int?>(null) }
    var save_held by remember { mutableStateOf<org.astermail.android.guard.guard_held_notice?>(null) }
    var duplicate_warning by remember { mutableStateOf<Int?>(null) }
    var skipped_duplicates by remember { mutableIntStateOf(0) }
    var switched_to_any by remember { mutableStateOf(false) }

    val busy = saving || deleting
    val request_back: () -> Unit = {
        when {
            busy -> Unit
            dirty -> show_discard = true
            else -> on_back()
        }
    }
    BackHandler(enabled = dirty || busy) { if (!busy) show_discard = true }

    val system_folder_names = org.astermail.android.mail_rules.RuleSystemFolder.entries.associateWith {
        stringResource(rule_system_folder_name_res(it))
    }
    val folders = remember(settings_state.labels, system_folder_names) {
        rule_folder_picker_items(settings_state.labels, system_folder_names)
    }
    val label_decrypt_failed = stringResource(R.string.label_decrypt_failed)
    val labels = remember(settings_state.labels, settings_state.tags, label_decrypt_failed) {
        val from_tags = org.astermail.android.labels.flatten_tag_tree(settings_state.tags)
            .map { node ->
                val tag = node.tag
                picker_item(
                    id = tag.tag_token,
                    label = org.astermail.android.labels.tag_display_name(tag, label_decrypt_failed),
                    icon = resolve_label_icon(tag.encrypted_icon?.takeIf { it.isNotBlank() }),
                    icon_tint = tag.encrypted_color?.let { parse_hex_color_safe(it) },
                    depth = node.depth,
                )
            }
        val from_labels = org.astermail.android.labels.label_rows(settings_state.labels)
            .map { label ->
                picker_item(
                    id = label.label_token,
                    label = label.encrypted_name.orEmpty(),
                    icon = resolve_label_icon(label.encrypted_icon?.takeIf { it.isNotBlank() }),
                    icon_tint = label.encrypted_color
                        ?.takeIf { it.startsWith("#") }
                        ?.let { parse_hex_color_safe(it) },
                )
            }
        from_tags + from_labels
    }
    val label_parent_options = remember(settings_state.tags) {
        org.astermail.android.ui.drawer.tag_parent_options(
            org.astermail.android.labels.tag_tree_index(settings_state.tags),
        )
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
    val alias_delivery = remember(settings_state.aliases) {
        settings_state.aliases.associate { alias ->
            alias.address.lowercase() to AliasDeliverySetting(
                delivery_folder_token = alias.delivery_folder_token,
                delivery_label_token = alias.delivery_label_token,
                never_inbox = alias.never_inbox,
            )
        }
    }
    val needs_alias_delivery = draft.actions.any { it is Action.MoveTo || it is Action.ApplyLabels }
    LaunchedEffect(needs_alias_delivery) {
        if (needs_alias_delivery) settings_vm.load_aliases()
    }

    val title = if (rule_id == null) {
        stringResource(R.string.mail_rules_new_rule)
    } else {
        stringResource(R.string.mail_rules_edit_rule)
    }

    detail_scaffold(title = title, on_back = request_back) {
        if (rule_id != null && held_rule == null) {
            if (state.is_loading) {
                Column(Modifier.fillMaxWidth().testTag("rule_editor_loading")) {
                    skeleton_hero_card(lines = 2)
                    v_gap(AsterSpacing.lg)
                    skeleton_section_label()
                    skeleton_card_list(rows = 2)
                    v_gap(AsterSpacing.lg)
                    skeleton_section_label()
                    skeleton_card_list(rows = 2)
                }
            } else {
                Box(Modifier.fillMaxWidth().testTag("rule_editor_error")) {
                    load_failed_card(
                        message = state.error?.let { stringResource(it) },
                        on_retry = { vm.load(force_refresh = true) },
                    )
                }
            }
            return@detail_scaffold
        }

        Column(Modifier.fillMaxWidth().testTag("rule_editor")) {
            if (is_read_only) {
                AsterAlert(
                    message = stringResource(R.string.mail_rules_advanced_notice),
                    severity = AsterAlertSeverity.info,
                    modifier = Modifier.testTag("rule_advanced_notice"),
                )
                v_gap(AsterSpacing.lg)
            }

            settings_group {
                Box(Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)) {
                    AsterTextField(
                        value = draft.name,
                        onValueChange = { value -> edit { it.copy(name = value.take(rule_name_max_length)) } },
                        label = stringResource(R.string.mail_rules_name),
                        placeholder = stringResource(R.string.mail_rules_name_placeholder),
                        enabled = editable,
                        keyboard_options = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().testTag("rule_name"),
                    )
                }
                settings_row_gap()
                val swatch = parse_hex_color_safe(draft.color) ?: colors.accent_blue
                detail_row(
                    title = stringResource(R.string.mail_rules_color),
                    on_click = if (editable) ({ show_color = true }) else null,
                    trailing = {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(swatch, CircleShape),
                        )
                    },
                )
            }

            v_gap(AsterSpacing.lg)

            val duplicate_indices = remember(draft.conditions) { duplicate_condition_indices(draft.conditions) }
            settings_group(
                title = stringResource(R.string.rules_when_title),
                modifier = Modifier.testTag("rule_when_group"),
            ) {
                Box(Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)) {
                    rule_dropdown(
                        value = stringResource(
                            if (draft.match_mode == MatchMode.ALL) R.string.rules_match_all_sentence else R.string.rules_match_any_sentence,
                        ),
                        expanded = open_menu == "match",
                        on_expand = { open_menu = "match" },
                        on_dismiss = { open_menu = null },
                        enabled = editable,
                        test_tag = "rule_match_mode",
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        aster_menu_item(
                            label = stringResource(R.string.rules_match_all_sentence),
                            selected = draft.match_mode == MatchMode.ALL,
                            on_click = {
                                open_menu = null
                                edit { it.copy(match_mode = MatchMode.ALL) }
                            },
                        )
                        aster_menu_item(
                            label = stringResource(R.string.rules_match_any_sentence),
                            selected = draft.match_mode == MatchMode.ANY,
                            on_click = {
                                open_menu = null
                                switched_to_any = false
                                edit { it.copy(match_mode = MatchMode.ANY) }
                            },
                        )
                    }
                }
                draft.conditions.forEachIndexed { index, condition ->
                    settings_row_gap()
                    condition_block(
                        index = index,
                        condition = condition,
                        editable = editable,
                        is_duplicate = index in duplicate_indices,
                        open_menu = open_menu,
                        on_open_menu = { open_menu = it },
                        on_change = { updated ->
                            duplicate_warning = null
                            skipped_duplicates = 0
                            edit { replace_condition(it, index, updated) }
                        },
                        on_remove = {
                            duplicate_warning = null
                            skipped_duplicates = 0
                            edit { remove_condition(it, index) }
                        },
                        on_pick_aliases = { alias_picker_index = index },
                    )
                }
                if (editable) {
                    settings_row_gap()
                    Box(Modifier.fillMaxWidth().testTag("add_condition")) {
                        detail_row(
                            title = stringResource(R.string.mail_rules_add_condition),
                            icon = TablerIcons.Plus,
                            on_click = { open_menu = "add_condition" },
                        )
                        aster_menu(
                            expanded = open_menu == "add_condition",
                            on_dismiss = { open_menu = null },
                        ) {
                            field_menu_items(selected = null) { picked ->
                                open_menu = null
                                edit { it.copy(conditions = it.conditions + default_condition_for(picked)) }
                            }
                        }
                    }
                }
            }

            if (editable) {
                duplicate_warning?.let {
                    v_gap(AsterSpacing.md)
                    AsterAlert(
                        message = stringResource(it),
                        severity = AsterAlertSeverity.danger,
                        modifier = Modifier.testTag("rule_duplicate_warning"),
                    )
                }
                if (skipped_duplicates > 0) {
                    v_gap(AsterSpacing.md)
                    AsterAlert(
                        message = pluralStringResource(
                            R.plurals.mail_rules_aliases_skipped,
                            skipped_duplicates,
                            skipped_duplicates,
                        ),
                        severity = AsterAlertSeverity.info,
                    )
                }
                if (switched_to_any && draft.match_mode == MatchMode.ANY) {
                    v_gap(AsterSpacing.md)
                    AsterAlert(
                        message = stringResource(R.string.mail_rules_switched_to_any),
                        severity = AsterAlertSeverity.info,
                    )
                }
                if (conditions_conflict_under_all(draft.conditions, draft.match_mode)) {
                    v_gap(AsterSpacing.md)
                    Column(Modifier.fillMaxWidth().testTag("rule_conflict_warning")) {
                        AsterAlert(
                            message = stringResource(R.string.mail_rules_conflict_warning),
                            severity = AsterAlertSeverity.warning,
                        )
                        AsterCompactGhostButton(
                            label = stringResource(R.string.mail_rules_conflict_switch),
                            onClick = { edit { it.copy(match_mode = MatchMode.ANY) } },
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }

            v_gap(AsterSpacing.lg)

            val used_kinds = used_action_kinds(draft.actions)
            settings_group(
                title = stringResource(R.string.rules_then_title),
                modifier = Modifier.testTag("rule_then_group"),
            ) {
                draft.actions.forEachIndexed { index, action ->
                    if (index > 0) settings_row_gap()
                    action_block(
                        index = index,
                        action = action,
                        used_kinds = used_action_kinds(draft.actions, except_index = index),
                        editable = editable,
                        open_menu = open_menu,
                        on_open_menu = { open_menu = it },
                        folders = folders,
                        labels = labels,
                        on_change = { updated -> edit { replace_action(it, index, updated) } },
                        on_remove = { edit { remove_action(it, index) } },
                        on_create_folder = {
                            open_menu = null
                            create_for_action = index
                            create_target = create_target_folder
                        },
                        on_create_label = {
                            open_menu = null
                            create_for_action = index
                            create_target = create_target_label
                        },
                    )
                }
                val addable = selectable_actions().filter { it !in used_kinds }
                if (editable && addable.isNotEmpty()) {
                    if (draft.actions.isNotEmpty()) settings_row_gap()
                    Box(Modifier.fillMaxWidth().testTag("add_action")) {
                        detail_row(
                            title = stringResource(R.string.mail_rules_add_action),
                            icon = TablerIcons.Plus,
                            on_click = { open_menu = "add_action" },
                        )
                        aster_menu(
                            expanded = open_menu == "add_action",
                            on_dismiss = { open_menu = null },
                        ) {
                            addable.forEach { id ->
                                aster_menu_item(
                                    label = action_label(id),
                                    icon = action_icon(id),
                                    test_tag = "add_action_${id.name}",
                                    on_click = {
                                        val new_index = draft.actions.size
                                        open_menu = if (id in actions_with_target) "target_$new_index" else null
                                        edit { it.copy(actions = it.actions + default_action_for(id)) }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (editable) {
                val delivery_conflict = rule_alias_delivery_conflict(
                    conditions = draft.conditions,
                    actions = draft.actions,
                    alias_delivery = alias_delivery,
                    folder_type_of = { token ->
                        org.astermail.android.mail_rules.rule_folder_type_of(settings_state.labels, token)
                    },
                )
                if (delivery_conflict != null) {
                    val archive_name = stringResource(R.string.folder_archive)
                    val missing_name = stringResource(R.string.alias_delivery_folder_missing)
                    val alias_target = delivery_conflict.alias_delivery.delivery_folder_token
                        ?.let { token -> folders.firstOrNull { it.id == token }?.label ?: missing_name }
                        ?: archive_name
                    val rule_target = folders
                        .firstOrNull { it.id == delivery_conflict.rule_folder_token }
                        ?.label
                        ?: missing_name
                    v_gap(AsterSpacing.md)
                    AsterAlert(
                        message = stringResource(
                            R.string.mail_rules_alias_delivery_conflict,
                            delivery_conflict.alias_address,
                            alias_target,
                            rule_target,
                        ),
                        severity = AsterAlertSeverity.warning,
                        modifier = Modifier.testTag("rule_alias_delivery_warning"),
                    )
                }
                val label_conflict = rule_alias_label_conflict(
                    conditions = draft.conditions,
                    actions = draft.actions,
                    alias_delivery = alias_delivery,
                )
                if (label_conflict != null) {
                    val missing_label_name = stringResource(R.string.alias_delivery_label_missing)
                    val alias_label = labels
                        .firstOrNull { it.id == label_conflict.alias_label_token }
                        ?.label
                        ?: missing_label_name
                    val rule_labels = label_conflict.rule_label_tokens
                        .map { token -> labels.firstOrNull { it.id == token }?.label ?: missing_label_name }
                        .joinToString(", ")
                    v_gap(AsterSpacing.md)
                    AsterAlert(
                        message = stringResource(
                            R.string.mail_rules_alias_label_conflict,
                            label_conflict.alias_address,
                            alias_label,
                            rule_labels,
                        ),
                        severity = AsterAlertSeverity.warning,
                        modifier = Modifier.testTag("rule_alias_label_warning"),
                    )
                }
            }

            v_gap(AsterSpacing.xl)

            save_held?.let {
                org.astermail.android.guard.GuardHeldBanner(it, Modifier.padding(bottom = AsterSpacing.sm))
            }
            save_error?.let {
                AsterAlert(
                    message = stringResource(it),
                    severity = AsterAlertSeverity.danger,
                    modifier = Modifier.padding(bottom = AsterSpacing.sm).testTag("rule_save_error"),
                )
            }
            val blocker = rule_save_blocker_of(draft, preserved.size)
            if (editable && blocker != null) {
                androidx.compose.material3.Text(
                    text = stringResource(rule_save_blocker_res(blocker)),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AsterSpacing.sm)
                        .testTag("rule_save_hint"),
                )
            }
            if (editable) {
                AsterButton(
                    label = stringResource(R.string.mail_rules_save),
                    is_loading = saving,
                    enabled = blocker == null && !saving && !deleting && (rule_id == null || held_rule != null),
                    modifier = Modifier.fillMaxWidth().testTag("save_rule"),
                    onClick = {
                        if (saving || deleting || blocker != null) return@AsterButton
                        val target = held_rule
                        if (rule_id != null && target == null) return@AsterButton
                        saving = true
                        save_error = null
                        save_held = null
                        val payload_actions = draft.actions + preserved
                        val on_failure: () -> Unit = {
                            saving = false
                            val held_notice = vm.state.value.held
                            save_held = held_notice
                            if (held_notice == null) {
                                save_error = vm.state.value.error ?: R.string.rules_save_failed
                            }
                        }
                        if (target == null) {
                            vm.create_rule(
                                name = draft.name.trim(),
                                color = draft.color,
                                enabled = true,
                                match_mode = draft.match_mode,
                                conditions = draft.conditions,
                                actions = payload_actions,
                            ) { id ->
                                if (id != null) {
                                    saving = false
                                    on_saved()
                                } else {
                                    on_failure()
                                }
                            }
                        } else {
                            vm.update_rule(
                                rule_id = target.id,
                                name = draft.name.trim(),
                                color = draft.color,
                                match_mode = draft.match_mode,
                                conditions = draft.conditions,
                                actions = payload_actions,
                            ) { ok ->
                                if (ok) {
                                    saving = false
                                    on_saved()
                                } else {
                                    on_failure()
                                }
                            }
                        }
                    },
                )
            }
            if (held_rule != null) {
                v_gap(AsterSpacing.md)
                AsterDestructiveButton(
                    label = stringResource(R.string.rules_delete_rule),
                    onClick = { confirm_delete = true },
                    enabled = !saving && !deleting,
                    is_loading = deleting,
                    modifier = Modifier.fillMaxWidth().testTag("delete_rule"),
                )
            }
            v_gap(AsterSpacing.xl)
        }
    }

    val picker_index = alias_picker_index
    if (picker_index != null) {
        val index = picker_index
        val target = draft.conditions.getOrNull(index)
        if (target == null || !condition_is_address_field(target)) {
            LaunchedEffect(index) { alias_picker_index = null }
        } else {
            LaunchedEffect(index) { settings_vm.load_aliases() }
            address_value_picker(
                on_dismiss = { alias_picker_index = null },
                title = stringResource(R.string.rules_choose_alias),
                initial = condition_text_value(target),
                case_sensitive = case_sensitive_of(target),
                is_regex = uses_regex_op(target),
                show_aliases = condition_offers_alias_picker(target),
                aliases = alias_options,
                aliases_loading = settings_state.aliases_loading && settings_state.aliases.isEmpty(),
                on_confirm = { values, case ->
                    val result = insert_condition_values(
                        conditions = draft.conditions,
                        index = index,
                        template = target,
                        values = values,
                        case = case,
                        match_mode = draft.match_mode,
                    )
                    switched_to_any = result.match_mode != draft.match_mode
                    if (normalize_address_values(values).size <= 1) {
                        skipped_duplicates = 0
                        duplicate_warning = if (result.skipped_duplicates > 0) R.string.mail_rules_duplicate_alias else null
                    } else {
                        duplicate_warning = null
                        skipped_duplicates = result.skipped_duplicates
                    }
                    edit { it.copy(conditions = result.conditions, match_mode = result.match_mode) }
                },
            )
        }
    }

    if (show_color) {
        color_picker(
            on_dismiss = { show_color = false },
            selected = draft.color,
            palette = org.astermail.android.ui.common.display_color_presets,
            on_pick = { picked -> edit { it.copy(color = picked) } },
        )
    }

    if (confirm_delete) {
        AsterDialog(
            on_dismiss = { if (!deleting) confirm_delete = false },
            title = stringResource(R.string.rules_delete_title),
            message = stringResource(R.string.rules_delete_message),
            is_busy = deleting,
        ) {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = { confirm_delete = false },
                enabled = !deleting,
                modifier = Modifier.weight(1f),
            )
            AsterDialogDestructiveButton(
                label = stringResource(R.string.delete),
                is_loading = deleting,
                modifier = Modifier.weight(1f).testTag("confirm_delete_rule"),
                onClick = {
                    val target = held_rule ?: return@AsterDialogDestructiveButton
                    if (deleting) return@AsterDialogDestructiveButton
                    deleting = true
                    save_error = null
                    save_held = null
                    vm.delete_rule(target.id) { ok ->
                        deleting = false
                        confirm_delete = false
                        if (ok) {
                            on_saved()
                        } else {
                            val held_notice = vm.state.value.held
                            save_held = held_notice
                            if (held_notice == null) {
                                save_error = vm.state.value.error ?: R.string.rules_error_delete
                            }
                        }
                    }
                },
            )
        }
    }

    if (show_discard) {
        AsterDialog(
            on_dismiss = { show_discard = false },
            title = stringResource(R.string.contact_discard_changes_title),
            message = stringResource(R.string.rules_discard_message),
        ) {
            AsterDialogOutlineButton(
                label = stringResource(R.string.contact_keep_editing),
                onClick = { show_discard = false },
                modifier = Modifier.weight(1f),
            )
            AsterDialogDestructiveButton(
                label = stringResource(R.string.discard),
                modifier = Modifier.weight(1f).testTag("discard_rule_changes"),
                onClick = {
                    show_discard = false
                    on_back()
                },
            )
        }
    }

    when (create_target) {
        create_target_folder -> create_folder_dialog(
            title = stringResource(R.string.create_folder),
            placeholder = stringResource(R.string.folder_name),
            parent_options = folder_parent_options,
            on_dismiss = { create_target = null; create_for_action = null },
            on_create = { folder_name, parent_token ->
                val action_index = create_for_action
                settings_vm.create_folder(
                    name = folder_name,
                    parent_token = parent_token,
                    on_created = { token ->
                        if (action_index != null) {
                            edit { d ->
                                apply_created_target(d.actions.getOrNull(action_index), token)
                                    ?.let { replace_action(d, action_index, it) }
                                    ?: d
                            }
                        }
                    },
                )
                create_target = null
                create_for_action = null
            },
        )
        create_target_label -> create_label_dialog(
            on_dismiss = { create_target = null; create_for_action = null },
            parent_options = label_parent_options,
            existing_names = labels.map { it.label },
            on_create = { label_name, label_color, label_icon, label_parent_token ->
                val action_index = create_for_action
                settings_vm.create_tag(
                    name = label_name,
                    color = label_color,
                    icon = label_icon,
                    parent_token = label_parent_token,
                    on_created = { token ->
                        if (action_index != null) {
                            edit { d ->
                                apply_created_target(d.actions.getOrNull(action_index), token)
                                    ?.let { replace_action(d, action_index, it) }
                                    ?: d
                            }
                        }
                    },
                )
                create_target = null
                create_for_action = null
            },
        )
    }
}

@Composable
private fun field_menu_items(selected: field_id?, on_pick: (field_id) -> Unit) {
    field_sections.forEach { (title_res, fields) ->
        aster_menu_section_label(stringResource(title_res))
        fields.forEach { field ->
            aster_menu_item(
                label = field_display(field),
                icon = field_icon(field),
                selected = field == selected,
                test_tag = "field_${field.name}",
                on_click = { on_pick(field) },
            )
        }
    }
}

@Composable
private fun condition_block(
    index: Int,
    condition: Condition,
    editable: Boolean,
    is_duplicate: Boolean,
    open_menu: String?,
    on_open_menu: (String?) -> Unit,
    on_change: (Condition) -> Unit,
    on_remove: () -> Unit,
    on_pick_aliases: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val field = field_of(condition)
    val kind = field?.let { field_kind_of(it) }
    val op_ids = operator_ids_for(condition)
    Column(Modifier.fillMaxWidth().testTag("cond_$index")) {
        Column(
            modifier = Modifier.padding(
                start = AsterSpacing.lg,
                end = AsterSpacing.xs,
                top = AsterSpacing.md,
                bottom = AsterSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                rule_dropdown(
                    value = field_display(field),
                    icon = field_icon(field),
                    expanded = open_menu == "field_$index",
                    on_expand = { on_open_menu("field_$index") },
                    on_dismiss = { on_open_menu(null) },
                    enabled = editable && field != null,
                    test_tag = "cond_field_$index",
                    modifier = Modifier.weight(1f),
                ) {
                    field_menu_items(selected = field) { picked ->
                        on_open_menu(null)
                        if (picked != field) on_change(switch_condition_field(condition, picked))
                    }
                }
                when {
                    op_ids.isNotEmpty() -> rule_dropdown(
                        value = operator_display(condition).orEmpty(),
                        expanded = open_menu == "op_$index",
                        on_expand = { on_open_menu("op_$index") },
                        on_dismiss = { on_open_menu(null) },
                        enabled = editable,
                        test_tag = "cond_op_$index",
                        modifier = Modifier.weight(1f),
                    ) {
                        val current = current_operator_id(condition)
                        op_ids.forEach { op_id ->
                            aster_menu_item(
                                label = operator_display(apply_operator(condition, op_id)).orEmpty(),
                                selected = op_id == current,
                                test_tag = "op_$op_id",
                                on_click = {
                                    on_open_menu(null)
                                    if (op_id != current) on_change(apply_operator(condition, op_id))
                                },
                            )
                        }
                    }
                    kind == field_kind.boolean -> {
                        val flag = condition_flag_of(condition) ?: true
                        rule_dropdown(
                            value = stringResource(if (flag) R.string.rules_value_yes else R.string.rules_value_no),
                            expanded = open_menu == "value_$index",
                            on_expand = { on_open_menu("value_$index") },
                            on_dismiss = { on_open_menu(null) },
                            enabled = editable,
                            test_tag = "cond_value_$index",
                            modifier = Modifier.weight(1f),
                        ) {
                            listOf(true, false).forEach { choice ->
                                aster_menu_item(
                                    label = stringResource(if (choice) R.string.rules_value_yes else R.string.rules_value_no),
                                    selected = choice == flag,
                                    on_click = {
                                        on_open_menu(null)
                                        on_change(set_condition_flag(condition, choice))
                                    },
                                )
                            }
                        }
                    }
                    kind == field_kind.auth -> {
                        val result = auth_result_of(condition)
                        rule_dropdown(
                            value = result?.let { auth_result_label(it) }.orEmpty(),
                            expanded = open_menu == "value_$index",
                            on_expand = { on_open_menu("value_$index") },
                            on_dismiss = { on_open_menu(null) },
                            enabled = editable,
                            test_tag = "cond_value_$index",
                            modifier = Modifier.weight(1f),
                        ) {
                            AuthResult.entries.forEach { choice ->
                                aster_menu_item(
                                    label = auth_result_label(choice),
                                    selected = choice == result,
                                    on_click = {
                                        on_open_menu(null)
                                        on_change(set_auth_result(condition, choice))
                                    },
                                )
                            }
                        }
                    }
                }
                if (editable) {
                    AsterIconButton(
                        icon = TablerIcons.X,
                        content_description = stringResource(R.string.rules_remove_condition),
                        onClick = on_remove,
                        tint = colors.text_muted,
                        icon_size = 18,
                        modifier = Modifier.testTag("cond_remove_$index"),
                    )
                }
            }
            Box(Modifier.padding(end = if (editable) AsterSpacing.md else 0.dp)) {
                condition_value_editor(
                    index = index,
                    condition = condition,
                    kind = kind,
                    editable = editable,
                    is_duplicate = is_duplicate,
                    open_menu = open_menu,
                    on_open_menu = on_open_menu,
                    on_change = on_change,
                    on_pick_aliases = on_pick_aliases,
                )
            }
        }
        if (supports_case_sensitive(condition) && needs_value(condition) && !uses_regex_op(condition)) {
            settings_toggle_row(
                title = stringResource(R.string.mail_rules_match_case),
                checked = case_sensitive_of(condition),
                enabled = editable,
                test_tag = "cond_case_$index",
                on_change = { checked -> on_change(set_case_sensitive(condition, checked)) },
            )
        }
    }
}

@Composable
private fun condition_value_editor(
    index: Int,
    condition: Condition,
    kind: field_kind?,
    editable: Boolean,
    is_duplicate: Boolean,
    open_menu: String?,
    on_open_menu: (String?) -> Unit,
    on_change: (Condition) -> Unit,
    on_pick_aliases: () -> Unit,
) {
    val duplicate_text = if (is_duplicate) {
        stringResource(
            if (condition_is_address_field(condition)) R.string.mail_rules_duplicate_alias else R.string.mail_rules_duplicate_condition,
        )
    } else {
        null
    }
    when (kind) {
        field_kind.address, field_kind.text, field_kind.attachment_name -> {
            if (!needs_value(condition)) return
            val text = condition_text_value(condition)
            val regex_error = if (text.isNotBlank()) condition_regex_error(condition)?.let { stringResource(it) } else null
            val is_address = kind == field_kind.address
            AsterTextField(
                value = text,
                onValueChange = { on_change(set_condition_text(condition, it)) },
                placeholder = stringResource(R.string.mail_rules_value_placeholder),
                error_text = regex_error ?: duplicate_text,
                enabled = editable,
                keyboard_options = KeyboardOptions(
                    keyboardType = if (is_address && !uses_regex_op(condition)) KeyboardType.Email else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                trailing_icon = if (editable && condition_offers_alias_picker(condition)) {
                    {
                        AsterIconButton(
                            icon = TablerIcons.At,
                            content_description = stringResource(R.string.rules_choose_alias),
                            onClick = on_pick_aliases,
                            icon_size = 18,
                            modifier = Modifier.testTag("cond_alias_$index"),
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().testTag("cond_value_$index"),
            )
        }
        field_kind.header -> {
            val header = condition as? Condition.Header ?: return
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                AsterTextField(
                    value = header.name,
                    onValueChange = { on_change(header.copy(name = it.trim())) },
                    label = stringResource(R.string.mail_rules_header_name),
                    placeholder = stringResource(R.string.mail_rules_header_name),
                    enabled = editable,
                    keyboard_options = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("cond_header_name_$index"),
                )
                if (needs_value(header)) {
                    val regex_error = if (header.value.isNotBlank()) condition_regex_error(header)?.let { stringResource(it) } else null
                    AsterTextField(
                        value = header.value,
                        onValueChange = { on_change(header.copy(value = it)) },
                        label = stringResource(R.string.mail_rules_header_value),
                        placeholder = stringResource(R.string.mail_rules_value_placeholder),
                        error_text = regex_error ?: duplicate_text,
                        enabled = editable,
                        keyboard_options = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().testTag("cond_value_$index"),
                    )
                }
            }
        }
        field_kind.numeric_size -> {
            val bytes = condition_number_of(condition) ?: return
            size_value_editor(
                index = index,
                field = field_of(condition),
                bytes = bytes,
                editable = editable,
                error_text = duplicate_text,
                open_menu = open_menu,
                on_open_menu = on_open_menu,
                on_change = { on_change(set_condition_number(condition, it)) },
            )
        }
        field_kind.numeric_plain, field_kind.date -> {
            if (condition is Condition.SpamScore) {
                score_value_editor(
                    index = index,
                    value = condition.value,
                    editable = editable,
                    error_text = duplicate_text,
                    on_change = { on_change(condition.copy(value = it)) },
                )
            } else {
                val number = condition_number_of(condition) ?: return
                whole_number_editor(
                    index = index,
                    field = field_of(condition),
                    value = number,
                    label = if (kind == field_kind.date) stringResource(R.string.rules_days_label) else null,
                    editable = editable,
                    error_text = duplicate_text,
                    on_change = { on_change(set_condition_number(condition, it)) },
                )
            }
        }
        else -> if (duplicate_text != null) {
            androidx.compose.material3.Text(
                text = duplicate_text,
                color = AsterMaterial.colors.danger,
                fontSize = 13.sp,
            )
        }
    }
}

private fun digits_only(text: String): String = text.filter { it.isDigit() }.take(number_max_length)

@Composable
private fun whole_number_editor(
    index: Int,
    field: field_id?,
    value: Long,
    label: String?,
    editable: Boolean,
    error_text: String?,
    on_change: (Long) -> Unit,
) {
    var text by rememberSaveable(index, field) { mutableStateOf(whole_number_text(value)) }
    val shown = if (parse_whole_number(text) == value) text else whole_number_text(value)
    AsterTextField(
        value = shown,
        onValueChange = { raw ->
            val cleaned = digits_only(raw)
            text = cleaned
            on_change(parse_whole_number(cleaned))
        },
        label = label,
        placeholder = stringResource(R.string.mail_rules_value_placeholder),
        error_text = error_text,
        enabled = editable,
        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("cond_value_$index"),
    )
}

@Composable
private fun score_value_editor(
    index: Int,
    value: Double,
    editable: Boolean,
    error_text: String?,
    on_change: (Double) -> Unit,
) {
    var text by rememberSaveable(index) { mutableStateOf(score_text(value)) }
    val shown = if (score_matches(text, value)) text else score_text(value)
    AsterTextField(
        value = shown,
        onValueChange = { raw ->
            val cleaned = score_input(raw)
            text = cleaned
            on_change(parse_score(cleaned))
        },
        placeholder = stringResource(R.string.mail_rules_value_placeholder),
        error_text = error_text,
        enabled = editable,
        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("cond_value_$index"),
    )
}

@Composable
private fun size_value_editor(
    index: Int,
    field: field_id?,
    bytes: Long,
    editable: Boolean,
    error_text: String?,
    open_menu: String?,
    on_open_menu: (String?) -> Unit,
    on_change: (Long) -> Unit,
) {
    var text by rememberSaveable(index, field) { mutableStateOf(size_text_of(bytes)) }
    var unit_name by rememberSaveable(index, field) {
        mutableStateOf(if (bytes < 0L) size_unit.mb.name else split_size(bytes).second.name)
    }
    val local_unit = size_unit.entries.firstOrNull { it.name == unit_name } ?: size_unit.mb
    val synced = size_text_matches(text, local_unit, bytes)
    val shown_text = if (synced) text else size_text_of(bytes)
    val shown_unit = if (synced) local_unit else split_size(bytes).second
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        AsterTextField(
            value = shown_text,
            onValueChange = { raw ->
                val cleaned = digits_only(raw)
                text = cleaned
                unit_name = shown_unit.name
                on_change(join_size(parse_whole_number(cleaned), shown_unit))
            },
            placeholder = stringResource(R.string.mail_rules_value_placeholder),
            error_text = error_text,
            enabled = editable,
            keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).testTag("cond_value_$index"),
        )
        rule_dropdown(
            value = size_unit_label(shown_unit),
            expanded = open_menu == "unit_$index",
            on_expand = { on_open_menu("unit_$index") },
            on_dismiss = { on_open_menu(null) },
            enabled = editable,
            test_tag = "cond_unit_$index",
            modifier = Modifier.weight(0.6f),
        ) {
            size_unit.entries.forEach { unit ->
                aster_menu_item(
                    label = size_unit_label(unit),
                    selected = unit == shown_unit,
                    on_click = {
                        on_open_menu(null)
                        text = shown_text
                        unit_name = unit.name
                        on_change(join_size(parse_whole_number(shown_text), unit))
                    },
                )
            }
        }
    }
}

@Composable
private fun action_block(
    index: Int,
    action: Action,
    used_kinds: Set<action_id>,
    editable: Boolean,
    open_menu: String?,
    on_open_menu: (String?) -> Unit,
    folders: List<picker_item>,
    labels: List<picker_item>,
    on_change: (Action) -> Unit,
    on_remove: () -> Unit,
    on_create_folder: () -> Unit,
    on_create_label: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val kind = action_of(action)
    val can_edit = editable && kind != null && kind !in unavailable_actions
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AsterSpacing.lg, end = AsterSpacing.xs, top = AsterSpacing.md, bottom = AsterSpacing.md)
            .testTag("action_$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        rule_dropdown(
            value = action_label(kind),
            icon = action_icon(kind),
            expanded = open_menu == "action_$index",
            on_expand = { on_open_menu("action_$index") },
            on_dismiss = { on_open_menu(null) },
            enabled = can_edit,
            test_tag = "action_kind_$index",
            modifier = Modifier.weight(1f),
        ) {
            selectable_actions().forEach { id ->
                aster_menu_item(
                    label = action_label(id),
                    icon = action_icon(id),
                    selected = id == kind,
                    enabled = id == kind || id !in used_kinds,
                    test_tag = "action_kind_${id.name}",
                    on_click = {
                        if (id == kind) {
                            on_open_menu(null)
                        } else {
                            on_open_menu(if (id in actions_with_target) "target_$index" else null)
                            on_change(default_action_for(id))
                        }
                    },
                )
            }
        }
        if (kind in actions_with_target) {
            action_target_dropdown(
                index = index,
                action = action,
                editable = can_edit,
                open_menu = open_menu,
                on_open_menu = on_open_menu,
                folders = folders,
                labels = labels,
                on_change = on_change,
                on_create_folder = on_create_folder,
                on_create_label = on_create_label,
                modifier = Modifier.weight(1f),
            )
        }
        if (editable) {
            AsterIconButton(
                icon = TablerIcons.X,
                content_description = stringResource(R.string.rules_remove_action),
                onClick = on_remove,
                tint = colors.text_muted,
                icon_size = 18,
                modifier = Modifier.testTag("action_remove_$index"),
            )
        }
    }
}

@Composable
private fun action_target_dropdown(
    index: Int,
    action: Action,
    editable: Boolean,
    open_menu: String?,
    on_open_menu: (String?) -> Unit,
    folders: List<picker_item>,
    labels: List<picker_item>,
    on_change: (Action) -> Unit,
    on_create_folder: () -> Unit,
    on_create_label: () -> Unit,
    modifier: Modifier,
) {
    val key = "target_$index"
    val expanded = open_menu == key
    val on_expand = { on_open_menu(key) }
    val on_dismiss = { on_open_menu(null) }
    val tag = "action_target_$index"
    when (action) {
        is Action.MoveTo -> {
            val token = action.folder_token
            val label = folders.firstOrNull { it.id == token }?.label
            rule_dropdown(
                value = when {
                    token.isBlank() -> stringResource(R.string.rules_choose_folder)
                    label != null -> label
                    else -> stringResource(R.string.alias_delivery_folder_missing)
                },
                placeholder = token.isBlank(),
                expanded = expanded,
                on_expand = on_expand,
                on_dismiss = on_dismiss,
                enabled = editable,
                test_tag = tag,
                modifier = modifier,
            ) {
                folders.forEach { item ->
                    aster_menu_item(
                        label = item.label,
                        icon = item.icon ?: TablerIcons.Folder,
                        icon_tint = item.icon_tint,
                        selected = item.id == token,
                        indent = (16 * item.depth).dp,
                        on_click = {
                            on_open_menu(null)
                            on_change(action.copy(folder_token = item.id))
                        },
                    )
                }
                aster_menu_item(
                    label = stringResource(R.string.rules_new_folder),
                    icon = TablerIcons.Plus,
                    test_tag = "rule_new_folder",
                    on_click = on_create_folder,
                )
            }
        }
        is Action.ApplyLabels -> {
            val names = action.label_tokens.mapNotNull { token -> labels.firstOrNull { it.id == token }?.label }
            rule_dropdown(
                value = if (names.isEmpty()) stringResource(R.string.rules_choose_labels) else names.joinToString(", "),
                placeholder = names.isEmpty(),
                expanded = expanded,
                on_expand = on_expand,
                on_dismiss = on_dismiss,
                enabled = editable,
                test_tag = tag,
                modifier = modifier,
            ) {
                labels.forEach { item ->
                    aster_menu_toggle_item(
                        label = item.label,
                        checked = item.id in action.label_tokens,
                        icon = item.icon ?: TablerIcons.Tag,
                        on_click = { on_change(toggle_label_token(action, item.id)) },
                    )
                }
                aster_menu_item(
                    label = stringResource(R.string.rules_new_label),
                    icon = TablerIcons.Plus,
                    test_tag = "rule_new_label",
                    on_click = on_create_label,
                )
            }
        }
        is Action.MarkAs -> rule_dropdown(
            value = stringResource(if (action.value == ReadState.READ) R.string.rules_value_read else R.string.rules_value_unread),
            expanded = expanded,
            on_expand = on_expand,
            on_dismiss = on_dismiss,
            enabled = editable,
            test_tag = tag,
            modifier = modifier,
        ) {
            ReadState.entries.forEach { choice ->
                aster_menu_item(
                    label = stringResource(if (choice == ReadState.READ) R.string.rules_value_read else R.string.rules_value_unread),
                    selected = choice == action.value,
                    on_click = {
                        on_open_menu(null)
                        on_change(action.copy(value = choice))
                    },
                )
            }
        }
        is Action.Categorize -> rule_dropdown(
            value = category_label(action.category),
            expanded = expanded,
            on_expand = on_expand,
            on_dismiss = on_dismiss,
            enabled = editable,
            test_tag = tag,
            modifier = modifier,
        ) {
            org.astermail.android.mail.RULE_CATEGORY_TARGETS.forEach { category ->
                aster_menu_item(
                    label = category_label(category),
                    selected = category == action.category,
                    on_click = {
                        on_open_menu(null)
                        on_change(action.copy(category = category))
                    },
                )
            }
        }
        is Action.Notify -> rule_dropdown(
            value = stringResource(if (action.enabled) R.string.rules_value_on else R.string.rules_value_off),
            expanded = expanded,
            on_expand = on_expand,
            on_dismiss = on_dismiss,
            enabled = editable,
            test_tag = tag,
            modifier = modifier,
        ) {
            listOf(true, false).forEach { choice ->
                aster_menu_item(
                    label = stringResource(if (choice) R.string.rules_value_on else R.string.rules_value_off),
                    selected = choice == action.enabled,
                    on_click = {
                        on_open_menu(null)
                        on_change(action.copy(enabled = choice))
                    },
                )
            }
        }
        is Action.Snooze -> {
            val valid = is_valid_rfc3339(action.until_iso8601)
            rule_dropdown(
                value = if (valid) {
                    stringResource(R.string.rules_snooze_until, format_snooze_until(action.until_iso8601))
                } else {
                    stringResource(R.string.rules_choose_time)
                },
                placeholder = !valid,
                expanded = expanded,
                on_expand = on_expand,
                on_dismiss = on_dismiss,
                enabled = editable,
                test_tag = tag,
                modifier = modifier,
            ) {
                snooze_choices.forEach { (label_res, duration) ->
                    aster_menu_item(
                        label = stringResource(label_res),
                        on_click = {
                            on_open_menu(null)
                            on_change(
                                action.copy(
                                    until_iso8601 = snooze_until_from_now(duration, java.time.OffsetDateTime.now()),
                                ),
                            )
                        },
                    )
                }
            }
        }
        else -> Unit
    }
}

internal fun rule_folder_picker_items(
    labels: List<org.astermail.android.api.labels.LabelItem>,
    system_folder_names: Map<org.astermail.android.mail_rules.RuleSystemFolder, String>,
): List<picker_item> {
    val system_items = org.astermail.android.mail_rules.rule_system_folders(labels)
        .map { entry ->
            picker_item(
                id = entry.folder.label_token,
                label = system_folder_names.getValue(entry.system_type),
                icon = rule_system_folder_icon(entry.system_type),
            )
        }
    return system_items + flatten_folder_tree(labels)
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

@Composable
internal fun action_label(id: action_id?): String = stringResource(
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
internal fun field_display(field: field_id?): String = stringResource(
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
internal fun operator_display(c: Condition): String? = when (c) {
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
        AddressOp.IS_NOT -> R.string.rules_op_is_not
        AddressOp.CONTAINS -> R.string.rules_op_contains
        AddressOp.DOES_NOT_CONTAIN -> R.string.rules_op_does_not_contain
        AddressOp.STARTS_WITH -> R.string.rules_op_starts_with
        AddressOp.ENDS_WITH -> R.string.rules_op_ends_with
        AddressOp.MATCHES_DOMAIN -> R.string.rules_op_matches_domain
        AddressOp.DOES_NOT_MATCH_DOMAIN -> R.string.rules_op_does_not_match_domain
        AddressOp.IS_EMPTY -> R.string.rules_op_is_empty
        AddressOp.MATCHES_REGEX -> R.string.rules_op_matches_regex
    },
)

@Composable
private fun text_op_label(op: TextOp): String = stringResource(
    when (op) {
        TextOp.IS -> R.string.rules_op_is
        TextOp.IS_NOT -> R.string.rules_op_is_not
        TextOp.CONTAINS -> R.string.rules_op_contains
        TextOp.DOES_NOT_CONTAIN -> R.string.rules_op_does_not_contain
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

@StringRes
private fun auth_result_res(result: AuthResult): Int = when (result) {
    AuthResult.PASS -> R.string.rules_auth_pass
    AuthResult.FAIL -> R.string.rules_auth_fail
    AuthResult.NONE -> R.string.rules_auth_none
    AuthResult.MISSING -> R.string.rules_auth_missing
}

@Composable
private fun auth_result_label(result: AuthResult): String = stringResource(auth_result_res(result))

@StringRes
private fun size_unit_res(unit: size_unit): Int = when (unit) {
    size_unit.bytes -> R.string.rules_unit_bytes
    size_unit.kb -> R.string.rules_unit_kb
    size_unit.mb -> R.string.rules_unit_mb
    size_unit.gb -> R.string.rules_unit_gb
}

@Composable
private fun size_unit_label(unit: size_unit): String = stringResource(size_unit_res(unit))

@Composable
private fun size_display(bytes: Long): String? {
    if (bytes < 0L) return null
    val (amount, unit) = split_size(bytes)
    return "$amount ${size_unit_label(unit)}"
}

@Composable
internal fun value_display(c: Condition): String? {
    val yes = stringResource(R.string.rules_value_yes)
    val no = stringResource(R.string.rules_value_no)
    return when (c) {
        is Condition.From -> if (c.op == AddressOp.IS_EMPTY) null else c.value
        is Condition.ReplyTo -> if (c.op == AddressOp.IS_EMPTY) null else c.value
        is Condition.To -> if (c.op == AddressOp.IS_EMPTY) null else c.value
        is Condition.Cc -> if (c.op == AddressOp.IS_EMPTY) null else c.value
        is Condition.Bcc -> if (c.op == AddressOp.IS_EMPTY) null else c.value
        is Condition.AnyRecipient -> if (c.op == AddressOp.IS_EMPTY) null else c.value
        is Condition.Subject -> if (c.op == TextOp.IS_EMPTY) null else c.value
        is Condition.Body -> if (c.op == TextOp.IS_EMPTY) null else c.value
        is Condition.ListId -> if (c.op == TextOp.IS_EMPTY) null else c.value
        is Condition.Header -> if (c.op == TextOp.IS_EMPTY) c.name else "${c.name}: ${c.value}"
        is Condition.AttachmentName -> c.value
        is Condition.HasAttachment -> if (c.`is`) yes else no
        is Condition.IsReply -> if (c.`is`) yes else no
        is Condition.IsForward -> if (c.`is`) yes else no
        is Condition.IsAutoSubmitted -> if (c.`is`) yes else no
        is Condition.HasCalendarInvite -> if (c.`is`) yes else no
        is Condition.HasListId -> if (c.`is`) yes else no
        is Condition.AttachmentSize -> size_display(c.value)
        is Condition.TotalSize -> size_display(c.value)
        is Condition.RecipientCount -> whole_number_text(c.value).ifEmpty { null }
        is Condition.SpamScore -> score_text(c.value).ifEmpty { null }
        is Condition.DateReceived -> if (c.value < 0L) {
            null
        } else {
            val days = c.value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            pluralStringResource(R.plurals.rules_value_days, days, days)
        }
        is Condition.DkimResult -> auth_result_label(c.value)
        is Condition.SpfResult -> auth_result_label(c.value)
        is Condition.DmarcResult -> auth_result_label(c.value)
        else -> null
    }
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
internal fun action_target_display(action: Action, folder_label: String?): String? = when (action) {
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
private fun category_label(category: String): String {
    if (category == "important") return stringResource(R.string.rules_category_important)
    val builtin = org.astermail.android.mail.builtin_category(category) ?: return category
    return stringResource(builtin.label_res)
}
