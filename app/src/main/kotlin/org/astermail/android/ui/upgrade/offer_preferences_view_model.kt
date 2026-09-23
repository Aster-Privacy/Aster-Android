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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OfferPreferencesUiState(
    val enabled: Boolean = true,
    val available: Boolean = false,
    val save_failed: Boolean = false,
)

@HiltViewModel
class OfferPreferencesViewModel @Inject constructor(
    private val store: OfferPreferencesStore,
) : ViewModel() {
    private val save_failed = MutableStateFlow(false)

    val state: StateFlow<OfferPreferencesUiState> = combine(store.state, save_failed) { preferences, failed ->
        OfferPreferencesUiState(
            enabled = preferences.enabled,
            available = preferences.loaded,
            save_failed = failed,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        OfferPreferencesUiState(
            enabled = store.state.value.enabled,
            available = store.state.value.loaded,
        ),
    )

    fun load() {
        viewModelScope.launch { store.load() }
    }

    fun set_enabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!store.set_enabled(enabled)) save_failed.value = true
        }
    }

    fun clear_save_failed() {
        save_failed.value = false
    }
}
