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

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Trash
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.api.domains.DnsZoneRecord
import org.astermail.android.auth.request_passkey_json
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlert
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterAlertSeverity
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterCompactButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.design.components.aster_spinner
import org.astermail.android.settings.domains.CaaTag
import org.astermail.android.settings.domains.DnsDraftField
import org.astermail.android.settings.domains.DnsFieldError
import org.astermail.android.settings.domains.DnsPendingOp
import org.astermail.android.settings.domains.DnsRecordDraft
import org.astermail.android.settings.domains.DnsRecordType
import org.astermail.android.settings.domains.DnsTtlPreset
import org.astermail.android.settings.domains.DnsZonePhase
import org.astermail.android.settings.domains.DnsZoneReason
import org.astermail.android.settings.domains.DnsZoneUiState
import org.astermail.android.settings.domains.DnsZoneViewModel
import org.astermail.android.settings.domains.dns_field_error_res
import org.astermail.android.settings.domains.dns_protected_reason
import org.astermail.android.settings.domains.dns_protected_reason_res
import org.astermail.android.settings.domains.dns_record_display_host
import org.astermail.android.settings.domains.dns_record_label
import org.astermail.android.settings.domains.dns_zone_reason_field
import org.astermail.android.settings.domains.dns_zone_reason_res
import org.astermail.android.settings.domains.draft_from_record
import org.astermail.android.settings.host_activity
import org.astermail.android.ui.common.aster_modal_sheet
import org.astermail.android.ui.common.remember_sheet_dismiss
import org.astermail.android.ui.common.sheet_container_color

private const val action_error_visible_ms = 6_000L

private sealed interface DnsEditorTarget {
    data object add : DnsEditorTarget
    data class edit(val record: DnsZoneRecord) : DnsEditorTarget
}

@Composable
internal fun dns_zone_section(state: DnsZoneUiState, vm: DnsZoneViewModel) {
    val context = LocalContext.current
    var editor by remember { mutableStateOf<DnsEditorTarget?>(null) }
    var deleting by remember { mutableStateOf<DnsZoneRecord?>(null) }

    val settled = state.phase != DnsZonePhase.idle && state.phase != DnsZonePhase.loading
    domain_reveal(visible = state.phase != DnsZonePhase.unavailable && (settled || state.records.isNotEmpty())) {
        Column(modifier = Modifier.fillMaxWidth()) {
            settings_row_gap()
            dns_zone_header(state = state, on_add = { editor = DnsEditorTarget.add })
            when (state.phase) {
                DnsZonePhase.idle, DnsZonePhase.loading -> dns_zone_loading(show = state.records.isEmpty())
                DnsZonePhase.unavailable -> Unit
                DnsZonePhase.unmanaged -> dns_zone_unmanaged()
                DnsZonePhase.locked -> AsterAlert(
                    message = stringResource(R.string.dns_zone_error_locked),
                    severity = AsterAlertSeverity.info,
                )
                DnsZonePhase.failed -> load_failed_card(
                    message = stringResource(dns_zone_reason_res(state.load_error ?: DnsZoneReason.generic)),
                    on_retry = { vm.refresh() },
                )
                DnsZonePhase.managed -> dns_zone_records(
                    state = state,
                    on_edit = { editor = DnsEditorTarget.edit(it) },
                    on_delete = { deleting = it },
                )
            }
            dns_zone_action_error(state.action_error, on_clear = { vm.clear_action_error() })
        }
    }

    editor?.let { target ->
        dns_record_editor_sheet(
            state = state,
            editing = (target as? DnsEditorTarget.edit)?.record,
            vm = vm,
            on_dismiss = {
                editor = null
                vm.clear_save_error()
            },
        )
    }

    deleting?.let { record ->
        AsterAlertDialog(
            on_dismiss = { deleting = null },
            title = stringResource(R.string.dns_zone_delete_title),
            message = stringResource(R.string.dns_zone_delete_message, dns_record_label(record, state.domain_name)),
            confirm_label = stringResource(R.string.dns_zone_delete_action),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                deleting = null
                vm.delete(record)
            },
        )
    }

    state.reauth?.let { prompt ->
        reauth_dialog(
            title = stringResource(R.string.confirm_its_you),
            message = stringResource(R.string.dns_zone_unlock_message),
            confirm_label = stringResource(R.string.dns_zone_unlock_action),
            prompt = prompt,
            on_dismiss = { vm.dismiss_reauth() },
            on_confirm = { password, code ->
                val host = context.host_activity() ?: context
                vm.confirm_reauth(
                    password = password,
                    totp_code = code,
                    get_credential = { json -> request_passkey_json(host, json) },
                )
            },
        )
    }
}

