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

package org.astermail.android.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.PlanLimitsResponse

data class PlanLimitsUiState(
    val limits: PlanLimitsResponse? = null,
    val is_loading: Boolean = true,
)

@HiltViewModel
class PlanLimitsViewModel @Inject constructor(
    private val billing_api: BillingApi,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanLimitsUiState())
    val state: StateFlow<PlanLimitsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val response = billing_api.get_plan_limits()
                _state.value = PlanLimitsUiState(limits = response, is_loading = false)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(is_loading = false)
            }
        }
    }

    fun is_feature_locked(feature_key: String): Boolean {
        val limits = _state.value.limits ?: return false
        val info = limits.limits[feature_key] ?: return false
        return info.limit == 0
    }
}
