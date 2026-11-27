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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.api_error_code
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardPendingChange
import org.astermail.android.api.guard.StepUpProof
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.GuardPasskeyException
import org.astermail.android.guard.GuardRepository
import org.astermail.android.guard.guard_hold_kind
import org.astermail.android.guard.guard_parse_instant
import org.astermail.android.guard.guard_passkey_failure
import org.astermail.android.guard.guard_passkey_failure_for
import org.astermail.android.guard.guard_passkey_proof
import org.astermail.android.guard.guard_passkey_sheet
import org.astermail.android.guard.guard_signer

const val GUARD_STEP_UP_REQUIRED_CODE = "STEP_UP_REQUIRED"

data class guard_change_row(
    val id: String,
    val kind: guard_hold_kind,
    val apply_after: Instant?,
    val created_at: Instant?,
    val server_skippable: Boolean = true,
    val server_cancellable: Boolean = true,
)

fun GuardPendingChange.to_row(): guard_change_row = guard_change_row(
    id = id,
    kind = guard_hold_kind.from_wire(kind),
    apply_after = guard_parse_instant(apply_after),
    created_at = guard_parse_instant(created_at),
    server_skippable = (skip_available ?: skippable) != false,
    server_cancellable = (cancel_available ?: cancellable) != false,
)

enum class guard_changes_notice {
    canceled,
    key_lost,
    no_lock_screen,
    no_device,
    failed,
    step_up_required,
    change_canceled,
    change_skipped,
    change_stopped,
    change_already_ended,
    change_stale,
}

data class guard_changes_ui(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val hidden: Boolean = false,
    val load_failed: Boolean = false,
    val changes: List<guard_change_row> = emptyList(),
    val is_guard_device: Boolean = false,
    val long_trusted: Boolean = false,
    val working_id: String? = null,
    val notice: guard_changes_notice? = null,
    val error: org.astermail.android.guard.guard_error_message? = null,
    val passkey_failure: guard_passkey_failure? = null,
) {
    fun can_skip(row: guard_change_row): Boolean = is_guard_device && long_trusted && row.skippable()
    fun skip_needs_trust(row: guard_change_row): Boolean = is_guard_device && !long_trusted && row.skippable()
    fun can_skip_with_passkey(row: guard_change_row): Boolean = skip_needs_trust(row)
    fun can_stop(row: guard_change_row): Boolean = is_guard_device && row.kind.can_veto
    fun can_cancel(row: guard_change_row): Boolean = is_guard_device && !row.kind.can_veto && row.server_cancellable

    private fun guard_change_row.skippable(): Boolean = kind.skippable && server_skippable
}

private enum class guard_change_op(val code: GuardActionCode, val done: guard_changes_notice) {
    cancel(GuardActionCode.CANCEL, guard_changes_notice.change_canceled),
    skip(GuardActionCode.SKIP, guard_changes_notice.change_skipped),
    stop(GuardActionCode.VETO, guard_changes_notice.change_stopped),
}

private data class guard_changes_snapshot(
    val user_id: java.util.UUID,
    val changes: List<guard_change_row>,
    val is_guard_device: Boolean,
    val long_trusted: Boolean,
)

private object guard_changes_memo {
    @Volatile
    var last: guard_changes_snapshot? = null
}

private fun guard_changes_initial(user_id: java.util.UUID?): guard_changes_ui {
    val cached = guard_changes_memo.last?.takeIf { user_id != null && it.user_id == user_id } ?: return guard_changes_ui()
    return guard_changes_ui(
        loading = false,
        loaded = true,
        changes = cached.changes,
        is_guard_device = cached.is_guard_device,
        long_trusted = cached.long_trusted,
    )
}

