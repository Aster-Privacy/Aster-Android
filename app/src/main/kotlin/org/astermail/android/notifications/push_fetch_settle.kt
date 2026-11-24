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

package org.astermail.android.notifications

import kotlinx.coroutines.withTimeoutOrNull

internal const val PUSH_FETCH_TIMEOUT_MS = 90_000L
internal const val PUSH_FETCH_WAKE_LOCK_MS = 100_000L

internal suspend fun settle_push_fetch(
    fetch_now: suspend () -> Unit,
    enqueue_fallback: () -> Unit,
    timeout_ms: Long = PUSH_FETCH_TIMEOUT_MS,
): Boolean {
    val completed = withTimeoutOrNull(timeout_ms) {
        runCatching { fetch_now() }.isSuccess
    } == true
    if (!completed) runCatching(enqueue_fallback)
    return completed
}
