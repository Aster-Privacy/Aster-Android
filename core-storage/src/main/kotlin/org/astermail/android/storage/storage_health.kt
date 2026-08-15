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

package org.astermail.android.storage

object StorageHealth {
    @Volatile
    var database_in_memory: Boolean = false
        private set

    @Volatile
    var secure_prefs_degraded: Boolean = false
        private set

    @Volatile
    var key_material_lost: Boolean = false
        private set

    fun mark_database_in_memory() {
        database_in_memory = true
    }

    fun mark_secure_prefs_degraded() {
        secure_prefs_degraded = true
    }

    fun mark_key_material_lost() {
        key_material_lost = true
    }

    fun is_healthy(): Boolean = !database_in_memory && !secure_prefs_degraded && !key_material_lost
}
