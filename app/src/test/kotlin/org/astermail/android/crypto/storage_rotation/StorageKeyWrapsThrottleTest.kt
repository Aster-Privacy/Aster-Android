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

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageKeyWrapsThrottleTest {

    private class CountingApi(var reply: ApiReply) : ResealApi {
        var wraps_gets = 0
        var other_gets = 0

        override suspend fun get(path: String): ApiReply {
            if (path == STORAGE_KEY_WRAPS_PATH) wraps_gets++ else other_gets++
            return reply
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply = ApiReply(200)

        override suspend fun put(path: String, body: JsonElement): ApiReply = ApiReply(200)
    }

    private class Clock(var ms: Long = 1_000_000L) {
        fun advance(by: Long) {
            ms += by
        }
    }

    private val limited = ApiReply(status = 429, code = "RATE_LIMITED", retry_after_secs = 3_600)
    private val empty_list = ApiReply(status = 200, data = JsonObject(mapOf("wraps" to JsonArray(emptyList()))))

    @Test
    fun an_unthrottled_caller_hits_the_server_on_every_attempt_after_a_429() = runBlocking {
        val server = CountingApi(limited)
        repeat(50) { assertNull(list_storage_key_wraps(server)) }
        assertEquals(50, server.wraps_gets)
    }

    @Test
    fun a_429_holds_every_later_attempt_until_retry_after_passes() = runBlocking {
        val clock = Clock()
        val throttle = StorageKeyWrapsThrottle { clock.ms }
        val server = CountingApi(limited)
        repeat(50) { assertNull(list_storage_key_wraps(ThrottledWrapsApi(server, throttle, "user-a"))) }
        assertEquals(1, server.wraps_gets)
        clock.advance(3_599_000L)
        assertNull(list_storage_key_wraps(ThrottledWrapsApi(server, throttle, "user-a")))
        assertEquals(1, server.wraps_gets)
        clock.advance(1_000L)
        server.reply = empty_list
        assertEquals(emptyList<StorageKeyWrap>(), list_storage_key_wraps(ThrottledWrapsApi(server, throttle, "user-a")))
        assertEquals(2, server.wraps_gets)
    }

    @Test
    fun failures_without_retry_after_back_off_exponentially_to_a_cap() {
        val clock = Clock()
        val throttle = StorageKeyWrapsThrottle { clock.ms }
        val failed = ApiReply(status = 503)
        val waits = mutableListOf<Long>()
        repeat(12) {
            throttle.record("user-a", failed)
            var waited = 0L
            while (!throttle.allows("user-a")) {
                clock.advance(1_000L)
                waited += 1_000L
            }
            waits += waited
        }
        assertEquals(WRAPS_MIN_BACKOFF_MS, waits[0])
        assertEquals(WRAPS_MIN_BACKOFF_MS * 2, waits[1])
        assertEquals(WRAPS_MIN_BACKOFF_MS * 4, waits[2])
        assertEquals(WRAPS_MAX_BACKOFF_MS, waits.last())
        assertTrue(waits.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun a_429_never_waits_less_than_the_backoff_or_longer_than_a_day() {
        val clock = Clock()
        val throttle = StorageKeyWrapsThrottle { clock.ms }
        throttle.record("user-a", ApiReply(status = 429, retry_after_secs = 1))
        clock.advance(WRAPS_MIN_BACKOFF_MS - 1)
        assertFalse(throttle.allows("user-a"))
        clock.advance(1)
        assertTrue(throttle.allows("user-a"))

        throttle.record("user-b", ApiReply(status = 429, retry_after_secs = 30L * 24 * 3600))
        clock.advance(WRAPS_MAX_RETRY_AFTER_MS)
        assertTrue(throttle.allows("user-b"))
    }

    @Test
    fun success_settles_and_resets_the_backoff() {
        val clock = Clock()
        val throttle = StorageKeyWrapsThrottle { clock.ms }
        repeat(5) { throttle.record("user-a", ApiReply(status = 503)) }
        throttle.note_settled("user-a")
        assertTrue(throttle.allows("user-a"))
        throttle.record("user-a", ApiReply(status = 503))
        clock.advance(WRAPS_MIN_BACKOFF_MS)
        assertTrue(throttle.allows("user-a"))
    }

    @Test
    fun a_fetch_that_did_not_settle_still_backs_off() {
        val clock = Clock()
        val throttle = StorageKeyWrapsThrottle { clock.ms }
        throttle.record("user-a", empty_list)
        assertTrue(throttle.allows("user-a"))
        throttle.note_unsettled("user-a")
        assertFalse(throttle.allows("user-a"))
        clock.advance(WRAPS_MIN_BACKOFF_MS)
        assertTrue(throttle.allows("user-a"))
    }

    @Test
    fun users_are_throttled_separately_and_forget_clears_everyone() {
        val clock = Clock()
        val throttle = StorageKeyWrapsThrottle { clock.ms }
        throttle.record("user-a", limited)
        assertFalse(throttle.allows("user-a"))
        assertTrue(throttle.allows("user-b"))
        throttle.forget()
        assertTrue(throttle.allows("user-a"))
    }

    @Test
    fun only_the_wraps_list_is_gated_and_only_real_fetches_count() = runBlocking {
        val clock = Clock()
        val throttle = StorageKeyWrapsThrottle { clock.ms }
        val server = CountingApi(limited)
        val first = ThrottledWrapsApi(server, throttle, "user-a")
        first.get(STORAGE_KEY_WRAPS_PATH)
        assertFalse(first.fetched_ok)
        val held = ThrottledWrapsApi(server, throttle, "user-a")
        assertEquals(WRAPS_THROTTLED_CODE, held.get(STORAGE_KEY_WRAPS_PATH).code)
        held.get("/core/v1/recovery/codes/status")
        assertEquals(1, server.other_gets)
        assertEquals(1, server.wraps_gets)
        assertFalse(held.fetched_ok)

        clock.advance(WRAPS_MAX_RETRY_AFTER_MS)
        server.reply = empty_list
        val later = ThrottledWrapsApi(server, throttle, "user-a")
        later.get(STORAGE_KEY_WRAPS_PATH)
        assertTrue(later.fetched_ok)
    }
}
