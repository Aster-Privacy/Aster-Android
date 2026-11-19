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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderKeyCacheTest {

    private var now = 1_000_000L
    private val cache = SenderKeyCache(hit_ttl_ms = 1_000L, miss_ttl_ms = 100L, now_ms = { now })

    @Test
    fun stored_key_is_returned_until_it_expires() {
        cache.remember("a@example.com", "key")
        now += 999
        assertEquals("key", cache.fresh_key("a@example.com"))
        now += 1
        assertNull(cache.fresh_key("a@example.com"))
    }

    @Test
    fun miss_is_remembered_for_the_shorter_window() {
        cache.remember("a@example.com", null)
        assertTrue(cache.recently_missed("a@example.com"))
        assertNull(cache.fresh_key("a@example.com"))
        now += 100
        assertFalse(cache.recently_missed("a@example.com"))
    }

    @Test
    fun blank_key_counts_as_a_miss() {
        cache.remember("a@example.com", " ")
        assertTrue(cache.recently_missed("a@example.com"))
    }

    @Test
    fun clock_moving_back_drops_the_entry() {
        cache.remember("a@example.com", "key")
        now -= 1
        assertNull(cache.fresh_key("a@example.com"))
    }

    @Test
    fun clear_forgets_everything() {
        cache.remember("a@example.com", "key")
        cache.clear()
        assertNull(cache.fresh_key("a@example.com"))
        assertFalse(cache.recently_missed("a@example.com"))
    }
}
