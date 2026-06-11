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

package org.astermail.android.vacation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.vacation.UpsertVacationReplyRequest
import org.astermail.android.api.vacation.VacationReplyApi

data class VacationReplyUiState(
    val is_loading: Boolean = false,
    val is_busy: Boolean = false,
    val error: String? = null,
    val saved_message: String? = null,
    val exists: Boolean = false,
    val is_enabled: Boolean = false,
    val subject: String = "",
    val body: String = "",
    val external_only: Boolean = false,
    val reply_count: Int = 0,
)

@HiltViewModel
class VacationReplyViewModel @Inject constructor(
    private val vacation_api: VacationReplyApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(VacationReplyUiState())
    val state: StateFlow<VacationReplyUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            val outcome = runCatching { vacation_api.get() }
            _state.value = outcome.fold(
                onSuccess = { reply ->
                    if (reply == null) {
                        _state.value.copy(
                            is_loading = false,
                            exists = false,
                            is_enabled = false,
                            subject = context.getString(R.string.vacation_default_subject),
                            body = context.getString(R.string.vacation_default_body),
                            external_only = false,
                            reply_count = 0,
                        )
                    } else {
                        _state.value.copy(
                            is_loading = false,
                            exists = true,
                            is_enabled = reply.is_enabled,
                            subject = reply.subject,
                            body = reply.body,
                            external_only = reply.external_only,
                            reply_count = reply.reply_count,
                        )
                    }
                },
                onFailure = { _state.value.copy(is_loading = false, error = readable(it)) },
            )
        }
    }

    fun update_subject(value: String) {
        _state.value = _state.value.copy(subject = value, error = null, saved_message = null)
    }

    fun update_body(value: String) {
        _state.value = _state.value.copy(body = value, error = null, saved_message = null)
    }

    fun set_enabled(value: Boolean) {
        _state.value = _state.value.copy(is_enabled = value, error = null, saved_message = null)
    }

    fun set_external_only(value: Boolean) {
        _state.value = _state.value.copy(external_only = value, error = null, saved_message = null)
    }

    fun save() {
        val s = _state.value
        if (s.is_busy) return
        if (s.subject.isBlank()) {
            _state.value = s.copy(error = context.getString(R.string.subject_required))
            return
        }
        if (s.body.isBlank()) {
            _state.value = s.copy(error = context.getString(R.string.reply_body_required))
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(is_busy = true, error = null, saved_message = null)
            val outcome = runCatching {
                vacation_api.upsert(
                    UpsertVacationReplyRequest(
                        subject = s.subject.trim(),
                        body = s.body.trim(),
                        is_enabled = s.is_enabled,
                        external_only = s.external_only,
                    ),
                )
            }
            _state.value = outcome.fold(
                onSuccess = {
                    _state.value.copy(
                        is_busy = false,
                        exists = true,
                        is_enabled = it.is_enabled,
                        subject = it.subject,
                        body = it.body,
                        external_only = it.external_only,
                        reply_count = it.reply_count,
                        saved_message = context.getString(R.string.saved),
                    )
                },
                onFailure = { _state.value.copy(is_busy = false, error = readable(it)) },
            )
        }
    }

    fun delete() {
        val s = _state.value
        if (s.is_busy || !s.exists) return
        viewModelScope.launch {
            _state.value = s.copy(is_busy = true, error = null, saved_message = null)
            val outcome = runCatching { vacation_api.delete() }
            _state.value = outcome.fold(
                onSuccess = {
                    _state.value.copy(
                        is_busy = false,
                        exists = false,
                        is_enabled = false,
                        reply_count = 0,
                        saved_message = context.getString(R.string.removed),
                    )
                },
                onFailure = { _state.value.copy(is_busy = false, error = readable(it)) },
            )
        }
    }

    private fun readable(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.something_went_wrong)
}
