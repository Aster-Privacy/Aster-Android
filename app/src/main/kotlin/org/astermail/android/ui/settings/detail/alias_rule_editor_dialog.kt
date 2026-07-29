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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import org.astermail.android.R
import org.astermail.android.api.aliases.AliasRuleActions
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField

private val rule_fields = listOf("from", "to", "subject", "all")
private val rule_operators = listOf("contains", "equals", "starts_with", "ends_with", "matches_regex")

@Composable
internal fun alias_rule_editor_dialog(
    on_dismiss: () -> Unit,
    on_save: (String, String, String, AliasRuleActions) -> Unit,
) {
    var field by remember { mutableStateOf(rule_fields.first()) }
    var operator by remember { mutableStateOf(rule_operators.first()) }
    var value by remember { mutableStateOf("") }
    var block by remember { mutableStateOf(true) }
    var to_trash by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }

    val needs_value = field != "all"
    val can_save = (!needs_value || value.isNotBlank()) && (block || to_trash || label.isNotBlank())

    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.alias_rule_add),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                rule_dropdown(
                    options = rule_fields,
                    selected = field,
                    label_for = { rule_field_label(it) },
                    test_tag = "alias_rule_field",
                    on_select = { field = it },
                )
                if (needs_value) {
                    rule_dropdown(
                        options = rule_operators,
                        selected = operator,
                        label_for = { rule_operator_label(it) },
                        test_tag = "alias_rule_operator",
                        on_select = { operator = it },
                    )
                    AsterTextField(
                        value = value,
                        onValueChange = { value = it.take(500) },
                        placeholder = stringResource(R.string.alias_rule_value_placeholder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alias_rule_value"),
                    )
                }
                panel_section_title(stringResource(R.string.alias_rule_actions_title))
                rule_action_toggle(
                    label = stringResource(R.string.alias_rule_action_block),
                    checked = block,
                    test_tag = "alias_rule_action_block",
                    on_change = { block = it },
                )
                rule_action_toggle(
                    label = stringResource(R.string.alias_rule_action_to_trash),
                    checked = to_trash,
                    test_tag = "alias_rule_action_to_trash",
                    on_change = { to_trash = it },
                )
                AsterTextField(
                    value = label,
                    onValueChange = { label = it.take(100) },
                    placeholder = stringResource(R.string.alias_rule_action_label_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alias_rule_action_label"),
                )
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
                modifier = Modifier.weight(1f),
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.save),
                onClick = {
                    on_save(
                        field,
                        operator,
                        value.trim(),
                        AliasRuleActions(
                            block = if (block) true else null,
                            to_trash = if (to_trash) true else null,
                            label = label.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = can_save,
                modifier = Modifier
                    .weight(1f)
                    .testTag("alias_rule_save"),
            )
        },
    )
}

@Composable
private fun rule_dropdown(
    options: List<String>,
    selected: String,
    label_for: (String) -> Int,
    test_tag: String,
    on_select: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bg_secondary)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag(test_tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(label_for(selected)),
                color = colors.text_primary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(label_for(option))) },
                    onClick = {
                        open = false
                        on_select(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun rule_action_toggle(
    label: String,
    checked: Boolean,
    test_tag: String,
    on_change: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = AsterMaterial.colors.text_primary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        AsterSwitch(
            checked = checked,
            onCheckedChange = on_change,
            modifier = Modifier.testTag(test_tag),
        )
    }
}
