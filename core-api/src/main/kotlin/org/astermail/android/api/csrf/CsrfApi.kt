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

package org.astermail.android.api.csrf

import org.astermail.android.api.ApiClient

interface CsrfApi {
    suspend fun ensure_token(): String?
    fun current(): String?
    fun clear()
}

class CsrfApiImpl(private val client: ApiClient) : CsrfApi {
    override suspend fun ensure_token(): String? = client.fetch_csrf_if_needed()
    override fun current(): String? = client.get_csrf()
    override fun clear() = client.clear_csrf()
}
