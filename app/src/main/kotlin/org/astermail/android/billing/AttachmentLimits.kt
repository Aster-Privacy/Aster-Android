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

object AttachmentLimits {
    const val free_max_bytes = 25L * 1024 * 1024
    const val paid_max_bytes = 250L * 1024 * 1024

    @Volatile
    private var cached_max_bytes = free_max_bytes

    fun max_bytes(): Long = cached_max_bytes

    fun total_max_bytes(): Long = cached_max_bytes

    fun can_upgrade(): Boolean =
        cached_max_bytes <= free_max_bytes && cached_max_bytes < paid_max_bytes

    fun update(bytes: Long) {
        if (bytes > 0) cached_max_bytes = bytes
    }

    fun reset() {
        cached_max_bytes = free_max_bytes
    }
}
