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

package org.astermail.android.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactMapQueryTest {

    @Test
    fun `joins every address field in order`() {
        val postal = ContactPostal(
            street = "1 Main St",
            city = "Springfield",
            region = "IL",
            postal_code = "62701",
            country = "United States",
        )
        assertEquals("1 Main St, Springfield, IL, 62701, United States", build_contact_map_query(postal))
    }

    @Test
    fun `skips empty and blank fields`() {
        val postal = ContactPostal(street = "  ", city = "Berlin", region = "", postal_code = "", country = " Germany ")
        assertEquals("Berlin, Germany", build_contact_map_query(postal))
    }

    @Test
    fun `flattens multiline streets and collapses whitespace`() {
        val postal = ContactPostal(street = "Flat 2\n10   High Street,", city = "London")
        assertEquals("Flat 2, 10 High Street, London", build_contact_map_query(postal))
    }

    @Test
    fun `blank address yields no query or uri`() {
        val query = build_contact_map_query(ContactPostal())
        assertEquals("", query)
        assertNull(build_contact_geo_uri(query))
        assertNull(build_contact_map_web_url(query))
    }

    @Test
    fun `geo uri percent encodes the query`() {
        assertEquals(
            "geo:0,0?q=1%20Main%20St%2C%20Z%C3%BCrich%20%26%20Co%23",
            build_contact_geo_uri("1 Main St, Zürich & Co#"),
        )
    }

    @Test
    fun `web fallback uses the same encoded query`() {
        assertEquals(
            "https://www.openstreetmap.org/search?query=Berlin%2C%20Germany",
            build_contact_map_web_url("Berlin, Germany"),
        )
    }
}
