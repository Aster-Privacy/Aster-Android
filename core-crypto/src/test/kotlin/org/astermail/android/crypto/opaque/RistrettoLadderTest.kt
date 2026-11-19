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

package org.astermail.android.crypto.opaque

import java.math.BigInteger
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RistrettoLadderTest {

    private fun by_adding(scalar: Int): ByteArray {
        val point = Ristretto255.base_point
        var sum = Ristretto255.multiply(point, BigInteger.ZERO)
        repeat(scalar) { sum = Ristretto255.add(sum, point) }
        return Ristretto255.encode(sum)
    }

    @Test
    fun the_ladder_matches_repeated_addition() {
        for (scalar in listOf(0, 1, 2, 3, 7, 16, 31)) {
            assertArrayEquals(
                by_adding(scalar),
                Ristretto255.encode(Ristretto255.multiply(Ristretto255.base_point, BigInteger.valueOf(scalar.toLong()))),
            )
        }
    }

    @Test
    fun the_order_maps_to_the_identity() {
        assertTrue(Ristretto255.is_identity(Ristretto255.multiply(Ristretto255.base_point, Ristretto255.group_order)))
    }

    @Test
    fun each_multiplication_uses_a_fresh_blinding() {
        val random = SecureRandom()
        val k = BigInteger("1234567890123456789012345678901234567890")
        val first = Ristretto255.blinded_scalar(k, random)
        val second = Ristretto255.blinded_scalar(k, random)
        assertNotEquals(first, second)
        assertNotEquals(k, first)
        assertEquals(k, first.mod(Ristretto255.group_order))
        assertEquals(k, second.mod(Ristretto255.group_order))
    }

    @Test
    fun a_blinded_scalar_gives_the_same_point() {
        val k = BigInteger("987654321987654321987654321")
        val once = Ristretto255.encode(Ristretto255.multiply(Ristretto255.base_point, k))
        repeat(4) {
            assertArrayEquals(once, Ristretto255.encode(Ristretto255.multiply(Ristretto255.base_point, k)))
        }
        val wide = Ristretto255.blinded_scalar(k, SecureRandom())
        assertArrayEquals(once, Ristretto255.encode(Ristretto255.multiply(Ristretto255.base_point, wide)))
    }
}
