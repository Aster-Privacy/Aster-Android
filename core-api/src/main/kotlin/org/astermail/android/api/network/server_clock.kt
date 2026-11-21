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

package org.astermail.android.api.network

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private const val SERVER_DATE_PRECISION_MS = 500L
private const val MIN_TRUSTED_SKEW_MS = 5_000L
private const val MAX_ROUND_TRIP_MS = 10_000L

fun server_clock_offset(date_header: String?, sent_ms: Long, received_ms: Long): Long? {
    if (date_header.isNullOrBlank()) return null
    if (received_ms < sent_ms || received_ms - sent_ms > MAX_ROUND_TRIP_MS) return null
    val server_ms = runCatching {
        ZonedDateTime.parse(date_header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull() ?: return null
    val local_mid = sent_ms + (received_ms - sent_ms) / 2
    val offset = server_ms + SERVER_DATE_PRECISION_MS - local_mid
    return if (abs(offset) < MIN_TRUSTED_SKEW_MS) 0L else offset
}

object server_clock {
    @Volatile
    private var offset_ms: Long = 0L

    fun note(date_header: String?, sent_ms: Long, received_ms: Long) {
        server_clock_offset(date_header, sent_ms, received_ms)?.let { offset_ms = it }
    }

    fun offset_ms(): Long = offset_ms

    fun now_ms(): Long = System.currentTimeMillis() + offset_ms

    fun reset() {
        offset_ms = 0L
    }
}
