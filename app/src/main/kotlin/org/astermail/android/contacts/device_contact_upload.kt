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

package org.astermail.android.contacts

import kotlinx.coroutines.CancellationException
import org.astermail.android.api.ApiError

data class DeviceContactUploadResult(
    val imported: Int,
    val failed: Int,
    val limit_reached: Boolean,
    val last_failure: Throwable?,
)

suspend fun <T> upload_device_contacts(
    contacts: List<T>,
    create: suspend (T) -> Result<*>,
    on_progress: (Int) -> Unit = {},
): DeviceContactUploadResult {
    var imported = 0
    var failed = 0
    var last_failure: Throwable? = null
    for ((index, contact) in contacts.withIndex()) {
        val error = create(contact).exceptionOrNull()
        when (error) {
            null -> imported++
            is CancellationException -> throw error
            is ApiError.PlanLimitExceeded -> return DeviceContactUploadResult(imported, failed, true, last_failure)
            else -> {
                last_failure = error
                failed++
            }
        }
        on_progress(index + 1)
    }
    return DeviceContactUploadResult(imported, failed, false, last_failure)
}
