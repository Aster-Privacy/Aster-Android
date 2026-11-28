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

package org.astermail.android.api.found_us

import org.junit.Assert.assertEquals
import org.junit.Test

class found_us_api_test {

    private fun code(vararg points: Int): String {
        val builder = StringBuilder()
        points.forEach { builder.appendCodePoint(it) }
        return builder.toString()
    }

    @Test
    fun `lists every source once in display order and ends with other`() {
        assertEquals(15, found_us_sources.size)
        assertEquals(found_us_sources.size, found_us_sources.toSet().size)
        assertEquals("reddit", found_us_sources.first())
        assertEquals(found_us_other, found_us_sources.last())
        assertEquals(200, found_us_max_detail_chars)
    }

    @Test
    fun `counts code points instead of utf16 units`() {
        assertEquals(2, count_found_us_chars(code(0x1F600, 0x1F600)))
        assertEquals(0, count_found_us_chars(""))
        assertEquals(3, count_found_us_chars("abc"))
    }

    @Test
    fun `limits by code point without splitting a surrogate pair`() {
        val text = code(0x1F600).repeat(5)
        val limited = limit_found_us_chars(text, 3)
        assertEquals(3, count_found_us_chars(limited))
        assertEquals(code(0x1F600).repeat(3), limited)
        assertEquals("abc", limit_found_us_chars("abc", 10))
    }

    @Test
    fun `caps at two hundred code points by default`() {
        val text = "a".repeat(199) + code(0x1F600, 0x1F600, 0x1F600)
        val limited = limit_found_us_chars(text)
        assertEquals(200, count_found_us_chars(limited))
        assertEquals("a".repeat(199) + code(0x1F600), limited)
    }

    @Test
    fun `collapses whitespace and trims`() {
        assertEquals("a podcast about privacy", clean_found_us_detail("  a podcast \n\t about   privacy "))
        assertEquals("x", clean_found_us_detail(code(0xA0, 0x3000) + "x" + code(0x85, 0x2028)))
        assertEquals("a b", clean_found_us_detail("a" + code(0x2009, 0x200A, 0x202F, 0x205F, 0x1680) + "b"))
    }

    @Test
    fun `removes control format surrogate and private use characters`() {
        val hidden = listOf(
            0x0, 0x7, 0x1B, 0x7F, 0x9F, 0xAD, 0x200B, 0x200D, 0x200E, 0x202A,
            0x202E, 0x2060, 0x2066, 0x2069, 0xFEFF, 0xE000, 0xD800,
        )
        hidden.forEach { point ->
            assertEquals("U+%04X".format(point), "ab", clean_found_us_detail("a" + point.toChar() + "b"))
        }
    }

    @Test
    fun `collapses spaces left around a removed character`() {
        assertEquals("a b", clean_found_us_detail("a " + code(0x200B) + " b"))
    }

    @Test
    fun `returns an empty string for hidden only input`() {
        assertEquals("", clean_found_us_detail(code(0x200B, 0x20, 0xFEFF, 0x9)))
    }

    @Test
    fun `keeps ordinary letters from many scripts`() {
        val text = "Un blog fran" + code(0xE7) + "ais, " + code(0x65E5, 0x672C) + " " + code(0x645, 0x62F, 0x648, 0x646, 0x629)
        assertEquals(text, clean_found_us_detail(text))
    }

    @Test
    fun `builds an empty body for a skip`() {
        assertEquals("{}", found_us_finish_body(null).toString())
    }

    @Test
    fun `sends only the source for a listed option`() {
        assertEquals(
            """{"source":"reddit"}""",
            found_us_finish_body(FoundUsAnswer("reddit", "ignored")).toString(),
        )
    }

    @Test
    fun `sends the detail only with other`() {
        assertEquals(
            """{"source":"other","detail":"a podcast"}""",
            found_us_finish_body(FoundUsAnswer("other", "a podcast")).toString(),
        )
        assertEquals("""{"source":"other"}""", found_us_finish_body(FoundUsAnswer("other")).toString())
    }

    @Test
    fun `escapes the detail as a json string`() {
        assertEquals(
            """{"source":"other","detail":"a \"quoted\" \\ note"}""",
            found_us_finish_body(FoundUsAnswer("other", "a \"quoted\" \\ note")).toString(),
        )
    }
}
