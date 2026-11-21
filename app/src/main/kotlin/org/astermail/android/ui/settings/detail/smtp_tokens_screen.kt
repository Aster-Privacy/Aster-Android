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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.api.settings.SmtpTokenConnectionSettings
import org.astermail.android.api.settings.SmtpTokenRow
import org.astermail.android.api.settings.SmtpTokenSettings
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.normalize_smtp_token_label
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.common.secure_screen
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.show_copy_result_toast
import org.astermail.android.ui.common.write_sensitive_to_clipboard
import org.astermail.android.util.schedule_sensitive_clipboard_clear

@Composable
fun SmtpTokensScreen(on_back: () -> Unit, on_upgrade: () -> Unit = {}) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    var show_create by remember { mutableStateOf(false) }
    var pending_revoke by remember { mutableStateOf<SmtpTokenRow?>(null) }
    var details_token_id by remember { mutableStateOf<String?>(null) }
    var rename_token_id by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(state.smtp_tokens, rename_token_id) {
        val open_id = rename_token_id
        if (open_id != null && state.smtp_tokens.none { it.id == open_id }) {
            rename_token_id = null
            vm.clear_smtp_token_rename()
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.clear_created_smtp_token() }
    }

    LaunchedEffect(state.smtp_token_renamed_id) {
        if (state.smtp_token_renamed_id != null) {
            rename_token_id = null
            vm.clear_smtp_token_rename()
        }
    }

    val is_locked = plan_state.limits?.plan_code == "free" && !plan_state.is_loading
    val addresses = state.custom_domain_addresses
        .filterNot { it.decryption_failed || it.encrypted_local_part.isBlank() }
    val body = smtp_tokens_body(
        addresses_known = addresses.isNotEmpty() || state.custom_domain_addresses_settled,
        has_addresses = addresses.isNotEmpty(),
        has_tokens = state.smtp_tokens.isNotEmpty(),
        loaded = state.smtp_tokens_loaded,
        load_failed = state.smtp_tokens_load_failed,
    )

    var show_load_placeholder by remember { mutableStateOf(false) }
    LaunchedEffect(body == SmtpTokensBody.placeholder) {
        if (body == SmtpTokensBody.placeholder) {
            delay(smtp_placeholder_defer_ms)
            show_load_placeholder = true
        } else {
            show_load_placeholder = false
        }
    }

    detail_scaffold(title = stringResource(R.string.settings_smtp_tokens), on_back = on_back) {
        if (!is_locked) {
            Text(
                text = stringResource(R.string.smtp_tokens_summary),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = AsterSpacing.md),
            )
        }

        when {
            is_locked -> {
                notice_card(
                    title = stringResource(R.string.smtp_tokens_upgrade_title),
                    body = stringResource(R.string.smtp_tokens_upgrade_description),
                )
                v_gap(AsterSpacing.md)
                AsterButton(
                    label = stringResource(R.string.upgrade),
                    onClick = on_upgrade,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            body == SmtpTokensBody.placeholder && addresses.isEmpty() -> {
                if (show_load_placeholder) preferences_load_placeholder()
            }

            body == SmtpTokensBody.no_domain -> notice_card(
                title = stringResource(R.string.smtp_tokens_no_domain_title),
                body = stringResource(R.string.smtp_tokens_no_domain_description),
            )

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.smtp_token_not_e2e_short),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.width(AsterSpacing.xs))
                    info_dialog_button(
                        title = stringResource(R.string.smtp_token_not_e2e_title),
                        description = stringResource(R.string.smtp_token_not_e2e_body),
                    )
                }

                if (body == SmtpTokensBody.placeholder) {
                    v_gap(AsterSpacing.md)
                    if (show_load_placeholder) preferences_load_placeholder()
                } else if (body == SmtpTokensBody.error) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AsterSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.failed_to_load),
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                        )
                        v_gap(AsterSpacing.sm)
                        Text(
                            text = stringResource(R.string.retry),
                            color = colors.accent_blue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { vm.load_smtp_tokens(force = true) }
                                .padding(AsterSpacing.sm),
                        )
                    }
                } else if (body == SmtpTokensBody.empty) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AsterSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = TablerIcons.Key,
                            contentDescription = null,
                            tint = colors.text_tertiary,
                            modifier = Modifier.size(32.dp),
                        )
                        v_gap(AsterSpacing.md)
                        Text(
                            text = stringResource(R.string.smtp_tokens_empty),
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    section_label(stringResource(R.string.settings_smtp_tokens))
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        state.smtp_tokens.forEachIndexed { i, token ->
                            smtp_token_row(
                                token = token,
                                on_details = { details_token_id = token.id },
                                on_rename = {
                                    vm.clear_smtp_token_rename()
                                    rename_token_id = token.id
                                },
                                on_revoke = { pending_revoke = token },
                            )
                            if (i < state.smtp_tokens.lastIndex) settings_row_gap(modifier = Modifier)
                        }
                    }
                }

                v_gap(AsterSpacing.md)

                AsterButton(
                    label = stringResource(R.string.smtp_token_generate),
                    onClick = {
                        vm.clear_smtp_token_create_error()
                        show_create = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        v_gap(AsterSpacing.xxl)
    }

    if (show_create && state.smtp_token_created == null) {
        smtp_token_create_dialog(
            addresses = addresses.map { it.encrypted_local_part to it.domain_name },
            is_busy = state.smtp_token_creating,
            error = state.smtp_token_create_error,
            on_dismiss = {
                show_create = false
                vm.clear_smtp_token_create_error()
            },
            on_create = { local_part, domain, name ->
                vm.create_smtp_token(name, local_part, domain)
            },
        )
    }

    val created = state.smtp_token_created
    if (created != null) {
        smtp_token_created_dialog(created.smtp_settings, on_done = { vm.clear_created_smtp_token() }, activates_at = created.activates_at)
    }

    val details_target = state.smtp_tokens.firstOrNull { it.id == details_token_id }
    val details_settings = details_target?.let { smtp_token_setup_details(it) }
    if (details_settings != null) {
        smtp_token_details_dialog(details_settings, on_done = { details_token_id = null })
    }

    val rename_target = state.smtp_tokens.firstOrNull { it.id == rename_token_id }
    if (rename_target != null) {
        smtp_token_rename_dialog(
            current_name = rename_target.decrypted_label,
            is_busy = state.smtp_token_renaming,
            error = state.smtp_token_rename_error,
            on_dismiss = {
                rename_token_id = null
                vm.clear_smtp_token_rename()
            },
            on_rename = { name -> vm.rename_smtp_token(rename_target.id, name) },
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
internal fun smtp_token_created_dialog(settings: SmtpTokenSettings, on_done: () -> Unit, activates_at: String? = null) {
    val context = LocalContext.current
    val clipboard_label = stringResource(R.string.clipboard_label_smtp_token)
    secure_screen()
    val connection = SmtpTokenConnectionSettings(
        host = settings.host,
        port = settings.port,
        security = settings.security,
        username = settings.username,
    )
    val connection_text = smtp_connection_copy_text(
        host_label = stringResource(R.string.smtp_token_host),
        port_label = stringResource(R.string.smtp_token_port),
        security_label = stringResource(R.string.smtp_token_security),
        username_label = stringResource(R.string.smtp_token_username),
        settings = connection,
    )
    val all_text = connection_text + "\n" + stringResource(R.string.smtp_token_password) + ": " + settings.password
    val copy_value: (String) -> Unit = { value ->
        if (write_sensitive_to_clipboard(context, clipboard_label, value)) {
            schedule_sensitive_clipboard_clear(context)
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.copied_to_clipboard),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } else {
            show_copy_failed_toast(context)
        }
    }
    AsterAlertDialog(
        on_dismiss = on_done,
        title = stringResource(R.string.smtp_token_ready_title),
        message = stringResource(R.string.smtp_token_ready_description),
        confirm_label = stringResource(R.string.done),
        on_confirm = on_done,
        secure = true,
        extra_content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                org.astermail.android.guard.guard_parse_instant(activates_at)?.let { at ->
                    Text(
                        text = stringResource(
                            R.string.smtp_token_activates_at,
                            org.astermail.android.guard.guard_format_instant(at),
                        ),
                        color = AsterMaterial.colors.warning,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = AsterSpacing.sm),
                    )
                }
                smtp_connection_rows(connection, on_copy = copy_value)
                smtp_setting_row(
                    label = stringResource(R.string.smtp_token_password),
                    value = settings.password,
                    on_copy = { copy_value(settings.password) },
                )
                v_gap(AsterSpacing.sm)
                AsterButton(
                    label = stringResource(R.string.smtp_token_copy_all),
                    onClick = {
                        if (write_sensitive_to_clipboard(context, clipboard_label, all_text)) {
                            schedule_sensitive_clipboard_clear(context)
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
internal fun smtp_token_details_dialog(settings: SmtpTokenConnectionSettings, on_done: () -> Unit) {
    val context = LocalContext.current
    val colors = AsterMaterial.colors
    val clipboard_label = stringResource(R.string.clipboard_label_smtp_token)
    val details_text = smtp_connection_copy_text(
        host_label = stringResource(R.string.smtp_token_host),
        port_label = stringResource(R.string.smtp_token_port),
        security_label = stringResource(R.string.smtp_token_security),
        username_label = stringResource(R.string.smtp_token_username),
        settings = settings,
    )
    val copy_value: (String) -> Unit = { value ->
        show_copy_result_toast(context, value, write_sensitive_to_clipboard(context, clipboard_label, value))
    }
    AsterAlertDialog(
        on_dismiss = on_done,
        title = stringResource(R.string.smtp_token_setup_details),
        message = stringResource(R.string.smtp_token_setup_description),
        confirm_label = stringResource(R.string.done),
        on_confirm = on_done,
        extra_content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                smtp_connection_rows(settings, on_copy = copy_value)
                smtp_setting_row(
                    label = stringResource(R.string.smtp_token_password),
                    value = stringResource(R.string.smtp_token_password_hidden),
                    on_copy = null,
                )
                v_gap(AsterSpacing.sm)
                AsterButton(
                    label = stringResource(R.string.smtp_token_copy_all),
                    onClick = {
                        if (write_sensitive_to_clipboard(context, clipboard_label, details_text)) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.copied_to_clipboard),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            show_copy_failed_toast(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                v_gap(AsterSpacing.sm)
                Text(
                    text = stringResource(R.string.smtp_token_password_note),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        },
    )
}

@Composable
private fun smtp_token_rename_dialog(
    current_name: String,
    is_busy: Boolean,
    error: String?,
    on_dismiss: () -> Unit,
    on_rename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current_name) }
    val normalized = normalize_smtp_token_label(name)
    val invalid_text = if (name.isNotBlank() && normalized == null) {
        stringResource(R.string.smtp_token_name_invalid)
    } else {
        null
    }
    AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.smtp_token_rename_title),
        message = stringResource(R.string.smtp_token_rename_description),
        confirm_label = stringResource(R.string.save),
        cancel_label = stringResource(R.string.cancel),
        confirm_enabled = normalized != null && normalized != current_name && !is_busy,
        is_busy = is_busy,
        dismiss_on_confirm = false,
        on_confirm = { if (normalized != null) on_rename(normalized) },
        extra_content = {
            AsterTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.smtp_token_name_label),
                placeholder = stringResource(R.string.smtp_token_name_placeholder),
                error_text = invalid_text ?: error,
                enabled = !is_busy,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun smtp_connection_rows(settings: SmtpTokenConnectionSettings, on_copy: (String) -> Unit) {
    smtp_setting_row(
        label = stringResource(R.string.smtp_token_host),
        value = settings.host,
        on_copy = { on_copy(settings.host) },
    )
    smtp_setting_row(
        label = stringResource(R.string.smtp_token_port),
        value = settings.port.toString(),
        on_copy = { on_copy(settings.port.toString()) },
    )
    smtp_setting_row(
        label = stringResource(R.string.smtp_token_security),
        value = settings.security,
        on_copy = { on_copy(settings.security) },
    )
    smtp_setting_row(
        label = stringResource(R.string.smtp_token_username),
        value = settings.username,
        on_copy = { on_copy(settings.username) },
    )
}

@Composable
private fun smtp_setting_row(label: String, value: String, on_copy: (() -> Unit)?) {
    val colors = AsterMaterial.colors
    val copy_label = stringResource(R.string.copy)
    val row_modifier = if (on_copy != null) {
        Modifier.fillMaxWidth().clickable(onClickLabel = copy_label, onClick = on_copy)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = row_modifier.padding(vertical = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = colors.text_tertiary, fontSize = 13.sp, maxLines = 1)
        Spacer(modifier = Modifier.width(AsterSpacing.md))
        Text(
            text = value,
            color = colors.text_primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun smtp_connection_copy_text(
    host_label: String,
    port_label: String,
    security_label: String,
    username_label: String,
    settings: SmtpTokenConnectionSettings,
): String = listOf(
    host_label + ": " + settings.host,
    port_label + ": " + settings.port,
    security_label + ": " + settings.security,
    username_label + ": " + settings.username,
).joinToString("\n")

internal fun smtp_token_setup_details(token: SmtpTokenRow): SmtpTokenConnectionSettings? =
    token.smtp_settings?.takeIf { it.host.isNotBlank() && it.username.isNotBlank() }

@Composable
private fun smtp_token_row(
    token: SmtpTokenRow,
    on_details: () -> Unit,
    on_rename: () -> Unit,
    on_revoke: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var menu_open by remember { mutableStateOf(false) }
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
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.smtp_token_subtitle,
                    token.bound_address,
                    format_smtp_last_used(token.last_used_at) ?: stringResource(R.string.smtp_token_never_used),
                ),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(AsterSpacing.sm))
        Box {
            AsterIconButton(
                icon = TablerIcons.DotsVertical,
                content_description = stringResource(R.string.more_options),
                onClick = { menu_open = true },
            )
            aster_menu(expanded = menu_open, on_dismiss = { menu_open = false }) {
                if (smtp_token_setup_details(token) != null) {
                    aster_menu_item(
                        label = stringResource(R.string.smtp_token_setup_details),
                        icon = TablerIcons.Settings,
                        on_click = {
                            menu_open = false
                            on_details()
                        },
                    )
                }
                aster_menu_item(
                    label = stringResource(R.string.rename),
                    icon = TablerIcons.Pencil,
                    on_click = {
                        menu_open = false
                        on_rename()
                    },
                )
                aster_menu_item(
                    label = stringResource(R.string.revoke),
                    icon = TablerIcons.Trash,
                    destructive = true,
                    on_click = {
                        menu_open = false
                        on_revoke()
                    },
                )
            }
        }
    }
}

private const val smtp_placeholder_defer_ms = 180L

internal enum class SmtpTokensBody { placeholder, no_domain, error, empty, list }

internal fun smtp_tokens_body(
    addresses_known: Boolean,
    has_addresses: Boolean,
    has_tokens: Boolean,
    loaded: Boolean,
    load_failed: Boolean,
): SmtpTokensBody = when {
    !addresses_known -> SmtpTokensBody.placeholder
    !has_addresses -> SmtpTokensBody.no_domain
    has_tokens -> SmtpTokensBody.list
    load_failed -> SmtpTokensBody.error
    loaded -> SmtpTokensBody.empty
    else -> SmtpTokensBody.placeholder
}

internal fun format_smtp_last_used(
    raw: String?,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    locale: java.util.Locale = java.util.Locale.getDefault(),
): String? {
    if (raw.isNullOrBlank()) return null
    val text = raw.trim()
    val instant = runCatching { java.time.OffsetDateTime.parse(text).toInstant() }
        .recoverCatching { java.time.Instant.parse(text) }
        .recoverCatching {
            java.time.LocalDateTime.parse(text.replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC)
        }
        .getOrNull() ?: return raw
    return instant.atZone(zone).format(
        java.time.format.DateTimeFormatter
            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withLocale(locale),
    )
}

@Composable
private fun smtp_token_create_dialog(
    addresses: List<Pair<String, String>>,
    is_busy: Boolean,
    error: String?,
    on_dismiss: () -> Unit,
    on_create: (String, String, String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(addresses.firstOrNull()) }
    val normalized = normalize_smtp_token_label(name)

    AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.smtp_token_create_title),
        confirm_label = stringResource(R.string.smtp_token_generate),
        cancel_label = stringResource(R.string.cancel),
        confirm_enabled = normalized != null && selected != null && !is_busy,
        is_busy = is_busy,
        dismiss_on_confirm = false,
        on_confirm = {
            val target = selected
            if (target != null && normalized != null) on_create(target.first, target.second, normalized)
        },
        extra_content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AsterTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.smtp_token_name_label),
                    placeholder = stringResource(R.string.smtp_token_name_placeholder),
                    error_text = if (name.isNotBlank() && normalized == null) {
                        stringResource(R.string.smtp_token_name_invalid)
                    } else {
                        null
                    },
                    enabled = !is_busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                v_gap(AsterSpacing.sm)
                section_label(stringResource(R.string.smtp_token_address_label))
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    addresses.forEachIndexed { i, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !is_busy) { selected = entry }
                                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.first,
                                    color = colors.text_primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "@" + entry.second,
                                    color = colors.text_tertiary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            if (selected == entry) {
                                Icon(
                                    imageVector = TablerIcons.Check,
                                    contentDescription = null,
                                    tint = colors.accent_blue,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (i < addresses.lastIndex) settings_row_gap(modifier = Modifier)
                    }
                }
                if (error != null) {
                    v_gap(AsterSpacing.sm)
                    Text(
                        text = error,
                        color = colors.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().testTag("smtp_token_create_error"),
                    )
                }
            }
        },
    )
}
