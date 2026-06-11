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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.imports.ExternalAccountsError
import org.astermail.android.imports.ExternalAccountsViewModel

@Composable
fun ExternalAccountsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    vm: ExternalAccountsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
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
        if (plan_vm.is_feature_locked("max_external_accounts") && !plan_state.is_loading) {
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
                    text = stringResource(R.string.ext_oauth_coming_soon),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(AsterSpacing.xs))
                Text(
                    text = stringResource(R.string.ext_oauth_coming_soon_body),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
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
                        enabled = imap_email.contains("@") && imap_host.isNotBlank() && imap_user.isNotBlank() && imap_pass.isNotBlank() && !state.manual_submitting,
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
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.xs)) {
                            val display_label = acct.oauth_email ?: when (acct.protocol) {
                                "oauth_google", "google" -> stringResource(R.string.ext_label_google_account)
                                "oauth_microsoft", "microsoft" -> stringResource(R.string.ext_label_microsoft_account)
                                "oauth_yahoo", "yahoo" -> stringResource(R.string.ext_label_yahoo_account)
                                "oauth_imap", "imap" -> stringResource(R.string.ext_label_imap_account)
                                else -> acct.protocol.ifBlank { stringResource(R.string.ext_label_linked_account) }
                            }
                            Text(
                                text = display_label,
                                color = colors.text_primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
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
                            v_gap(AsterSpacing.xs)
                            Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                                AsterButton(
                                    label = stringResource(R.string.ext_sync_now),
                                    onClick = { vm.trigger_sync(acct.account_token) },
                                )
                                AsterButton(
                                    label = stringResource(R.string.ext_delete),
                                    onClick = { vm.delete_account(acct.account_token) },
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
            }
            error_banner(msg)
        }

        v_gap(AsterSpacing.xxl)
    }
}