@Composable
private fun dns_zone_header(state: DnsZoneUiState, on_add: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            section_label(stringResource(R.string.dns_zone_title))
            if (state.phase == DnsZonePhase.managed && state.max_records > 0) {
                Text(
                    text = stringResource(R.string.dns_zone_usage, state.records.size, state.max_records),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
        }
        if (state.phase == DnsZonePhase.managed) {
            AsterCompactButton(
                label = stringResource(R.string.dns_zone_add_record),
                onClick = on_add,
                enabled = !state.at_limit,
            )
        }
    }
    v_gap(AsterSpacing.sm)
}

@Composable
private fun dns_zone_loading(show: Boolean) {
    val visible = remember_delayed_flag(show)
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp)) {
        if (visible) domain_cards_pulse_skeleton(rows = 2)
    }
}

@Composable
private fun dns_zone_unmanaged() {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Text(
                text = stringResource(R.string.dns_zone_unmanaged_title),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            v_gap(4.dp)
            Text(
                text = stringResource(R.string.dns_zone_unmanaged_body),
                color = colors.text_secondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun dns_zone_action_error(reason: DnsZoneReason?, on_clear: () -> Unit) {
    LaunchedEffect(reason) {
        if (reason != null) {
            delay(action_error_visible_ms)
            on_clear()
        }
    }
    var last by remember { mutableStateOf(reason) }
    if (reason != null) last = reason
    domain_reveal(visible = reason != null) {
        Column {
            v_gap(AsterSpacing.sm)
            last?.let { AsterAlert(message = stringResource(dns_zone_reason_res(it))) }
        }
    }
}

@Composable
private fun dns_zone_records(
    state: DnsZoneUiState,
    on_edit: (DnsZoneRecord) -> Unit,
    on_delete: (DnsZoneRecord) -> Unit,
) {
    val colors = AsterMaterial.colors
    val editable = state.records.filter { !it.protected }
    val managed = state.records.filter { it.protected }
    if (state.at_limit) {
        AsterAlert(
            message = stringResource(R.string.dns_zone_limit_reached, state.max_records),
            severity = AsterAlertSeverity.warning,
        )
        v_gap(AsterSpacing.sm)
    }
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().animateContentSize(domain_tween())) {
            if (editable.isEmpty()) {
                Text(
                    text = stringResource(R.string.dns_zone_empty),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(AsterSpacing.lg),
                )
            }
            editable.forEachIndexed { index, record ->
                key(record.id, index) {
                    if (index > 0) HorizontalDivider(color = colors.border_secondary, thickness = 0.5.dp)
                    dns_zone_record_row(
                        record = record,
                        domain_name = state.domain_name,
                        pending = state.pending[record.id],
                        on_edit = { on_edit(record) },
                        on_delete = { on_delete(record) },
                    )
                }
            }
        }
    }
    if (managed.isNotEmpty()) {
        v_gap(AsterSpacing.md)
        settings_group(title = stringResource(R.string.dns_zone_protected_group)) {
            managed.forEachIndexed { index, record ->
                key(record.id, index) {
                    if (index > 0) HorizontalDivider(color = colors.border_secondary, thickness = 0.5.dp)
                    dns_zone_record_row(
                        record = record,
                        domain_name = state.domain_name,
                        pending = null,
                        on_edit = null,
                        on_delete = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun dns_zone_record_row(
    record: DnsZoneRecord,
    domain_name: String,
    pending: DnsPendingOp?,
    on_edit: (() -> Unit)?,
    on_delete: (() -> Unit)?,
) {
    val colors = AsterMaterial.colors
    val busy = pending != null
    val content_alpha by animateFloatAsState(
        targetValue = if (busy) 0.5f else 1f,
        animationSpec = domain_tween(),
        label = "dns_row_alpha",
    )
    val display_host = remember(record.host, domain_name) { dns_record_display_host(record.host, domain_name) }
    val row_label = stringResource(R.string.dns_zone_edit_record_label, dns_record_label(record, domain_name))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (on_edit != null && !busy) {
                    Modifier
                        .clickable(role = Role.Button, onClick = on_edit)
                        .semantics { contentDescription = row_label }
                } else {
                    Modifier
                },
            )
            .padding(start = AsterSpacing.lg, end = AsterSpacing.xs, top = AsterSpacing.sm, bottom = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).alpha(content_alpha)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                dns_type_chip(record.type)
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = display_host,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            v_gap(2.dp)
            Text(
                text = listOfNotNull(record.priority?.toString(), record.value).joinToString(" "),
                color = colors.text_secondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            v_gap(2.dp)
            if (record.protected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TablerIcons.Lock,
                        contentDescription = null,
                        tint = colors.text_tertiary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(dns_protected_reason_res(dns_protected_reason(record.protected_reason))),
                        color = colors.text_tertiary,
                        fontSize = 11.sp,
                    )
                }
            } else {
                Text(
                    text = dns_ttl_label(record.ttl),
                    color = colors.text_tertiary,
                    fontSize = 11.sp,
                )
            }
        }
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> aster_spinner(modifier = Modifier.size(18.dp), stroke_width = 2.dp)
                on_delete != null -> AsterIconButton(
                    icon = TablerIcons.Trash,
                    content_description = stringResource(
                        R.string.dns_zone_delete_record_label,
                        dns_record_label(record, domain_name),
                    ),
                    onClick = on_delete,
                    tint = colors.text_tertiary,
                    icon_size = 18,
                )
            }
        }
    }
}

@Composable
internal fun dns_ttl_label(seconds: Int): String = when (DnsTtlPreset.for_seconds(seconds)) {
    DnsTtlPreset.one_hour -> stringResource(R.string.dns_zone_ttl_one_hour)
    DnsTtlPreset.four_hours -> stringResource(R.string.dns_zone_ttl_four_hours)
    DnsTtlPreset.twelve_hours -> stringResource(R.string.dns_zone_ttl_twelve_hours)
    DnsTtlPreset.one_day -> stringResource(R.string.dns_zone_ttl_one_day)
    null -> pluralStringResource(R.plurals.dns_zone_ttl_seconds, seconds, seconds)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun dns_record_editor_sheet(
    state: DnsZoneUiState,
    editing: DnsZoneRecord?,
    vm: DnsZoneViewModel,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = remember_sheet_dismiss(sheet_state, on_dismiss)
    var draft by remember(editing?.id) {
        mutableStateOf(editing?.let { draft_from_record(it) } ?: DnsRecordDraft())
    }
    var attempted by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(emptySet<DnsDraftField>()) }
    val start_count = remember { state.save_done_count }
    val result = remember(draft, state.records) { vm.validate(draft, editing?.id) }
    val errors = result.errors
    val server_field = dns_zone_reason_field(state.save_error)

    LaunchedEffect(state.save_done_count) {
        if (state.save_done_count != start_count) dismiss()
    }

    fun update(field: DnsDraftField, next: DnsRecordDraft) {
        draft = next
        touched = touched + field
        if (state.save_error != null) vm.clear_save_error()
    }

    @Composable
    fun error_for(field: DnsDraftField, error: DnsFieldError?): String? {
        if (server_field == field && state.save_error != null) {
            return stringResource(dns_zone_reason_res(state.save_error))
        }
        if (error == null || !(attempted || field in touched)) return null
        return stringResource(dns_field_error_res(error))
    }

    aster_modal_sheet(
        on_dismiss_request = on_dismiss,
        sheet_state = sheet_state,
        container_color = sheet_container_color(colors),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        ) {
            Text(
                text = stringResource(if (editing == null) R.string.dns_zone_add_title else R.string.dns_zone_edit_title),
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            v_gap(AsterSpacing.md)

            dns_editor_label(R.string.dns_field_type_label)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                DnsRecordType.entries.forEach { type ->
                    dns_choice_chip(
                        label = type.wire,
                        selected = draft.type == type,
                        enabled = editing == null || draft.type == type,
                        on_click = {
                            if (draft.type != type) {
                                draft = draft.copy(type = type)
                                touched = emptySet()
                                attempted = false
                            }
                        },
                    )
                }
            }
            if (editing != null) {
                v_gap(4.dp)
                Text(
                    text = stringResource(R.string.dns_field_type_locked),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                )
            }
            v_gap(AsterSpacing.md)

            AsterTextField(
                value = draft.host,
                onValueChange = { update(DnsDraftField.host, draft.copy(host = it)) },
                label = stringResource(R.string.dns_field_host_label),
                placeholder = stringResource(
                    if (draft.type == DnsRecordType.srv) R.string.dns_field_host_srv_placeholder else R.string.dns_field_host_placeholder,
                ),
                helper_text = stringResource(R.string.dns_field_host_helper, state.domain_name),
                error_text = error_for(DnsDraftField.host, errors.host),
                keyboard_options = dns_keyboard(KeyboardType.Uri),
            )

            if (draft.type.has_priority) {
                v_gap(AsterSpacing.sm)
                AsterTextField(
                    value = draft.priority,
                    onValueChange = { update(DnsDraftField.priority, draft.copy(priority = it.filter(Char::isDigit).take(5))) },
                    label = stringResource(R.string.dns_field_priority_label),
                    helper_text = stringResource(R.string.dns_field_priority_helper),
                    error_text = error_for(DnsDraftField.priority, errors.priority),
                    keyboard_options = dns_keyboard(KeyboardType.Number),
                )
            }

            if (draft.type == DnsRecordType.srv) {
                v_gap(AsterSpacing.sm)
                Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                    AsterTextField(
                        value = draft.weight,
                        onValueChange = { update(DnsDraftField.weight, draft.copy(weight = it.filter(Char::isDigit).take(5))) },
                        label = stringResource(R.string.dns_field_weight_label),
                        error_text = error_for(DnsDraftField.weight, errors.weight),
                        keyboard_options = dns_keyboard(KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    AsterTextField(
                        value = draft.port,
                        onValueChange = { update(DnsDraftField.port, draft.copy(port = it.filter(Char::isDigit).take(5))) },
                        label = stringResource(R.string.dns_field_port_label),
                        error_text = error_for(DnsDraftField.port, errors.port),
                        keyboard_options = dns_keyboard(KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (draft.type == DnsRecordType.caa) {
                v_gap(AsterSpacing.md)
                dns_editor_label(R.string.dns_field_caa_tag_label)
                Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                    CaaTag.entries.forEach { tag ->
                        dns_choice_chip(
                            label = tag.wire,
                            selected = draft.caa_tag == tag,
                            on_click = { update(DnsDraftField.value, draft.copy(caa_tag = tag)) },
                        )
                    }
                }
                v_gap(AsterSpacing.sm)
                settings_toggle_row(
                    title = stringResource(R.string.dns_field_caa_critical_label),
                    subtitle = stringResource(R.string.dns_field_caa_critical_helper),
                    checked = draft.caa_critical,
                    on_change = { draft = draft.copy(caa_critical = it) },
                )
            }

            v_gap(AsterSpacing.sm)
            AsterTextField(
                value = draft.value,
                onValueChange = { update(DnsDraftField.value, draft.copy(value = it)) },
                label = stringResource(dns_value_label_res(draft.type)),
                placeholder = stringResource(dns_value_placeholder_res(draft.type, draft.caa_tag)),
                error_text = error_for(DnsDraftField.value, errors.value),
                singleLine = draft.type != DnsRecordType.txt,
                min_lines = if (draft.type == DnsRecordType.txt) 3 else 1,
                keyboard_options = dns_keyboard(dns_value_keyboard(draft.type)),
                text_style = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            )

            v_gap(AsterSpacing.md)
            dns_editor_label(R.string.dns_field_ttl_label)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                DnsTtlPreset.entries.forEach { preset ->
                    dns_choice_chip(
                        label = dns_ttl_label(preset.seconds),
                        selected = draft.ttl_preset == preset,
                        on_click = {
                            update(DnsDraftField.ttl, draft.copy(ttl = preset.seconds, ttl_preset = preset))
                        },
                    )
                }
            }
            error_for(DnsDraftField.ttl, errors.ttl)?.let {
                v_gap(4.dp)
                Text(text = it, color = colors.danger, fontSize = 12.sp)
            }

            val general_error = state.save_error?.takeIf { server_field == null }
            domain_reveal(visible = general_error != null) {
                Column {
                    v_gap(AsterSpacing.md)
                    general_error?.let { AsterAlert(message = stringResource(dns_zone_reason_res(it))) }
                }
            }

            v_gap(AsterSpacing.lg)
            AsterButton(
                label = stringResource(if (editing == null) R.string.dns_zone_add_action else R.string.dns_zone_save_action),
                onClick = {
                    attempted = true
                    if (errors.is_valid) vm.submit(draft, editing?.id)
                },
                enabled = !state.saving && !(attempted && !errors.is_valid),
                is_loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            v_gap(AsterSpacing.sm)
            AsterGhostButton(
                label = stringResource(R.string.cancel),
                onClick = dismiss,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            v_gap(AsterSpacing.md)
        }
    }
}

private fun dns_keyboard(type: KeyboardType): KeyboardOptions = KeyboardOptions(
    keyboardType = type,
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    imeAction = ImeAction.Next,
)

private fun dns_value_keyboard(type: DnsRecordType): KeyboardType = when (type) {
    DnsRecordType.a, DnsRecordType.aaaa, DnsRecordType.cname, DnsRecordType.mx, DnsRecordType.srv -> KeyboardType.Uri
    DnsRecordType.txt, DnsRecordType.caa -> KeyboardType.Ascii
}

@StringRes
private fun dns_value_label_res(type: DnsRecordType): Int = when (type) {
    DnsRecordType.a -> R.string.dns_field_value_ipv4
    DnsRecordType.aaaa -> R.string.dns_field_value_ipv6
    DnsRecordType.cname, DnsRecordType.mx, DnsRecordType.srv -> R.string.dns_field_value_target
    DnsRecordType.txt -> R.string.dns_field_value_text
    DnsRecordType.caa -> R.string.dns_field_value_caa
}

@StringRes
private fun dns_value_placeholder_res(type: DnsRecordType, tag: CaaTag): Int = when (type) {
    DnsRecordType.a -> R.string.dns_field_value_ipv4_placeholder
    DnsRecordType.aaaa -> R.string.dns_field_value_ipv6_placeholder
    DnsRecordType.cname, DnsRecordType.mx, DnsRecordType.srv -> R.string.dns_field_value_target_placeholder
    DnsRecordType.txt -> R.string.dns_field_value_text_placeholder
    DnsRecordType.caa -> if (tag == CaaTag.iodef) {
        R.string.dns_field_value_caa_iodef_placeholder
    } else {
        R.string.dns_field_value_caa_placeholder
    }
}

@Composable
private fun dns_editor_label(@StringRes res: Int) {
    Text(
        text = stringResource(res),
        color = AsterMaterial.colors.text_secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = AsterSpacing.xs),
    )
}

@Composable
internal fun dns_choice_chip(
    label: String,
    selected: Boolean,
    on_click: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val background by animateColorAsState(
        targetValue = if (selected) colors.accent_blue.copy(alpha = 0.14f) else colors.bg_tertiary,
        animationSpec = domain_tween(),
        label = "dns_chip_bg",
    )
    val border_color = if (selected) colors.accent_blue else colors.border_secondary
    Text(
        text = label,
        color = if (selected) colors.accent_blue else colors.text_primary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .background(background, AsterShapes.pill)
            .border(1.dp, border_color, AsterShapes.pill)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = on_click)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
