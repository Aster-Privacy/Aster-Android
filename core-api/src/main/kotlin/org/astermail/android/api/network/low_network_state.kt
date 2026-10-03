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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val SLOW_LINK_WINDOW_MS = 180_000L

object low_network_state {

    private val preference_enabled = AtomicBoolean(false)
    private val slow_link_until = AtomicLong(0L)
    private val active = MutableStateFlow(false)

    val is_active: StateFlow<Boolean> = active.asStateFlow()

    fun set_preference(enabled: Boolean) {
        preference_enabled.set(enabled)
        recompute()
    }

    fun is_preference_enabled(): Boolean = preference_enabled.get()

    fun active(): Boolean = active.value

    fun note_timeout(now_ms: Long = System.currentTimeMillis()) {
        slow_link_until.set(now_ms + SLOW_LINK_WINDOW_MS)
    }

    fun extend_timeouts(now_ms: Long = System.currentTimeMillis()): Boolean =
        active() || now_ms < slow_link_until.get()

    fun reset() {
        preference_enabled.set(false)
        slow_link_until.set(0L)
        recompute()
    }

    private fun recompute() {
        active.value = preference_enabled.get()
    }
}
