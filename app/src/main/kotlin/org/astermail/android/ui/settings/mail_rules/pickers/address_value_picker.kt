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

package org.astermail.android.ui.settings.mail_rules.pickers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.ui.settings.mail_rules.regex_error_res

data class alias_option(
    val address: String,
    val display_name: String? = null,
)

private const val alias_list_max_height_dp = 240

fun filter_alias_options(options: List<alias_option>, query: String): List<alias_option> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return options
    return options.filter { option ->
        option.address.lowercase().contains(needle) ||
            option.display_name?.lowercase()?.contains(needle) == true
    }
}

@Composable
fun address_value_picker(
    on_dismiss: () -> Unit,
    title: String,
    initial: String,
    case_sensitive: Boolean,
    is_regex: Boolean,
    show_aliases: Boolean,
    aliases: List<alias_option>,
    aliases_loading: Boolean,
    on_confirm: (List<String>, Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    val initial_selection = remember(initial, aliases) {
        val match = aliases.firstOrNull { it.address.equals(initial.trim(), ignoreCase = true) }
        if (match == null) emptySet() else setOf(match.address)
    }
    var selected by remember(initial_selection) { mutableStateOf(initial_selection) }
    var value by remember(initial, initial_selection) {
        mutableStateOf(if (initial_selection.isEmpty()) initial else "")
    }
    var case by remember(case_sensitive) { mutableStateOf(case_sensitive) }
    var query by remember { mutableStateOf("") }

    val regex_error = if (is_regex) regex_error_res(value) else null
    val filtered = remember(aliases, query) { filter_alias_options(aliases, query) }
    val confirm_values = remember(selected, value, aliases) {
        aliases.map { it.address }.filter { it in selected } + listOf(value)
    }

    base_sheet(on_dismiss = on_dismiss, title = title) {
        Column {
            if (show_aliases) {
                Box(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
                    AsterTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.mail_rules_search_aliases),
                        leading_icon = {
                            Icon(
                                imageVector = TablerIcons.Search,
                                contentDescription = null,
                                tint = colors.text_tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.testTag("alias_search"),
                    )
                }
                Spacer(Modifier.height(AsterSpacing.sm))
                when {
                    aliases.isEmpty() && aliases_loading -> alias_list_note(
                        text = stringResource(R.string.mail_rules_aliases_loading),
                        test_tag = "alias_loading",
                    )
                    aliases.isEmpty() -> alias_list_note(
                        text = stringResource(R.string.mail_rules_no_aliases),
                        test_tag = "alias_empty",
                    )
                    filtered.isEmpty() -> alias_list_note(
                        text = stringResource(R.string.mail_rules_no_alias_matches),
                        test_tag = "alias_no_matches",
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = alias_list_max_height_dp.dp)) {
                        items(filtered, key = { it.address }) { option ->
                            row_select(
                                label = option.address,
                                sublabel = option.display_name?.takeIf { it.isNotBlank() },
                                selected = option.address in selected,
                                on_click = {
                                    selected = if (option.address in selected) {
                                        selected - option.address
                                    } else {
                                        selected + option.address
                                    }
                                },
                                test_tag = "alias_${option.address}",
                            )
                        }
                    }
                }
                if (selected.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.mail_rules_aliases_selected,
                            selected.size,
                            selected.size,
                        ),
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm)
                            .testTag("alias_selected_count"),
                    )
                }
                Spacer(Modifier.height(AsterSpacing.sm))
                AsterDivider()
                Spacer(Modifier.height(AsterSpacing.md))
            }

            Column(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
                AsterTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = if (show_aliases) stringResource(R.string.mail_rules_other_address) else null,
                    placeholder = stringResource(R.string.mail_rules_value_placeholder),
                    singleLine = false,
                    modifier = Modifier.testTag("value_input"),
                )
                regex_error?.let {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = stringResource(it),
                        color = colors.danger,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("regex_error"),
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.mail_rules_match_case),
                        modifier = Modifier.weight(1f),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                    )
                    AsterSwitch(checked = case, onCheckedChange = { case = it })
                }
                Spacer(Modifier.height(AsterSpacing.lg))
                AsterButton(
                    label = stringResource(R.string.mail_rules_confirm),
                    onClick = {
                        on_confirm(confirm_values, case)
                        on_dismiss()
                    },
                    enabled = regex_error == null,
                    modifier = Modifier.testTag("confirm_value"),
                )
                Spacer(Modifier.height(AsterSpacing.md))
            }
        }
    }
}

@Composable
private fun alias_list_note(text: String, test_tag: String) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_tertiary,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)
            .testTag(test_tag),
    )
}
