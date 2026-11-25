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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.contacts.ContactEvent
import org.astermail.android.contacts.ContactEventKind
import org.astermail.android.contacts.MAX_CONTACT_EVENTS
import org.astermail.android.contacts.MAX_EVENT_LABEL
import org.astermail.android.contacts.MIN_EVENT_YEAR
import org.astermail.android.contacts.cap_code_points
import org.astermail.android.contacts.current_event_year
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item

data class EditableEvent(
    val kind: ContactEventKind = ContactEventKind.ANNIVERSARY,
    val date: String = "",
    val label: String = "",
) {
    fun to_event(): ContactEvent =
        ContactEvent(kind, date.trim(), if (kind == ContactEventKind.CUSTOM) label.trim() else "")

    companion object {
        fun of(event: ContactEvent) = EditableEvent(event.kind, event.date, event.label)
    }
}

val editable_event_list_saver: Saver<List<EditableEvent>, List<String>> = Saver(
    save = { list -> list.flatMap { listOf(it.kind.id, it.date, it.label) } },
    restore = { flat ->
        flat.chunked(3).filter { it.size == 3 }.map {
            EditableEvent(ContactEventKind.from_id(it[0]) ?: ContactEventKind.CUSTOM, it[1], it[2])
        }
    },
)

fun event_kinds_for_row(events: List<EditableEvent>, index: Int): List<ContactEventKind> {
    val birthday_taken = events.withIndex().any { (i, e) -> i != index && e.kind == ContactEventKind.BIRTHDAY }
    return ContactEventKind.entries.filter { it != ContactEventKind.BIRTHDAY || !birthday_taken }
}

fun next_event_kind(events: List<EditableEvent>): ContactEventKind =
    if (events.none { it.kind == ContactEventKind.BIRTHDAY }) ContactEventKind.BIRTHDAY else ContactEventKind.ANNIVERSARY

@Composable
fun event_kind_names(): Map<ContactEventKind, String> = mapOf(
    ContactEventKind.BIRTHDAY to stringResource(R.string.birthday),
    ContactEventKind.ANNIVERSARY to stringResource(R.string.contact_event_anniversary),
    ContactEventKind.CUSTOM to stringResource(R.string.contact_type_custom),
)

fun event_display_name(event: ContactEvent, names: Map<ContactEventKind, String>): String =
    if (event.kind == ContactEventKind.CUSTOM) {
        event.label.trim().ifEmpty { names[ContactEventKind.CUSTOM].orEmpty() }
    } else {
        names[event.kind].orEmpty()
    }

@Composable
private fun EventKindPicker(
    event: EditableEvent,
    kinds: List<ContactEventKind>,
    names: Map<ContactEventKind, String>,
    on_pick: (ContactEventKind) -> Unit,
) {
    val colors = AsterMaterial.colors
    var expanded by remember { mutableStateOf(false) }
    val shown = names[event.kind].orEmpty()
    val picker_label = stringResource(R.string.contact_event_type)
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = picker_label) { expanded = true }
                .semantics { contentDescription = "$picker_label: $shown" }
                .padding(horizontal = AsterSpacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shown,
                color = colors.accent_blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
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
            for (kind in kinds) {
                aster_menu_item(
                    label = names[kind].orEmpty(),
                    selected = kind == event.kind,
                    on_click = {
                        expanded = false
                        on_pick(kind)
                    },
                )
            }
        }
    }
}

@Composable
fun ContactEventRows(
    events: List<EditableEvent>,
    on_change: (List<EditableEvent>) -> Unit,
) {
    val names = event_kind_names()
    val year_range = remember { MIN_EVENT_YEAR..(current_event_year() + 1) }
    Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
        events.forEachIndexed { index, event ->
            fun update(next: EditableEvent) = on_change(events.toMutableList().also { it[index] = next })
            Column(verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        EventKindPicker(event, event_kinds_for_row(events, index), names) { update(event.copy(kind = it)) }
                    }
                    AsterIconButton(
                        icon = TablerIcons.X,
                        content_description = stringResource(R.string.contact_remove_event),
                        onClick = { on_change(events.filterIndexed { i, _ -> i != index }) },
                    )
                }
                if (event.kind == ContactEventKind.CUSTOM) {
                    AsterTextField(
                        value = event.label,
                        onValueChange = { update(event.copy(label = cap_code_points(it, MAX_EVENT_LABEL))) },
                        label = stringResource(R.string.contact_entry_label),
                        keyboard_options = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    )
                }
                ContactBirthdayField(
                    label = stringResource(R.string.date),
                    value = event.date,
                    on_change = { update(event.copy(date = it)) },
                    year_range = year_range,
                    show_label = false,
                )
            }
        }
        if (events.size < MAX_CONTACT_EVENTS) {
            AddRowButton(stringResource(R.string.contact_add_event)) {
                on_change(events + EditableEvent(kind = next_event_kind(events)))
            }
        }
    }
}
