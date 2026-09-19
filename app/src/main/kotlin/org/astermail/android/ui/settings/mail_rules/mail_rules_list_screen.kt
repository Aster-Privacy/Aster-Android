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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.MailRule
import org.astermail.android.api.mail_rules.MatchMode
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogDestructiveButton
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.ui.settings.detail.detail_row
import org.astermail.android.ui.settings.detail.settings_group
import org.astermail.android.ui.settings.detail.settings_row_gap
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTopBar
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.design.parse_hex_color_safe
import org.astermail.android.folders.flatten_folder_tree
import org.astermail.android.mail_rules.MailRulesViewModel
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

@Composable
fun MailRulesListScreen(
    on_back: () -> Unit,
    on_edit: (String) -> Unit,
    on_new: () -> Unit,
    vm: MailRulesViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val settings_vm: SettingsViewModel = shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    val rules_context = androidx.compose.ui.platform.LocalContext.current

    val folder_names = remember(settings_state.labels) {
        flatten_folder_tree(settings_state.labels)
            .filter { !it.label.encrypted_name.isNullOrBlank() }
            .associate { it.label.label_token to it.label.encrypted_name.orEmpty() }
    }

    LaunchedEffect(state.error, state.rules.isEmpty()) {
        val message_res = state.error ?: return@LaunchedEffect
        if (state.rules.isEmpty()) return@LaunchedEffect
        android.widget.Toast.makeText(
            rules_context,
            rules_context.getString(message_res),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        vm.clear_error()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AsterTopBar(title = stringResource(R.string.mail_rules_title), on_back = on_back)
            val copy_suffix = stringResource(R.string.rules_copy_suffix)
            val rules_key = when {
                state.is_loading -> 0
                state.rules.isEmpty() && state.error != null -> 3
                state.rules.isEmpty() -> 1
                else -> 2
            }
            AnimatedContent(
                targetState = rules_key,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "rules_content",
            ) { key ->
                when (key) {
                    0 -> mail_rules_skeleton()
                    1 -> empty_state(on_new = on_new)
                    3 -> error_state(
                        message = stringResource(state.error ?: R.string.rules_error_load),
                        on_retry = { vm.load(force_refresh = true) },
                    )
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = AsterSpacing.lg),
                    ) {
                        settings_group {
                            sort_rules_for_display(state.rules).forEachIndexed { index, rule ->
                                if (index > 0) settings_row_gap()
                                rule_row(
                                    rule = rule,
                                    folder_names = folder_names,
                                    on_open = { on_edit(rule.id) },
                                    on_toggle = { vm.toggle_enabled(rule.id) },
                                    on_duplicate = { vm.duplicate_rule(rule.id, copy_suffix) },
                                    on_delete = { vm.delete_rule(rule.id) },
                                    on_run = { vm.run_on_existing(rule.id) },
                                )
                            }
                        }
                        caption(stringResource(R.string.mail_rules_subtitle))
                        settings_group {
                            detail_row(
                                title = stringResource(R.string.mail_rules_new_rule),
                                icon = TablerIcons.Plus,
                                icon_tint = colors.accent_blue,
                                on_click = on_new,
                            )
                        }
                        Spacer(Modifier.height(AsterSpacing.xxl))
                    }
                }
            }
        }
    }
}

@Composable
private fun caption(text: String) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_tertiary,
        fontSize = 13.sp,
        modifier = Modifier.padding(
            start = AsterSpacing.xs,
            end = AsterSpacing.xs,
            top = AsterSpacing.sm,
        ),
    )
}

@Composable
private fun mail_rules_skeleton() {
    val shimmer_state = org.astermail.android.design.components.shimmer_state()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AsterSpacing.lg),
    ) {
        settings_group {
            repeat(6) { index ->
                if (index > 0) settings_row_gap()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .shimmer(shimmer_state, CircleShape),
                    )
                    Spacer(Modifier.width(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(14.dp)
                                .shimmer(shimmer_state, RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(11.dp)
                                .shimmer(shimmer_state, RoundedCornerShape(4.dp)),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .shimmer(shimmer_state, RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun empty_state(on_new: () -> Unit) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AsterSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = TablerIcons.Bolt,
            contentDescription = null,
            tint = colors.text_tertiary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.mail_rules_empty_title),
            color = colors.text_primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(AsterSpacing.xs))
        Text(
            text = stringResource(R.string.mail_rules_empty_subtitle),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.lg))
        AsterButton(
            label = stringResource(R.string.mail_rules_create),
            onClick = on_new,
            modifier = Modifier.fillMaxWidth().testTag("add_rule"),
        )
    }
}

@Composable
private fun error_state(message: String, on_retry: () -> Unit) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AsterSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = colors.text_secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(AsterSpacing.md))
        AsterButton(
            label = stringResource(R.string.retry),
            onClick = on_retry,
            modifier = Modifier.testTag("rules_retry"),
        )
    }
}

