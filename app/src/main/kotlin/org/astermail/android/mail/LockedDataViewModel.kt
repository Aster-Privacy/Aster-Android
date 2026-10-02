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

package org.astermail.android.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.storage.SessionKeyStore

enum class LockedDataRecoveryOutcome { SUCCESS, PARTIAL, NO_MATCH, CODE_NO_MATCH, RATE_LIMITED, FAILED }

fun locked_data_recovery_outcome(result: LockedDataRecovery): LockedDataRecoveryOutcome = when {
    result.rate_limited -> LockedDataRecoveryOutcome.RATE_LIMITED
    result.restored_key_sets > 0 || result.recovered_sent_mail > 0 ->
        if (result.incomplete > 0) LockedDataRecoveryOutcome.PARTIAL else LockedDataRecoveryOutcome.SUCCESS
    result.failed -> LockedDataRecoveryOutcome.FAILED
    else -> LockedDataRecoveryOutcome.NO_MATCH
}

fun locked_data_code_recovery_outcome(result: LockedDataRecovery): LockedDataRecoveryOutcome =
    locked_data_recovery_outcome(result).let {
        if (it == LockedDataRecoveryOutcome.NO_MATCH) LockedDataRecoveryOutcome.CODE_NO_MATCH else it
    }

data class LockedDataUiState(
    val status: LockedDataStatus? = null,
    val vault_unlocked: Boolean = false,
    val dismissed_signature: String? = null,
    val recovering: Boolean = false,
    val last_outcome: LockedDataRecoveryOutcome? = null,
)

@HiltViewModel
class LockedDataViewModel @Inject constructor(
    private val service: LockedDataService,
    private val session_key_store: SessionKeyStore,
    locked_sent_mail_store: LockedSentMailStore,
) : ViewModel() {
    private val _state = MutableStateFlow(LockedDataUiState())
    val state: StateFlow<LockedDataUiState> = _state.asStateFlow()

    private val _outcomes = Channel<LockedDataRecoveryOutcome>(Channel.BUFFERED)
    val outcomes: Flow<LockedDataRecoveryOutcome> = _outcomes.receiveAsFlow()

    private var refresh_job: Job? = null

    init {
        viewModelScope.launch {
            locked_sent_mail_store.version.drop(1).collect { refresh() }
        }
    }

    fun refresh() {
        refresh_job?.cancel()
        refresh_job = viewModelScope.launch {
            val account_id = session_key_store.get_user_id().orEmpty()
            val passphrase = session_key_store.get_passphrase()
            val unlocked = passphrase != null && passphrase.isNotEmpty() &&
                !session_key_store.get_identity_key().isNullOrEmpty()
            passphrase?.fill(0)
            val status = if (unlocked) service.status(account_id) else null
            _state.update { it.copy(status = status, vault_unlocked = unlocked) }
        }
    }

    fun mark_dismissed(signature: String) {
        _state.update { it.copy(dismissed_signature = signature) }
    }

    fun clear_outcome() {
        _state.update { it.copy(last_outcome = null) }
    }

    fun recover(password: String) {
        if (_state.value.recovering || password.isEmpty()) return
        run_recovery(::locked_data_recovery_outcome) { service.recover_locked_data(it, password) }
    }

    fun recover_with_code(code: String) {
        if (_state.value.recovering || code.isBlank()) return
        run_recovery(::locked_data_code_recovery_outcome) { service.recover_locked_data_with_code(it, code) }
    }

    private fun run_recovery(
        to_outcome: (LockedDataRecovery) -> LockedDataRecoveryOutcome,
        recover: suspend (String) -> LockedDataRecovery,
    ) {
        val account_id = session_key_store.get_user_id().orEmpty()
        _state.update { it.copy(recovering = true, last_outcome = null) }
        viewModelScope.launch {
            val result = try {
                recover(account_id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                LockedDataRecovery(failed = true)
            }
            if (session_key_store.get_user_id().orEmpty() != account_id) {
                _state.update { it.copy(recovering = false) }
                return@launch
            }
            val outcome = to_outcome(result)
            _state.update {
                it.copy(
                    recovering = false,
                    last_outcome = outcome.takeIf { value -> value != LockedDataRecoveryOutcome.SUCCESS },
                )
            }
            _outcomes.trySend(outcome)
            refresh()
        }
    }
}
