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
import org.astermail.android.api.domains.BIMI_MAX_LOGO_BYTES
import org.astermail.android.api.domains.BimiApi
import org.astermail.android.api.domains.BimiLogoInvalid
import org.astermail.android.api.domains.BimiState
import org.astermail.android.api.domains.BimiView

enum class BimiStep { loading, logo, publish, manage }

enum class BimiErrorKind { generic, throttled, domain_not_active, file_too_large }

const val BIMI_AUTO_CHECK_INTERVAL_MS = 20_000L

private val bimi_auto_check_states = setOf(BimiState.pending, BimiState.attention)

fun bimi_initial_step(state: BimiState): BimiStep = when (state) {
    BimiState.off -> BimiStep.logo
    BimiState.draft, BimiState.pending, BimiState.attention -> BimiStep.publish
    BimiState.live, BimiState.external -> BimiStep.manage
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
    val adjustments: List<String> = emptyList(),
    val logo_errors: List<String> = emptyList(),
    val error: BimiErrorKind? = null,
    val show_remove_record_note: Boolean = false,
) {
    val bimi_state: BimiState get() = view?.bimi_state ?: BimiState.off
    val is_published: Boolean get() = bimi_state != BimiState.off && bimi_state != BimiState.draft
}

@HiltViewModel
class BimiViewModel @Inject constructor(
    private val bimi_api: BimiApi,
) : ViewModel() {

    private val _state = MutableStateFlow(BimiUiState())
    val state: StateFlow<BimiUiState> = _state.asStateFlow()

    private var auto_check_job: Job? = null
    private var publish_visible = false

    fun load(domain_id: String) {
        if (_state.value.domain_id == domain_id && _state.value.view != null) return
        _state.value = BimiUiState(domain_id = domain_id)
        viewModelScope.launch {
            try {
                val view = bimi_api.get_bimi(domain_id)
                _state.update { it.copy(view = view, step = bimi_initial_step(view.bimi_state)) }
                sync_auto_check()
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                _state.update { it.copy(load_failed = true) }
            }
        }
    }

    fun retry_load() {
        val domain_id = _state.value.domain_id
        _state.value = BimiUiState()
        load(domain_id)
    }

    fun upload_logo(bytes: ByteArray?) {
        val current = _state.value
        if (current.uploading || current.domain_id.isEmpty()) return
        if (bytes == null) {
            _state.update { it.copy(error = BimiErrorKind.generic, logo_errors = emptyList(), adjustments = emptyList()) }
            return
        }
        if (bytes.size > BIMI_MAX_LOGO_BYTES) {
            _state.update { it.copy(error = BimiErrorKind.file_too_large, logo_errors = emptyList(), adjustments = emptyList()) }
            return
        }
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
                    it.copy(uploading = false, view = response.bimi, adjustments = response.adjustments)
                }
                sync_auto_check()
            } catch (t: CancellationException) {
                throw t
            } catch (t: BimiLogoInvalid) {
                _state.update { it.copy(uploading = false, logo_errors = t.errors.ifEmpty { listOf("malformed") }) }
            } catch (t: Throwable) {
                _state.update { it.copy(uploading = false, error = error_kind(t)) }
            }
        }
    }

    fun publish() {
        val current = _state.value
        if (current.publishing || current.domain_id.isEmpty()) return
        _state.update { it.copy(publishing = true, error = null) }
        viewModelScope.launch {
            try {
                val view = bimi_api.publish(current.domain_id)
                _state.update { it.copy(publishing = false, view = view) }
                sync_auto_check()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(publishing = false, error = error_kind(t)) }
            }
        }
    }

    fun check() {
        val current = _state.value
        if (current.checking || current.domain_id.isEmpty()) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val view = bimi_api.check(current.domain_id)
                _state.update { it.copy(checking = false, view = view) }
                sync_auto_check()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(checking = false, error = error_kind(t)) }
            }
        }
    }

    fun turn_off() {
        val current = _state.value
        if (current.turning_off || current.domain_id.isEmpty()) return
        val managed = current.view?.managed_dns == true
        val was_published = current.is_published
        _state.update { it.copy(turning_off = true, error = null) }
        viewModelScope.launch {
            try {
                val view = bimi_api.turn_off(current.domain_id)
                _state.update {
                    it.copy(
                        turning_off = false,
                        view = view,
                        step = BimiStep.logo,
                        replacing = false,
                        adjustments = emptyList(),
                        logo_errors = emptyList(),
                        show_remove_record_note = was_published && !managed,
                    )
                }
                sync_auto_check()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(turning_off = false, error = error_kind(t)) }
            }
        }
    }

    fun go_to_publish() {
        _state.update { it.copy(step = BimiStep.publish, error = null, replacing = false) }
    }

    fun go_to_logo() {
        _state.update { it.copy(step = BimiStep.logo, error = null) }
    }

    fun replace_logo() {
        _state.update {
            it.copy(step = BimiStep.logo, replacing = true, error = null, adjustments = emptyList(), logo_errors = emptyList())
        }
    }

    fun go_to_manage() {
        _state.update {
            it.copy(step = BimiStep.manage, replacing = false, error = null, adjustments = emptyList(), logo_errors = emptyList())
        }
    }

    fun set_publish_visible(visible: Boolean) {
        publish_visible = visible
        sync_auto_check()
    }

    private fun sync_auto_check() {
        val should_run = publish_visible && _state.value.bimi_state in bimi_auto_check_states
        if (!should_run) {
            auto_check_job?.cancel()
            auto_check_job = null
            return
        }
        if (auto_check_job?.isActive == true) return
        auto_check_job = viewModelScope.launch {
            while (true) {
                delay(BIMI_AUTO_CHECK_INTERVAL_MS)
                auto_check()
            }
        }
    }

    private suspend fun auto_check() {
        val current = _state.value
        if (current.checking || current.publishing || current.uploading || current.turning_off) return
        try {
            val view = bimi_api.check(current.domain_id)
            _state.update { it.copy(view = view) }
            sync_auto_check()
        } catch (t: CancellationException) {
            throw t
        } catch (_: Throwable) {
        }
    }

    private fun error_kind(t: Throwable): BimiErrorKind = when {
        t is ApiError.RateLimited -> BimiErrorKind.throttled
        t is ApiError.ValidationError && t.code == BIMI_DOMAIN_NOT_ACTIVE_CODE -> BimiErrorKind.domain_not_active
        else -> BimiErrorKind.generic
    }
}
