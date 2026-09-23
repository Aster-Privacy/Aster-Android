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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.OfferPreferences

data class OfferPreferencesState(
    val enabled: Boolean = true,
    val loaded: Boolean = false,
)

@Singleton
class OfferPreferencesStore @Inject constructor(
    private val billing_api: BillingApi,
) {
    private val _state = MutableStateFlow(OfferPreferencesState())
    val state: StateFlow<OfferPreferencesState> = _state.asStateFlow()

    private var write_generation = 0

    suspend fun load(): Boolean {
        val generation = write_generation
        return try {
            val preferences = billing_api.get_offer_preferences()
            if (generation == write_generation) {
                _state.update { it.copy(enabled = preferences.in_app_offers_enabled, loaded = true) }
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            false
        }
    }

    fun reset() {
        write_generation++
        _state.value = OfferPreferencesState()
    }

    suspend fun set_enabled(enabled: Boolean): Boolean {
        val previous = _state.value.enabled
        val generation = ++write_generation
        _state.update { it.copy(enabled = enabled) }
        return try {
            val saved = billing_api.set_offer_preferences(OfferPreferences(in_app_offers_enabled = enabled))
            if (generation == write_generation) {
                _state.update { it.copy(enabled = saved.in_app_offers_enabled, loaded = true) }
            }
            true
        } catch (cancelled: CancellationException) {
            if (generation == write_generation) _state.update { it.copy(enabled = previous) }
            throw cancelled
        } catch (t: Throwable) {
            if (generation == write_generation) _state.update { it.copy(enabled = previous) }
            false
        }
    }
}
