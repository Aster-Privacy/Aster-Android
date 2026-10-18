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
import compose.icons.tablericons.X

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val chip_height = 34.dp

internal val DATE_PRESET_LABELS: List<Pair<DatePreset, Int>> = listOf(
    DatePreset.ANY to R.string.chip_any_time,
    DatePreset.LAST_7_DAYS to R.string.chip_last_7_days,
    DatePreset.LAST_30_DAYS to R.string.chip_last_30_days,
    DatePreset.LAST_6_MONTHS to R.string.chip_last_6_months,
    DatePreset.LAST_YEAR to R.string.chip_last_year,
    DatePreset.OLDER_THAN_YEAR to R.string.chip_older_than_year,
    DatePreset.CUSTOM to R.string.chip_custom_range,
)

internal val WITHIN_DAYS_LABELS: List<Pair<Int, Int>> = listOf(
    0 to R.string.chip_any_time,
    7 to R.string.chip_last_7_days,
    30 to R.string.chip_last_30_days,
    183 to R.string.chip_last_6_months,
    365 to R.string.chip_last_year,
)

internal fun scope_label_res(scope: String): Int = when (normalize_scope_value(scope)) {
    "anywhere" -> R.string.chip_everywhere
    "inbox" -> R.string.folder_inbox
    "starred" -> R.string.folder_starred
    "sent" -> R.string.folder_sent
    "drafts" -> R.string.folder_drafts
    "archive" -> R.string.folder_archive
    "spam" -> R.string.folder_spam
    "trash" -> R.string.folder_trash
    else -> R.string.folder_all_mail
}

internal fun attachment_type_label_res(type: String): Int = when (type) {
    "image" -> R.string.chip_attachment_image
    "document" -> R.string.chip_attachment_document
    "pdf" -> R.string.chip_attachment_pdf
    else -> R.string.chip_attachment_video
}

internal fun account_today(): LocalDate {
    val cal = org.astermail.android.ui.mail.AsterTimePreferences.account_calendar()
    return LocalDate.of(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH),
    )
}

