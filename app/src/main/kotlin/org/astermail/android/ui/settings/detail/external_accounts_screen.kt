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

import android.content.ClipData
import org.astermail.android.ui.common.show_copy_result_toast
import org.astermail.android.ui.common.write_to_clipboard
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.CircleX
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Trash
import org.astermail.android.R
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogDestructiveButton
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.imports.ExternalAccountsError
import org.astermail.android.imports.ExternalAccountsViewModel
import org.astermail.android.ui.common.app_toast
import org.astermail.android.ui.common.show_copied_toast
import org.astermail.android.ui.common.start_external_intent
import org.astermail.android.util.ascii_digits

@Composable
fun ExternalAccountsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    start_gmail_wizard: Boolean = false,
    vm: ExternalAccountsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sync_started_message = stringResource(R.string.ext_sync_started)
    val sync_failed_message = stringResource(R.string.ext_error_sync_failed)
    val account_removed_message = stringResource(R.string.ext_account_removed)
    val account_remove_failed_message = stringResource(R.string.ext_error_delete_failed)
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(state.authorize_url) {
        val url = state.authorize_url
        if (!url.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            start_external_intent(context, intent)
            vm.consume_authorize_url()
        }
    }

    var manual_open by rememberSaveable { mutableStateOf(false) }
    var confirm_delete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleting_token by remember { mutableStateOf<String?>(null) }
    var delete_messages_on_disconnect by remember { mutableStateOf(false) }
    var imap_email by rememberSaveable { mutableStateOf("") }
    var imap_host by rememberSaveable { mutableStateOf("") }
    var imap_port by rememberSaveable { mutableStateOf("993") }
    var imap_user by rememberSaveable { mutableStateOf("") }
    var imap_pass by remember { mutableStateOf("") }
    var smtp_host by rememberSaveable { mutableStateOf("") }
    var smtp_port by rememberSaveable { mutableStateOf("587") }
    var smtp_user by rememberSaveable { mutableStateOf("") }
    var smtp_pass by remember { mutableStateOf("") }
    var imap_pass_visible by remember { mutableStateOf(false) }
    var smtp_pass_visible by remember { mutableStateOf(false) }
    var smtp_same_as_incoming by rememberSaveable { mutableStateOf(true) }
    var server_settings_open by rememberSaveable { mutableStateOf(false) }
    var gmail_wizard_open by rememberSaveable { mutableStateOf(start_gmail_wizard) }
    var expanded_error_token by remember { mutableStateOf<String?>(null) }
    var editing_token by remember { mutableStateOf<String?>(null) }
    var edit_host by remember { mutableStateOf("") }
    var edit_port by remember { mutableStateOf("993") }
    var edit_user by remember { mutableStateOf("") }
    var edit_pass by remember { mutableStateOf("") }
    var edit_smtp_host by remember { mutableStateOf("") }
    var edit_smtp_port by remember { mutableStateOf("587") }
    var edit_smtp_user by remember { mutableStateOf("") }
    var edit_pass_visible by remember { mutableStateOf(false) }
    val account_updated_message = stringResource(R.string.ext_account_updated)
    val account_update_failed_message = stringResource(R.string.ext_error_update_failed)

    LaunchedEffect(state.manual_success) {
        if (state.manual_success && gmail_wizard_open) {
            gmail_wizard_open = false
            imap_email = ""
            imap_pass = ""
        }
    }

    detail_scaffold(title = stringResource(R.string.external_accounts), on_back = on_back) {
        if (plan_vm.is_feature_locked("has_external_accounts") && !plan_state.is_loading) {
            UpgradeGate(
                title = stringResource(R.string.external_accounts),
                description = stringResource(R.string.external_accounts_paywall_description),
                plan_name = "Star",
                on_upgrade = { on_open("billing") },
                requires_label = stringResource(R.string.requires_plan, "Star"),
                button_label = stringResource(R.string.upgrade),
            )
            return@detail_scaffold
        }
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.connect_external),
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(AsterSpacing.xs))
                Text(
                    text = stringResource(R.string.external_description),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.ext_section_oauth))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.ext_oauth_body),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.size(AsterSpacing.md))
                oauth_provider_row(
                    icon_res = R.drawable.ic_brand_outlook,
                    label = stringResource(R.string.ext_oauth_connect_microsoft),
                    enabled = state.connecting_provider == null,
                    is_loading = state.connecting_provider == "microsoft",
                    on_click = { vm.start_oauth("microsoft") },
                )
                Spacer(Modifier.size(AsterSpacing.sm))
                oauth_provider_row(
                    icon_res = R.drawable.ic_brand_yahoo,
                    label = stringResource(R.string.ext_oauth_connect_yahoo),
                    enabled = state.connecting_provider == null,
                    is_loading = state.connecting_provider == "yahoo",
                    on_click = { vm.start_oauth("yahoo") },
                )
                if (state.connecting_provider != null) {
                    Spacer(Modifier.size(AsterSpacing.md))
                    AsterButton(
                        label = stringResource(R.string.cancel),
                        onClick = { vm.cancel_oauth() },
                    )
                }
            }
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.ext_section_manual))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                if (!manual_open) {
                    oauth_provider_row(
                        icon_res = R.drawable.ic_brand_gmail,
                        label = stringResource(R.string.ext_gmail_wizard_title),
                        enabled = true,
                        is_loading = false,
                        on_click = { gmail_wizard_open = true },
                    )
                    Spacer(Modifier.size(AsterSpacing.md))
                    AsterButton(
                        label = stringResource(R.string.ext_add_manual_imap),
                        onClick = { manual_open = true },
                    )
                } else {
                    val preset = remember(imap_email) { external_provider_preset(imap_email) }
                    val app_password_url = preset?.app_password_url

                    AsterTextField(
                        value = imap_email,
                        onValueChange = { value ->
                            imap_email = value
                            val trimmed = value.trim()
                            imap_user = trimmed
                            if (!smtp_same_as_incoming) smtp_user = trimmed
                            val matched = external_provider_preset(value)
                            if (imap_host.isBlank() || is_external_preset_host(imap_host)) {
                                imap_host = matched?.host.orEmpty()
                                imap_port = (matched?.port ?: 993).toString()
                                smtp_host = matched?.smtp_host.orEmpty()
                                smtp_port = (matched?.smtp_port ?: 587).toString()
                            }
                        },
                        modifier = Modifier.onFocusChanged { focus ->
                            if (!focus.isFocused &&
                                imap_email.contains("@") &&
                                external_provider_preset(imap_email) == null
                            ) {
                                server_settings_open = true
                            }
                        },
                        label = stringResource(R.string.ext_field_email),
                        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Email),
                        content_type = ContentType.EmailAddress,
                    )
                    v_gap(AsterSpacing.sm)
                    AsterTextField(
                        value = imap_pass,
                        onValueChange = { imap_pass = it },
                        label = stringResource(R.string.ext_field_password),
                        helper_text = if (app_password_url != null) {
                            stringResource(R.string.ext_app_password_required)
                        } else {
                            null
                        },
                        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visual_transformation = if (imap_pass_visible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailing_icon = {
                            AsterIconButton(
                                icon = if (imap_pass_visible) TablerIcons.EyeOff else TablerIcons.Eye,
                                content_description = stringResource(
                                    if (imap_pass_visible) R.string.hide_password else R.string.show_password,
                                ),
                                onClick = { imap_pass_visible = !imap_pass_visible },
                                tint = colors.text_muted,
                            )
                        },
                        content_type = ContentType.Password,
                    )
                    if (app_password_url != null) {
                        v_gap(AsterSpacing.sm)
                        AsterButton(
                            label = stringResource(R.string.ext_app_password_create),
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(app_password_url))

                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                start_external_intent(context, intent)
                            },
                        )
                    }
                    v_gap(AsterSpacing.md)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { server_settings_open = !server_settings_open }
                            .padding(vertical = AsterSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.ext_server_settings),
                            color = colors.text_primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (server_settings_open) {
                                TablerIcons.ChevronUp
                            } else {
                                TablerIcons.ChevronDown
                            },
                            contentDescription = null,
                            tint = colors.text_muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (server_settings_open) {
                        Text(
                            text = stringResource(R.string.ext_server_settings_hint),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                        )
                        v_gap(AsterSpacing.sm)
                        AsterTextField(
                            value = imap_host,
                            onValueChange = { imap_host = it },
                            label = stringResource(R.string.ext_imap_host),
                        )
                        v_gap(AsterSpacing.xs)
                        AsterTextField(
                            value = imap_port,
                            onValueChange = { imap_port = ascii_digits(it) },
                            label = stringResource(R.string.ext_imap_port),
                            keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        v_gap(AsterSpacing.xs)
                        AsterTextField(
                            value = imap_user,
                            onValueChange = { imap_user = it },
                            label = stringResource(R.string.ext_imap_username),
                        )
                        v_gap(AsterSpacing.xs)
                        AsterTextField(
                            value = smtp_host,
                            onValueChange = { smtp_host = it },
                            label = stringResource(R.string.ext_smtp_host),
                        )
                        v_gap(AsterSpacing.xs)
                        AsterTextField(
                            value = smtp_port,
                            onValueChange = { smtp_port = ascii_digits(it) },
                            label = stringResource(R.string.ext_smtp_port),
                            keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        v_gap(AsterSpacing.sm)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.ext_smtp_same_as_incoming),
                                color = colors.text_primary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            AsterSwitch(
                                checked = smtp_same_as_incoming,
                                onCheckedChange = { checked ->
                                    smtp_same_as_incoming = checked
                                    if (!checked && smtp_user.isBlank()) smtp_user = imap_user.trim()
                                },
                            )
                        }
                        if (!smtp_same_as_incoming) {
                            v_gap(AsterSpacing.xs)
                            AsterTextField(
                                value = smtp_user,
                                onValueChange = { smtp_user = it },
                                label = stringResource(R.string.ext_smtp_username),
                            )
                            v_gap(AsterSpacing.xs)
                            AsterTextField(
                                value = smtp_pass,
                                onValueChange = { smtp_pass = it },
                                label = stringResource(R.string.ext_smtp_password),
                                keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visual_transformation = if (smtp_pass_visible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailing_icon = {
                                    AsterIconButton(
                                        icon = if (smtp_pass_visible) TablerIcons.EyeOff else TablerIcons.Eye,
                                        content_description = stringResource(
                                            if (smtp_pass_visible) R.string.hide_password else R.string.show_password,
                                        ),
                                        onClick = { smtp_pass_visible = !smtp_pass_visible },
                                        tint = colors.text_muted,
                                    )
                                },
                                content_type = ContentType.Password,
                            )
                        }
                    }
                    v_gap(AsterSpacing.md)
                    AsterButton(
                        label = stringResource(R.string.ext_save_imap),
                        onClick = {
                            val email = imap_email.trim()
                            val incoming_host = imap_host.trim().ifBlank { preset?.host.orEmpty() }
                            val outgoing_host = smtp_host.trim().ifBlank { preset?.smtp_host.orEmpty() }
                            val incoming_user = imap_user.trim().ifBlank { email }
                            val incoming_password = normalize_app_password(incoming_host, imap_pass)

                            vm.submit_manual_imap(
                                email = email,
                                host = incoming_host,
                                port = imap_port.toIntOrNull() ?: preset?.port ?: 993,
                                username = incoming_user,
                                password = incoming_password,
                                use_tls = preset?.use_tls ?: true,
                                smtp_host = outgoing_host,
                                smtp_port = smtp_port.toIntOrNull() ?: preset?.smtp_port ?: 587,
                                smtp_username = if (smtp_same_as_incoming) {
                                    incoming_user
                                } else {
                                    smtp_user.trim()
                                },
                                smtp_password = if (smtp_same_as_incoming) {
                                    incoming_password
                                } else {
                                    normalize_app_password(outgoing_host, smtp_pass)
                                },
                            )
                        },
                        enabled = imap_email.contains("@") &&
                            imap_pass.isNotBlank() &&
                            (imap_host.isNotBlank() || preset != null) &&
                            (imap_port.isEmpty() || (imap_port.toIntOrNull() ?: 0) in 1..65535) &&
                            (smtp_port.isEmpty() || (smtp_port.toIntOrNull() ?: 0) in 1..65535) &&
                            !state.manual_submitting,
                        is_loading = state.manual_submitting,
                    )
                    if (state.manual_success) {
                        v_gap(AsterSpacing.sm)
                        Text(
                            text = stringResource(R.string.ext_manual_saved),
                            color = colors.success,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.ext_section_accounts))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                if (state.accounts.isEmpty() && state.error == ExternalAccountsError.LOAD_FAILED) {
                    Text(
                        text = stringResource(R.string.ext_error_load_failed),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.size(AsterSpacing.md))
                    AsterButton(
                        label = stringResource(R.string.retry),
                        onClick = { vm.load() },
                    )
                } else if (state.accounts.isEmpty() && state.loading) {
                    CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(20.dp))
                } else if (state.accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ext_no_accounts),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                } else {
                    for ((index, acct) in state.accounts.withIndex()) {
                        val decrypted_email = state.decrypted[acct.account_token]?.email
                        val account_email = acct.oauth_email ?: decrypted_email
                        val display_label = account_email ?: when (acct.protocol) {
                            "oauth_google", "google" -> stringResource(R.string.ext_label_google_account)
                            "oauth_microsoft", "microsoft" -> stringResource(R.string.ext_label_microsoft_account)
                            "oauth_yahoo", "yahoo" -> stringResource(R.string.ext_label_yahoo_account)
                            "oauth_imap", "imap" -> stringResource(R.string.ext_label_imap_account)
                            else -> acct.protocol.ifBlank { stringResource(R.string.ext_label_linked_account) }
                        }
                        val is_syncing = acct.account_token in state.syncing_tokens
                        val is_toggling = acct.account_token in state.toggling_tokens
                        val has_error = acct.last_sync_status.equals("error", ignoreCase = true)

                        if (index > 0) {
                            v_gap(AsterSpacing.md)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.border_thread_divider),
                            )
                            v_gap(AsterSpacing.md)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    hapticFeedbackEnabled = false,
                                    onClick = {
                                        if (!account_email.isNullOrBlank()) {
                                            copy_account_email(context, account_email)
                                        }
                                    },
                                    onLongClick = {
                                        if (!account_email.isNullOrBlank()) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            copy_account_email(context, account_email)
                                        }
                                    },
                                )
                                .padding(vertical = AsterSpacing.xs),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(
                                        provider_icon_res(
                                            oauth_provider = acct.oauth_provider,
                                            email = account_email,
                                            protocol = acct.protocol,
                                        ),
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(AsterSpacing.sm))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(SquircleShape(4.dp))
                                        .background(
                                            sync_health_color(
                                                needs_reauth = acct.needs_reauth,
                                                has_error = has_error,
                                                has_synced = !acct.last_sync_at.isNullOrBlank(),
                                            ),
                                        ),
                                )
                                Spacer(Modifier.width(AsterSpacing.sm))
                                Text(
                                    text = display_label,
                                    color = colors.text_primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                protocol_label(acct.protocol)?.let { protocol ->
                                    Spacer(Modifier.width(AsterSpacing.sm))
                                    account_badge(label = protocol, tone = colors.text_muted)
                                }
                                Spacer(Modifier.width(AsterSpacing.sm))
                                account_badge(
                                    label = stringResource(
                                        if (acct.is_enabled) R.string.active else R.string.ext_status_paused,
                                    ),
                                    tone = if (acct.is_enabled) colors.success else colors.text_muted,
                                )
                            }
                            v_gap(AsterSpacing.xs)
                            val sync_summary = when {
                                is_syncing -> stringResource(R.string.syncing)
                                acct.needs_reauth -> stringResource(R.string.ext_password_reauth)
                                has_error -> stringResource(R.string.ext_sync_error_short)
                                acct.last_sync_at.isNullOrBlank() -> stringResource(R.string.ext_never_synced)
                                else -> {
                                    val pretty = remember(acct.last_sync_at) {
                                        runCatching {
                                            val instant = java.time.Instant.parse(acct.last_sync_at)
                                            val zoned = instant.atZone(
                                                org.astermail.android.ui.mail.AsterTimePreferences.account_zone_id(),
                                            )
                                            zoned.format(
                                                java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                                                    java.time.format.FormatStyle.MEDIUM,
                                                    java.time.format.FormatStyle.SHORT,
                                                ),
                                            )
                                        }.getOrDefault(acct.last_sync_at.orEmpty())
                                    }
                                    stringResource(R.string.ext_last_sync, pretty)
                                }
                            }
                            val status_tone = when {
                                is_syncing -> colors.accent_blue
                                acct.needs_reauth -> colors.warning
                                has_error -> colors.danger
                                acct.last_sync_at.isNullOrBlank() -> colors.text_tertiary
                                else -> colors.success
                            }
                            val error_is_expandable = has_error && !is_syncing &&
                                !acct.needs_reauth && !acct.last_sync_error.isNullOrBlank()
                            val error_expanded = expanded_error_token == acct.account_token
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = if (error_is_expandable) {
                                    Modifier.clickable {
                                        expanded_error_token = if (error_expanded) {
                                            null
                                        } else {
                                            acct.account_token
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ) {
                                if (is_syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = colors.accent_blue,
                                        strokeWidth = 1.5.dp,
                                    )
                                    Spacer(Modifier.width(AsterSpacing.xs))
                                } else {
                                    val status_icon = when {
                                        acct.needs_reauth -> TablerIcons.AlertTriangle
                                        has_error -> TablerIcons.CircleX
                                        acct.last_sync_at.isNullOrBlank() -> null
                                        else -> TablerIcons.CircleCheck
                                    }
                                    if (status_icon != null) {
                                        Icon(
                                            imageVector = status_icon,
                                            contentDescription = null,
                                            tint = status_tone,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(AsterSpacing.xs))
                                    }
                                }
                                Text(
                                    text = sync_summary,
                                    color = status_tone,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (error_is_expandable) {
                                    Spacer(Modifier.width(AsterSpacing.xs))
                                    Icon(
                                        imageVector = if (error_expanded) {
                                            TablerIcons.ChevronUp
                                        } else {
                                            TablerIcons.ChevronDown
                                        },
                                        contentDescription = stringResource(
                                            if (error_expanded) {
                                                R.string.phishing_hide_details
                                            } else {
                                                R.string.phishing_show_details
                                            },
                                        ),
                                        tint = colors.danger,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                if (acct.email_count > 0) {
                                    Spacer(Modifier.width(AsterSpacing.sm))
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.rules_emails_count,
                                            acct.email_count,
                                            acct.email_count,
                                        ),
                                        color = colors.text_muted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            if (error_is_expandable && error_expanded) {
                                v_gap(AsterSpacing.xs)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(SquircleShape(10.dp))
                                        .background(colors.bg_tertiary)
                                        .padding(AsterSpacing.md),
                                ) {
                                    Text(
                                        text = clean_sync_error(acct.last_sync_error.orEmpty()),
                                        color = colors.danger,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                            v_gap(AsterSpacing.sm)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
                            ) {
                                AsterSwitch(
                                    checked = acct.is_enabled,
                                    onCheckedChange = { checked ->
                                        vm.toggle_account(acct.account_token, checked)
                                    },
                                    enabled = !is_toggling,
                                )
                                Spacer(Modifier.weight(1f))
                                if (is_syncing) {
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            color = colors.accent_blue,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                } else {
                                    AsterIconButton(
                                        icon = TablerIcons.Refresh,
                                        content_description = stringResource(R.string.ext_sync_now),
                                        onClick = {
                                            vm.trigger_sync(acct.account_token) { ok ->
                                                app_toast.show(
                                                    if (ok) sync_started_message else sync_failed_message,
                                                )
                                            }
                                        },
                                        enabled = acct.is_enabled,
                                        tint = colors.text_secondary,
                                    )
                                }
                                AsterIconButton(
                                    icon = TablerIcons.Pencil,
                                    content_description = stringResource(R.string.ext_edit_account),
                                    onClick = {
                                        if (editing_token == acct.account_token) {
                                            editing_token = null
                                        } else {
                                            editing_token = acct.account_token
                                            edit_pass = ""
                                            edit_pass_visible = false
                                            vm.load_connection_settings(acct.account_token)
                                        }
                                    },
                                    tint = colors.text_secondary,
                                )
                                AsterIconButton(
                                    icon = TablerIcons.Trash,
                                    content_description = stringResource(R.string.ext_remove_account),
                                    onClick = { confirm_delete = acct.account_token to display_label },
                                    enabled = deleting_token != acct.account_token,
                                    tint = colors.danger,
                                )
                            }
                            if (editing_token == acct.account_token) {
                                val settings = state.connection_settings[acct.account_token]

                                LaunchedEffect(settings) {
                                    if (settings != null) {
                                        edit_host = settings.host
                                        edit_port = settings.port.toString()
                                        edit_user = settings.username
                                        edit_smtp_host = settings.smtp_host
                                        edit_smtp_port = settings.smtp_port.toString()
                                        edit_smtp_user = settings.smtp_username
                                    }
                                }
                                v_gap(AsterSpacing.md)
                                if (settings == null) {
                                    CircularProgressIndicator(
                                        color = colors.accent_blue,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.ext_edit_title),
                                        color = colors.text_primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    v_gap(AsterSpacing.sm)
                                    AsterTextField(
                                        value = edit_pass,
                                        onValueChange = { edit_pass = it },
                                        label = stringResource(R.string.ext_field_password),
                                        helper_text = stringResource(R.string.ext_password_keep),
                                        keyboard_options = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                        ),
                                        visual_transformation = if (edit_pass_visible) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        trailing_icon = {
                                            AsterIconButton(
                                                icon = if (edit_pass_visible) {
                                                    TablerIcons.EyeOff
                                                } else {
                                                    TablerIcons.Eye
                                                },
                                                content_description = stringResource(
                                                    if (edit_pass_visible) {
                                                        R.string.hide_password
                                                    } else {
                                                        R.string.show_password
                                                    },
                                                ),
                                                onClick = { edit_pass_visible = !edit_pass_visible },
                                                tint = colors.text_muted,
                                            )
                                        },
                                        content_type = ContentType.Password,
                                    )
                                    v_gap(AsterSpacing.sm)
                                    AsterTextField(
                                        value = edit_host,
                                        onValueChange = { edit_host = it },
                                        label = stringResource(R.string.ext_imap_host),
                                    )
                                    v_gap(AsterSpacing.xs)
                                    AsterTextField(
                                        value = edit_port,
                                        onValueChange = { edit_port = ascii_digits(it) },
                                        label = stringResource(R.string.ext_imap_port),
                                        keyboard_options = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                        ),
                                    )
                                    v_gap(AsterSpacing.xs)
                                    AsterTextField(
                                        value = edit_user,
                                        onValueChange = { edit_user = it },
                                        label = stringResource(R.string.ext_imap_username),
                                    )
                                    v_gap(AsterSpacing.xs)
                                    AsterTextField(
                                        value = edit_smtp_host,
                                        onValueChange = { edit_smtp_host = it },
                                        label = stringResource(R.string.ext_smtp_host),
                                    )
                                    v_gap(AsterSpacing.xs)
                                    AsterTextField(
                                        value = edit_smtp_port,
                                        onValueChange = { edit_smtp_port = ascii_digits(it) },
                                        label = stringResource(R.string.ext_smtp_port),
                                        keyboard_options = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                        ),
                                    )
                                    v_gap(AsterSpacing.xs)
                                    AsterTextField(
                                        value = edit_smtp_user,
                                        onValueChange = { edit_smtp_user = it },
                                        label = stringResource(R.string.ext_smtp_username),
                                    )
                                    v_gap(AsterSpacing.md)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                                    ) {
                                        AsterButton(
                                            label = stringResource(R.string.ext_save_changes),
                                            onClick = {
                                                val token = acct.account_token
                                                val host = edit_host.trim()
                                                val smtp = edit_smtp_host.trim()

                                                vm.submit_update(
                                                    account_token = token,
                                                    email = account_email.orEmpty(),
                                                    host = host,
                                                    port = edit_port.toIntOrNull() ?: 993,
                                                    username = edit_user.trim(),
                                                    password = normalize_app_password(host, edit_pass),
                                                    use_tls = settings.use_tls,
                                                    smtp_host = smtp,
                                                    smtp_port = edit_smtp_port.toIntOrNull() ?: 587,
                                                    smtp_username = edit_smtp_user.trim(),
                                                    smtp_password = normalize_app_password(smtp, edit_pass),
                                                ) { ok ->
                                                    if (ok) editing_token = null
                                                    app_toast.show(
                                                        if (ok) {
                                                            account_updated_message
                                                        } else {
                                                            account_update_failed_message
                                                        },
                                                    )
                                                }
                                            },
                                            enabled = edit_host.isNotBlank() &&
                                                (edit_port.toIntOrNull() ?: 0) in 1..65535 &&
                                                (edit_smtp_port.toIntOrNull() ?: 0) in 1..65535 &&
                                                state.updating_token != acct.account_token,
                                            is_loading = state.updating_token == acct.account_token,
                                        )
                                        AsterButton(
                                            label = stringResource(R.string.cancel),
                                            onClick = { editing_token = null },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        state.error?.takeIf { it != ExternalAccountsError.LOAD_FAILED || state.accounts.isNotEmpty() }?.let { err ->
            v_gap(AsterSpacing.md)
            val msg = when (err) {
                ExternalAccountsError.LOAD_FAILED -> stringResource(R.string.ext_error_load_failed)
                ExternalAccountsError.OAUTH_FAILED -> stringResource(R.string.ext_error_oauth_failed)
                ExternalAccountsError.MANUAL_FAILED -> stringResource(R.string.ext_error_manual_failed)
                ExternalAccountsError.NO_SESSION_KEY -> stringResource(R.string.ext_error_no_session)
                ExternalAccountsError.DELETE_FAILED -> stringResource(R.string.ext_error_delete_failed)
                ExternalAccountsError.SYNC_FAILED -> stringResource(R.string.ext_error_sync_failed)
                ExternalAccountsError.TOGGLE_FAILED -> stringResource(R.string.ext_error_toggle_failed)
                ExternalAccountsError.UPDATE_FAILED -> stringResource(R.string.ext_error_update_failed)
            }
            error_banner(msg)
        }

        v_gap(AsterSpacing.xxl)
    }

    if (gmail_wizard_open) {
        gmail_setup_wizard(
            email = imap_email,
            password = imap_pass,
            on_email_change = { imap_email = it },
            on_password_change = { imap_pass = it },
            on_dismiss = { gmail_wizard_open = false },
            on_connect = {
                val email = imap_email.trim()
                val preset = external_provider_preset(email)
                val incoming_host = preset?.host ?: "imap.gmail.com"
                val outgoing_host = preset?.smtp_host ?: "smtp.gmail.com"
                val incoming_password = normalize_app_password(incoming_host, imap_pass)

                vm.submit_manual_imap(
                    email = email,
                    host = incoming_host,
                    port = preset?.port ?: 993,
                    username = email,
                    password = incoming_password,
                    use_tls = preset?.use_tls ?: true,
                    smtp_host = outgoing_host,
                    smtp_port = preset?.smtp_port ?: 587,
                    smtp_username = email,
                    smtp_password = incoming_password,
                )
            },
            is_submitting = state.manual_submitting,
        )
    }

    confirm_delete?.let { (token, label) ->
        val is_disconnecting = deleting_token == token
        AsterDialog(
            on_dismiss = { if (!is_disconnecting) confirm_delete = null },
            title = stringResource(R.string.ext_disconnect_title),
            body = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = label,
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    v_gap(AsterSpacing.xs)
                    Text(
                        text = stringResource(R.string.ext_disconnect_confirm),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    v_gap(AsterSpacing.md)
                    acknowledge_row(
                        checked = delete_messages_on_disconnect,
                        label = stringResource(R.string.ext_disconnect_delete_messages),
                        on_change = { checked ->
                            if (!is_disconnecting) delete_messages_on_disconnect = checked
                        },
                    )
                }
            },
            footer = {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { confirm_delete = null },
                    enabled = !is_disconnecting,
                )
                AsterDialogDestructiveButton(
                    label = stringResource(R.string.ext_disconnect_button),
                    onClick = {
                        val also_delete = delete_messages_on_disconnect
                        deleting_token = token
                        vm.delete_account(token, also_delete) { ok ->
                            deleting_token = null
                            confirm_delete = null
                            delete_messages_on_disconnect = false
                            app_toast.show(
                                if (ok) account_removed_message else account_remove_failed_message,
                            )
                        }
                    },
                    enabled = !is_disconnecting,
                    is_loading = is_disconnecting,
                )
            },
        )
    }
}

@Composable
private fun sync_health_color(
    needs_reauth: Boolean,
    has_error: Boolean,
    has_synced: Boolean,
) = when {
    needs_reauth -> AsterMaterial.colors.warning
    has_error -> AsterMaterial.colors.danger
    has_synced -> AsterMaterial.colors.success
    else -> AsterMaterial.colors.text_muted
}

@Composable
private fun account_badge(label: String, tone: androidx.compose.ui.graphics.Color) {
    val shape = SquircleShape(7.dp)
    Text(
        text = label,
        color = tone,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(shape)
            .background(tone.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun protocol_label(protocol: String?): String? = when (protocol?.lowercase()) {
    "imap", "oauth_imap" -> "IMAP"
    "jmap" -> "JMAP"
    "pop3" -> "POP3"
    else -> null
}

private val imap_auth_prefix = Regex("^IMAP authentication failed:\\s*", RegexOption.IGNORE_CASE)

internal fun clean_sync_error(message: String): String =
    imap_auth_prefix.replace(message.trim(), "")

@Composable
private fun oauth_provider_row(
    @DrawableRes icon_res: Int,
    label: String,
    enabled: Boolean,
    is_loading: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.text_tertiary.copy(alpha = 0.18f), shape)
            .clickable(enabled = enabled && !is_loading, onClick = on_click)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(SquircleShape(8.dp))
                .background(colors.bg_tertiary),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = icon_res),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.size(AsterSpacing.md))
        Text(
            text = label,
            color = colors.text_primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (is_loading) {
            CircularProgressIndicator(
                color = colors.accent_blue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@DrawableRes
internal fun provider_icon_res(oauth_provider: String?, email: String?, protocol: String?): Int {
    when (oauth_provider?.lowercase()) {
        "google" -> return R.drawable.ic_brand_gmail
        "microsoft" -> return R.drawable.ic_brand_outlook
        "yahoo" -> return R.drawable.ic_brand_yahoo
        "apple", "icloud" -> return R.drawable.ic_brand_icloud
    }
    when (protocol?.lowercase()) {
        "oauth_google", "google" -> return R.drawable.ic_brand_gmail
        "oauth_microsoft", "microsoft" -> return R.drawable.ic_brand_outlook
        "oauth_yahoo", "yahoo" -> return R.drawable.ic_brand_yahoo
    }
    val lower = email?.lowercase().orEmpty()
    return when {
        lower.contains("gmail") || lower.contains("google") -> R.drawable.ic_brand_gmail
        lower.contains("outlook") || lower.contains("hotmail") || lower.contains("live") -> R.drawable.ic_brand_outlook
        lower.contains("yahoo") -> R.drawable.ic_brand_yahoo
        lower.contains("icloud") || lower.contains("me.com") || lower.contains("mac.com") -> R.drawable.ic_brand_icloud
        else -> R.drawable.ic_brand_imap
    }
}

private fun copy_account_email(context: Context, email: String) {
    val copied = write_to_clipboard(context, ClipData.newPlainText("account", email))
    show_copy_result_toast(context, email, copied)
}