@Composable
private fun rule_condition_summaries(rule: MailRule): List<String> =
    rule.conditions.map { condition ->
        listOfNotNull(
            field_display(field_of(condition)),
            operator_display(condition)?.takeIf { it.isNotBlank() },
            value_display(condition)?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    }

@Composable
private fun rule_action_summaries(rule: MailRule, folder_names: Map<String, String>): List<String> =
    rule.actions.map { action ->
        val folder_label = (action as? Action.MoveTo)?.folder_token?.let { folder_names[it] }
        listOfNotNull(
            action_label(action_of(action)),
            action_target_display(action, folder_label)?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    }

@Composable
private fun rule_summary_text(rule: MailRule, folder_names: Map<String, String>): String {
    val advanced_label = stringResource(R.string.mail_rules_field_advanced)
    val is_advanced = rule_is_advanced(rule)
    val conditions = if (is_advanced) listOf(advanced_label) else rule_condition_summaries(rule)
    val actions = if (is_advanced) emptyList() else rule_action_summaries(rule, folder_names)
    val joiner = stringResource(
        if (rule.match_mode == MatchMode.ANY) R.string.mail_rules_or else R.string.mail_rules_and,
    )
    return rule_summary_line(
        when_label = stringResource(R.string.mail_rules_when_section),
        then_label = stringResource(R.string.mail_rules_then_section),
        joiner = joiner,
        conditions = conditions,
        actions = actions,
    )
}

@Composable
private fun rule_row(
    rule: MailRule,
    folder_names: Map<String, String>,
    on_open: () -> Unit,
    on_toggle: () -> Unit,
    on_duplicate: () -> Unit,
    on_delete: () -> Unit,
    on_run: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var menu_open by remember { mutableStateOf(false) }
    var confirm_delete by remember { mutableStateOf(false) }
    var confirm_run by remember { mutableStateOf(false) }
    if (confirm_delete) {
        AsterDialog(
            on_dismiss = { confirm_delete = false },
            title = stringResource(R.string.rules_delete_title),
            message = stringResource(R.string.rules_delete_message),
            footer = {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { confirm_delete = false },
                )
                AsterDialogDestructiveButton(
                    label = stringResource(R.string.delete),
                    onClick = { confirm_delete = false; on_delete() },
                )
            },
        )
    }
    if (confirm_run) {
        AsterDialog(
            on_dismiss = { confirm_run = false },
            title = stringResource(R.string.mail_rules_run_on_existing),
            message = stringResource(R.string.mail_rules_run_on_existing_confirm),
            footer = {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { confirm_run = false },
                )
                AsterDialogDestructiveButton(
                    label = stringResource(R.string.mail_rules_run_on_existing),
                    onClick = { confirm_run = false; on_run() },
                )
            },
        )
    }
    val summary = rule_summary_text(rule = rule, folder_names = folder_names)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_open)
            .heightIn(min = 56.dp)
            .padding(
                start = AsterSpacing.lg,
                end = AsterSpacing.xs,
                top = AsterSpacing.sm,
                bottom = AsterSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parse_color(rule.color)),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                color = if (rule.enabled) colors.text_primary else colors.text_secondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text =
                    pluralStringResource(
                        R.plurals.mail_rules_applied_count,
                        rule.applied_count.toInt(),
                        rule.applied_count.toInt(),
                    ),
                color = colors.text_tertiary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        AsterSwitch(
            checked = rule.enabled,
            onCheckedChange = { on_toggle() },
        )
        Box {
            IconButton(onClick = { menu_open = true }) {
                Icon(
                    imageVector = TablerIcons.DotsVertical,
                    contentDescription = stringResource(R.string.more_options),
                    tint = colors.text_secondary,
                )
            }
            aster_dropdown_menu(expanded = menu_open, on_dismiss = { menu_open = false }) {
                aster_dropdown_item(
                    label = stringResource(R.string.mail_rules_edit_rule),
                    icon = TablerIcons.Pencil,
                    on_click = { menu_open = false; on_open() },
                )
                if (!rule_is_advanced(rule)) {
                    aster_dropdown_item(
                        label = stringResource(R.string.mail_rules_duplicate),
                        icon = TablerIcons.Copy,
                        on_click = { menu_open = false; on_duplicate() },
                    )
                }
                aster_dropdown_item(
                    label = stringResource(R.string.mail_rules_run_on_existing),
                    icon = TablerIcons.PlayerPlay,
                    on_click = { menu_open = false; confirm_run = true },
                )
                aster_dropdown_item(
                    label = stringResource(R.string.mail_rules_delete),
                    icon = TablerIcons.Trash,
                    destructive = true,
                    on_click = { menu_open = false; confirm_delete = true },
                )
            }
        }
    }
}

private fun parse_color(hex: String): Color =
    parse_hex_color_safe(hex) ?: Color(0xFF3B82F6)
