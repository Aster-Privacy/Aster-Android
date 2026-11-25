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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Users
import compose.icons.tablericons.Search
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.DecryptedAliasPin
import org.astermail.android.settings.MAX_CONTACT_PICK
import org.astermail.android.settings.PickCandidate
import org.astermail.android.settings.PickEmail
import org.astermail.android.settings.PickEmailStatus
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.build_pick_candidates
import org.astermail.android.settings.clear_visible
import org.astermail.android.settings.filter_pick_candidates
import org.astermail.android.settings.finalize_picks
import org.astermail.android.settings.select_all_saturated
import org.astermail.android.settings.select_all_visible
import org.astermail.android.settings.toggle_pick
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.contacts.ContactAvatar
import org.astermail.android.ui.contacts.contact_entry_type_label

@Composable
internal fun alias_add_from_contacts_button(enabled: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.text_primary.copy(alpha = 0.12f), shape)
            .background(colors.bg_secondary, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = on_click)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag("alias_sender_add_from_contacts"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = TablerIcons.Users,
            contentDescription = null,
            tint = if (enabled) colors.text_primary else colors.text_muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.alias_sender_add_from_contacts),
            color = if (enabled) colors.text_primary else colors.text_muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private sealed interface PickLoad {
    data object Loading : PickLoad
    data object Failed : PickLoad
    data class Ready(val contacts: List<Contact>) : PickLoad
}

