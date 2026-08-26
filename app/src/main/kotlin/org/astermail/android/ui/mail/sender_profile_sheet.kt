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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Mail
import compose.icons.tablericons.Search
import compose.icons.tablericons.ShieldLock
import compose.icons.tablericons.UserMinus
import compose.icons.tablericons.UserPlus
import org.astermail.android.R
import org.astermail.android.contacts.ContactsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.ui.contacts.Contact

private val internal_sender_domains = setOf("aster.cx", "astermail.org")

fun is_internal_sender(email: String): Boolean {
    val domain = email.substringAfter('@', "").lowercase().trim()
    return domain in internal_sender_domains
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val state = rememberModalBottomSheetState()
    val contacts_state by contacts_vm.state.collectAsStateWithLifecycle()
    val normalized = remember(sender_email) { sender_email.trim().lowercase() }
    val is_internal = remember(normalized) { is_internal_sender(normalized) }
    val existing_contact = remember(contacts_state.contacts, normalized) {
        contacts_state.contacts.firstOrNull { it.email.trim().lowercase() == normalized }
    }

    LaunchedEffect(Unit) {
        contacts_vm.load_contacts()
    }

    ModalBottomSheet(
        onDismissRequest = on_close,
        sheetState = state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.xl)
                    .padding(bottom = AsterSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SenderAvatar(email = normalized, name = sender_name, size = 64.dp)
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = sender_name.ifBlank { normalized },
                    color = colors.text_primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!sender_name.equals(normalized, ignoreCase = true)) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = normalized,
                        color = colors.text_muted,
                        fontSize = 13.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (is_internal) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Row(
                        modifier = Modifier
                            .clip(SquircleShape(999.dp))
                            .background(colors.accent_blue.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = TablerIcons.ShieldLock,
                            contentDescription = null,
                            tint = colors.accent_blue,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.sender_on_aster),
                            color = colors.accent_blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            AsterDivider()
            sheet_row(
                stringResource(R.string.copy_address),
                colors.text_primary,
                TablerIcons.Copy,
            ) {
                on_copy(normalized)
                on_close()
            }
            sheet_row(
                stringResource(R.string.messages_from_sender),
                colors.text_primary,
                TablerIcons.Search,
            ) {
                on_close()
                on_search_sender(normalized)
            }
            sheet_row(
                stringResource(R.string.send_email_to_sender),
                colors.text_primary,
                TablerIcons.Mail,
            ) {
                on_close()
                on_send_email(normalized)
            }
            if (existing_contact != null) {
                sheet_row(
                    stringResource(R.string.remove_from_contacts),
                    colors.text_primary,
                    TablerIcons.UserMinus,
                ) {
                    contacts_vm.delete_contact(existing_contact.id) { removed ->
                        on_result(
                            if (removed) {
                                context.getString(R.string.contact_removed_named, normalized)
                            } else {
                                context.getString(R.string.contact_remove_failed, normalized)
                            },
                        )
                    }
                    on_close()
                }
            } else {
                sheet_row(
                    stringResource(R.string.add_to_contacts),
                    colors.text_primary,
                    TablerIcons.UserPlus,
                ) {
                    contacts_vm.save_contact(
                        Contact(
                            id = "",
                            name = sender_name.ifBlank { normalized.substringBefore('@') },
                            email = normalized,
                        ),
                    ) { saved ->
                        on_result(
                            if (saved) {
                                context.getString(R.string.contact_added_named, normalized)
                            } else {
                                context.getString(R.string.contact_add_failed, normalized)
                            },
                        )
                    }
                    on_close()
                }
            }
            if (!is_internal) {
                AsterDivider()
                sheet_row(
                    stringResource(R.string.block_sender),
                    colors.danger,
                    TablerIcons.Ban,
                ) {
                    on_close()
                    on_block(normalized)
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}
