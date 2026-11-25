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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardStatus
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.GuardKeyLostException
import org.astermail.android.guard.GuardNoDeviceException
import org.astermail.android.guard.GuardNoLockScreenException
import org.astermail.android.guard.GuardRepository
import org.astermail.android.guard.guard_error_message
import org.astermail.android.guard.guard_error_message_for
import org.astermail.android.guard.guard_held_notice
import org.astermail.android.guard.guard_signer
import org.astermail.android.guard.to_notice
import org.astermail.android.storage.AccountStore

fun interface guard_current_user {
    fun user_id(): UUID?
}

@Module
@InstallIn(SingletonComponent::class)
object GuardCurrentUserModule {
    @Provides
    fun provide_guard_current_user(accounts: AccountStore): guard_current_user = guard_current_user {
        accounts.get_current_id()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}

enum class guard_action_notice { canceled, key_lost, no_lock_screen, no_device, failed }

fun guard_removal_strands(status: GuardStatus?, device_id: String): Boolean {
    val devices = status?.devices ?: return false
    if (status.switches == 0 || devices.none { it.id == device_id }) return false
    return devices.none { it.id != device_id }
}

fun guard_action_notice_for(t: Throwable): guard_action_notice = when (t) {
    is GuardKeyLostException -> guard_action_notice.key_lost
    is GuardNoLockScreenException -> guard_action_notice.no_lock_screen
    is GuardNoDeviceException -> guard_action_notice.no_device
    else -> guard_action_notice.failed
}

data class guard_switches_ui(
    val loading: Boolean = true,
    val hidden: Boolean = false,
    val load_failed: Boolean = false,
    val status: GuardStatus? = null,
    val draft_switches: Int = 0,
    val draft_scopes: Int = 0,
    val held_notice: guard_held_notice? = null,
    val pending_off_switches: Int = 0,
    val pending_off_scopes: Int = 0,
    val paused_until: String? = null,
    val saving: Boolean = false,
    val removing_id: String? = null,
    val notice: guard_action_notice? = null,
    val error: guard_error_message? = null,
) {
    val guard_on: Boolean get() = status?.let { guard_is_on(it) } == true
    val is_guard_device: Boolean get() = status?.this_device != null
    val dirty: Boolean
        get() = status != null && (draft_switches != status.switches || draft_scopes != status.hold_scopes)
    val can_save: Boolean get() = dirty && !saving && is_guard_device && valid_switches(draft_switches, draft_scopes)
    val missing_rescue: Boolean get() = guard_on && status?.has_rescue_code == false
}

@HiltViewModel
class GuardSwitchesViewModel @Inject constructor(
    private val api: GuardApi,
    private val store: GuardDeviceStore,
    private val user: guard_current_user,
) : ViewModel() {

    private val _state = MutableStateFlow(guard_switches_ui())
    val state: StateFlow<guard_switches_ui> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { org.astermail.android.guard.guard_live_events.events.collect { load_quietly() } }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(loading = it.status == null, load_failed = false) }
        val status = try {
            api.status()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            _state.update {
                if (guard_is_disabled(t)) it.copy(loading = false, hidden = true) else it.copy(loading = false, load_failed = true)
            }
            return
        }
        apply_status(status)
    }

    private fun apply_status(status: GuardStatus) = _state.update {
        it.copy(
            loading = false,
            hidden = false,
            load_failed = false,
            status = status,
            draft_switches = status.switches,
            draft_scopes = status.hold_scopes,
            paused_until = status.paused_until,
            pending_off_switches = it.pending_off_switches and status.switches,
            pending_off_scopes = it.pending_off_scopes and status.hold_scopes,
        )
    }

    fun toggle(bit: Int) = _state.update {
        val next = guard_toggle_switch(guard_selection(it.draft_switches, it.draft_scopes), bit)
        it.copy(draft_switches = next.switches, draft_scopes = next.scopes, notice = null)
    }

    fun toggle_scope(bit: Int) = _state.update {
        val next = guard_toggle_scope(guard_selection(it.draft_switches, it.draft_scopes), bit)
        it.copy(draft_switches = next.switches, draft_scopes = next.scopes, notice = null)
    }

    fun discard() = _state.update {
        val status = it.status ?: return@update it
        it.copy(draft_switches = status.switches, draft_scopes = status.hold_scopes, notice = null)
    }

    fun save(signer: guard_signer) {
        if (_state.value.saving) return
        viewModelScope.launch { run_save(signer) }
    }

    suspend fun run_save(signer: guard_signer) {
        val ui = _state.value
        val status = ui.status ?: return
        val id = user.user_id() ?: return
        if (!ui.can_save) return
        val switches = ui.draft_switches
        val scopes = ui.draft_scopes
        _state.update { it.copy(saving = true, notice = null) }
        try {
            val action = GuardRepository(api, store) { id }
                .signed_with(signer, GuardActionCode.SWITCHES, GuardDigests.switches_subject(switches, scopes))
            if (action == null) {
                _state.update { it.copy(saving = false, notice = guard_action_notice.canceled) }
                return
            }
            val updated = api.put_switches(switches, scopes, action)
            _state.update { it.copy(saving = false, held_notice = null) }
            apply_status(updated)
        } catch (t: CancellationException) {
            throw t
        } catch (held: ApiError.GuardHeld) {
            _state.update {
                it.copy(
                    saving = false,
                    held_notice = held.to_notice(),
                    pending_off_switches = status.switches and switches.inv(),
                    pending_off_scopes = status.hold_scopes and scopes.inv(),
                    draft_switches = status.switches or switches,
                    draft_scopes = status.hold_scopes or scopes,
                )
            }
            load_quietly()
        } catch (t: Throwable) {
            if (guard_is_disabled(t)) {
                _state.update { it.copy(saving = false, hidden = true) }
            } else {
                _state.update { it.copy(saving = false, notice = guard_action_notice_for(t), error = guard_error_message_for(t)) }
            }
        }
    }

    fun remove_device(signer: guard_signer, device_id: String) {
        if (_state.value.removing_id != null) return
        viewModelScope.launch { run_remove_device(signer, device_id) }
    }

    suspend fun run_remove_device(signer: guard_signer, device_id: String) {
        val id = user.user_id() ?: return
        val device_uuid = runCatching { UUID.fromString(device_id) }.getOrNull() ?: return
        if (guard_removal_strands(_state.value.status, device_id)) {
            _state.update {
                it.copy(notice = guard_action_notice.failed, error = guard_error_message(R.string.guard_error_last_device))
            }
            return
        }
        _state.update { it.copy(removing_id = device_id, notice = null) }
        try {
            val action = GuardRepository(api, store) { id }
                .signed_with(signer, GuardActionCode.REMOVE_DEVICE, GuardDigests.id_subject(device_uuid))
            if (action == null) {
                _state.update { it.copy(removing_id = null, notice = guard_action_notice.canceled) }
                return
            }
            api.remove_device(device_id, action)
            _state.update { it.copy(removing_id = null) }
            load_quietly()
        } catch (t: CancellationException) {
            throw t
        } catch (held: ApiError.GuardHeld) {
            _state.update { it.copy(removing_id = null, held_notice = held.to_notice()) }
            load_quietly()
        } catch (t: Throwable) {
            _state.update { it.copy(removing_id = null, notice = guard_action_notice_for(t), error = guard_error_message_for(t)) }
        }
    }

    private suspend fun load_quietly() {
        val status = try {
            api.status()
        } catch (t: CancellationException) {
            throw t
        } catch (_: Throwable) {
            return
        }
        val held = _state.value.held_notice
        apply_status(status)
        if (held != null) {
            _state.update {
                it.copy(
                    draft_switches = status.switches,
                    draft_scopes = status.hold_scopes,
                )
            }
        }
    }

    fun dismiss_notice() = _state.update { it.copy(notice = null) }
}

