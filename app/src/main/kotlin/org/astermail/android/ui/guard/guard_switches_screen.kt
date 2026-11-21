//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.guard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.astermail.android.R
import org.astermail.android.api.guard.GuardDeviceInfo
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.guard.GuardHeldBanner
import org.astermail.android.guard.guard_format_instant
import org.astermail.android.guard.guard_parse_instant
import org.astermail.android.guard.guard_prompt_text
import org.astermail.android.ui.settings.detail.detail_row
import org.astermail.android.ui.settings.detail.detail_scaffold
import org.astermail.android.ui.settings.detail.error_banner
import org.astermail.android.ui.settings.detail.load_failed_card
import org.astermail.android.ui.settings.detail.recommendation_note
import org.astermail.android.ui.settings.detail.section_label
import org.astermail.android.ui.settings.detail.settings_row_gap

@Composable
fun guard_switches_host(
    view_model: GuardSwitchesViewModel,
    on_back: () -> Unit,
    on_open: (String) -> Unit,
    rescued: Boolean = false,
) {
    val state by view_model.state.collectAsState()
    val activity = find_fragment_activity(LocalContext.current)
    val lifecycle_owner = LocalLifecycleOwner.current
    val current_state by rememberUpdatedState(state)
    DisposableEffect(lifecycle_owner) {
        var resumed_once = false
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (resumed_once && guard_switches_can_refresh(current_state)) view_model.refresh()
            resumed_once = true
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }
    val save_prompt = guard_prompt_text(
        title = stringResource(R.string.guard_prompt_title),
        subtitle = stringResource(R.string.guard_switches_prompt_subtitle),
    )
    val remove_prompt = guard_prompt_text(
        title = stringResource(R.string.guard_prompt_title),
        subtitle = stringResource(R.string.guard_devices_remove_prompt_subtitle),
    )
    guard_switches_screen(
        state = state,
        on_back = on_back,
        on_toggle = view_model::toggle,
        on_toggle_scope = view_model::toggle_scope,
        on_save = { view_model.save(guard_prompt_signer(activity, save_prompt)) },
        on_discard = view_model::discard,
        on_remove = { id -> view_model.remove_device(guard_prompt_signer(activity, remove_prompt), id) },
        on_retry = view_model::refresh,
        on_open = on_open,
        rescued = rescued,
    )
}

internal fun guard_switches_can_refresh(state: guard_switches_ui): Boolean =
    !state.dirty && !state.saving && state.removing_id == null

@Composable
fun guard_switches_screen(
    state: guard_switches_ui,
    on_back: () -> Unit,
    on_toggle: (Int) -> Unit,
    on_toggle_scope: (Int) -> Unit,
    on_save: () -> Unit,
    on_discard: () -> Unit,
    on_remove: (String) -> Unit,
    on_retry: () -> Unit,
    on_open: (String) -> Unit,
    rescued: Boolean = false,
) {
    detail_scaffold(title = stringResource(R.string.guard_setup_title), on_back = on_back) {
        if (rescued && state.status != null && !state.guard_on) {
            org.astermail.android.ui.settings.detail.recommendation_note(stringResource(R.string.guard_rescued_notice))
            Spacer(Modifier.height(AsterSpacing.md))
        }
        when {
            state.hidden -> error_banner(stringResource(R.string.guard_setup_unavailable))
            state.load_failed && state.status == null -> load_failed_card(null, on_retry)
            state.status == null -> Unit
            !state.guard_on -> guard_switches_off_card(on_open)
            else -> guard_switches_on_content(state, on_toggle, on_toggle_scope, on_save, on_discard, on_remove, on_open)
        }
    }
}

@Composable
private fun guard_switches_off_card(on_open: (String) -> Unit) {
    guard_status_row(on = false, devices = emptyList(), on_turn_on = { on_open("guard_setup") })
}

