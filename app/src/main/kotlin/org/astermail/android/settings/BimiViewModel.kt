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

package org.astermail.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.ApiError
import org.astermail.android.api.domains.BIMI_DOMAIN_NOT_ACTIVE_CODE
import org.astermail.android.api.domains.BIMI_LOGO_REQUIRED_CODE
import org.astermail.android.api.domains.BIMI_MAX_LOGO_BYTES
import org.astermail.android.api.domains.BimiApi
import org.astermail.android.api.domains.BimiLogoInvalid
import org.astermail.android.api.domains.BimiState
import org.astermail.android.api.domains.BimiView

enum class BimiStep { loading, logo, publish, manage }

enum class BimiErrorKind { generic, throttled, domain_not_active, file_too_large, logo_required }

const val BIMI_AUTO_CHECK_INTERVAL_MS = 20_000L

private val bimi_auto_check_states = setOf(BimiState.pending, BimiState.attention)

private val bimi_throttled_codes = setOf("BIMI_UPLOAD_THROTTLED", "BIMI_ACTION_THROTTLED", "RATE_LIMIT_EXCEEDED")

private const val PAYLOAD_TOO_LARGE_CODE = "PAYLOAD_TOO_LARGE"

fun bimi_initial_step(state: BimiState): BimiStep = when (state) {
    BimiState.off -> BimiStep.logo
    BimiState.draft -> BimiStep.publish
    BimiState.pending, BimiState.attention, BimiState.live, BimiState.external -> BimiStep.manage
}

fun bimi_error_kind(t: Throwable): BimiErrorKind {
    if (t is ApiError.AttachmentTooLarge) return BimiErrorKind.file_too_large
    val code = when (t) {
        is ApiError.ValidationError -> t.code
        is ApiError.Conflict -> t.code
        is ApiError.ForbiddenError -> t.code
        is ApiError.RateLimited -> t.code
        else -> null
    }
    return when {
        t is ApiError.RateLimited -> BimiErrorKind.throttled
        code in bimi_throttled_codes -> BimiErrorKind.throttled
        code == BIMI_LOGO_REQUIRED_CODE -> BimiErrorKind.logo_required
        code == BIMI_DOMAIN_NOT_ACTIVE_CODE -> BimiErrorKind.domain_not_active
        code == PAYLOAD_TOO_LARGE_CODE -> BimiErrorKind.file_too_large
        else -> BimiErrorKind.generic
    }
}

data class BimiUiState(
    val domain_id: String = "",
    val step: BimiStep = BimiStep.loading,
    val view: BimiView? = null,
    val load_failed: Boolean = false,
    val replacing: Boolean = false,
    val uploading: Boolean = false,
    val publishing: Boolean = false,
    val checking: Boolean = false,
    val turning_off: Boolean = false,
    val confirm_turn_off: Boolean = false,
    val adjustments: List<String> = emptyList(),
    val logo_errors: List<String> = emptyList(),
    val error: BimiErrorKind? = null,
    val show_remove_record_note: Boolean = false,
) {
    val bimi_state: BimiState get() = view?.bimi_state ?: BimiState.off
    val is_published: Boolean get() = bimi_state != BimiState.off && bimi_state != BimiState.draft
    val busy: Boolean get() = uploading || publishing || checking || turning_off
    val auto_checking: Boolean get() = step == BimiStep.manage && bimi_state in bimi_auto_check_states
}

