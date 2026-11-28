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

package org.astermail.android.survey

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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.ApiError
import org.astermail.android.api.found_us.FoundUsAnswer
import org.astermail.android.api.found_us.FoundUsApi
import org.astermail.android.api.found_us.clean_found_us_detail
import org.astermail.android.api.found_us.found_us_other
import org.astermail.android.api.found_us.found_us_sources

enum class FoundUsFormError { PICK, DETAIL }

enum class FoundUsOutcome { THANKS, SEND_FAILED, CLOSED }

sealed interface FoundUsValidation {
    data class Valid(val answer: FoundUsAnswer) : FoundUsValidation
    data class Invalid(val error: FoundUsFormError) : FoundUsValidation
}

fun validate_found_us_answer(source: String?, detail: String): FoundUsValidation {
    if (source == null || source !in found_us_sources) {
        return FoundUsValidation.Invalid(FoundUsFormError.PICK)
    }
    if (source != found_us_other) return FoundUsValidation.Valid(FoundUsAnswer(source))
    val cleaned = clean_found_us_detail(detail)
    if (cleaned.isEmpty()) return FoundUsValidation.Invalid(FoundUsFormError.DETAIL)
    return FoundUsValidation.Valid(FoundUsAnswer(source, cleaned))
}

data class FoundUsUiState(
    val account_id: String? = null,
    val eligible_account_id: String? = null,
    val hidden: Boolean = false,
    val choosing_dismissal: Boolean = false,
    val is_sending: Boolean = false,
) {
    val is_eligible: Boolean get() = account_id != null && eligible_account_id == account_id
    val is_banner_visible: Boolean get() = is_eligible && !hidden
}

private enum class SendResult { OK, NOT_ASKED, FAILED }

@HiltViewModel
class FoundUsViewModel @Inject constructor(
    private val api: FoundUsApi,
) : ViewModel() {
    private val _state = MutableStateFlow(FoundUsUiState())
    val state: StateFlow<FoundUsUiState> = _state.asStateFlow()

    private val _outcomes = Channel<FoundUsOutcome>(Channel.BUFFERED)
    val outcomes: Flow<FoundUsOutcome> = _outcomes.receiveAsFlow()

    private var load_job: Job? = null
    private var loaded_once = false

    fun load(account_id: String?) {
        if (loaded_once && account_id == _state.value.account_id) return
        loaded_once = true
        load_job?.cancel()
        _state.value = FoundUsUiState(account_id = account_id)
        if (account_id == null) return
        load_job = viewModelScope.launch {
            val eligible = try {
                api.get_status().eligible
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            if (!eligible) return@launch
            _state.update { current ->
                if (current.account_id == account_id) current.copy(eligible_account_id = account_id) else current
            }
        }
    }

    fun choose_dismissal() {
        if (!_state.value.is_banner_visible) return
        _state.update { it.copy(choosing_dismissal = true) }
    }

    fun remind_later() {
        if (!_state.value.is_banner_visible) return
        _state.update { it.copy(hidden = true) }
        fire_and_forget { api.snooze() }
    }

    fun dont_ask() {
        if (!_state.value.is_banner_visible) return
        _state.update { it.copy(hidden = true) }
        fire_and_forget { api.finish(null) }
    }

    fun submit(source: String?, detail: String): FoundUsFormError? {
        if (_state.value.is_sending || !_state.value.is_eligible) return null
        val answer = when (val result = validate_found_us_answer(source, detail)) {
            is FoundUsValidation.Invalid -> return result.error
            is FoundUsValidation.Valid -> result.answer
        }
        send(answer)
        return null
    }

    fun skip() {
        if (_state.value.is_sending || !_state.value.is_eligible) return
        send(null)
    }

    private fun send(answer: FoundUsAnswer?) {
        val account_id = _state.value.account_id
        _state.update { it.copy(is_sending = true) }
        viewModelScope.launch {
            val result = try {
                api.finish(answer)
                SendResult.OK
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ApiError.ForbiddenError) {
                SendResult.NOT_ASKED
            } catch (_: Throwable) {
                SendResult.FAILED
            }
            if (_state.value.account_id != account_id) return@launch
            val with_answer = answer != null
            if (with_answer && result == SendResult.FAILED) {
                _state.update { it.copy(is_sending = false) }
                _outcomes.trySend(FoundUsOutcome.SEND_FAILED)
                return@launch
            }
            _state.update { it.copy(is_sending = false, hidden = true) }
            val outcome = if (with_answer && result == SendResult.OK) FoundUsOutcome.THANKS else FoundUsOutcome.CLOSED
            _outcomes.trySend(outcome)
        }
    }

    private fun fire_and_forget(call: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                call()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            }
        }
    }
}
