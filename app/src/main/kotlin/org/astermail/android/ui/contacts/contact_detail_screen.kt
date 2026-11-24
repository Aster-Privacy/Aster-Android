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

import org.astermail.android.ui.common.vertical_scroll_with_indicator
import androidx.compose.ui.text.style.TextOverflow
import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Icon
import org.astermail.android.design.acrylic
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.astermail.android.contacts.ContactsViewModel
import org.astermail.android.contacts.safe_social_url
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterDuration
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.ui.search.build_contact_mail_query
import androidx.compose.material.icons.filled.Star
import org.astermail.android.ui.common.page_surface
import org.astermail.android.ui.common.show_copy_failed_toast

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactDetailScreen(
    contact_id: String,
    on_back: () -> Unit,
    on_edit: (String) -> Unit,
    on_compose: ((String) -> Unit)? = null,
    on_search_mail: ((String) -> Unit)? = null,
    vm: ContactsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val ui_state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboard_scope = rememberCoroutineScope()
    val share_scope = rememberCoroutineScope()
    var show_share_menu by remember { mutableStateOf(false) }
    val share_failed_message = stringResource(R.string.could_not_open_link)
    val copied_value_message = stringResource(R.string.copied)
    val copy_value: (String) -> Unit = { value ->
        clipboard_scope.launch {
            val copied = runCatching {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", value)))
            }.isSuccess
            if (copied) {
                org.astermail.android.ui.common.app_toast.show(copied_value_message.toString())
            } else {
                show_copy_failed_toast(context)
            }
        }
    }

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

    LaunchedEffect(Unit) {
        if (!vm.state.value.contacts_loaded) vm.load_contacts(quiet = vm.state.value.contacts.isNotEmpty())
    }
    val contact = ui_state.selected_contact?.takeIf { it.id == contact_id }
        ?: ui_state.contacts.firstOrNull { it.id == contact_id }
    val nav_bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    var is_favorite by remember(contact) { mutableStateOf(contact?.is_favorite == true) }

    LaunchedEffect(ui_state.error) {
        val message = ui_state.error ?: return@LaunchedEffect
        if (!delete_requested && !favorite_pending) return@LaunchedEffect
        if (favorite_pending) {
            favorite_pending = false
            is_favorite = contact?.is_favorite == true
        }
        delete_requested = false
        org.astermail.android.ui.common.app_toast.show(org.astermail.android.ui.common.TopToastState(message = message.toString(), duration_ms = org.astermail.android.ui.common.toast_action_duration_ms))
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
            .page_surface(colors)
            .statusBarsPadding()
            .windowInsetsPadding(
                androidx.compose.foundation.layout.WindowInsets.navigationBars
                    .only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal),
            ),
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
                icon = if (is_favorite) {
                    Icons.Filled.Star
                } else {
                    TablerIcons.Star
                },
                content_description = if (is_favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite),
                enabled = contact != null && !favorite_pending,
                onClick = {
                    val target = contact ?: return@AsterIconButton
                    is_favorite = !is_favorite
                    favorite_pending = true
                    vm.save_contact(target.copy(is_favorite = is_favorite), target.id)
                },
                tint = if (is_favorite) colors.star else Color.Unspecified,
            )
            Box {
                AsterIconButton(
                    icon = TablerIcons.Share,
                    content_description = stringResource(R.string.share_contact),
                    enabled = contact != null,
                    onClick = { show_share_menu = true },
                )
                aster_menu(expanded = show_share_menu, on_dismiss = { show_share_menu = false }) {
                    aster_menu_item(
                        label = stringResource(R.string.contact_share_as_text),
                        icon = TablerIcons.FileText,
                        on_click = {
                            show_share_menu = false
                            val target = contact ?: return@aster_menu_item
                            if (!share_contact_as_text(context, target)) {
                                org.astermail.android.ui.common.app_toast.show(share_failed_message.toString())
                            }
                        },
                    )
                    aster_menu_item(
                        label = stringResource(R.string.contact_share_as_vcard),
                        icon = TablerIcons.Id,
                        on_click = {
                            show_share_menu = false
                            val target = contact ?: return@aster_menu_item
                            share_scope.launch {
                                if (!share_contact_as_vcard(context, target)) {
                                    org.astermail.android.ui.common.app_toast.show(share_failed_message.toString())
                                }
                            }
                        },
                    )
                }
            }
            AsterIconButton(
                icon = TablerIcons.Edit,
                content_description = stringResource(R.string.edit),
                onClick = { contact?.let { on_edit(it.id) } },
            )
        }
        AsterDivider()

        if (contact == null) {
            Box(Modifier.fillMaxSize().padding(bottom = nav_bottom), contentAlignment = Alignment.Center) {
                if (ui_state.contact_loading || ui_state.is_loading) {
                    org.astermail.android.design.components.aster_spinner(
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
                .vertical_scroll_with_indicator()
                .padding(bottom = AsterSpacing.xxl + nav_bottom),
        ) {
            val header_shape = SquircleShape(AsterRadius.xl)
            val header_email = contact.email_entries().firstOrNull()?.value.orEmpty().ifBlank { contact.email }
            val header_title = contact.name.ifBlank { header_email }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg)
                    .padding(top = AsterSpacing.md, bottom = AsterSpacing.lg)
                    .clip(header_shape)
                    .acrylic(colors, header_shape, colors.bg_secondary)
                    .padding(AsterSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(
                    avatar_url = contact.avatar_url,
                    email = header_email,
                    name = contact.name,
                    size = contact_header_avatar_size,
                    content_description = stringResource(R.string.contact_photo),
                    profile_color = contact.profile_color,
                )
                Spacer(Modifier.width(AsterSpacing.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = header_title,
                        color = colors.text_primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (header_email.isNotBlank() && header_email != header_title) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = header_email,
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { copy_value(header_email) },
                            ),
                        )
                    }
                    if (contact.company.isNotBlank() || contact.title.isNotBlank()) {
                        val sub = listOf(contact.title, contact.company)
                            .filter { it.isNotBlank() }
                            .joinToString(" - ")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = sub,
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (contact.id in ui_state.pending_ids) {
                        Spacer(Modifier.height(AsterSpacing.xs))
                        contact_pending_sync_label()
                    }
                }
            }

            val email_entries = contact.email_entries()
            val phone_entries = contact.phone_entries()
            val address_entries = contact.address_entries()
            val primary_email = email_entries.firstOrNull()?.value.orEmpty()
            val primary_phone = phone_entries.firstOrNull()?.value.orEmpty()
            val mail_query = build_contact_mail_query(email_entries.map { it.value })
            fun dial(number: String) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
                try {
                    context.startActivity(intent)
                } catch (_: Throwable) {
                    org.astermail.android.ui.common.app_toast.show(context.getString(R.string.could_not_open_link).toString())
                }
            }
            val can_mail = on_compose != null && primary_email.isNotBlank()
            val can_call = primary_phone.isNotBlank()
            val can_search = on_search_mail != null && mail_query.isNotEmpty()
            val can_copy = primary_email.isNotBlank()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                if (can_mail) {
                    QuickAction(
                        icon = TablerIcons.Mail,
                        label = stringResource(R.string.mail),
                        modifier = Modifier.weight(1f),
                    ) {
                        on_compose?.invoke(primary_email)
                    }
                }
                if (can_call) {
                    QuickAction(
                        icon = TablerIcons.Phone,
                        label = stringResource(R.string.call),
                        modifier = Modifier.weight(1f),
                    ) {
                        dial(primary_phone)
                    }
                }
                if (can_search) {
                    QuickAction(
                        icon = TablerIcons.Search,
                        label = stringResource(R.string.all_mail),
                        modifier = Modifier.weight(1f),
                    ) {
                        on_search_mail?.invoke(mail_query)
                    }
                }
                if (can_copy) {
                    val copied_message = stringResource(R.string.email_copied)
                    QuickAction(
                        icon = TablerIcons.Copy,
                        label = stringResource(R.string.copy),
                        modifier = Modifier.weight(1f),
                    ) {
                        clipboard_scope.launch {
                            val copied = runCatching {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("", primary_email)),
                                )
                            }.isSuccess
                            if (copied) {
                                org.astermail.android.ui.common.app_toast.show(copied_message.toString())
                            } else {
                                show_copy_failed_toast(context)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.xl))

            if (email_entries.isNotEmpty()) {
                detail_section(stringResource(R.string.email)) {
                    email_entries.forEachIndexed { index, entry ->
                        if (index > 0) detail_row_divider()
                        DetailRow(
                            label = contact_entry_type_label(entry.type, entry.label),
                            value = entry.value,
                            on_open = on_compose?.let { compose -> { compose(entry.value) } },
                            on_copy = copy_value,
                        )
                    }
                }
            }

            if (phone_entries.isNotEmpty()) {
                detail_section(stringResource(R.string.phone)) {
                    phone_entries.forEachIndexed { index, entry ->
                        if (index > 0) detail_row_divider()
                        DetailRow(
                            label = contact_entry_type_label(entry.type, entry.label),
                            value = entry.value,
                            on_open = { dial(entry.value) },
                            on_copy = copy_value,
                        )
                    }
                }
            }

            if (contact.company.isNotBlank() || contact.title.isNotBlank()) {
                detail_section(stringResource(R.string.company)) {
                    if (contact.company.isNotBlank()) DetailRow(stringResource(R.string.company), contact.company)
                    if (contact.company.isNotBlank() && contact.title.isNotBlank()) detail_row_divider()
                    if (contact.title.isNotBlank()) DetailRow(stringResource(R.string.title), contact.title)
                }
            }

            if (contact.birthday.isNotBlank()) {
                detail_section(stringResource(R.string.birthday)) {
                    DetailRow(stringResource(R.string.date), contact_date_label(contact.birthday))
                }
            }

            if (address_entries.isNotEmpty()) {
                detail_section(stringResource(R.string.address)) {
                    address_entries.forEachIndexed { index, postal ->
                        if (index > 0) detail_row_divider()
                        DetailRow(
                            label = contact_entry_type_label(postal.type, postal.label),
                            value = postal.lines().joinToString("\n"),
                        )
                    }
                }
            }

            val relation_rows = contact.relation_entries()
            if (relation_rows.isNotEmpty()) {
                val relation_names = relation_kind_names()
                detail_section(stringResource(R.string.contact_relations)) {
                    relation_rows.forEachIndexed { index, relation ->
                        if (index > 0) detail_row_divider()
                        val kind_name = relation_display_name(relation, relation_names)
                        DetailRow(
                            label = kind_name,
                            value = relation.value,
                            a11y_text = "$kind_name: ${relation.value}",
                        )
                    }
                }
            }

            val social_rows = contact.social_entries()
            val has_social = contact.website.isNotBlank() ||
                contact.twitter.isNotBlank() ||
                contact.linkedin.isNotBlank() ||
                social_rows.isNotEmpty()
            if (has_social) {
                fun open_contact_url(url: String) {
                    val uri = Uri.parse(url)
                    if (uri.scheme?.lowercase() !in setOf("http", "https")) return
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        org.astermail.android.ui.common.app_toast.show(context.getString(R.string.could_not_open_link).toString())
                    }
                }
                detail_section(stringResource(R.string.social)) {
                    if (contact.website.isNotBlank()) {
                        val url = build_contact_social_url("website", contact.website)
                        DetailRow(
                            stringResource(R.string.website),
                            contact.website,
                            on_open = url?.let { { open_contact_url(it) } },
                            on_copy = copy_value,
                        )
                    }
                    if (contact.website.isNotBlank() && contact.twitter.isNotBlank()) {
                        detail_row_divider()
                    }
                    if (contact.twitter.isNotBlank()) {
                        val url = build_contact_social_url("twitter", contact.twitter)
                        DetailRow(
                            stringResource(R.string.twitter),
                            contact.twitter,
                            on_open = url?.let { { open_contact_url(it) } },
                            on_copy = copy_value,
                        )
                    }
                    if ((contact.website.isNotBlank() || contact.twitter.isNotBlank()) && contact.linkedin.isNotBlank()) {
                        detail_row_divider()
                    }
                    if (contact.linkedin.isNotBlank()) {
                        val url = build_contact_social_url("linkedin", contact.linkedin)
                        DetailRow(
                            stringResource(R.string.linkedin),
                            contact.linkedin,
                            on_open = url?.let { { open_contact_url(it) } },
                            on_copy = copy_value,
                        )
                    }
                    val social_names = social_type_names()
                    val has_fixed_social = contact.website.isNotBlank() ||
                        contact.twitter.isNotBlank() ||
                        contact.linkedin.isNotBlank()
                    social_rows.forEachIndexed { index, social ->
                        if (index > 0 || has_fixed_social) detail_row_divider()
                        val url = safe_social_url(social)
                        val name = social_display_name(social, social_names)
                        DetailRow(
                            label = name,
                            value = social.value,
                            on_open = url?.let { { open_contact_url(it) } },
                            on_copy = copy_value,
                            a11y_text = "$name: ${social.value}",
                        )
                    }
                }
            }

            if (contact.notes.isNotBlank()) {
                detail_section(stringResource(R.string.notes)) {
                    SelectionContainer {
                        Text(
                            text = contact.notes,
                            color = colors.text_primary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(
                                horizontal = AsterSpacing.lg,
                                vertical = AsterSpacing.md,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(AsterSpacing.xl))
            Box(modifier = Modifier.padding(horizontal = AsterSpacing.lg)) {
                AsterDestructiveButton(
                    label = stringResource(R.string.delete_contact),
                    onClick = { if (!delete_requested) show_delete_confirm = true },
                    is_loading = delete_requested,
                )
            }
            if (show_delete_confirm) {
                org.astermail.android.design.components.AsterAlertDialog(
                    on_dismiss = { show_delete_confirm = false },
                    title = stringResource(R.string.move_to_trash),
                    message = stringResource(R.string.contact_trash_confirm_message),
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
    val reduce_motion = aster_reduce_motion()
    val interaction_source = remember { MutableInteractionSource() }
    val pressed by interaction_source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce_motion) 0.96f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) AsterDuration.tap_down else AsterDuration.tap_up,
            easing = if (pressed) AsterEasing.tap_down else AsterEasing.tap_up,
        ),
        label = "quick_action_scale",
    )
    val resting = org.astermail.android.design.control_surface_color(colors)
    val container by animateColorAsState(
        targetValue = if (pressed) {
            colors.text_primary.copy(alpha = 0.08f).compositeOver(resting)
        } else {
            resting
        },
        animationSpec = tween(durationMillis = AsterDuration.tap_up, easing = AsterEasing.standard),
        label = "quick_action_container",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(quick_action_height)
            .clip(org.astermail.android.design.AsterShapes.control)
            .background(container)
            .clickable(
                interactionSource = interaction_source,
                indication = ripple(color = colors.text_secondary),
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = on_click,
            )
            .padding(horizontal = AsterSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.text_primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val quick_action_height = 68.dp

private val contact_header_avatar_size = 64.dp

@Composable
private fun detail_section(label: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(AsterRadius.xl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
    ) {
        Text(
            text = label,
            color = colors.text_tertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = AsterSpacing.md, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .acrylic(colors, shape, colors.bg_secondary),
            content = content,
        )
    }
}

@Composable
private fun detail_row_divider() {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AsterSpacing.lg)
            .height(1.dp)
            .background(colors.border_secondary),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailRow(
    label: String,
    value: String,
    on_open: (() -> Unit)? = null,
    on_copy: ((String) -> Unit)? = null,
    a11y_text: String? = null,
) {
    val colors = AsterMaterial.colors
    val interactive = on_open != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (a11y_text != null) {
                    Modifier.semantics(mergeDescendants = true) { contentDescription = a11y_text }
                } else {
                    Modifier
                },
            )
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        onClick = { on_open?.invoke() },
                        onLongClick = on_copy?.let { copy -> { copy(value) } },
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
    ) {
        Text(
            text = label,
            color = colors.text_tertiary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(2.dp))
        if (interactive) {
            Text(
                text = value,
                color = colors.accent_blue,
                fontSize = 15.sp,
            )
        } else {
            SelectionContainer {
                Text(
                    text = value,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

private val social_url_scheme_pattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

private fun build_contact_social_url(kind: String, raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val has_scheme = social_url_scheme_pattern.containsMatchIn(value)
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