@HiltViewModel
class GuardChangesViewModel @Inject constructor(
    private val api: GuardApi,
    private val store: GuardDeviceStore,
    private val user: guard_current_user,
) : ViewModel() {

    private val _state = MutableStateFlow(guard_changes_initial(user.user_id()))
    val state: StateFlow<guard_changes_ui> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { org.astermail.android.guard.guard_live_events.events.collect { load() } }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(loading = !it.loaded, load_failed = false) }
        try {
            val status = api.status()
            val list = api.changes()
            val rows = list.changes.map { change -> change.to_row() }.sortedBy { row -> row.apply_after ?: Instant.MAX }
            val is_guard_device = status.this_device != null
            val long_trusted = status.this_device?.long_trusted == true
            _state.update {
                it.copy(
                    loading = false,
                    loaded = true,
                    hidden = false,
                    changes = rows,
                    is_guard_device = is_guard_device,
                    long_trusted = long_trusted,
                )
            }
            user.user_id()?.let { id ->
                guard_changes_memo.last = guard_changes_snapshot(id, rows, is_guard_device, long_trusted)
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            _state.update {
                if (guard_is_disabled(t)) it.copy(loading = false, hidden = true) else it.copy(loading = false, load_failed = true)
            }
        }
    }

    fun cancel(signer: guard_signer, id: String) = launch_op(signer, id, guard_change_op.cancel)

    fun skip(signer: guard_signer, id: String) = launch_op(signer, id, guard_change_op.skip)

    fun stop(signer: guard_signer, id: String) = launch_op(signer, id, guard_change_op.stop)

    fun skip_with_passkey(signer: guard_signer, sheet: guard_passkey_sheet, id: String) {
        if (_state.value.working_id != null) return
        viewModelScope.launch { run_op(signer, id, guard_change_op.skip, sheet) }
    }

    suspend fun run_skip_with_passkey(signer: guard_signer, sheet: guard_passkey_sheet, id: String) =
        run_op(signer, id, guard_change_op.skip, sheet)

    suspend fun run_cancel(signer: guard_signer, id: String) = run_op(signer, id, guard_change_op.cancel)

    suspend fun run_skip(signer: guard_signer, id: String) = run_op(signer, id, guard_change_op.skip)

    suspend fun run_stop(signer: guard_signer, id: String) = run_op(signer, id, guard_change_op.stop)

    private fun launch_op(signer: guard_signer, id: String, op: guard_change_op) {
        if (_state.value.working_id != null) return
        viewModelScope.launch { run_op(signer, id, op) }
    }

    private suspend fun run_op(signer: guard_signer, id: String, op: guard_change_op, sheet: guard_passkey_sheet? = null) {
        val user_id = user.user_id() ?: return
        val change_uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return
        _state.update { it.copy(working_id = id, notice = null, passkey_failure = null) }
        try {
            val proof: StepUpProof? = sheet?.let { guard_passkey_proof(api, user_id, user::user_id, it) }
            val action = GuardRepository(api, store) { user_id }
                .signed_with(signer, op.code, GuardDigests.id_subject(change_uuid), proof)
            if (action == null) {
                _state.update { it.copy(working_id = null, notice = guard_changes_notice.canceled) }
                return
            }
            if (sheet != null && user.user_id() != user_id) {
                throw GuardPasskeyException(guard_passkey_failure.account_changed)
            }
            val done = when (op) {
                guard_change_op.cancel ->
                    if (api.cancel_change(id, action).cancelled) op.done else guard_changes_notice.change_already_ended
                guard_change_op.skip ->
                    if (api.skip_change(id, action).stale) guard_changes_notice.change_stale else op.done
                guard_change_op.stop -> {
                    api.veto_signed(id, action)
                    op.done
                }
            }
            _state.update { it.copy(working_id = null, notice = done) }
            load()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            val passkey_failure = guard_passkey_failure_for(t)
            if (passkey_failure != null) {
                _state.update {
                    it.copy(working_id = null, passkey_failure = passkey_failure.takeUnless { f -> f == guard_passkey_failure.canceled })
                }
                return
            }
            val notice = when {
                sheet != null && api_error_code(t) == GUARD_STEP_UP_REQUIRED_CODE -> {
                    _state.update { it.copy(working_id = null, passkey_failure = guard_passkey_failure.failed) }
                    return
                }
                api_error_code(t) == GUARD_STEP_UP_REQUIRED_CODE -> guard_changes_notice.step_up_required
                else -> when (guard_action_notice_for(t)) {
                    guard_action_notice.key_lost -> guard_changes_notice.key_lost
                    guard_action_notice.no_lock_screen -> guard_changes_notice.no_lock_screen
                    guard_action_notice.no_device -> guard_changes_notice.no_device
                    else -> guard_changes_notice.failed
                }
            }
            _state.update { it.copy(working_id = null, notice = notice, error = org.astermail.android.guard.guard_error_message_for(t)) }
        }
    }

    fun dismiss_notice() = _state.update { it.copy(notice = null, passkey_failure = null) }
}
