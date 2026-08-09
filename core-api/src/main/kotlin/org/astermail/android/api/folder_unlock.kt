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

const val folder_unlock_header = "X-Folder-Unlock"

data class folder_unlock_request(
    val method: String,
    val path: String,
    val parameters: Map<String, List<String>>,
)

object folder_unlock_resolver {
    @Volatile
    private var resolver: ((folder_unlock_request) -> String?)? = null

    fun register(block: (folder_unlock_request) -> String?) {
        resolver = block
    }

    fun clear() {
        resolver = null
    }

    fun resolve(request: folder_unlock_request): String? {
        val active = resolver ?: return null
        return runCatching { active(request) }.getOrNull()
    }
}
