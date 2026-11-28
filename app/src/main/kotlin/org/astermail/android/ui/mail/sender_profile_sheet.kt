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

package org.astermail.android.ui.mail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Check
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Mail
import compose.icons.tablericons.Search
import compose.icons.tablericons.User
import compose.icons.tablericons.UserMinus
import compose.icons.tablericons.UserPlus
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.contacts.ContactsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_haptic
import org.astermail.android.design.remember_haptic
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.security.SenderAuthState
import org.astermail.android.security.SenderTrust
import org.astermail.android.ui.common.aster_modal_sheet
import org.astermail.android.ui.common.aster_sheet_shape
import org.astermail.android.ui.common.sheet_container_color
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.settings.detail.settings_row_gap
import org.astermail.android.ui.icons.alias_at_icon

private val internal_sender_domains =
    setOf("aster.cx", "astermail.org", "astermail.me", "astermail.net")

private const val contact_save_timeout_ms = 15_000L

fun is_internal_sender(email: String): Boolean {
    val domain = email.substringAfter('@', "").lowercase().trim()
    return domain in internal_sender_domains
}

private enum class sender_contact_slot { add, adding, view, saved }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun sender_profile_sheet(
    sender_email: String,
    sender_name: String,
    on_close: () -> Unit,
    on_copy: (String) -> Unit,
    on_search_sender: (String) -> Unit,
    on_send_email: (String) -> Unit,
    on_block: (String) -> Unit,
    on_result: (String) -> Unit,
    contacts_vm: ContactsViewModel = hiltViewModel(),
    trust: SenderTrust? = null,
    is_own_address: Boolean = false,
    on_open_alias_settings: ((String) -> Unit)? = null,
    on_open_contact: ((String) -> Unit)? = null,
    is_blocked: Boolean = false,
    on_unblock: ((String) -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val aster_haptics = remember_haptic()
    val header_tap = remember { MutableInteractionSource() }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contacts_state by contacts_vm.state.collectAsStateWithLifecycle()
    val normalized = remember(sender_email) { sender_email.trim().lowercase() }
    val marks_hidden = trust?.show_identity_marks == false
    val is_internal = remember(normalized, trust) {
        if (trust == null) {
            is_internal_sender(normalized)
        } else {
            trust.auth_state == SenderAuthState.internal && trust.warning == null && is_internal_sender(normalized)
        }
    }
    val shows_official = is_internal && is_official_address(normalized)
    val shows_verified = !is_internal && !shows_official && trust?.show_verified_badge == true
    val existing_contact = remember(contacts_state.contacts, normalized) {
        contacts_state.contacts.firstOrNull { it.email.trim().lowercase() == normalized }
    }
    val display_name = remember(sender_name, normalized) {
        sender_name.trim().takeUnless { it.isBlank() || it.equals(normalized, ignoreCase = true) }
    }
    val identity_text = remember(display_name, normalized) {
        if (display_name == null) normalized else "$display_name <$normalized>"
    }

    var adding_contact by remember { mutableStateOf(false) }
    var contact_added by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contacts_vm.load_contacts(quiet = contacts_vm.state.value.contacts.isNotEmpty())
    }
    LaunchedEffect(adding_contact) {
        if (adding_contact) {
            delay(contact_save_timeout_ms)
            adding_contact = false
        }
    }

    val contact_slot = when {
        adding_contact -> sender_contact_slot.adding
        existing_contact != null && on_open_contact != null -> sender_contact_slot.view
        existing_contact != null || contact_added -> sender_contact_slot.saved
        else -> sender_contact_slot.add
    }

    val copy_address = {
        aster_haptics(aster_haptic.tap)
        on_copy(normalized)
    }

    val add_contact = {
        aster_haptics(aster_haptic.tap)
        adding_contact = true
        contacts_vm.save_contact(
            Contact(
                id = "",
                name = display_name ?: normalized.substringBefore('@'),
                email = normalized,
            ),
        ) { saved ->
            adding_contact = false
            if (saved) contact_added = true
            aster_haptics(if (saved) aster_haptic.confirm else aster_haptic.reject)
            on_result(
                if (saved) {
                    context.getString(R.string.contact_added_named, normalized)
                } else {
                    context.getString(R.string.contact_add_failed, normalized)
                },
            )
        }
    }

    val show_alias_settings = is_own_address && on_open_alias_settings != null
    val show_remove_contact = existing_contact != null
    val show_unblock = is_blocked && on_unblock != null
    val show_block = !show_unblock && !is_internal && !is_own_address
    val has_secondary_rows = show_alias_settings || show_remove_contact || show_unblock || show_block

    aster_modal_sheet(
        shape = aster_sheet_shape,
        on_dismiss_request = on_close,
        sheet_state = state,
        container_color = sheet_container_color(colors),
        drag_handle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AsterSpacing.lg)
                .padding(bottom = AsterSpacing.xl),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = header_tap,
                        indication = null,
                        onClickLabel = stringResource(R.string.copy_address),
                        onClick = copy_address,
                        onLongClickLabel = stringResource(R.string.copy_address),
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            on_copy(identity_text)
                        },
                    )
                    .padding(horizontal = AsterSpacing.xs)
                    .padding(top = AsterSpacing.xs, bottom = AsterSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SenderAvatar(email = normalized, name = sender_name, size = 56.dp, plain = marks_hidden)
                Spacer(Modifier.width(AsterSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = display_name ?: normalized,
                            color = colors.text_primary,
                            fontSize = 18.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = if (display_name == null) 1 else 2,
                            overflow = if (display_name == null) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (shows_official) {
                            Spacer(Modifier.width(6.dp))
                            official_badge(size = 28.dp)
                        }
                    }
                    if (display_name != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = normalized,
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                    if (is_internal || shows_verified) {
                        Spacer(Modifier.height(AsterSpacing.sm))
                        if (is_internal) {
                            aster_account_badge()
                        } else {
                            sender_identity_badge(
                                label = stringResource(R.string.sender_verified_badge),
                                icon = TablerIcons.CircleCheck,
                                tint = colors.accent_blue,
                            )
                        }
                    }
                }
            }

            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    sender_sheet_row(
                        label = stringResource(R.string.sender_action_new_message),
                        icon = TablerIcons.Mail,
                        on_click = {
                            on_close()
                            on_send_email(normalized)
                        },
                    )
                    settings_row_gap()
                    sender_sheet_row(
                        label = stringResource(R.string.copy_address),
                        icon = TablerIcons.Copy,
                        on_click = copy_address,
                    )
                    settings_row_gap()
                    when (contact_slot) {
                        sender_contact_slot.view -> sender_sheet_row(
                            label = stringResource(R.string.sender_action_view_contact),
                            icon = TablerIcons.User,
                            show_chevron = true,
                            on_click = {
                                val id = existing_contact?.id
                                val open = on_open_contact
                                if (id != null && open != null) {
                                    on_close()
                                    open(id)
                                }
                            },
                        )
                        sender_contact_slot.saved -> sender_sheet_row(
                            label = stringResource(R.string.sender_action_in_contacts),
                            icon = TablerIcons.Check,
                            highlighted = true,
                            enabled = false,
                            on_click = {},
                        )
                        else -> sender_sheet_row(
                            label = stringResource(R.string.sender_action_add_contact),
                            icon = TablerIcons.UserPlus,
                            busy = contact_slot == sender_contact_slot.adding,
                            enabled = contact_slot == sender_contact_slot.add,
                            on_click = add_contact,
                        )
                    }
                    settings_row_gap()
                    sender_sheet_row(
                        label = stringResource(R.string.search_mail),
                        icon = TablerIcons.Search,
                        on_click = {
                            on_close()
                            on_search_sender(normalized)
                        },
                    )
                }
            }

            if (has_secondary_rows) {
                Spacer(Modifier.height(AsterSpacing.md))
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        var row_index = 0
                        if (show_alias_settings) {
                            row_index++
                            sender_sheet_row(
                                label = stringResource(R.string.alias_open_settings),
                                icon = alias_at_icon,
                                show_chevron = true,
                                on_click = {
                                    on_close()
                                    on_open_alias_settings?.invoke(normalized)
                                },
                            )
                        }
                        if (show_remove_contact) {
                            if (row_index++ > 0) settings_row_gap()
                            sender_sheet_row(
                                label = stringResource(R.string.remove_from_contacts),
                                icon = TablerIcons.UserMinus,
                                destructive = true,
                                on_click = {
                                    val target = existing_contact ?: return@sender_sheet_row
                                    contacts_vm.delete_contact(target.id) { removed ->
                                        on_result(
                                            if (removed) {
                                                context.getString(R.string.contact_removed_named, normalized)
                                            } else {
                                                context.getString(R.string.contact_remove_failed, normalized)
                                            },
                                        )
                                    }
                                    on_close()
                                },
                            )
                        }
                        if (show_unblock) {
                            if (row_index++ > 0) settings_row_gap()
                            sender_sheet_row(
                                label = stringResource(R.string.unblock),
                                icon = TablerIcons.CircleCheck,
                                on_click = {
                                    on_close()
                                    on_unblock?.invoke(normalized)
                                },
                            )
                        }
                        if (show_block) {
                            if (row_index++ > 0) settings_row_gap()
                            sender_sheet_row(
                                label = stringResource(R.string.block_sender),
                                icon = TablerIcons.Ban,
                                destructive = true,
                                on_click = {
                                    on_close()
                                    on_block(normalized)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
