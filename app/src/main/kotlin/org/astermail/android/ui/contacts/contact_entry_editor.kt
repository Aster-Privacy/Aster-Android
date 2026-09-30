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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Plus
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item

private const val MAX_ENTRY_ROWS = 20

enum class ContactEntryKind(val types: List<String>, val default_type: String) {
    EMAIL(
        listOf(ContactEntry.TYPE_HOME, ContactEntry.TYPE_PERSONAL, ContactEntry.TYPE_WORK, ContactEntry.TYPE_OTHER),
        ContactEntry.TYPE_HOME,
    ),
    PHONE(
        listOf(
            ContactEntry.TYPE_MOBILE,
            ContactEntry.TYPE_HOME,
            ContactEntry.TYPE_PERSONAL,
            ContactEntry.TYPE_WORK,
            ContactEntry.TYPE_FAX,
            ContactEntry.TYPE_PAGER,
            ContactEntry.TYPE_OTHER,
        ),
        ContactEntry.TYPE_MOBILE,
    ),
    ADDRESS(
        listOf(ContactEntry.TYPE_HOME, ContactEntry.TYPE_WORK, ContactEntry.TYPE_OTHER),
        ContactEntry.TYPE_HOME,
    ),
}

data class EditableEntry(
    val value: String = "",
    val type: String,
    val label: String = "",
    val custom: Boolean = false,
) {
    fun to_entry(): ContactEntry = ContactEntry(value, type, if (custom) label else "")

    companion object {
        fun of(entry: ContactEntry) = EditableEntry(
            value = entry.value,
            type = entry.type,
            label = entry.label,
            custom = entry.type == ContactEntry.TYPE_OTHER && entry.label.isNotBlank(),
        )
    }
}

data class EditablePostal(
    val street: String = "",
    val city: String = "",
    val region: String = "",
    val postal_code: String = "",
    val country: String = "",
    val type: String,
    val label: String = "",
    val custom: Boolean = false,
) {
    fun to_postal(): ContactPostal =
        ContactPostal(street, city, region, postal_code, country, type, if (custom) label else "")

    companion object {
        fun of(postal: ContactPostal) = EditablePostal(
            street = postal.street,
            city = postal.city,
            region = postal.region,
            postal_code = postal.postal_code,
            country = postal.country,
            type = postal.type,
            label = postal.label,
            custom = postal.type == ContactEntry.TYPE_OTHER && postal.label.isNotBlank(),
        )
    }
}

val editable_entry_list_saver: Saver<List<EditableEntry>, List<String>> = Saver(
    save = { list -> list.flatMap { listOf(it.value, it.type, it.label, it.custom.toString()) } },
    restore = { flat ->
        flat.chunked(4).filter { it.size == 4 }.map { EditableEntry(it[0], it[1], it[2], it[3] == "true") }
    },
)

val editable_postal_list_saver: Saver<List<EditablePostal>, List<String>> = Saver(
    save = { list ->
        list.flatMap {
            listOf(it.street, it.city, it.region, it.postal_code, it.country, it.type, it.label, it.custom.toString())
        }
    },
    restore = { flat ->
        flat.chunked(8).filter { it.size == 8 }.map {
            EditablePostal(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7] == "true")
        }
    },
)

@Composable
private fun TypePicker(
    kind: ContactEntryKind,
    type: String,
    label: String,
    custom: Boolean,
    on_pick: (type: String, custom: Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    var expanded by remember { mutableStateOf(false) }
    val shown = if (custom) {
        label.trim().ifEmpty { stringResource(R.string.contact_type_custom) }
    } else {
        contact_entry_type_label(type, "")
    }
    val change_type = stringResource(R.string.contact_change_type)
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = change_type) { expanded = true }
                .padding(horizontal = AsterSpacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shown,
                color = colors.accent_blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(14.dp),
            )
        }
        aster_menu(expanded = expanded, on_dismiss = { expanded = false }) {
            val options = if (type in kind.types) kind.types else kind.types + type
            for (option in options) {
                aster_menu_item(
                    label = contact_entry_type_label(option, ""),
                    selected = !custom && option == type,
                    on_click = {
                        expanded = false
                        on_pick(option, false)
                    },
                )
            }
            aster_menu_item(
                label = stringResource(R.string.contact_type_custom),
                selected = custom,
                on_click = {
                    expanded = false
                    on_pick(ContactEntry.TYPE_OTHER, true)
                },
            )
        }
    }
}

