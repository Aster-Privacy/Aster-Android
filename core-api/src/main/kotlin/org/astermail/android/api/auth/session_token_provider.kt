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

package org.astermail.android.api.auth

import io.ktor.client.plugins.auth.providers.BearerTokens
import org.astermail.android.api.TokenProvider

class SessionTokenProvider(
    private val read_access_token: () -> String?,
    private val read_refresh_token: () -> String?,
    private val refresh_session: suspend () -> RefreshOutcome,
    private val clear_tokens: suspend () -> Unit = {},
) : TokenProvider {

    override suspend fun load(): BearerTokens? {
        val access = read_access_token() ?: return null
        return BearerTokens(access, read_refresh_token() ?: access)
    }

    override suspend fun refresh(): BearerTokens? = refresh(null)

    override suspend fun refresh(failed_access_token: String?): BearerTokens? {
        val stored = read_access_token()
        if (!failed_access_token.isNullOrEmpty() && !stored.isNullOrEmpty() && stored != failed_access_token) {
            return load()
        }
        return when (refresh_session()) {
            RefreshOutcome.AuthFailed -> null
            RefreshOutcome.Success -> load()
            RefreshOutcome.Transient -> {
                val unchanged = !failed_access_token.isNullOrEmpty() && read_access_token() == failed_access_token
                if (unchanged) null else load()
            }
        }
    }

    override suspend fun clear() {
        clear_tokens()
    }
}