@Composable
internal fun alias_contact_pick_dialog(
    alias_id: String,
    pins: List<DecryptedAliasPin>,
    vm: SettingsViewModel,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var reload_token by remember { mutableIntStateOf(0) }
    var load by remember { mutableStateOf<PickLoad>(PickLoad.Loading) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(reload_token) {
        load = PickLoad.Loading
        load = vm.load_alias_pick_contacts().fold(
            onSuccess = { PickLoad.Ready(it) },
            onFailure = { PickLoad.Failed },
        )
    }

    val contacts = (load as? PickLoad.Ready)?.contacts.orEmpty()
    val statuses = remember(contacts, pins) { vm.alias_pick_statuses(contacts, pins) }
    val candidates = remember(contacts, statuses) { build_pick_candidates(contacts, statuses) }
    val visible = remember(candidates, query) { filter_pick_candidates(candidates, query) }
    val open_count = remember(candidates) { candidates.count { it.open_emails.isNotEmpty() } }
    val final = remember(selected, statuses) { finalize_picks(selected, statuses) }
    val at_limit = selected.size >= MAX_CONTACT_PICK

    fun submit() {
        if (final.isEmpty() || adding) return
        adding = true
        vm.add_alias_pins_from_contacts(alias_id, final) { result ->
            adding = false
            if (result.failed.isEmpty()) {
                on_dismiss()
            } else {
                selected = result.failed.toSet()
            }
        }
    }

    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.alias_sender_pick_title),
        message = stringResource(R.string.alias_sender_pick_description),
        is_busy = adding,
        body = {
            Column(modifier = Modifier.fillMaxWidth().testTag("alias_pick_dialog")) {
                when (val state = load) {
                    PickLoad.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .testTag("alias_pick_loading"),
                        contentAlignment = Alignment.Center,
                    ) {
                        org.astermail.android.design.components.aster_spinner(
                            color = colors.accent_blue,
                            stroke_width = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    PickLoad.Failed -> pick_notice(
                        icon = TablerIcons.Users,
                        title = stringResource(R.string.failed_to_load),
                        subtitle = null,
                        action_label = stringResource(R.string.retry),
                        on_action = { reload_token += 1 },
                        test_tag = "alias_pick_load_failed",
                    )
                    is PickLoad.Ready -> if (candidates.isEmpty()) {
                        pick_notice(
                            icon = TablerIcons.Users,
                            title = stringResource(R.string.alias_sender_pick_no_contacts_title),
                            subtitle = stringResource(R.string.alias_sender_pick_no_contacts_description),
                            action_label = null,
                            on_action = {},
                            test_tag = "alias_pick_no_contacts",
                        )
                    } else {
                        pick_body(
                            query = query,
                            on_query = { query = it },
                            visible = visible,
                            open_count = open_count,
                            selected = selected,
                            at_limit = at_limit,
                            adding = adding,
                            on_toggle = { address, checked -> selected = toggle_pick(selected, address, checked) },
                            on_toggle_all = { checked ->
                                selected = if (checked) {
                                    select_all_visible(selected, visible)
                                } else {
                                    clear_visible(selected, visible)
                                }
                            },
                        )
                    }
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
                enabled = !adding,
                modifier = Modifier.weight(1f),
            )
            AsterDialogPrimaryButton(
                label = if (final.isEmpty()) {
                    stringResource(R.string.alias_sender_pick_submit)
                } else {
                    pluralStringResource(R.plurals.alias_sender_pick_submit_count, final.size, final.size)
                },
                onClick = ::submit,
                enabled = final.isNotEmpty() && !adding,
                is_loading = adding,
                modifier = Modifier
                    .weight(1f)
                    .testTag("alias_pick_submit"),
            )
        },
    )
}

@Composable
private fun pick_body(
    query: String,
    on_query: (String) -> Unit,
    visible: List<PickCandidate>,
    open_count: Int,
    selected: Set<String>,
    at_limit: Boolean,
    adding: Boolean,
    on_toggle: (String, Boolean) -> Unit,
    on_toggle_all: (Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    AsterTextField(
        value = query,
        onValueChange = { on_query(it.take(200)) },
        placeholder = stringResource(R.string.search_contacts),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alias_pick_search"),
    )
    Spacer(Modifier.height(AsterSpacing.sm))
    if (open_count == 0) {
        Text(
            text = stringResource(R.string.alias_sender_pick_all_added),
            color = colors.text_secondary,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(vertical = AsterSpacing.xs)
                .testTag("alias_pick_all_added"),
        )
        Spacer(Modifier.height(AsterSpacing.xs))
    } else {
        val all_checked = select_all_saturated(selected, visible)
        val can_toggle_all = visible.any { it.open_emails.isNotEmpty() } && !adding
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(SquircleShape(AsterRadius.sm))
                    .clickable(enabled = can_toggle_all, role = Role.Checkbox) { on_toggle_all(!all_checked) }
                    .padding(end = AsterSpacing.sm)
                    .testTag("alias_pick_select_all"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = all_checked,
                    onCheckedChange = null,
                    enabled = can_toggle_all,
                    colors = pick_checkbox_colors(),
                )
                Text(
                    text = stringResource(R.string.select_all),
                    color = colors.text_primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = pluralStringResource(R.plurals.alias_sender_pick_selected, selected.size, selected.size),
                color = if (selected.isEmpty()) colors.text_muted else colors.accent_blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("alias_pick_selected_count"),
            )
        }
    }
    if (visible.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AsterSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.Search,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = stringResource(R.string.no_contacts_match, query.trim()),
                color = colors.text_secondary,
                fontSize = 14.sp,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            .clip(SquircleShape(AsterRadius.lg))
            .background(colors.text_primary.copy(alpha = 0.04f))
            .border(1.dp, colors.text_primary.copy(alpha = 0.08f), SquircleShape(AsterRadius.lg))
            .padding(6.dp)
            .testTag("alias_pick_list"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(visible, key = { it.contact_id }) { candidate ->
            if (candidate.emails.size == 1) {
                pick_single_row(candidate, candidate.emails.first(), selected, at_limit, adding, on_toggle)
            } else {
                pick_group(candidate, selected, at_limit, adding, on_toggle)
            }
        }
    }
    if (at_limit) {
        Spacer(Modifier.height(AsterSpacing.xs))
        Text(
            text = stringResource(R.string.alias_sender_pick_limit, MAX_CONTACT_PICK),
            color = colors.text_muted,
            fontSize = 12.sp,
            modifier = Modifier.testTag("alias_pick_limit"),
        )
    }
}

@Composable
private fun pick_checkbox_colors() = CheckboxDefaults.colors(
    checkedColor = AsterMaterial.colors.accent_blue,
    checkmarkColor = AsterMaterial.colors.on_accent,
)

@Composable
private fun pick_status_text(email: PickEmail): String? = when (email.status) {
    PickEmailStatus.ADDED -> stringResource(R.string.alias_sender_pick_already_added)
    PickEmailStatus.BLOCKED -> stringResource(R.string.alias_sender_pick_blocked)
    PickEmailStatus.AVAILABLE -> null
}

@Composable
private fun pick_row_modifier(
    email: PickEmail,
    checked: Boolean,
    selectable: Boolean,
    on_toggle: (String, Boolean) -> Unit,
): Modifier {
    val colors = AsterMaterial.colors
    return Modifier
        .fillMaxWidth()
        .clip(SquircleShape(AsterRadius.sm))
        .background(if (checked) colors.accent_blue.copy(alpha = 0.12f) else Color.Transparent)
        .clickable(enabled = selectable, role = Role.Checkbox) { on_toggle(email.address, !checked) }
        .testTag("alias_pick_option_" + email.address)
}

@Composable
private fun pick_trailing(email: PickEmail, checked: Boolean, selectable: Boolean) {
    val status = pick_status_text(email)
    if (status != null) {
        pick_status_chip(status, email.status == PickEmailStatus.BLOCKED)
    } else {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = selectable || checked,
            colors = pick_checkbox_colors(),
        )
    }
}

@Composable
private fun pick_single_row(
    candidate: PickCandidate,
    email: PickEmail,
    selected: Set<String>,
    at_limit: Boolean,
    adding: Boolean,
    on_toggle: (String, Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    val checked = email.address in selected
    val selectable = email.status == PickEmailStatus.AVAILABLE && !adding && (checked || !at_limit)
    val muted = email.status != PickEmailStatus.AVAILABLE
    Row(
        modifier = pick_row_modifier(email, checked, selectable, on_toggle)
            .padding(start = AsterSpacing.sm, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            avatar_url = candidate.avatar_url,
            email = email.address,
            name = candidate.name,
            size = 32.dp,
            profile_color = candidate.profile_color,
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.name.ifBlank { email.address },
                color = if (muted) colors.text_muted else colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (candidate.name.isNotBlank()) {
                Text(
                    text = email.address,
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.xs))
        pick_trailing(email, checked, selectable)
    }
}

@Composable
private fun pick_group(
    candidate: PickCandidate,
    selected: Set<String>,
    at_limit: Boolean,
    adding: Boolean,
    on_toggle: (String, Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxWidth().testTag("alias_pick_group_" + candidate.contact_id)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.sm, end = AsterSpacing.sm, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContactAvatar(
                avatar_url = candidate.avatar_url,
                email = candidate.emails.first().address,
                name = candidate.name,
                size = 32.dp,
                profile_color = candidate.profile_color,
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.name.ifBlank { candidate.emails.first().address },
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.alias_sender_pick_address_count,
                        candidate.emails.size,
                        candidate.emails.size,
                    ),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
            }
        }
        candidate.emails.forEach { email ->
            val checked = email.address in selected
            val selectable = email.status == PickEmailStatus.AVAILABLE && !adding && (checked || !at_limit)
            Row(
                modifier = pick_row_modifier(email, checked, selectable, on_toggle)
                    .padding(start = 48.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = email.address,
                        color = if (email.status == PickEmailStatus.AVAILABLE) {
                            colors.text_primary
                        } else {
                            colors.text_muted
                        },
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = contact_entry_type_label(email.type, email.label),
                        color = colors.text_muted,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(AsterSpacing.xs))
                pick_trailing(email, checked, selectable)
            }
        }
    }
}

@Composable
private fun pick_status_chip(label: String, danger: Boolean) {
    val colors = AsterMaterial.colors
    val tone = if (danger) colors.danger else colors.text_secondary
    Text(
        text = label,
        color = tone,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .padding(end = AsterSpacing.sm)
            .clip(RoundedCornerShape(6.dp))
            .background(tone.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun pick_notice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    action_label: String?,
    on_action: () -> Unit,
    test_tag: String,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AsterSpacing.lg)
            .testTag(test_tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = title,
            color = colors.text_primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                text = subtitle,
                color = colors.text_muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (action_label != null) {
            Spacer(Modifier.height(AsterSpacing.md))
            AsterDialogOutlineButton(
                label = action_label,
                onClick = on_action,
                modifier = Modifier.testTag(test_tag + "_action"),
            )
        }
    }
}
