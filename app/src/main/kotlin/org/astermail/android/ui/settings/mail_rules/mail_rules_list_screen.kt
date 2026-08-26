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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.api.mail_rules.MailRule
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogDestructiveButton
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTopBar
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.design.parse_hex_color_safe
import org.astermail.android.mail_rules.MailRulesViewModel

@Composable
fun MailRulesListScreen(
    on_back: () -> Unit,
    on_edit: (String) -> Unit,
    on_new: () -> Unit,
    vm: MailRulesViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    val rules_context = androidx.compose.ui.platform.LocalContext.current

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
            AsterDivider()
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
                    1 -> empty_state()
                    3 -> error_state(
                        message = stringResource(state.error ?: R.string.rules_error_load),
                        on_retry = { vm.load(force_refresh = true) },
                    )
                    else -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        state.rules.forEach { rule ->
                            rule_row(
                                rule = rule,
                                on_open = { on_edit(rule.id) },
                                on_toggle = { vm.toggle_enabled(rule.id) },
                                on_duplicate = { vm.duplicate_rule(rule.id, copy_suffix) },
                                on_delete = { vm.delete_rule(rule.id) },
                                on_run = { vm.run_on_existing(rule.id) },
                            )
                            AsterDivider()
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = on_new,
            containerColor = colors.accent_blue,
            contentColor = Color.White,
            shape = SquircleShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AsterSpacing.lg)
                .testTag("add_rule"),
        ) {
            Icon(
                imageVector = TablerIcons.Plus,
                contentDescription = stringResource(R.string.mail_rules_new_rule),
            )
        }
    }
}

@Composable
private fun mail_rules_skeleton() {
    val brush = org.astermail.android.design.components.shimmer_brush()
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.md, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(brush),
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush),
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush),
                )
            }
            AsterDivider()
        }
    }
}

@Composable
private fun empty_state() {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AsterSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.mail_rules_empty_title),
            color = colors.text_primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.mail_rules_empty_subtitle),
            color = colors.text_tertiary,
            fontSize = 14.sp,
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
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = message,
            color = colors.text_secondary,
            fontSize = 14.sp,
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
private fun rule_row(
    rule: MailRule,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_open)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(parse_color(rule.color)),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = AsterSpacing.sm),
        ) {
            Text(
                text = rule.name,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.mail_rules_applied_count,
                        rule.applied_count.toInt(),
                        rule.applied_count.toInt(),
                    ),
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
        }
        AsterSwitch(
            checked = rule.enabled,
            onCheckedChange = { on_toggle() },
        )
        Spacer(Modifier.width(AsterSpacing.sm))
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
