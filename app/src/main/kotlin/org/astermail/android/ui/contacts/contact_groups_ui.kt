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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Minus
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import compose.icons.tablericons.Users
import org.astermail.android.R
import org.astermail.android.contacts.ContactGroup
import org.astermail.android.contacts.DEFAULT_CONTACT_GROUP_COLOR
import org.astermail.android.contacts.MAX_CONTACT_GROUPS
import org.astermail.android.contacts.MAX_CONTACT_GROUP_NAME_LENGTH
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.parse_hex_color_safe
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterTextField

internal val contact_group_color_presets = listOf(
    "#ef4444",
    "#f97316",
    "#f59e0b",
    "#eab308",
    "#84cc16",
    "#22c55e",
    "#10b981",
    "#14b8a6",
    "#06b6d4",
    "#0ea5e9",
    "#3b82f6",
    "#6366f1",
    "#8b5cf6",
    "#a855f7",
    "#d946ef",
    "#ec4899",
    "#f43f5e",
)

internal fun contact_group_color(hex: String?): Color =
    parse_hex_color_safe(hex) ?: parse_hex_color_safe(DEFAULT_CONTACT_GROUP_COLOR) ?: Color(0xFF4F46E5)

internal enum class GroupMembershipState { none, some, all }

internal fun membership_state_of(contacts: List<Contact>, group_id: String): GroupMembershipState {
    if (contacts.isEmpty()) return GroupMembershipState.none
    val matches = contacts.count { group_id in it.groups }
    return when (matches) {
        0 -> GroupMembershipState.none
        contacts.size -> GroupMembershipState.all
        else -> GroupMembershipState.some
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun contact_group_editor_dialog(
    group: ContactGroup?,
    existing_names: List<String>,
    group_count: Int,
    on_dismiss: () -> Unit,
    on_submit: (name: String, color: String) -> Unit,
    on_delete: (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    var name_value by remember { mutableStateOf(group?.name.orEmpty()) }
    var selected_color by remember {
        mutableStateOf(group?.color?.takeIf { it.isNotBlank() } ?: DEFAULT_CONTACT_GROUP_COLOR)
    }
    val name_focus = remember { FocusRequester() }
    val trimmed_name = name_value.trim()
    val current_name = group?.name?.trim().orEmpty()
    val is_duplicate = trimmed_name.isNotEmpty() && existing_names.any {
        it.trim().equals(trimmed_name, ignoreCase = true) &&
            !it.trim().equals(current_name, ignoreCase = true)
    }
    val is_too_long = trimmed_name.length > MAX_CONTACT_GROUP_NAME_LENGTH
    val is_over_limit = group == null && group_count >= MAX_CONTACT_GROUPS
    val error_text = when {
        is_duplicate -> stringResource(R.string.contact_group_name_taken)
        is_too_long -> stringResource(R.string.contact_group_name_too_long, MAX_CONTACT_GROUP_NAME_LENGTH)
        is_over_limit -> stringResource(R.string.contact_group_limit_reached, MAX_CONTACT_GROUPS)
        else -> null
    }
    val can_submit = trimmed_name.isNotEmpty() && error_text == null

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        runCatching { name_focus.requestFocus() }
    }

    AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = stringResource(
            if (group == null) R.string.create_contact_group else R.string.rename_contact_group,
        ),
        confirm_label = stringResource(if (group == null) R.string.create else R.string.save),
        cancel_label = stringResource(R.string.cancel),
        confirm_enabled = can_submit,
        on_confirm = {
            if (can_submit) on_submit(trimmed_name, selected_color)
        },
        extra_content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = TablerIcons.Users,
                        contentDescription = null,
                        tint = contact_group_color(selected_color),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = name_value.ifBlank { stringResource(R.string.preview) },
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                AsterTextField(
                    value = name_value,
                    onValueChange = { name_value = it },
                    placeholder = stringResource(R.string.contact_group_name),
                    singleLine = true,
                    error_text = error_text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(name_focus)
                        .testTag("contact_group_name_field"),
                )

                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.color_label),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    contact_group_color_presets.forEach { hex ->
                        val is_selected = hex.equals(selected_color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(contact_group_color(hex))
                                .then(
                                    if (is_selected) {
                                        Modifier.border(2.dp, colors.text_primary, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, colors.border_secondary, CircleShape)
                                    },
                                )
                                .clickable { selected_color = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (is_selected) {
                                Icon(
                                    imageVector = TablerIcons.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                if (group != null && on_delete != null) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(12.dp))
                            .clickable(onClick = on_delete)
                            .padding(vertical = 8.dp, horizontal = AsterSpacing.xs)
                            .testTag("contact_group_delete"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = TablerIcons.Trash,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Text(
                            text = stringResource(R.string.delete_contact_group),
                            color = colors.danger,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun manage_contact_groups_dialog(
    groups: List<ContactGroup>,
    contacts: List<Contact>,
    on_dismiss: () -> Unit,
    on_toggle: (group_id: String, should_add: Boolean) -> Unit,
    on_create: () -> Unit,
) {
    val colors = AsterMaterial.colors
    AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.manage_contact_groups),
        confirm_label = stringResource(R.string.close),
        on_confirm = on_dismiss,
        extra_content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (groups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.add_contacts_to_group_hint),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = AsterSpacing.sm),
                    )
                }
                groups.forEach { group ->
                    val state = membership_state_of(contacts, group.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(12.dp))
                            .clickable {
                                on_toggle(group.id, state != GroupMembershipState.all)
                            }
                            .padding(vertical = 10.dp, horizontal = AsterSpacing.xs)
                            .testTag("manage_group_" + group.id),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val swatch = contact_group_color(group.color)
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(SquircleShape(6.dp))
                                .background(
                                    if (state == GroupMembershipState.none) Color.Transparent else swatch,
                                )
                                .border(
                                    1.dp,
                                    if (state == GroupMembershipState.none) colors.border_secondary else swatch,
                                    SquircleShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (state) {
                                GroupMembershipState.all -> Icon(
                                    imageVector = TablerIcons.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                GroupMembershipState.some -> Icon(
                                    imageVector = TablerIcons.Minus,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                GroupMembershipState.none -> Unit
                            }
                        }
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(swatch),
                        )
                        Spacer(Modifier.width(AsterSpacing.sm))
                        Text(
                            text = group.name,
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = group.contact_count.toString(),
                            color = colors.text_muted,
                            fontSize = 12.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SquircleShape(12.dp))
                        .clickable(onClick = on_create)
                        .padding(vertical = 10.dp, horizontal = AsterSpacing.xs)
                        .testTag("manage_groups_create"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = TablerIcons.Plus,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.new_contact_group),
                        color = colors.accent_blue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun contact_group_chip(
    label: String,
    active: Boolean,
    color: Color? = null,
    trailing: String? = null,
    dashed: Boolean = false,
    on_long_click: (() -> Unit)? = null,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val bg = when {
        active -> colors.accent_blue
        dashed -> Color.Transparent
        colors.is_dark -> colors.input_bg
        else -> colors.bg_secondary
    }
    val fg = if (active) Color.White else colors.text_secondary
    Row(
        modifier = Modifier
            .clip(SquircleShape(AsterRadius.pill))
            .background(bg)
            .then(
                if (dashed && !active) {
                    Modifier.border(1.dp, colors.border_secondary, SquircleShape(AsterRadius.pill))
                } else {
                    Modifier
                },
            )
            .combinedClickable(onLongClick = on_long_click, onClick = on_click)
            .padding(horizontal = AsterSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (active) Color.White else color),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = trailing,
                color = if (active) Color.White else colors.text_muted,
                fontSize = 11.sp,
            )
        }
    }
}
