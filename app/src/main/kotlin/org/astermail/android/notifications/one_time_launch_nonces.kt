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

import java.security.MessageDigest
import java.security.SecureRandom

class OneTimeLaunchNonces(
    private val ttl_ms: Long = DEFAULT_TTL_MS,
    private val max_outstanding: Int = DEFAULT_MAX_OUTSTANDING,
    private val random: SecureRandom = SecureRandom(),
) {
    private val issued = LinkedHashMap<String, Long>()

    @Synchronized
    fun issue(now_ms: Long): String {
        prune(now_ms)
        while (issued.size >= max_outstanding) issued.remove(issued.keys.first())
        val nonce = internal_launch_token.new_token(random)
        issued[nonce] = now_ms + ttl_ms
        return nonce
    }

    @Synchronized
    fun consume(presented: String?, now_ms: Long): Boolean {
        prune(now_ms)
        if (presented.isNullOrBlank()) return false
        val presented_bytes = presented.toByteArray(Charsets.UTF_8)
        val match = issued.keys.firstOrNull {
            MessageDigest.isEqual(it.toByteArray(Charsets.UTF_8), presented_bytes)
        } ?: return false
        issued.remove(match)
        return true
    }

    private fun prune(now_ms: Long) {
        issued.entries.removeAll { it.value <= now_ms }
    }

    companion object {
        const val DEFAULT_TTL_MS = 10L * 60 * 1000
        const val DEFAULT_MAX_OUTSTANDING = 4
    }
}

object contact_sync_launch {
    const val EXTRA_NONCE = "contact_sync_nonce"
    const val SYSTEM_UID = 1000
    const val PER_USER_RANGE = 100_000

    fun app_id(uid: Int): Int = uid % PER_USER_RANGE

    fun caller_allowed(caller_uid: Int, own_uid: Int): Boolean =
        caller_uid >= 0 && own_uid >= 0 &&
            (app_id(caller_uid) == app_id(own_uid) || app_id(caller_uid) == SYSTEM_UID)

    private val nonces = OneTimeLaunchNonces()

    fun issue(): String = nonces.issue(android.os.SystemClock.elapsedRealtime())

    fun consume(presented: String?): Boolean =
        nonces.consume(presented, android.os.SystemClock.elapsedRealtime())
}
