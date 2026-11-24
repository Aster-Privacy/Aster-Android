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
package org.astermail.android.crypto.storage_rotation

import kotlinx.serialization.json.JsonElement

const val WRAPS_THROTTLED_CODE = "storage_key_wraps_throttled"
const val WRAPS_MIN_BACKOFF_MS = 60_000L
const val WRAPS_MAX_BACKOFF_MS = 6 * 3_600_000L
const val WRAPS_MAX_RETRY_AFTER_MS = 24 * 3_600_000L
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MAX_BACKOFF_DOUBLINGS = 16

class StorageKeyWrapsThrottle(private val now_ms: () -> Long = System::currentTimeMillis) {
    private val next_allowed_at = HashMap<String, Long>()
    private val misses = HashMap<String, Int>()

    @Synchronized
    fun allows(user_id: String): Boolean = now_ms() >= (next_allowed_at[user_id] ?: 0L)

    @Synchronized
    fun record(user_id: String, reply: ApiReply) {
        if (!reply.error) return
        val retry_after_ms = if (reply.status == HTTP_TOO_MANY_REQUESTS) {
            (reply.retry_after_secs ?: 0L).coerceIn(0L, WRAPS_MAX_RETRY_AFTER_MS / 1000) * 1000
        } else {
            0L
        }
        back_off(user_id, retry_after_ms)
    }

    @Synchronized
    fun note_unsettled(user_id: String) = back_off(user_id, 0L)

    @Synchronized
    fun note_settled(user_id: String) {
        misses.remove(user_id)
        next_allowed_at.remove(user_id)
    }

    @Synchronized
    fun forget() {
        misses.clear()
        next_allowed_at.clear()
    }

    private fun back_off(user_id: String, floor_ms: Long) {
        val count = (misses[user_id] ?: 0) + 1
        misses[user_id] = count
        val doublings = (count - 1).coerceAtMost(MAX_BACKOFF_DOUBLINGS)
        val backoff = (WRAPS_MIN_BACKOFF_MS shl doublings).coerceAtMost(WRAPS_MAX_BACKOFF_MS)
        next_allowed_at[user_id] = now_ms() + maxOf(backoff, floor_ms)
    }
}

class ThrottledWrapsApi(
    private val inner: ResealApi,
    private val throttle: StorageKeyWrapsThrottle,
    private val user_id: String,
) : ResealApi {
    @Volatile var fetched_ok: Boolean = false
        private set

    override suspend fun get(path: String): ApiReply {
        if (path != STORAGE_KEY_WRAPS_PATH) return inner.get(path)
        if (!throttle.allows(user_id)) return ApiReply(status = null, code = WRAPS_THROTTLED_CODE)
        return inner.get(path).also { reply ->
            throttle.record(user_id, reply)
            if (!reply.error) fetched_ok = true
        }
    }

    override suspend fun post(path: String, body: JsonElement): ApiReply = inner.post(path, body)

    override suspend fun put(path: String, body: JsonElement): ApiReply = inner.put(path, body)
}
