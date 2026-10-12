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

import compose.icons.TablerIcons
import compose.icons.tablericons.Adjustments
import compose.icons.tablericons.ChevronDown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu
import org.astermail.android.ui.common.sheet_container_color
import org.astermail.android.ui.common.picker_theme_res
import org.astermail.android.ui.mail.SenderAvatar
import org.astermail.android.design.acrylic
import org.astermail.android.design.local_acrylic
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val chip_height = 34.dp

@Composable
internal fun quick_chip(
    label: String,
    active: Boolean,
    show_caret: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val text_color = if (active) colors.on_accent else colors.text_primary
    val shape = CircleShape

    Row(
        modifier = Modifier
            .height(chip_height)
            .clip(shape)
            .then(
                if (active) {
                    Modifier.background(colors.accent_blue)
                } else {
                    Modifier.acrylic(colors, shape, field_surface_color(colors))
                },
            )
            .clickable(onClick = on_click)
            .padding(start = 14.dp, end = if (show_caret) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = text_color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (show_caret) {
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = text_color,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun person_picker_sheet(
    title: String,
    people: List<ChipPerson>,
    current: String?,
    placeholder: String,
    on_apply: (String) -> Unit,
    on_clear: () -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var typed by remember { mutableStateOf(current ?: "") }
    val matches = remember(typed, people) {
        val needle = fold_text(typed.trim())
        if (needle.isEmpty()) people
        else people.filter {
            fold_text(it.email).contains(needle) || fold_text(it.name).contains(needle)
        }
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
                .fillMaxHeight(0.8f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text_primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!current.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.clear),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent_blue,
                        modifier = Modifier
                            .clip(SquircleShape(8.dp))
                            .clickable { on_clear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            AsterTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = placeholder,
                singleLine = true,
                keyboard_actions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { on_apply(typed) },
                ),
                keyboard_options = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
            )

            if (matches.isEmpty()) {
                Text(
                    text = stringResource(R.string.chip_no_people),
                    fontSize = 14.sp,
                    color = colors.text_secondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(matches, key = { it.email }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleShape(14.dp))
                                .clickable { on_apply(person.email) }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SenderAvatar(email = person.email, name = person.name, size = 40.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = person.name.ifEmpty { person.email },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.text_primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (person.name.isNotEmpty()) {
                                    Text(
                                        text = person.email,
                                        fontSize = 13.sp,
                                        color = colors.text_secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun format_search_date(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)

@Composable
internal fun date_value_text(value: String): String {
    val day = parse_iso_date(value)
    val days_back = parse_relative_days(value)

    return when {
        day != null -> format_search_date(day)
        days_back != null -> pluralStringResource(R.plurals.search_days_ago, days_back, days_back)
        else -> value
    }
}

@Composable
internal fun operator_value_text(op: SearchOperator): String {
    val label_res = operator_value_label(op)

    return when {
        label_res != null -> stringResource(label_res)
        op.key == "before" || op.key == "after" || op.key == "date" -> date_value_text(op.value)
        else -> op.value
    }
}

@Composable
private fun date_bound_text(key_label: String, value: String?): String? {
    if (value == null) return null

    return stringResource(R.string.search_chip_format, key_label, date_value_text(value))
}

@Composable
internal fun date_filter_label(operators: List<SearchOperator>): String {
    val preset_res = when (detect_date_preset(operators)) {
        DatePreset.ANY -> R.string.chip_any_time
        DatePreset.WEEK -> R.string.chip_older_than_week
        DatePreset.MONTH -> R.string.chip_older_than_month
        DatePreset.SIX_MONTHS -> R.string.chip_older_than_six_months
        DatePreset.YEAR -> R.string.chip_older_than_year
        DatePreset.CUSTOM -> null
    }
    val range = custom_date_range(operators)
    val after_text = date_bound_text(
        stringResource(R.string.search_chip_after),
        operator_value(operators, "after"),
    )
    val before_text = date_bound_text(
        stringResource(R.string.search_chip_before),
        operator_value(operators, "before"),
    )

    return when {
        preset_res != null -> stringResource(preset_res)
        range != null && range.first == range.second -> format_search_date(range.first)
        range != null -> stringResource(
            R.string.search_date_range,
            format_search_date(range.first),
            format_search_date(range.second),
        )
        else -> listOfNotNull(after_text, before_text)
            .joinToString(", ")
            .ifEmpty { stringResource(R.string.chip_custom_range) }
    }
}

@Composable
internal fun search_chip_row(
    operators: List<SearchOperator>,
    people: List<ChipPerson>,
    recipient_people: List<ChipPerson>,
    on_operators_change: (List<SearchOperator>) -> Unit,
    on_advanced_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val theme_res = picker_theme_res()
    var person_sheet by remember { mutableStateOf<String?>(null) }
    var date_menu_open by remember { mutableStateOf(false) }

    val from_value = operator_value(operators, "from")
    val to_value = operator_value(operators, "to")
    val attachment_active = has_attachment_active(operators)
    val unread_active = operators.any { !it.negated && it.key == "is" && it.value == "unread" }
    val date_preset = detect_date_preset(operators)

    val date_label = date_filter_label(operators)
    val start_title = stringResource(R.string.vacation_start_date)
    val end_title = stringResource(R.string.vacation_end_date)
    val from_label = stringResource(R.string.from)
    val to_label = stringResource(R.string.to)
    val attachment_label = stringResource(R.string.has_attachment)
    val unread_label = stringResource(R.string.chip_is_unread)

    val launch_custom_range = {
        val cal = org.astermail.android.ui.mail.AsterTimePreferences.account_calendar()
        val today = LocalDate.of(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
        val current_range = custom_date_range(operators)
        val start_seed = current_range?.first ?: today
        android.app.DatePickerDialog(
            context,
            theme_res,
            { _, start_year, start_month, start_day ->
                val first_day = LocalDate.of(start_year, start_month + 1, start_day)
                val end_seed = listOfNotNull(current_range?.second, today)
                    .firstOrNull { !it.isBefore(first_day) }
                    ?: first_day
                val earliest_end = java.util.Calendar.getInstance().apply {
                    clear()
                    set(start_year, start_month, start_day)
                }
                android.app.DatePickerDialog(
                    context,
                    theme_res,
                    { _, end_year, end_month, end_day ->
                        on_operators_change(
                            apply_custom_range(
                                operators,
                                first_day,
                                LocalDate.of(end_year, end_month + 1, end_day),
                            ),
                        )
                    },
                    end_seed.year,
                    end_seed.monthValue - 1,
                    end_seed.dayOfMonth,
                ).apply {
                    setTitle(end_title)
                    datePicker.minDate = earliest_end.timeInMillis
                }.show()
            },
            start_seed.year,
            start_seed.monthValue - 1,
            start_seed.dayOfMonth,
        ).apply { setTitle(start_title) }.show()
    }

    val chips = mutableListOf<Pair<Boolean, @Composable () -> Unit>>()

    chips.add(
        (from_value != null) to {
            quick_chip(
                label = from_value ?: from_label,
                active = from_value != null,
                show_caret = true,
                on_click = { person_sheet = "from" },
            )
        },
    )
    chips.add(
        (date_preset != DatePreset.ANY) to {
            Box {
                quick_chip(
                    label = date_label,
                    active = date_preset != DatePreset.ANY,
                    show_caret = true,
                    on_click = { date_menu_open = true },
                )
                aster_menu(
                    expanded = date_menu_open,
                    on_dismiss = { date_menu_open = false },
                ) {
                    val presets = listOf(
                        DatePreset.ANY to R.string.chip_any_time,
                        DatePreset.WEEK to R.string.chip_older_than_week,
                        DatePreset.MONTH to R.string.chip_older_than_month,
                        DatePreset.SIX_MONTHS to R.string.chip_older_than_six_months,
                        DatePreset.YEAR to R.string.chip_older_than_year,
                    )
                    presets.forEach { (preset, label_res) ->
                        aster_menu_item(
                            label = stringResource(label_res),
                            selected = date_preset == preset,
                            on_click = {
                                date_menu_open = false
                                on_operators_change(apply_date_preset(operators, preset))
                            },
                        )
                    }
                    aster_menu_item(
                        label = stringResource(R.string.chip_custom_range),
                        selected = date_preset == DatePreset.CUSTOM,
                        on_click = {
                            date_menu_open = false
                            launch_custom_range()
                        },
                    )
                }
            }
        },
    )
    chips.add(
        attachment_active to {
            quick_chip(
                label = attachment_label,
                active = attachment_active,
                show_caret = false,
                on_click = { on_operators_change(toggle_attachment(operators)) },
            )
        },
    )
    chips.add(
        (to_value != null) to {
            quick_chip(
                label = to_value ?: to_label,
                active = to_value != null,
                show_caret = true,
                on_click = { person_sheet = "to" },
            )
        },
    )
    chips.add(
        unread_active to {
            quick_chip(
                label = unread_label,
                active = unread_active,
                show_caret = false,
                on_click = { on_operators_change(toggle_unread(operators)) },
            )
        },
    )

    val ordered = chips.filter { it.first } + chips.filter { !it.first }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ordered.forEach { it.second() }
        Row(
            modifier = Modifier
                .height(chip_height)
                .clip(CircleShape)
                .acrylic(colors, CircleShape, field_surface_color(colors))
                .clickable(onClick = on_advanced_click)
                .padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = TablerIcons.Adjustments,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.chip_advanced_search),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accent_blue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    val open_sheet = person_sheet

    if (open_sheet != null) {
        person_picker_sheet(
            title = if (open_sheet == "from") from_label else to_label,
            people = if (open_sheet == "from") people else recipient_people,
            current = if (open_sheet == "from") from_value else to_value,
            placeholder = stringResource(
                if (open_sheet == "from") R.string.chip_name_or_email else R.string.email_address,
            ),
            on_apply = { value ->
                person_sheet = null
                on_operators_change(set_person(operators, open_sheet, value))
            },
            on_clear = {
                person_sheet = null
                on_operators_change(without_key(operators, open_sheet))
            },
            on_dismiss = { person_sheet = null },
        )
    }
}
