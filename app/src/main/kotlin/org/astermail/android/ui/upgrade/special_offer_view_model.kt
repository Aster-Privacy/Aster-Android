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

package org.astermail.android.ui.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.api.billing.BillingApi

data class SpecialOfferState(
    val is_loaded: Boolean = false,
    val available: Boolean = false,
    val auto_show: Boolean = false,
    val percent_off: Int = 0,
    val duration_months: Int = 0,
    val plan_code: String = "nova",
    val is_open: Boolean = false,
    val is_claiming: Boolean = false,
    val is_accepting: Boolean = false,
)

@HiltViewModel
class SpecialOfferViewModel @Inject constructor(
    private val billing_api: BillingApi,
) : ViewModel() {
    private val _state = MutableStateFlow(SpecialOfferState())
    val state: StateFlow<SpecialOfferState> = _state.asStateFlow()

    private var has_loaded = false

    fun load() {
        if (has_loaded) return
        has_loaded = true
        viewModelScope.launch {
            try {
                val status = billing_api.get_special_offer()
                _state.value = _state.value.copy(
                    is_loaded = true,
                    available = status.available,
                    auto_show = status.auto_show,
                    percent_off = status.percent_off,
                    duration_months = status.duration_months,
                    plan_code = status.plan_code.ifBlank { "nova" },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                has_loaded = false
                _state.value = _state.value.copy(is_loaded = true, available = false, auto_show = false)
            }
        }
    }

    fun claim_and_open() {
        if (_state.value.is_claiming || _state.value.is_open) return
        _state.value = _state.value.copy(is_claiming = true)
        viewModelScope.launch {
            val granted = try {
                billing_api.claim_special_offer().granted
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                false
            }
            _state.value = _state.value.copy(is_claiming = false, is_open = granted, auto_show = false)
        }
    }

    fun close() {
        _state.value = _state.value.copy(is_open = false)
    }

    fun accept_then(on_accepted: () -> Unit) {
        if (_state.value.is_accepting) return
        _state.value = _state.value.copy(is_accepting = true)
        viewModelScope.launch {
            try {
                billing_api.accept_special_offer()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
            }
            _state.value = _state.value.copy(is_accepting = false)
            on_accepted()
        }
    }

    fun dismiss_forever() {
        _state.value = _state.value.copy(is_open = false, available = false, auto_show = false)
        viewModelScope.launch {
            try {
                billing_api.dismiss_special_offer()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
            }
        }
    }
}