@Composable
private fun RowHeader(
    kind: ContactEntryKind,
    type: String,
    label: String,
    custom: Boolean,
    on_pick: (String, Boolean) -> Unit,
    on_remove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TypePicker(kind, type, label, custom, on_pick)
        Spacer(Modifier.weight(1f))
        AsterIconButton(
            icon = TablerIcons.X,
            content_description = stringResource(R.string.remove),
            onClick = on_remove,
        )
    }
}

@Composable
private fun CustomLabelField(label: String, on_change: (String) -> Unit) {
    AsterTextField(
        value = label,
        onValueChange = { on_change(it.take(64)) },
        label = stringResource(R.string.contact_custom_label),
        keyboard_options = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
private fun AddRowButton(label: String, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = on_click)
            .padding(vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Plus,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(text = label, color = colors.accent_blue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ContactEntryRows(
    kind: ContactEntryKind,
    entries: List<EditableEntry>,
    on_change: (List<EditableEntry>) -> Unit,
    add_label: String,
    field_label: String,
    keyboard_type: KeyboardType,
    error_for: (String) -> String? = { null },
) {
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        entries.forEachIndexed { index, entry ->
            fun update(next: EditableEntry) = on_change(entries.toMutableList().also { it[index] = next })
            Column {
                RowHeader(
                    kind = kind,
                    type = entry.type,
                    label = entry.label,
                    custom = entry.custom,
                    on_pick = { type, custom -> update(entry.copy(type = type, custom = custom)) },
                    on_remove = { on_change(entries.filterIndexed { i, _ -> i != index }) },
                )
                AsterTextField(
                    value = entry.value,
                    onValueChange = { update(entry.copy(value = it)) },
                    label = field_label,
                    error_text = error_for(entry.value),
                    keyboard_options = KeyboardOptions(keyboardType = keyboard_type, imeAction = ImeAction.Next),
                )
                if (entry.custom) {
                    Spacer(Modifier.height(AsterSpacing.xs))
                    CustomLabelField(entry.label) { update(entry.copy(label = it)) }
                }
            }
        }
        if (entries.size < MAX_ENTRY_ROWS) {
            AddRowButton(add_label) { on_change(entries + EditableEntry(type = kind.default_type)) }
        }
    }
}

@Composable
fun ContactPostalRows(
    postals: List<EditablePostal>,
    on_change: (List<EditablePostal>) -> Unit,
) {
    val kind = ContactEntryKind.ADDRESS
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        postals.forEachIndexed { index, postal ->
            fun update(next: EditablePostal) = on_change(postals.toMutableList().also { it[index] = next })
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
                RowHeader(
                    kind = kind,
                    type = postal.type,
                    label = postal.label,
                    custom = postal.custom,
                    on_pick = { type, custom -> update(postal.copy(type = type, custom = custom)) },
                    on_remove = { on_change(postals.filterIndexed { i, _ -> i != index }) },
                )
                if (postal.custom) CustomLabelField(postal.label) { update(postal.copy(label = it)) }
                PostalField(stringResource(R.string.street), postal.street, KeyboardCapitalization.Words) {
                    update(postal.copy(street = it))
                }
                PostalField(stringResource(R.string.city), postal.city, KeyboardCapitalization.Words) {
                    update(postal.copy(city = it))
                }
                PostalField(stringResource(R.string.region), postal.region, KeyboardCapitalization.Words) {
                    update(postal.copy(region = it))
                }
                PostalField(stringResource(R.string.postal_code), postal.postal_code, KeyboardCapitalization.Characters) {
                    update(postal.copy(postal_code = it))
                }
                PostalField(stringResource(R.string.country), postal.country, KeyboardCapitalization.Words) {
                    update(postal.copy(country = it))
                }
            }
        }
        if (postals.size < MAX_ENTRY_ROWS) {
            val used = postals.map { it.type }.toSet()
            val next_type = listOf(ContactEntry.TYPE_HOME, ContactEntry.TYPE_WORK).firstOrNull { it !in used }
                ?: ContactEntry.TYPE_OTHER
            AddRowButton(stringResource(R.string.contact_add_address)) {
                on_change(postals + EditablePostal(type = next_type))
            }
        }
    }
}

@Composable
private fun PostalField(
    label: String,
    value: String,
    capitalization: KeyboardCapitalization,
    on_change: (String) -> Unit,
) {
    AsterTextField(
        value = value,
        onValueChange = on_change,
        label = label,
        keyboard_options = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Next),
    )
}
