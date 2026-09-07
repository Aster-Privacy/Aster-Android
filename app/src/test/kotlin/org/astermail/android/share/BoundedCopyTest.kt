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
package org.astermail.android.share

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedCopyTest {

    private fun payload(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    @Test
    fun copies_everything_under_the_limit() {
        val data = payload(10_000)
        val out = ByteArrayOutputStream()
        val result = bounded_copy(ByteArrayInputStream(data), out, max_bytes = 10_000, buffer_size = 1024)
        assertEquals(BoundedCopyResult.Copied(10_000), result)
        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun copies_an_empty_stream() {
        val out = ByteArrayOutputStream()
        val result = bounded_copy(ByteArrayInputStream(ByteArray(0)), out, max_bytes = 16)
        assertEquals(BoundedCopyResult.Copied(0), result)
        assertEquals(0, out.size())
    }

    @Test
    fun stops_before_writing_past_the_limit() {
        val data = payload(10_001)
        val out = ByteArrayOutputStream()
        val result = bounded_copy(ByteArrayInputStream(data), out, max_bytes = 10_000, buffer_size = 1024)
        assertEquals(BoundedCopyResult.Exceeded(10_000), result)
        assertTrue(out.size() <= 10_000)
    }

    @Test
    fun exact_limit_is_allowed() {
        val data = payload(2048)
        val out = ByteArrayOutputStream()
        val result = bounded_copy(ByteArrayInputStream(data), out, max_bytes = 2048, buffer_size = 512)
        assertEquals(BoundedCopyResult.Copied(2048), result)
        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun negative_limit_rejects_immediately() {
        val out = ByteArrayOutputStream()
        val result = bounded_copy(ByteArrayInputStream(payload(8)), out, max_bytes = -1)
        assertEquals(BoundedCopyResult.Exceeded(-1), result)
        assertEquals(0, out.size())
    }
}
