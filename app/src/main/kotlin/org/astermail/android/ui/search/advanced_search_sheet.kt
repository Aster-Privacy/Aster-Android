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

package org.astermail.android.ui.search

import org.astermail.android.ui.common.vertical_scroll_with_indicator
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterActionRow
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.acrylic
import org.astermail.android.design.local_acrylic
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import org.astermail.android.ui.common.sheet_container_color

private val ROW_LABEL_WIDTH = 104.dp

@Composable
private fun advanced_section(title: String, content: @Composable () -> Unit) {
    val colors = AsterMaterial.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.text_muted,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .acrylic(colors, SquircleShape(18.dp), colors.bg_secondary),
        ) {
            content()
        }
    }
}

@Composable
private fun advanced_row_label(label: String) {
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = AsterMaterial.colors.text_secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(ROW_LABEL_WIDTH),
    )
}

@Composable
private fun advanced_input_row(
    label: String,
    value: String,
    placeholder: String,
    ime_action: ImeAction,
    on_change: (String) -> Unit,
    on_submit: () -> Unit,
) {
    val colors = AsterMaterial.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        advanced_row_label(label)
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = on_change,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = colors.text_primary,
                ),
                cursorBrush = SolidColor(colors.accent_blue),
                keyboardOptions = KeyboardOptions(imeAction = ime_action),
                keyboardActions = KeyboardActions(
                    onNext = { on_submit() },
                    onSearch = { on_submit() },
                ),
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 15.sp,
                    color = colors.text_muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun advanced_value_row(
    label: String,
    value: String,
    on_click: () -> Unit,
    menu: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = on_click)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            advanced_row_label(label)
            Text(
                text = value,
                fontSize = 15.sp,
                color = colors.text_primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp),
            )
        }
        menu()
    }
}

