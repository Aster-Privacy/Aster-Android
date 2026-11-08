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

package org.astermail.android.folders

import kotlinx.coroutines.CancellationException
import org.astermail.android.api.labels.VerifyFolderPasswordResponse

suspend fun open_folder_unlock_session(
    folder_id: String,
    verify: suspend () -> VerifyFolderPasswordResponse,
): Boolean {
    val response = try {
        verify()
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        null
    }
    val unlock_token = response?.unlock_token?.takeIf { response.verified && it.isNotBlank() }
    if (response == null || unlock_token == null) {
        folder_lock_store.lock(folder_id)
        return false
    }
    folder_lock_store.mark_unlocked(
        folder_id = folder_id,
        unlock_token = unlock_token,
        unlock_expires_at = response.unlock_expires_at,
        encrypted_folder_key = response.encrypted_folder_key,
        folder_key_nonce = response.folder_key_nonce,
    )
    return true
}
