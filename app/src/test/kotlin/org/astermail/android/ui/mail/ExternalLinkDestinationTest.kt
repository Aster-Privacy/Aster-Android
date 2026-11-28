// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinkDestinationTest {

    @Test
    fun `a web link shows its site above the full address`() {
        val link = "https://www.Example.com/path?utm_source=mail"
        val destination = describe_external_link(link)
        assertEquals("example.com", destination.heading)
        assertEquals(link, destination.address)
        assertFalse(destination.is_mail)
    }

    @Test
    fun `a punycode host stays in its ascii form`() {
        val destination = describe_external_link("https://xn--pple-43d.com/login")
        assertEquals("xn--pple-43d.com", destination.heading)
    }

    @Test
    fun `a mail link shows only the address`() {
        val destination = describe_external_link("mailto:ada@example.com?subject=hi")
        assertEquals("ada@example.com", destination.heading)
        assertNull(destination.address)
        assertTrue(destination.is_mail)
    }

    @Test
    fun `a link without a host falls back to the raw value`() {
        val destination = describe_external_link("not a url")
        assertEquals("not a url", destination.heading)
        assertNull(destination.address)
    }
}