@Composable
private fun advanced_toggle_row(label: String, checked: Boolean, on_change: (Boolean) -> Unit) {
    val colors = AsterMaterial.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = colors.text_primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AsterSwitch(checked = checked, onCheckedChange = on_change)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun advanced_search_sheet(
    operators: List<SearchOperator>,
    free_text: String,
    on_apply: (List<SearchOperator>, String) -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val focus_manager = LocalFocusManager.current
    val initial = remember { advanced_fields(operators) }

    var from_text by rememberSaveable { mutableStateOf(initial.from) }
    var to_text by rememberSaveable { mutableStateOf(initial.to) }
    var subject_text by rememberSaveable { mutableStateOf(initial.subject) }
    var words_text by rememberSaveable { mutableStateOf(free_text) }
    var attachment_on by rememberSaveable { mutableStateOf(initial.attachment) }
    var unread_on by rememberSaveable { mutableStateOf(initial.unread) }
    var starred_on by rememberSaveable { mutableStateOf(initial.starred) }
    var preset by rememberSaveable { mutableStateOf(initial.preset) }
    var scope by rememberSaveable { mutableStateOf(initial.scope) }
    var cleared by rememberSaveable { mutableStateOf(false) }
    var date_menu_open by remember { mutableStateOf(false) }
    var scope_menu_open by remember { mutableStateOf(false) }

    val preset_labels = listOf(
        DatePreset.ANY to R.string.chip_any_time,
        DatePreset.WEEK to R.string.chip_older_than_week,
        DatePreset.MONTH to R.string.chip_older_than_month,
        DatePreset.SIX_MONTHS to R.string.chip_older_than_six_months,
        DatePreset.YEAR to R.string.chip_older_than_year,
    )
    val scope_labels = mapOf(
        "" to R.string.folder_all_mail,
        "inbox" to R.string.folder_inbox,
        "sent" to R.string.folder_sent,
        "drafts" to R.string.folder_drafts,
        "archive" to R.string.folder_archive,
        "spam" to R.string.folder_spam,
        "trash" to R.string.folder_trash,
        "anywhere" to R.string.search_scope_anywhere,
    )
    val custom_label = date_filter_label(operators)
    val custom_available = initial.preset == DatePreset.CUSTOM && !cleared
    val preset_label = preset_labels.firstOrNull { it.first == preset }
        ?.let { stringResource(it.second) }
        ?: custom_label

    val apply_search = {
        val lifted = lift_operators(words_text, submit = true)
        val built = build_advanced_operators(
            operators,
            AdvancedFields(
                from = from_text,
                to = to_text,
                subject = subject_text,
                attachment = attachment_on,
                unread = unread_on,
                starred = starred_on,
                preset = preset,
                scope = scope,
                cleared = cleared,
            ),
        )
        on_apply(merge_lifted(built, lifted.operators), lifted.text.trim())
    }

    val focus_next = { focus_manager.moveFocus(FocusDirection.Down) }

    val reset_all = {
        from_text = ""
        to_text = ""
        subject_text = ""
        words_text = ""
        attachment_on = false
        unread_on = false
        starred_on = false
        preset = DatePreset.ANY
        scope = ""
        cleared = true
    }

    val sheet_shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(
        onDismissRequest = on_dismiss,
        sheetState = sheet_state,
        shape = sheet_shape,
        containerColor = sheet_container_color(colors),
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chip_advanced_search),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text_primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.clear),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.accent_blue,
                    modifier = Modifier
                        .clip(SquircleShape(10.dp))
                        .clickable(onClick = reset_all)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            AsterDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .vertical_scroll_with_indicator()
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                advanced_section(title = stringResource(R.string.contacts)) {
                    advanced_input_row(
                        label = stringResource(R.string.from),
                        value = from_text,
                        placeholder = stringResource(R.string.chip_name_or_email),
                        ime_action = ImeAction.Next,
                        on_change = { from_text = it },
                        on_submit = { focus_next() },
                    )
                    AsterDivider(modifier = Modifier.padding(start = 16.dp))
                    advanced_input_row(
                        label = stringResource(R.string.to),
                        value = to_text,
                        placeholder = stringResource(R.string.email_address),
                        ime_action = ImeAction.Next,
                        on_change = { to_text = it },
                        on_submit = { focus_next() },
                    )
                }

                advanced_section(title = stringResource(R.string.message)) {
                    advanced_input_row(
                        label = stringResource(R.string.subject),
                        value = subject_text,
                        placeholder = "",
                        ime_action = ImeAction.Next,
                        on_change = { subject_text = it },
                        on_submit = { focus_next() },
                    )
                    AsterDivider(modifier = Modifier.padding(start = 16.dp))
                    advanced_input_row(
                        label = stringResource(R.string.advanced_has_words),
                        value = words_text,
                        placeholder = "",
                        ime_action = ImeAction.Search,
                        on_change = { words_text = it },
                        on_submit = { apply_search() },
                    )
                }

                advanced_section(title = stringResource(R.string.date)) {
                    advanced_value_row(
                        label = stringResource(R.string.advanced_date_within),
                        value = preset_label,
                        on_click = { date_menu_open = true },
                    ) {
                        aster_menu(
                            expanded = date_menu_open,
                            on_dismiss = { date_menu_open = false },
                        ) {
                            preset_labels.forEach { (option, label_res) ->
                                aster_menu_item(
                                    label = stringResource(label_res),
                                    selected = preset == option,
                                    on_click = {
                                        preset = option
                                        date_menu_open = false
                                    },
                                )
                            }
                            if (custom_available) {
                                aster_menu_item(
                                    label = custom_label,
                                    selected = preset == DatePreset.CUSTOM,
                                    on_click = {
                                        preset = DatePreset.CUSTOM
                                        date_menu_open = false
                                    },
                                )
                            }
                        }
                    }
                }

                advanced_section(title = stringResource(R.string.filters)) {
                    advanced_value_row(
                        label = stringResource(R.string.advanced_search_scope),
                        value = stringResource(scope_labels[scope] ?: R.string.folder_all_mail),
                        on_click = { scope_menu_open = true },
                    ) {
                        aster_menu(
                            expanded = scope_menu_open,
                            on_dismiss = { scope_menu_open = false },
                        ) {
                            ADVANCED_SCOPE_VALUES.forEach { option ->
                                aster_menu_item(
                                    label = stringResource(scope_labels[option] ?: R.string.folder_all_mail),
                                    selected = scope == option,
                                    on_click = {
                                        scope = option
                                        scope_menu_open = false
                                    },
                                )
                            }
                        }
                    }
                    AsterDivider(modifier = Modifier.padding(start = 16.dp))
                    advanced_toggle_row(
                        label = stringResource(R.string.has_attachment),
                        checked = attachment_on,
                        on_change = { attachment_on = it },
                    )
                    AsterDivider(modifier = Modifier.padding(start = 16.dp))
                    advanced_toggle_row(
                        label = stringResource(R.string.chip_is_unread),
                        checked = unread_on,
                        on_change = { unread_on = it },
                    )
                    AsterDivider(modifier = Modifier.padding(start = 16.dp))
                    advanced_toggle_row(
                        label = stringResource(R.string.starred),
                        checked = starred_on,
                        on_change = { starred_on = it },
                    )
                }
            }

            AsterDivider()

            AsterActionRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 18.dp),
                spacing = 12.dp,
            ) {
                AsterSecondaryButton(
                    label = stringResource(R.string.cancel),
                    onClick = on_dismiss,
                    modifier = Modifier.weight(1f),
                )
                AsterButton(
                    label = stringResource(R.string.search),
                    onClick = { apply_search() },
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}
