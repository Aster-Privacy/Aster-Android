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

package org.astermail.android.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.RefreshOutcome

private val subject_json = Json { ignoreUnknownKeys = true }

fun jwt_subject(token: String?): String? {
    val payload = token?.split('.')?.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val decoded = java.util.Base64.getUrlDecoder().decode(payload.trimEnd('='))
        val claims = subject_json.parseToJsonElement(String(decoded, Charsets.UTF_8)) as JsonObject
        claims["sub"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

fun session_token_owner(refresh_token: String?, access_token: String?): String? =
    (jwt_subject(refresh_token) ?: jwt_subject(access_token))?.lowercase(java.util.Locale.ROOT)

fun same_account(a: String?, b: String?): Boolean =
    a != null && b != null && a.equals(b, ignoreCase = true)

data class RotatedTokens(val access_token: String, val refresh_token: String?)

class AccountBoundRefresh(
    private val gate: Mutex,
    private val read_access_token: () -> String?,
    private val read_refresh_token: () -> String?,
    private val resolve_owner: (String?) -> String?,
    private val call_refresh: suspend (String?) -> RotatedTokens,
    private val read_csrf: () -> String?,
    private val restore_csrf: (String?) -> Unit,
    private val save_current: suspend (access: String, refresh: String) -> Unit,
    private val save_for_account: (account_id: String, expected_refresh: String?, access: String, refresh: String, csrf: String?) -> Unit,
) {

    suspend fun refresh(presented: String?): RefreshOutcome = gate.withLock {
        if (read_refresh_token() != presented) return@withLock RefreshOutcome.Success
        val owner = resolve_owner(session_token_owner(presented, read_access_token()))
        val csrf_before = read_csrf()
        val rotated = try {
            call_refresh(presented)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiError.UnauthorizedError) {
            return@withLock RefreshOutcome.AuthFailed
        } catch (_: Throwable) {
            return@withLock RefreshOutcome.Transient
        }
        val issued_csrf = read_csrf()
        val issued_to = jwt_subject(rotated.access_token)
        if (owner != null && issued_to != null && !same_account(owner, issued_to)) {
            restore_csrf(csrf_before)
            return@withLock RefreshOutcome.Transient
        }
        val refresh = rotated.refresh_token ?: presented ?: rotated.access_token
        val still_current = read_refresh_token() == presented
        if (still_current) {
            save_current(rotated.access_token, refresh)
        } else {
            restore_csrf(csrf_before)
        }
        if (owner != null) {
            runCatching {
                save_for_account(owner, if (still_current) null else presented, rotated.access_token, refresh, issued_csrf)
            }
        }
        RefreshOutcome.Success
    }
}