@Composable
internal fun remember_date_range_picker(
    on_range: (LocalDate, LocalDate) -> Unit,
): (Pair<LocalDate, LocalDate>?) -> Unit {
    val context = LocalContext.current
    val theme_res = picker_theme_res()
    val start_title = stringResource(R.string.from_date)
    val end_title = stringResource(R.string.to_date)
    val latest_on_range by rememberUpdatedState(on_range)

    return remember(context, theme_res, start_title, end_title) {
        { current_range: Pair<LocalDate, LocalDate>? ->
            val today = account_today()
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
                            latest_on_range(first_day, LocalDate.of(end_year, end_month + 1, end_day))
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
    }
}

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

@Composable
private fun outline_chip(label: String, icon: ImageVector?, on_click: () -> Unit) {
    val colors = AsterMaterial.colors

    Row(
        modifier = Modifier
            .height(chip_height)
            .clip(CircleShape)
            .acrylic(colors, CircleShape, field_surface_color(colors))
            .clickable(onClick = on_click)
            .padding(start = if (icon != null) 12.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.accent_blue,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun operator_chip(op: SearchOperator, on_remove: () -> Unit) {
    val colors = AsterMaterial.colors
    val label_key = when (op.key) {
        "from" -> stringResource(R.string.search_chip_from)
        "to" -> stringResource(R.string.search_chip_to)
        "contact" -> stringResource(R.string.search_chip_contact)
        "subject" -> stringResource(R.string.search_chip_subject)
        "has" -> stringResource(R.string.search_chip_has)
        "is" -> stringResource(R.string.search_chip_is)
        "in" -> stringResource(R.string.search_chip_in)
        "before" -> stringResource(R.string.search_chip_before)
        "after" -> stringResource(R.string.search_chip_after)
        "date" -> stringResource(R.string.search_chip_date)
        "label" -> stringResource(R.string.search_chip_label)
        "filename" -> stringResource(R.string.search_chip_filename)
        TEXT_OPERATOR_KEY -> ""
        else -> op.key.replaceFirstChar { it.uppercase() }
    }
    val value = operator_value_text(op)
    val chip_label = when {
        op.key == TEXT_OPERATOR_KEY -> stringResource(R.string.search_chip_negated_term, value)
        op.negated -> stringResource(
            R.string.search_chip_format_negated,
            stringResource(R.string.search_chip_not_prefix),
            label_key,
            value,
        )
        else -> stringResource(R.string.search_chip_format, label_key, value)
    }
    Row(
        modifier = Modifier
            .height(chip_height)
            .clip(CircleShape)
            .background(colors.accent_blue.copy(alpha = 0.14f))
            .border(1.dp, colors.accent_blue.copy(alpha = 0.35f), CircleShape)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chip_label,
            color = colors.accent_blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = on_remove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TablerIcons.X,
                contentDescription = stringResource(R.string.remove),
                tint = colors.accent_blue,
                modifier = Modifier.size(14.dp),
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
    val preset = detect_date_preset(operators)
    val preset_res = DATE_PRESET_LABELS
        .firstOrNull { it.first == preset && preset != DatePreset.CUSTOM }
        ?.second
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
internal fun scope_menu_items(
    current: String?,
    on_select: (String) -> Unit,
) {
    val colors = AsterMaterial.colors

    SEARCH_SCOPES.forEach { scope ->
        aster_menu_item(
            label = stringResource(scope_label_res(scope)),
            selected = current == scope,
            on_click = { on_select(scope) },
        )
        if (scope == "anywhere") {
            Text(
                text = stringResource(R.string.chip_everywhere_hint),
                fontSize = 12.sp,
                color = colors.text_muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
internal fun search_chip_row(
    operators: List<SearchOperator>,
    people: List<ChipPerson>,
    recipient_people: List<ChipPerson>,
    folder: String?,
    free_text: String,
    on_operators_change: (List<SearchOperator>) -> Unit,
    on_advanced_click: () -> Unit,
) {
    var person_sheet by remember { mutableStateOf<String?>(null) }
    var date_menu_open by remember { mutableStateOf(false) }
    var scope_menu_open by remember { mutableStateOf(false) }

    val from_value = operator_value(operators, "from")
    val to_value = operator_value(operators, "to")
    val attachment_active = attachment_filter_active(operators)
    val attachment_types = active_attachment_types(operators)
    val unread_active = has_operator(operators, "is", "unread")
    val date_preset = detect_date_preset(operators)
    val scope_state = query_search_scope(operators, folder)
    val scope_active = scope_chip_active(operators, folder)
    val extra_operators = extra_chip_operators(operators)
    val has_free_text = free_text.isNotBlank()

    val date_label = date_filter_label(operators)
    val from_label = stringResource(R.string.from)
    val to_label = stringResource(R.string.to)
    val scope_label = scope_state?.let { stringResource(scope_label_res(it.scope)) }
        ?: stringResource(R.string.advanced_search_scope)

    val launch_custom_range = remember_date_range_picker { first_day, last_day ->
        on_operators_change(apply_custom_range(operators, first_day, last_day))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            quick_chip(
                label = scope_label,
                active = scope_active,
                show_caret = true,
                on_click = { scope_menu_open = true },
            )
            aster_menu(
                expanded = scope_menu_open,
                on_dismiss = { scope_menu_open = false },
            ) {
                scope_menu_items(
                    current = scope_state?.scope,
                    on_select = { scope ->
                        scope_menu_open = false
                        on_operators_change(set_search_scope(operators, folder, scope, has_free_text))
                    },
                )
            }
        }
        quick_chip(
            label = from_value ?: from_label,
            active = from_value != null,
            show_caret = true,
            on_click = { person_sheet = "from" },
        )
        quick_chip(
            label = to_value ?: to_label,
            active = to_value != null,
            show_caret = true,
            on_click = { person_sheet = "to" },
        )
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
                DATE_PRESET_LABELS.forEach { (preset, label_res) ->
                    aster_menu_item(
                        label = stringResource(label_res),
                        selected = date_preset == preset,
                        on_click = {
                            date_menu_open = false
                            if (preset == DatePreset.CUSTOM) {
                                launch_custom_range(custom_date_range(operators))
                            } else {
                                on_operators_change(apply_date_preset(operators, preset))
                            }
                        },
                    )
                }
            }
        }
        quick_chip(
            label = stringResource(R.string.chip_has_attachment),
            active = attachment_active,
            show_caret = false,
            on_click = { on_operators_change(toggle_attachment(operators)) },
        )
        if (attachment_active) {
            CHIP_ATTACHMENT_TYPES.forEach { type ->
                quick_chip(
                    label = stringResource(attachment_type_label_res(type)),
                    active = attachment_types.contains(type),
                    show_caret = false,
                    on_click = { on_operators_change(toggle_attachment_type(operators, type)) },
                )
            }
        }
        quick_chip(
            label = stringResource(R.string.chip_is_unread),
            active = unread_active,
            show_caret = false,
            on_click = { on_operators_change(toggle_unread(operators)) },
        )
        extra_operators.forEach { op ->
            operator_chip(op) {
                on_operators_change(operators.filterNot { it === op })
            }
        }
        outline_chip(
            label = stringResource(R.string.chip_advanced_search),
            icon = TablerIcons.Adjustments,
            on_click = on_advanced_click,
        )
        if (has_active_filters(operators, folder)) {
            outline_chip(
                label = stringResource(R.string.chip_clear_filters),
                icon = null,
                on_click = { on_operators_change(clear_search_filters(folder, has_free_text)) },
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
