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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.api.settings.SmtpTokenRow
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.common.write_to_clipboard

@Composable
fun SmtpTokensScreen(on_back: () -> Unit) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    var show_create by remember { mutableStateOf(false) }
    var pending_revoke by remember { mutableStateOf<SmtpTokenRow?>(null) }

    LaunchedEffect(Unit) {
        vm.load_smtp_tokens()
        vm.load_custom_domain_addresses()
    }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    LaunchedEffect(state.smtp_token_created) {
        if (state.smtp_token_created != null) show_create = false
    }

    val is_locked = plan_state.limits?.plan_code == "free" && !plan_state.is_loading
    val addresses = state.custom_domain_addresses
        .filterNot { it.decryption_failed || it.encrypted_local_part.isBlank() }
    val clipboard_label = stringResource(R.string.clipboard_label_smtp_token)

    detail_scaffold(title = stringResource(R.string.settings_smtp_tokens), on_back = on_back) {
        Text(
            text = stringResource(R.string.smtp_tokens_description),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = AsterSpacing.md),
        )

        when {
            is_locked -> notice_card(
                title = stringResource(R.string.smtp_tokens_upgrade_title),
                body = stringResource(R.string.smtp_tokens_upgrade_description),
            )

            addresses.isEmpty() -> notice_card(
                title = stringResource(R.string.smtp_tokens_no_domain_title),
                body = stringResource(R.string.smtp_tokens_no_domain_description),
            )

            else -> {
                notice_card(
                    title = stringResource(R.string.smtp_token_not_e2e_title),
                    body = stringResource(R.string.smtp_token_not_e2e_body),
                )

                section_label(stringResource(R.string.settings_smtp_tokens))

                if (state.smtp_tokens_loading && state.smtp_tokens.isEmpty()) {
                    preferences_load_placeholder()
                } else if (state.smtp_tokens.isEmpty()) {
                    Text(
                        text = stringResource(R.string.smtp_tokens_empty),
                        color = colors.text_tertiary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = AsterSpacing.md),
                    )
                } else {
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        state.smtp_tokens.forEachIndexed { i, token ->
                            smtp_token_row(token = token, on_revoke = { pending_revoke = token })
                            if (i < state.smtp_tokens.lastIndex) AsterDivider(modifier = Modifier)
                        }
                    }
                }

                v_gap(AsterSpacing.md)

                AsterButton(
                    label = stringResource(R.string.smtp_token_generate),
                    onClick = { show_create = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        v_gap(AsterSpacing.xxl)
    }

    if (show_create) {
        smtp_token_create_dialog(
            addresses = addresses.map { it.encrypted_local_part to it.domain_name },
            is_busy = state.smtp_token_creating,
            on_dismiss = { show_create = false },
            on_create = { local_part, domain, name ->
                vm.create_smtp_token(name, local_part, domain)
            },
        )
    }

    val created = state.smtp_token_created
    if (created != null) {
        val settings = created.smtp_settings
        val all_text = buildString {
            appendLine(context.getString(R.string.smtp_token_host) + ": " + settings.host)
            appendLine(context.getString(R.string.smtp_token_port) + ": " + settings.port)
            appendLine(context.getString(R.string.smtp_token_security) + ": " + settings.security)
            appendLine(context.getString(R.string.smtp_token_username) + ": " + settings.username)
            append(context.getString(R.string.smtp_token_password) + ": " + settings.password)
        }
        AsterAlertDialog(
            on_dismiss = { vm.clear_created_smtp_token() },
            title = stringResource(R.string.smtp_token_ready_title),
            message = stringResource(R.string.smtp_token_ready_description),
            confirm_label = stringResource(R.string.done),
            on_confirm = { vm.clear_created_smtp_token() },
            extra_content = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    smtp_setting_row(stringResource(R.string.smtp_token_host), settings.host)
                    smtp_setting_row(stringResource(R.string.smtp_token_port), settings.port.toString())
                    smtp_setting_row(stringResource(R.string.smtp_token_security), settings.security)
                    smtp_setting_row(stringResource(R.string.smtp_token_username), settings.username)
                    smtp_setting_row(stringResource(R.string.smtp_token_password), settings.password)
                    v_gap(AsterSpacing.sm)
                    AsterButton(
                        label = stringResource(R.string.smtp_token_copy_all),
                        onClick = {
                            if (write_to_clipboard(context, ClipData.newPlainText(clipboard_label, all_text))) {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.copied_to_clipboard),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    val revoke_target = pending_revoke
    if (revoke_target != null) {
        val label = revoke_target.decrypted_label.ifBlank { revoke_target.bound_address }
        AsterAlertDialog(
            on_dismiss = { pending_revoke = null },
            title = stringResource(R.string.smtp_token_revoke_title),
            message = stringResource(R.string.smtp_token_revoke_message, label),
            confirm_label = stringResource(R.string.revoke),
            cancel_label = stringResource(R.string.cancel),
            on_confirm = {
                pending_revoke = null
                vm.revoke_smtp_token(revoke_target.id)
            },
        )
    }
}

@Composable
private fun notice_card(title: String, body: String) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(AsterSpacing.md)) {
            Text(text = title, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.size(AsterSpacing.xs))
            Text(text = body, color = colors.text_tertiary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun smtp_setting_row(label: String, value: String) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = colors.text_tertiary, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(AsterSpacing.md))
        Text(text = value, color = colors.text_primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun smtp_token_row(token: SmtpTokenRow, on_revoke: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = token.decrypted_label.ifBlank { token.bound_address },
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(text = token.bound_address, color = colors.text_tertiary, fontSize = 13.sp)
            Text(
                text = stringResource(R.string.smtp_token_last_used) + ": " +
                    (token.last_used_at ?: stringResource(R.string.smtp_token_never_used)),
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.width(AsterSpacing.sm))
        Icon(
            imageVector = TablerIcons.Trash,
            contentDescription = stringResource(R.string.revoke),
            tint = colors.danger,
            modifier = Modifier.size(20.dp).clickable(onClick = on_revoke),
        )
    }
}

@Composable
private fun smtp_token_create_dialog(
    addresses: List<Pair<String, String>>,
    is_busy: Boolean,
    on_dismiss: () -> Unit,
    on_create: (String, String, String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(addresses.firstOrNull()) }

    AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.smtp_token_create_title),
        message = stringResource(R.string.smtp_token_create_description),
        confirm_label = stringResource(R.string.smtp_token_generate),
        cancel_label = stringResource(R.string.cancel),
        confirm_enabled = name.isNotBlank() && selected != null && !is_busy,
        is_busy = is_busy,
        on_confirm = {
            val target = selected
            if (target != null && name.isNotBlank()) on_create(target.first, target.second, name.trim())
        },
        extra_content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AsterTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.smtp_token_name_label),
                    placeholder = stringResource(R.string.smtp_token_name_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
                v_gap(AsterSpacing.sm)
                section_label(stringResource(R.string.smtp_token_address_label))
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    addresses.forEachIndexed { i, entry ->
                        val address = entry.first + "@" + entry.second
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = entry }
                                .padding(AsterSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = address,
                                color = colors.text_primary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected == entry) {
                                Icon(
                                    imageVector = TablerIcons.Check,
                                    contentDescription = null,
                                    tint = colors.accent_blue,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (i < addresses.lastIndex) AsterDivider(modifier = Modifier)
                    }
                }
                v_gap(AsterSpacing.xs)
                Text(
                    text = stringResource(R.string.smtp_token_address_hint),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
        },
    )
}