@Composable
private fun guard_switches_on_content(
    state: guard_switches_ui,
    on_toggle: (Int) -> Unit,
    on_toggle_scope: (Int) -> Unit,
    on_save: () -> Unit,
    on_discard: () -> Unit,
    on_remove: (String) -> Unit,
    on_open: (String) -> Unit,
) {
    val status = state.status ?: return
    val editable = state.is_guard_device && !state.saving
    state.held_notice?.let {
        GuardHeldBanner(notice = it)
        Spacer(Modifier.height(AsterSpacing.md))
    }
    guard_notice_text(state.notice)?.let {
        error_banner(it)
        Spacer(Modifier.height(AsterSpacing.md))
    }
    guard_parse_instant(state.paused_until)?.let {
        recommendation_note(stringResource(R.string.guard_switches_paused, guard_format_instant(it)))
        Spacer(Modifier.height(AsterSpacing.md))
    }
    if (state.missing_rescue) {
        recommendation_note(stringResource(R.string.guard_switches_missing_rescue))
        Spacer(Modifier.height(AsterSpacing.md))
    }
    if (!state.is_guard_device) {
        recommendation_note(stringResource(R.string.guard_switches_not_guard_device))
        Spacer(Modifier.height(AsterSpacing.sm))
        AsterButton(
            label = stringResource(R.string.guard_join_action),
            onClick = { on_open("guard_setup") },
            modifier = Modifier.fillMaxWidth().testTag("guard_join_start"),
        )
        Spacer(Modifier.height(AsterSpacing.md))
    }

    guard_status_row(on = true, devices = status.devices)
    Spacer(Modifier.height(AsterSpacing.lg))
    section_label(stringResource(R.string.guard_switches_section))
    guard_card {
        val hold_on = state.draft_switches and GuardSwitch.HOLD_CHANGES != 0
        guard_protections_list(
            switches = state.draft_switches,
            scopes = state.draft_scopes,
            on_toggle = on_toggle,
            on_toggle_scope = on_toggle_scope,
            switch_enabled = { bit -> editable && state.pending_off_switches and bit == 0 },
            scope_enabled = { bit -> editable && hold_on && state.pending_off_scopes and bit == 0 },
            switch_subtitle = { bit ->
                guard_with_held_text(stringResource(guard_switch_body(bit)), state.pending_off_switches and bit != 0, state)
            },
            scope_subtitle = { bit ->
                guard_with_held_text(stringResource(guard_scope_body(bit)), state.pending_off_scopes and bit != 0, state)
            },
        )
    }
    if (state.dirty) {
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.guard_switches_off_waits),
            color = AsterMaterial.colors.text_secondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(AsterSpacing.sm))
        AsterButton(
            label = stringResource(R.string.guard_switches_save),
            onClick = on_save,
            enabled = state.can_save,
            is_loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AsterSpacing.sm))
        AsterGhostButton(
            label = stringResource(R.string.guard_switches_discard),
            onClick = on_discard,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(AsterSpacing.lg))
    section_label(stringResource(R.string.guard_switches_tools_section))
    guard_card {
        detail_row(
            title = stringResource(R.string.guard_code_title),
            subtitle = stringResource(R.string.guard_switches_code_subtitle),
            icon = TablerIcons.Key,
            on_click = { on_open("guard_code") },
        )
        settings_row_gap()
        detail_row(
            title = stringResource(R.string.guard_changes_title),
            subtitle = if (status.pending_changes.isNotEmpty()) {
                pluralStringResource(R.plurals.guard_changes_row_count, status.pending_changes.size, status.pending_changes.size)
            } else {
                stringResource(R.string.guard_changes_row_none)
            },
            icon = TablerIcons.Clock,
            on_click = { on_open("guard_changes") },
        )
        settings_row_gap()
        detail_row(
            title = stringResource(R.string.guard_panic_entry),
            subtitle = stringResource(R.string.guard_panic_row_subtitle),
            icon = TablerIcons.AlertTriangle,
            on_click = { on_open("guard_panic") },
        )
    }

    Spacer(Modifier.height(AsterSpacing.lg))
    section_label(stringResource(R.string.guard_devices_section))
    guard_card {
        status.devices.forEachIndexed { index, device ->
            if (index > 0) settings_row_gap()
            guard_device_row(
                device = device,
                is_this = device.id == status.this_device?.id,
                can_remove = state.is_guard_device && device.id != status.this_device?.id,
                removing = state.removing_id == device.id,
                on_remove = { on_remove(device.id) },
            )
        }
    }
}

@Composable
private fun guard_with_held_text(body: String, held_off: Boolean, state: guard_switches_ui): String {
    if (!held_off) return body
    return body + "\n" + guard_held_off_text(state)
}

@Composable
private fun guard_held_off_text(state: guard_switches_ui): String =
    state.held_notice?.apply_after?.let {
        stringResource(R.string.guard_switches_turns_off_at, guard_format_instant(it))
    } ?: stringResource(R.string.guard_switches_turns_off_later)

@Composable
private fun guard_device_row(
    device: GuardDeviceInfo,
    is_this: Boolean,
    can_remove: Boolean,
    removing: Boolean,
    on_remove: () -> Unit,
) {
    val subtitle = if (is_this) stringResource(R.string.guard_devices_this_device) else null
    detail_row(
        title = device.name.ifBlank { stringResource(R.string.guard_devices_unnamed) },
        subtitle = subtitle,
        leading = {
            guard_vector_tile(
                icon = guard_os_icon(guard_os_kind_for(null, device.device_type)),
                color = AsterMaterial.colors.text_secondary,
                size = 44.dp,
            )
        },
        trailing = if (can_remove) {
            {
                AsterGhostButton(
                    label = stringResource(R.string.guard_devices_remove),
                    onClick = on_remove,
                    enabled = !removing,
                )
            }
        } else {
            null
        },
    )
}

@Composable
internal fun guard_notice_text(notice: guard_action_notice?): String? = when (notice) {
    null -> null
    guard_action_notice.canceled -> stringResource(R.string.guard_switches_canceled)
    guard_action_notice.key_lost -> stringResource(R.string.guard_approve_key_lost)
    guard_action_notice.no_lock_screen -> stringResource(R.string.guard_setup_no_lock_screen)
    guard_action_notice.no_device -> stringResource(R.string.guard_approve_no_device)
    guard_action_notice.failed -> stringResource(R.string.guard_setup_failed)
}

@Composable
fun guard_security_row(on_open: (String) -> Unit, entry: guard_entry_ui) {
    if (!entry.visible) return
    detail_row(
        title = stringResource(R.string.guard_setup_title),
        subtitle = stringResource(if (entry.on) R.string.guard_security_row_on else R.string.guard_security_row_off),
        icon = guard_mark_icon,
        on_click = { on_open(if (entry.on) "guard" else "guard_setup") },
    )
}
