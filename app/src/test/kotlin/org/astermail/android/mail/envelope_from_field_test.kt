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

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class envelope_from_field_test {
    @Test
    fun `reads the sender of a sent copy written with flat keys`() {
        val obj = JSONObject(
            """{"subject":"Lanterns 4K Release","from_name":"glp@astermail.org","from_email":"glp@astermail.org","to":["press@example.com"]}"""
        )
        assertEquals("" to "glp@astermail.org", parse_envelope_from_field(obj))
    }

    @Test
    fun `keeps a flat display name that differs from the address`() {
        val obj = JSONObject("""{"from_name":"Green Lanterns","from_email":"glp@astermail.org"}""")
        assertEquals("Green Lanterns" to "glp@astermail.org", parse_envelope_from_field(obj))
    }

    @Test
    fun `prefers the from object when both shapes are present`() {
        val obj = JSONObject(
            """{"from":{"name":"","email":"glp@astermail.org"},"from_name":"glp@astermail.org","from_email":"glp@astermail.org"}"""
        )
        assertEquals("" to "glp@astermail.org", parse_envelope_from_field(obj))
    }

    @Test
    fun `fills a blank from object from the flat keys`() {
        val obj = JSONObject("""{"from":{"name":"","email":""},"from_email":"glp@astermail.org"}""")
        assertEquals("" to "glp@astermail.org", parse_envelope_from_field(obj))
    }

    @Test
    fun `still reads the from object and string forms`() {
        assertEquals(
            "Sam" to "sam@example.com",
            parse_envelope_from_field(JSONObject("""{"from":{"name":"Sam","email":"sam@example.com"}}"""))
        )
        assertEquals(
            "Sam" to "sam@example.com",
            parse_envelope_from_field(JSONObject("""{"from":"\"Sam\" <sam@example.com>"}"""))
        )
        assertEquals("" to "sam@example.com", parse_envelope_from_field(JSONObject("""{"from":"sam@example.com"}""")))
    }

    @Test
    fun `ignores null flat keys`() {
        val obj = JSONObject("""{"from_name":null,"from_email":null}""")
        assertEquals("" to "", parse_envelope_from_field(obj))
    }
}
