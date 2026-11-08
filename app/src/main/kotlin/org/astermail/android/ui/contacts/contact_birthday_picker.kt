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

package org.astermail.android.ui.contacts

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Month
import java.time.format.TextStyle
import org.astermail.android.R
import org.astermail.android.contacts.ContactDates
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterTextField

@Composable
internal fun contact_date_label(value: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(value, locale) {
        ContactDates.format(value, locale) { skeleton -> DateFormat.getBestDateTimePattern(locale, skeleton) }
    }
}

@Composable
internal fun ContactBirthdayField(
    label: String,
    value: String,
    on_change: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    var show_picker by rememberSaveable { mutableStateOf(false) }
    val display = contact_date_label(value)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(field_surface_color(colors), AsterShapes.control)
                .clip(AsterShapes.control)
                .clickable(role = Role.Button, onClickLabel = label) { show_picker = true }
                .padding(horizontal = AsterSpacing.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = display.ifBlank { label },
                color = if (display.isBlank()) colors.text_muted else colors.text_primary,
                fontSize = 16.sp,
            )
        }
    }
    if (show_picker) {
        ContactBirthdayDialog(
            initial = value,
            title = label,
            on_dismiss = { show_picker = false },
            on_save = {
                on_change(it)
                show_picker = false
            },
        )
    }
}

@Composable
private fun ContactBirthdayDialog(
    initial: String,
    title: String,
    on_dismiss: () -> Unit,
    on_save: (String) -> Unit,
) {
    val parts = remember(initial) { ContactDates.parse(initial) }
    var month by rememberSaveable { mutableIntStateOf(parts?.month ?: 0) }
    var day by rememberSaveable { mutableIntStateOf(parts?.day ?: 0) }
    var year_text by rememberSaveable { mutableStateOf(parts?.year?.toString().orEmpty()) }
    val year_result = ContactDates.picker_year(year_text)
    val year = year_result.getOrNull()
    val max_day = if (month == 0) 31 else ContactDates.days_in_month(year, month)
    val clamped_day = if (day > max_day) 0 else day
    val result = if (month == 0 || clamped_day == 0) null else ContactDates.from_picker(month, clamped_day, year_text)
    val locale = LocalConfiguration.current.locales[0]
    val month_names = remember(locale) {
        Month.values().map { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale) }
    }

    AsterDialog(
        on_dismiss = on_dismiss,
        title = title,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                PickerHeading(stringResource(R.string.contact_birthday_month))
                ChoiceGrid(
                    labels = month_names,
                    columns = 4,
                    selected_index = month - 1,
                    enabled_count = 12,
                    on_select = { month = it + 1 },
                )
                PickerHeading(stringResource(R.string.contact_birthday_day))
                ChoiceGrid(
                    labels = (1..31).map { it.toString() },
                    columns = 7,
                    selected_index = clamped_day - 1,
                    enabled_count = max_day,
                    on_select = { day = it + 1 },
                )
                AsterTextField(
                    value = year_text,
                    onValueChange = { text -> year_text = text.filter { it in '0'..'9' }.take(4) },
                    label = stringResource(R.string.contact_birthday_year_optional),
                    helper_text = stringResource(R.string.contact_birthday_year_hint),
                    error_text = if (year_result.isFailure) stringResource(R.string.contact_birthday_year_invalid) else null,
                    keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
        },
        footer = {
            if (initial.isNotBlank()) {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.clear),
                    onClick = { on_save("") },
                    modifier = Modifier.weight(1f),
                )
            }
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
                modifier = Modifier.weight(1f),
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.save),
                onClick = { result?.let(on_save) },
                modifier = Modifier.weight(1f),
                enabled = result != null,
            )
        },
    )
}

@Composable
private fun PickerHeading(text: String) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ChoiceGrid(
    labels: List<String>,
    columns: Int,
    selected_index: Int,
    enabled_count: Int,
    on_select: (Int) -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(AsterRadius.item)
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        labels.indices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
                row.forEach { index ->
                    val is_selected = index == selected_index
                    val is_enabled = index < enabled_count
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(if (is_selected) colors.accent_blue else colors.bg_secondary, shape)
                            .border(1.dp, if (is_selected) colors.accent_blue else colors.border_primary, shape)
                            .semantics { selected = is_selected }
                            .clickable(enabled = is_enabled, role = Role.RadioButton) { on_select(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = labels[index],
                            color = when {
                                is_selected -> colors.on_accent
                                is_enabled -> colors.text_primary
                                else -> colors.text_muted
                            },
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
