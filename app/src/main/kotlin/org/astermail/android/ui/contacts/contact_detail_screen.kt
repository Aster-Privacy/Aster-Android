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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
    val clipboard = LocalClipboard.current
    val clipboard_scope = rememberCoroutineScope()

    LaunchedEffect(contact_id) {
        vm.load_contact(contact_id)
    }

    LaunchedEffect(ui_state.delete_success) {
        if (ui_state.delete_success) {
            vm.clear_flags()
            on_back()
        }
    }

    var delete_requested by remember { mutableStateOf(false) }
    var show_delete_confirm by remember { mutableStateOf(false) }
    var favorite_pending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load_contacts() }
    val contact = ui_state.selected_contact ?: ui_state.contacts.firstOrNull { it.id == contact_id }
    var is_favorite by remember(contact) { mutableStateOf(contact?.is_favorite == true) }

    LaunchedEffect(ui_state.error) {
        val message = ui_state.error ?: return@LaunchedEffect
        if (!delete_requested && !favorite_pending) return@LaunchedEffect
        if (favorite_pending) {
            favorite_pending = false
            is_favorite = contact?.is_favorite == true
        }
        delete_requested = false
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        vm.clear_flags()
    }

    LaunchedEffect(ui_state.save_success) {
        if (ui_state.save_success && favorite_pending) {
            favorite_pending = false
            vm.clear_flags()
        }
    }

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
                icon = TablerIcons.ArrowLeft,
                auto_mirror = true,
                content_description = stringResource(R.string.back),
                onClick = on_back,
                modifier = Modifier.testTag("back"),
            )
            Spacer(Modifier.weight(1f))
            AsterIconButton(
                icon = if (is_favorite) TablerIcons.Star else TablerIcons.Star,
                content_description = if (is_favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                enabled = contact != null && !favorite_pending,
                onClick = {
                    val target = contact ?: return@AsterIconButton
                    is_favorite = !is_favorite
                    favorite_pending = true
                    vm.save_contact(target.copy(is_favorite = is_favorite), target.id)
                },
                tint = if (is_favorite) colors.warning else Color.Unspecified,
            )
            AsterIconButton(
                icon = TablerIcons.Edit,
                content_description = stringResource(R.string.edit),
                onClick = { contact?.let { on_edit(it.id) } },
            )
        }
        AsterDivider()

        if (contact == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (ui_state.is_loading) {
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
                QuickAction(TablerIcons.Mail, stringResource(R.string.mail), Modifier.weight(1f)) {
                    if (on_compose != null && contact.email.isNotBlank()) {
                        on_compose(contact.email)
                    }
                }
                QuickAction(TablerIcons.Phone, stringResource(R.string.call), Modifier.weight(1f)) {
                    val phone = contact.phone.ifBlank { contact.work_phone }
                    if (phone.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone}"))

                        try {
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.could_not_open_link),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                QuickAction(TablerIcons.Copy, stringResource(R.string.copy), Modifier.weight(1f)) {
                    if (contact.email.isNotBlank()) {
                        clipboard_scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("", contact.email)),
                            )
                        }
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
                fun open_contact_url(url: String) {
                    val uri = Uri.parse(url)
                    if (uri.scheme?.lowercase() !in setOf("http", "https")) return
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Throwable) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.could_not_open_link),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                DetailCard(title = stringResource(R.string.social)) {
                    if (contact.website.isNotBlank()) {
                        val url = build_contact_social_url("website", contact.website)
                        DetailRow(
                            stringResource(R.string.website),
                            contact.website,
                            on_open = url?.let { { open_contact_url(it) } },
                        )
                    }
                    if (contact.twitter.isNotBlank()) {
                        if (contact.website.isNotBlank()) AsterDivider()
                        val url = build_contact_social_url("twitter", contact.twitter)
                        DetailRow(
                            stringResource(R.string.twitter),
                            contact.twitter,
                            on_open = url?.let { { open_contact_url(it) } },
                        )
                    }
                    if (contact.linkedin.isNotBlank()) {
                        if (contact.website.isNotBlank() || contact.twitter.isNotBlank()) AsterDivider()
                        val url = build_contact_social_url("linkedin", contact.linkedin)
                        DetailRow(
                            stringResource(R.string.linkedin),
                            contact.linkedin,
                            on_open = url?.let { { open_contact_url(it) } },
                        )
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
                    onClick = { show_delete_confirm = true },
                    enabled = !ui_state.is_loading,
                )
            }
            if (show_delete_confirm) {
                org.astermail.android.design.components.AsterAlertDialog(
                    on_dismiss = { show_delete_confirm = false },
                    title = stringResource(R.string.delete_contact),
                    message = stringResource(
                        R.string.alias_delete_confirm_message,
                        contact.name.takeIf { it.isNotBlank() } ?: contact.email,
                    ),
                    confirm_label = stringResource(R.string.delete),
                    cancel_label = stringResource(R.string.cancel),
                    confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
                    on_confirm = {
                        show_delete_confirm = false
                        delete_requested = true
                        vm.delete_contact(contact_id)
                    },
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
private fun DetailRow(label: String, value: String, on_open: (() -> Unit)? = null) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (on_open != null) Modifier.clickable(onClick = on_open) else Modifier)
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
            color = if (on_open != null) colors.accent_blue else colors.text_primary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun build_contact_social_url(kind: String, raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val has_scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(value)
    fun parse_web_url(candidate: String): Uri? {
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return uri
    }
    if (kind == "website") {
        val parsed = parse_web_url(value) ?: if (has_scheme) null else parse_web_url("https://$value")
        return parsed?.toString()
    }
    val hosts = when (kind) {
        "linkedin" -> listOf("linkedin.com")
        "twitter" -> listOf("twitter.com", "x.com")
        else -> return null
    }
    val parsed = parse_web_url(value)
    if (parsed != null) {
        val host = parsed.host?.lowercase() ?: return null
        return if (hosts.any { host == it || host.endsWith(".$it") }) parsed.toString() else null
    }
    if (has_scheme) return null
    val handle = java.net.URLEncoder.encode(value.removePrefix("@"), "UTF-8")
    if (handle.isBlank()) return null
    return when (kind) {
        "linkedin" -> "https://linkedin.com/in/$handle"
        "twitter" -> "https://x.com/$handle"
        else -> null
    }
}
