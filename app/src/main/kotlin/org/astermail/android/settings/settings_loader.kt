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

package org.astermail.android.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.api.ApiError
import org.astermail.android.mail.is_transient_failure

internal val settings_load_backoff_ms: List<Long> = listOf(800L, 2_000L, 4_000L)
internal const val settings_session_wait_ms: Long = 10_000L

internal fun is_retryable_settings_load_error(error: Throwable?): Boolean =
    error is ApiError.UnauthorizedError || is_transient_failure(error)

internal suspend fun await_settings_session(
    signed_in: StateFlow<Boolean>?,
    session_expired: StateFlow<Boolean>?,
    wait_ms: Long = settings_session_wait_ms,
) {
    withTimeoutOrNull(wait_ms) {
        signed_in?.first { it }
        session_expired?.first { !it }
    }
}

internal suspend fun <T> load_settings_resource(
    await_session: suspend () -> Unit,
    backoff_ms: List<Long> = settings_load_backoff_ms,
    fetch: suspend () -> T,
): Result<T> {
    await_session()
    var attempt = 0
    while (true) {
        try {
            return Result.success(fetch())
        } catch (t: Throwable) {
            if (t is CancellationException && t !is TimeoutCancellationException) throw t
            currentCoroutineContext().ensureActive()
            if (attempt >= backoff_ms.size || !is_retryable_settings_load_error(t)) return Result.failure(t)
            delay(backoff_ms[attempt])
            attempt++
            await_session()
        }
    }
}
