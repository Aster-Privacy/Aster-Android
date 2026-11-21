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

package org.astermail.android.mail

import kotlinx.coroutines.delay
import org.astermail.android.api.ApiError

internal const val RATE_LIMIT_WAIT_ATTEMPTS = 6
private const val RATE_LIMIT_WAIT_BASE_MS = 2_000L
private const val RATE_LIMIT_WAIT_MAX_MS = 65_000L

internal fun rate_limited_cause(error: Throwable?): ApiError.RateLimited? {
    var current = error
    var depth = 0
    while (current != null && depth < 8) {
        if (current is ApiError.RateLimited) return current
        if (current.cause === current) break
        current = current.cause
        depth++
    }
    return null
}

internal fun rate_limit_wait_ms(error: ApiError.RateLimited, attempt: Int, now_ms: Long): Long {
    val hinted = error.resets_at?.let { stamp ->
        runCatching { java.time.Instant.parse(stamp).toEpochMilli() - now_ms }.getOrNull()
    }
    val wait = if (hinted != null && hinted > 0) {
        hinted
    } else {
        RATE_LIMIT_WAIT_BASE_MS shl (attempt - 1).coerceIn(0, 10)
    }
    return wait.coerceAtMost(RATE_LIMIT_WAIT_MAX_MS)
}

internal suspend fun <T> with_rate_limit_wait(
    attempts: Int = RATE_LIMIT_WAIT_ATTEMPTS,
    now_ms: () -> Long = System::currentTimeMillis,
    wait: suspend (Long) -> Unit = { delay(it) },
    block: suspend () -> T,
): T {
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (error: Throwable) {
            val limited = rate_limited_cause(error)
            if (limited == null || attempt >= attempts) throw error
            wait(rate_limit_wait_ms(limited, attempt, now_ms()))
            attempt++
        }
    }
}
