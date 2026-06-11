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

package org.astermail.android.api

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class UpgradeEvent {
    data class PlanLimit(val message: String, val resource: String?) : UpgradeEvent()
    data class StorageFull(val message: String) : UpgradeEvent()
}

object UpgradeEventBus {
    private val _events = MutableSharedFlow<UpgradeEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<UpgradeEvent> = _events.asSharedFlow()

    fun emit_plan_limit(message: String, resource: String?) {
        _events.tryEmit(UpgradeEvent.PlanLimit(message, resource))
    }

    fun emit_storage_full(message: String) {
        _events.tryEmit(UpgradeEvent.StorageFull(message))
    }
}