data class guard_entry_ui(
    val visible: Boolean = false,
    val on: Boolean = false,
    val is_guard_device: Boolean = false,
    val criterion_met: Boolean = false,
    val loaded: Boolean = false,
)

fun guard_criterion_met(status: GuardStatus): Boolean = status.switches and GuardSwitch.NEW_DEVICE_APPROVAL != 0

internal object guard_entry_memory {
    private val entries = java.util.concurrent.ConcurrentHashMap<String, guard_entry_ui>()

    fun read(account_key: String?): guard_entry_ui? =
        account_key?.takeIf { it.isNotBlank() }?.let { entries[it] }

    fun write(account_key: String?, entry: guard_entry_ui) {
        val key = account_key?.takeIf { it.isNotBlank() } ?: return
        entries[key] = entry
    }
}

@HiltViewModel
class GuardEntryViewModel @Inject constructor(private val api: GuardApi) : ViewModel() {
    private val _state = MutableStateFlow(guard_entry_ui())
    val state: StateFlow<guard_entry_ui> = _state.asStateFlow()
    private var account_key: String? = null

    fun bind_account(key: String?) {
        if (key == account_key) return
        account_key = key
        _state.value = guard_entry_memory.read(key) ?: guard_entry_ui()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        val key = account_key
        val status = try {
            api.status()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            val current = _state.value
            val keep = current.loaded && t !is ApiError.NotFoundError
            publish(key, if (keep) current else guard_entry_ui(loaded = true))
            return
        }
        publish(
            key,
            guard_entry_ui(
                visible = true,
                on = guard_is_on(status),
                is_guard_device = status.this_device != null,
                criterion_met = guard_criterion_met(status),
                loaded = true,
            ),
        )
    }

    private fun publish(key: String?, entry: guard_entry_ui) {
        if (key != account_key) return
        guard_entry_memory.write(key, entry)
        _state.value = entry
    }
}
