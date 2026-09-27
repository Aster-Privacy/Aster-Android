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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class TokenLifetime(val issued_at_s: Long, val expires_at_s: Long)

private val lenient_json = Json { ignoreUnknownKeys = true }

fun read_token_lifetime(token: String?): TokenLifetime? {
    val payload = token?.split('.')?.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val decoded = java.util.Base64.getUrlDecoder().decode(payload.trimEnd('='))
        val claims = lenient_json.parseToJsonElement(String(decoded, Charsets.UTF_8)) as JsonObject
        val exp = claims["exp"]?.jsonPrimitive?.longOrNull ?: return null
        val iat = claims["iat"]?.jsonPrimitive?.longOrNull ?: return null
        if (exp <= iat) null else TokenLifetime(iat, exp)
    }.getOrNull()
}

fun access_token_needs_refresh(access_token: String?, now_s: Long): Boolean {
    val lifetime = read_token_lifetime(access_token) ?: return false
    val total = lifetime.expires_at_s - lifetime.issued_at_s
    val remaining = lifetime.expires_at_s - now_s
    return remaining < total / PROACTIVE_REFRESH_DIVISOR
}

suspend fun refresh_if_expiring(
    access_token: String?,
    refresher: SessionRefresher,
    now_s: Long = System.currentTimeMillis() / 1000L,
): RefreshOutcome? {
    if (!access_token_needs_refresh(access_token, now_s)) return null
    return refresher.refresh()
}

private const val PROACTIVE_REFRESH_DIVISOR = 3L
