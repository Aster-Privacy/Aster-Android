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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.contacts.ContactsViewModel
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.ui.mail.SenderAvatar

@Composable
fun ContactDetailScreen(
    contact_id: String,
    on_back: () -> Unit,
    on_edit: (String) -> Unit,
    on_compose: ((String) -> Unit)? = null,
    vm: ContactsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val ui_state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard_manager = LocalClipboardManager.current

    LaunchedEffect(contact_id) {
        vm.load_contact(contact_id)
    }

    LaunchedEffect(ui_state.delete_success) {
        if (ui_state.delete_success) {
            vm.clear_flags()
            on_back()
        }
    }

    LaunchedEffect(Unit) { vm.load_contacts() }
    val contact = ui_state.selected_contact ?: ui_state.contacts.firstOrNull { it.id == contact_id }
    var is_favorite by remember(contact) { mutableStateOf(contact?.is_favorite == true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsterIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                content_description = stringResource(R.string.back),
                onClick = on_back,
                modifier = Modifier.testTag("back"),
            )
            Spacer(Modifier.weight(1f))
            AsterIconButton(
                icon = if (is_favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                content_description = if (is_favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                onClick = {
                    is_favorite = !is_favorite
                    contact?.let { c ->
                        vm.save_contact(c.copy(is_favorite = !c.is_favorite), c.id)
                    }
                },
                tint = if (is_favorite) colors.warning else Color.Unspecified,
            )
            AsterIconButton(
                icon = Icons.Outlined.Edit,
                content_description = stringResource(R.string.edit),
                onClick = { contact?.let { on_edit(it.id) } },
            )
        }
        AsterDivider()

        if (contact == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (ui_state.is_loading || ui_state.contacts.isEmpty()) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = colors.accent_blue,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.contact_unavailable),
                        color = colors.text_muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = AsterSpacing.xxl),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AsterSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SenderAvatar(
                    email = contact.email,
                    name = contact.name,
                    size = 96.dp,
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = contact.name,
                    color = colors.text_primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                )
                if (contact.company.isNotBlank() || contact.title.isNotBlank()) {
                    val sub = listOf(contact.title, contact.company)
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sub,
                        color = colors.text_muted,
                        fontSize = 14.sp,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            ) {
                QuickAction(Icons.Outlined.Email, stringResource(R.string.mail), Modifier.weight(1f)) {
                    if (on_compose != null && contact.email.isNotBlank()) {
                        on_compose(contact.email)
                    }
                }
                QuickAction(Icons.Outlined.Phone, stringResource(R.string.call), Modifier.weight(1f)) {
                    val phone = contact.phone.ifBlank { contact.work_phone }
                    if (phone.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone}"))
                        context.startActivity(intent)
                    }
                }
                QuickAction(Icons.Outlined.ContentCopy, stringResource(R.string.copy), Modifier.weight(1f)) {
                    if (contact.email.isNotBlank()) {
                        clipboard_manager.setText(AnnotatedString(contact.email))
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.xl))

            DetailCard(title = stringResource(R.string.email)) {
                DetailRow(stringResource(R.string.personal), contact.email)
                if (contact.work_email.isNotBlank()) {
                    AsterDivider()
                    DetailRow(stringResource(R.string.work), contact.work_email)
                }
            }

            if (contact.phone.isNotBlank() || contact.work_phone.isNotBlank()) {
                Spacer(Modifier.height(AsterSpacing.md))
                DetailCard(title = stringResource(R.string.phone)) {
                    if (contact.phone.isNotBlank()) {
                        DetailRow(stringResource(R.string.mobile), contact.phone)
                    }
                    if (contact.work_phone.isNotBlank()) {
                        if (contact.phone.isNotBlank()) AsterDivider()
                        DetailRow(stringResource(R.string.work), contact.work_phone)
                    }
                }
            }

            if (contact.company.isNotBlank() || contact.title.isNotBlank()) {
                Spacer(Modifier.height(AsterSpacing.md))
                DetailCard(title = stringResource(R.string.work)) {
                    if (contact.company.isNotBlank()) DetailRow(stringResource(R.string.company), contact.company)
                    if (contact.title.isNotBlank()) {
                        if (contact.company.isNotBlank()) AsterDivider()
                        DetailRow(stringResource(R.string.title), contact.title)
                    }
                }
            }

            if (contact.birthday.isNotBlank()) {
                Spacer(Modifier.height(AsterSpacing.md))
                DetailCard(title = stringResource(R.string.birthday)) {
                    DetailRow(stringResource(R.string.date), contact.birthday)
                }
            }

            val has_address = listOf(
                contact.address,
                contact.city,
                contact.region,
                contact.postal_code,
                contact.country,
            ).any { it.isNotBlank() }
            if (has_address) {
                Spacer(Modifier.height(AsterSpacing.md))
                DetailCard(title = stringResource(R.string.address)) {
                    val lines = listOf(
                        contact.address,
                        listOf(contact.city, contact.region, contact.postal_code)
                            .filter { it.isNotBlank() }
                            .joinToString(", "),
                        contact.country,
                    ).filter { it.isNotBlank() }
                    DetailRow(stringResource(R.string.location), lines.joinToString("\n"))
                }
            }

            val has_social = contact.website.isNotBlank() ||
                contact.twitter.isNotBlank() ||
                contact.linkedin.isNotBlank()
            if (has_social) {
                Spacer(Modifier.height(AsterSpacing.md))
                DetailCard(title = stringResource(R.string.social)) {
                    if (contact.website.isNotBlank()) DetailRow(stringResource(R.string.website), contact.website)
                    if (contact.twitter.isNotBlank()) {
                        if (contact.website.isNotBlank()) AsterDivider()
                        DetailRow(stringResource(R.string.twitter), contact.twitter)
                    }
                    if (contact.linkedin.isNotBlank()) {
                        if (contact.website.isNotBlank() || contact.twitter.isNotBlank()) AsterDivider()
                        DetailRow(stringResource(R.string.linkedin), contact.linkedin)
                    }
                }
            }

            if (contact.notes.isNotBlank()) {
                Spacer(Modifier.height(AsterSpacing.md))
                DetailCard(title = stringResource(R.string.notes)) {
                    Box(modifier = Modifier.padding(AsterSpacing.md)) {
                        Text(
                            text = contact.notes,
                            color = colors.text_primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(AsterSpacing.xl))
            Box(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
                AsterDestructiveButton(
                    label = stringResource(R.string.delete_contact),
                    onClick = { vm.delete_contact(contact_id) },
                )
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier
            .clip(SquircleShape(18.dp))
            .background(colors.bg_tertiary)
            .border(1.dp, colors.border_primary, SquircleShape(18.dp))
            .clickable(onClick = on_click)
            .padding(vertical = AsterSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg),
    ) {
        Text(
            text = title,
            color = colors.text_tertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = AsterSpacing.sm, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleShape(18.dp))
                .background(colors.bg_card)
                .border(1.dp, colors.border_primary, SquircleShape(18.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = colors.text_muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = value,
            color = colors.text_primary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