@HiltViewModel
class BimiViewModel @Inject constructor(
    private val bimi_api: BimiApi,
) : ViewModel() {

    private val _state = MutableStateFlow(BimiUiState())
    val state: StateFlow<BimiUiState> = _state.asStateFlow()

    private var auto_check_job: Job? = null
    private var load_job: Job? = null
    private var screen_visible = false
    private var generation = 0L

    private fun next_generation(): Long {
        generation += 1
        return generation
    }

    private fun is_current(gen: Long): Boolean = gen == generation

    fun load(domain_id: String) {
        val current = _state.value
        if (current.domain_id == domain_id && (current.view != null || load_job?.isActive == true)) return
        start_load(domain_id)
    }

    fun retry_load() {
        val domain_id = _state.value.domain_id
        if (domain_id.isEmpty() || load_job?.isActive == true) return
        start_load(domain_id)
    }

    private fun start_load(domain_id: String) {
        val gen = next_generation()
        _state.value = BimiUiState(domain_id = domain_id)
        sync_auto_check()
        load_job = viewModelScope.launch {
            try {
                val view = bimi_api.get_bimi(domain_id)
                if (!is_current(gen)) return@launch
                _state.update { it.copy(view = view, step = bimi_initial_step(view.bimi_state), load_failed = false) }
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                if (!is_current(gen)) return@launch
                _state.update { it.copy(load_failed = true) }
            }
            sync_auto_check()
        }
    }

    fun upload_logo(bytes: ByteArray?) {
        val current = _state.value
        if (current.busy || current.view == null) return
        if (bytes == null) {
            _state.update { it.copy(error = BimiErrorKind.generic, logo_errors = emptyList(), adjustments = emptyList()) }
            return
        }
        if (bytes.size > BIMI_MAX_LOGO_BYTES) {
            _state.update { it.copy(error = BimiErrorKind.file_too_large, logo_errors = emptyList(), adjustments = emptyList()) }
            return
        }
        val gen = next_generation()
        _state.update {
            it.copy(
                uploading = true,
                error = null,
                logo_errors = emptyList(),
                adjustments = emptyList(),
                show_remove_record_note = false,
            )
        }
        viewModelScope.launch {
            try {
                val response = bimi_api.upload_logo(current.domain_id, bytes)
                _state.update {
                    if (is_current(gen)) {
                        it.copy(uploading = false, view = response.bimi, adjustments = response.adjustments)
                    } else {
                        it.copy(uploading = false)
                    }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: BimiLogoInvalid) {
                _state.update { it.copy(uploading = false, logo_errors = t.errors.ifEmpty { listOf("malformed") }) }
            } catch (t: Throwable) {
                _state.update { it.copy(uploading = false, error = bimi_error_kind(t)) }
            }
            sync_auto_check()
        }
    }

    fun publish() {
        val current = _state.value
        val view = current.view
        if (current.busy || view == null) return
        if (!view.domain_active) {
            _state.update { it.copy(error = BimiErrorKind.domain_not_active) }
            return
        }
        val gen = next_generation()
        _state.update { it.copy(publishing = true, error = null) }
        viewModelScope.launch {
            try {
                val published = bimi_api.publish(current.domain_id)
                _state.update {
                    if (is_current(gen)) {
                        it.copy(
                            publishing = false,
                            view = published,
                            step = BimiStep.manage,
                            replacing = false,
                            adjustments = emptyList(),
                            logo_errors = emptyList(),
                        )
                    } else {
                        it.copy(publishing = false)
                    }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(publishing = false, error = bimi_error_kind(t)) }
            }
            sync_auto_check()
        }
    }

    fun check() {
        val current = _state.value
        if (current.busy || current.view == null) return
        val gen = next_generation()
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val view = bimi_api.check(current.domain_id)
                _state.update {
                    if (is_current(gen)) it.copy(checking = false, view = view) else it.copy(checking = false)
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(checking = false, error = bimi_error_kind(t)) }
            }
            sync_auto_check()
        }
    }

    fun request_turn_off() {
        val current = _state.value
        if (current.busy || current.view == null) return
        _state.update { it.copy(confirm_turn_off = true) }
    }

    fun dismiss_turn_off() {
        _state.update { it.copy(confirm_turn_off = false) }
    }

    fun turn_off() {
        val current = _state.value
        val view = current.view
        if (current.busy || view == null) {
            _state.update { it.copy(confirm_turn_off = false) }
            return
        }
        val managed = view.managed_dns
        val was_published = current.is_published
        val gen = next_generation()
        _state.update { it.copy(turning_off = true, confirm_turn_off = false, error = null) }
        viewModelScope.launch {
            try {
                val turned_off = bimi_api.turn_off(current.domain_id)
                _state.update {
                    if (is_current(gen)) {
                        it.copy(
                            turning_off = false,
                            view = turned_off,
                            step = BimiStep.logo,
                            replacing = false,
                            adjustments = emptyList(),
                            logo_errors = emptyList(),
                            show_remove_record_note = was_published && !managed,
                        )
                    } else {
                        it.copy(turning_off = false)
                    }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(turning_off = false, error = bimi_error_kind(t)) }
            }
            sync_auto_check()
        }
    }

    fun go_to_publish() {
        if (_state.value.busy) return
        _state.update { it.copy(step = BimiStep.publish, error = null, replacing = false) }
        sync_auto_check()
    }

    fun go_to_logo() {
        if (_state.value.busy) return
        _state.update { it.copy(step = BimiStep.logo, error = null) }
        sync_auto_check()
    }

    fun replace_logo() {
        if (_state.value.busy) return
        _state.update {
            it.copy(
                step = BimiStep.logo,
                replacing = true,
                error = null,
                adjustments = emptyList(),
                logo_errors = emptyList(),
                show_remove_record_note = false,
            )
        }
        sync_auto_check()
    }

    fun go_to_manage() {
        if (_state.value.busy) return
        _state.update {
            it.copy(
                step = if (it.is_published) BimiStep.manage else bimi_initial_step(it.bimi_state),
                replacing = false,
                error = null,
                adjustments = emptyList(),
                logo_errors = emptyList(),
            )
        }
        sync_auto_check()
    }

    fun set_screen_visible(visible: Boolean) {
        screen_visible = visible
        sync_auto_check()
    }

    private fun sync_auto_check() {
        val should_run = screen_visible && _state.value.auto_checking
        if (!should_run) {
            auto_check_job?.cancel()
            auto_check_job = null
            return
        }
        if (auto_check_job?.isActive == true) return
        auto_check_job = viewModelScope.launch {
            while (screen_visible && _state.value.auto_checking) {
                delay(BIMI_AUTO_CHECK_INTERVAL_MS)
                auto_check()
            }
        }
    }

    private suspend fun auto_check() {
        val current = _state.value
        if (current.busy || current.confirm_turn_off || !current.auto_checking) return
        val gen = next_generation()
        val view = try {
            bimi_api.check(current.domain_id)
        } catch (t: CancellationException) {
            throw t
        } catch (_: Throwable) {
            return
        }
        if (!is_current(gen)) return
        val latest = _state.value
        if (latest.busy || latest.confirm_turn_off || latest.domain_id != current.domain_id) return
        _state.update { it.copy(view = view) }
    }
}
