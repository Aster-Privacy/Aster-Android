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

package org.astermail.android.api.network

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object low_network_state {

    private val preference_enabled = AtomicBoolean(false)
    private val data_saver_enabled = AtomicBoolean(false)
    private val active = MutableStateFlow(false)

    val is_active: StateFlow<Boolean> = active.asStateFlow()

    fun set_preference(enabled: Boolean) {
        preference_enabled.set(enabled)
        recompute()
    }

    fun set_data_saver(enabled: Boolean) {
        data_saver_enabled.set(enabled)
        recompute()
    }

    fun is_preference_enabled(): Boolean = preference_enabled.get()

    fun is_data_saver_enabled(): Boolean = data_saver_enabled.get()

    fun active(): Boolean = active.value

    fun reset() {
        preference_enabled.set(false)
        data_saver_enabled.set(false)
        recompute()
    }

    private fun recompute() {
        active.value = preference_enabled.get() || data_saver_enabled.get()
    }
}
