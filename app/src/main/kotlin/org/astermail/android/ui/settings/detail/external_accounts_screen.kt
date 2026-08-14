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
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.imports.ExternalAccountsError
import org.astermail.android.imports.ExternalAccountsViewModel
import org.astermail.android.ui.common.app_toast
import org.astermail.android.ui.common.show_copied_toast

@Composable
fun ExternalAccountsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
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
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            vm.consume_authorize_url()
        }
    }

    var manual_open by remember { mutableStateOf(false) }
    var imap_email by remember { mutableStateOf("") }
    var imap_host by remember { mutableStateOf("") }
    var imap_port by remember { mutableStateOf("993") }
    var imap_user by remember { mutableStateOf("") }
    var imap_pass by remember { mutableStateOf("") }
    var smtp_host by remember { mutableStateOf("") }
    var smtp_port by remember { mutableStateOf("587") }
    var smtp_user by remember { mutableStateOf("") }
    var smtp_pass by remember { mutableStateOf("") }

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
                    icon_res = R.drawable.ic_brand_gmail,
                    label = stringResource(R.string.ext_oauth_connect_google),
                    enabled = state.connecting_provider == null,
                    is_loading = state.connecting_provider == "google",
                    on_click = { vm.start_oauth("google") },
                )
                Spacer(Modifier.size(AsterSpacing.sm))
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
            }
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.ext_section_manual))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                if (!manual_open) {
                    AsterButton(
                        label = stringResource(R.string.ext_add_manual_imap),
                        onClick = { manual_open = true },
                    )
                } else {
                    AsterTextField(
                        value = imap_email,
                        onValueChange = { imap_email = it },
                        placeholder = stringResource(R.string.ext_imap_email),
                        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    v_gap(AsterSpacing.xs)
                    AsterTextField(
                        value = imap_host,
                        onValueChange = { imap_host = it },
                        placeholder = stringResource(R.string.ext_imap_host),
                    )
                    v_gap(AsterSpacing.xs)
                    AsterTextField(
                        value = imap_port,
                        onValueChange = { imap_port = it.filter { ch -> ch.isDigit() } },
                        placeholder = stringResource(R.string.ext_imap_port),
                        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    v_gap(AsterSpacing.xs)
                    AsterTextField(
                        value = imap_user,
                        onValueChange = { imap_user = it },
                        placeholder = stringResource(R.string.ext_imap_username),
                    )
                    v_gap(AsterSpacing.xs)
                    AsterTextField(
                        value = imap_pass,
                        onValueChange = { imap_pass = it },
                        placeholder = stringResource(R.string.ext_imap_password),
                        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    v_gap(AsterSpacing.xs)
                    AsterTextField(
                        value = smtp_host,
                        onValueChange = { smtp_host = it },
                        placeholder = stringResource(R.string.ext_smtp_host),
                    )
                    v_gap(AsterSpacing.xs)
                    AsterTextField(
                        value = smtp_port,
                        onValueChange = { smtp_port = it.filter { ch -> ch.isDigit() } },
                        placeholder = stringResource(R.string.ext_smtp_port),
                        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    v_gap(AsterSpacing.xs)
                    AsterTextField(
                        value = smtp_user,
                        onValueChange = { smtp_user = it },
                        placeholder = stringResource(R.string.ext_smtp_username),
                    )
                    v_gap(AsterSpacing.xs)
                    AsterTextField(
                        value = smtp_pass,
                        onValueChange = { smtp_pass = it },
                        placeholder = stringResource(R.string.ext_smtp_password),
                        keyboard_options = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    v_gap(AsterSpacing.sm)
                    AsterButton(
                        label = stringResource(R.string.ext_save_imap),
                        onClick = {
                            vm.submit_manual_imap(
                                email = imap_email.trim(),
                                host = imap_host.trim(),
                                port = imap_port.toIntOrNull() ?: 993,
                                username = imap_user.trim(),
                                password = imap_pass,
                                use_tls = true,
                                smtp_host = smtp_host.trim(),
                                smtp_port = smtp_port.toIntOrNull() ?: 587,
                                smtp_username = smtp_user.trim(),
                                smtp_password = smtp_pass,
                            )
                        },
                        enabled = imap_email.contains("@") && imap_host.isNotBlank() && imap_user.isNotBlank() && imap_pass.isNotBlank() && (imap_port.toIntOrNull() ?: 0) in 1..65535 && !state.manual_submitting,
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
                if (state.accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ext_no_accounts),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                } else {
                    for (acct in state.accounts) {
                        val decrypted_email = state.decrypted[acct.account_token]?.email
                        val account_email = acct.oauth_email ?: decrypted_email
                        val display_label = account_email ?: when (acct.protocol) {
                            "oauth_google", "google" -> stringResource(R.string.ext_label_google_account)
                            "oauth_microsoft", "microsoft" -> stringResource(R.string.ext_label_microsoft_account)
                            "oauth_yahoo", "yahoo" -> stringResource(R.string.ext_label_yahoo_account)
                            "oauth_imap", "imap" -> stringResource(R.string.ext_label_imap_account)
                            else -> acct.protocol.ifBlank { stringResource(R.string.ext_label_linked_account) }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},
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
                                Text(
                                    text = display_label,
                                    color = colors.text_primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            val last_sync = acct.last_sync_at
                            if (!last_sync.isNullOrBlank()) {
                                val pretty = remember(last_sync) {
                                    runCatching {
                                        val instant = java.time.Instant.parse(last_sync)
                                        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
                                        zoned.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
                                    }.getOrDefault(last_sync)
                                }
                                Text(
                                    text = stringResource(R.string.ext_last_sync, pretty),
                                    color = colors.text_tertiary,
                                    fontSize = 12.sp,
                                )
                            }
                            val sync_error = acct.last_sync_error
                            if (!sync_error.isNullOrBlank()) {
                                Text(
                                    text = sync_error,
                                    color = colors.danger,
                                    fontSize = 12.sp,
                                )
                            }
                            v_gap(AsterSpacing.xs)
                            Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                                AsterButton(
                                    label = stringResource(R.string.ext_sync_now),
                                    onClick = {
                                        vm.trigger_sync(acct.account_token) { ok ->
                                            app_toast.show(if (ok) sync_started_message else sync_failed_message)
                                        }
                                    },
                                    enabled = acct.account_token !in state.syncing_tokens,
                                    is_loading = acct.account_token in state.syncing_tokens,
                                )
                                AsterButton(
                                    label = stringResource(R.string.ext_delete),
                                    onClick = {
                                        vm.delete_account(acct.account_token) { ok ->
                                            app_toast.show(if (ok) account_removed_message else account_remove_failed_message)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        state.error?.let { err ->
            v_gap(AsterSpacing.md)
            val msg = when (err) {
                ExternalAccountsError.LOAD_FAILED -> stringResource(R.string.ext_error_load_failed)
                ExternalAccountsError.OAUTH_FAILED -> stringResource(R.string.ext_error_oauth_failed)
                ExternalAccountsError.MANUAL_FAILED -> stringResource(R.string.ext_error_manual_failed)
                ExternalAccountsError.NO_SESSION_KEY -> stringResource(R.string.ext_error_no_session)
                ExternalAccountsError.DELETE_FAILED -> stringResource(R.string.ext_error_delete_failed)
                ExternalAccountsError.SYNC_FAILED -> stringResource(R.string.ext_error_sync_failed)
            }
            error_banner(msg)
        }

        v_gap(AsterSpacing.xxl)
    }
}

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
                .background(colors.text_tertiary.copy(alpha = 0.06f)),
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
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("account", email))
    show_copied_toast(context, email)
}
