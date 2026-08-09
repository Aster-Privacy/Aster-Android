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

import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedKeyCacheTest {

    private fun key_of(value: Int): ByteArray = ByteArray(32) { value.toByte() }

    @Test
    fun `cache never exceeds its bound`() {
        val cache = BoundedKeyCache(4)
        repeat(100) { index -> cache.put("salt_$index", key_of(index)) }
        assertEquals(4, cache.size())
    }

    @Test
    fun `evicted key material is zeroed`() {
        val cache = BoundedKeyCache(2)
        val first = key_of(1)
        cache.put("a", first)
        cache.put("b", key_of(2))
        cache.put("c", key_of(3))
        assertNull(cache.get("a"))
        assertArrayEquals(ByteArray(32), first)
    }

    @Test
    fun `recently used entries survive eviction`() {
        val cache = BoundedKeyCache(2)
        cache.put("a", key_of(1))
        cache.put("b", key_of(2))
        assertNotNull(cache.get("a"))
        cache.put("c", key_of(3))
        assertNotNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `get or put derives once and reuses the cached key`() {
        val cache = BoundedKeyCache(4)
        var derivations = 0
        val first = cache.get_or_put("v1") { derivations++; key_of(7) }
        val second = cache.get_or_put("v1") { derivations++; key_of(7) }
        assertEquals(1, derivations)
        assertArrayEquals(first, second)
    }

    @Test
    fun `clear zeroes every remaining key`() {
        val cache = BoundedKeyCache(4)
        val held = key_of(9)
        cache.put("a", held)
        cache.clear()
        assertEquals(0, cache.size())
        assertArrayEquals(ByteArray(32), held)
    }

    @Test
    fun `replacing a key with identical material does not zero the live array`() {
        val cache = BoundedKeyCache(4)
        val first = key_of(5)
        cache.put("a", first)
        cache.put("a", key_of(5))
        assertArrayEquals(key_of(5), first)
    }

    @Test
    fun `hkdf with an empty salt matches rfc 5869 test case 3`() {
        val ikm = ByteArray(22) { 0x0b }
        val okm = RatchetCrypto.hkdf_sha256(ikm, ByteArray(0), ByteArray(0), 42)
        val expected = (
            "8da4e775a563c18f715f802a063c5a31" +
                "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                "9d201395faa4b61a96c8"
            ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, okm)
    }
}
