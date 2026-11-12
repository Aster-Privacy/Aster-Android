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
package org.astermail.android.mail.ratchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinIdentityTest {

    @Test
    fun `aster addresses fold dots tags and case into one id`() {
        val expected = "janedoe@astermail.org"

        assertEquals(expected, PinIdentity.canonical_id("janedoe@astermail.org"))
        assertEquals(expected, PinIdentity.canonical_id("  Jane.Doe@AsterMail.org "))
        assertEquals(expected, PinIdentity.canonical_id("jane.doe+news@astermail.org"))
        assertEquals(expected, PinIdentity.canonical_id("j.a.n.e.d.o.e+a+b@astermail.org"))
        assertEquals("janedoe@aster.cx", PinIdentity.canonical_id("jane.doe@aster.cx"))
    }

    @Test
    fun `outside addresses are only trimmed and lowercased`() {
        assertEquals("jane.doe+news@example.com", PinIdentity.canonical_id(" Jane.Doe+News@Example.com "))
        assertNotEquals(
            PinIdentity.canonical_id("jane.doe@example.com"),
            PinIdentity.canonical_id("janedoe@example.com"),
        )
        assertEquals(PeerKind.EXTERNAL, PinIdentity.peer_kind("jane.doe@example.com"))
        assertEquals(PeerKind.BUILTIN, PinIdentity.peer_kind("jane.doe@astermail.org"))
    }

    @Test
    fun `hosted addresses drop the tag and keep dots`() {
        assertEquals("jane.doe@team.example", PinIdentity.canonical_id("Jane.Doe+x@team.example", PeerKind.HOSTED))
    }

    @Test
    fun `an address without a usable local part is left as typed`() {
        assertEquals("+tag@astermail.org", PinIdentity.canonical_id("+tag@astermail.org"))
        assertEquals("not-an-address", PinIdentity.canonical_id(" Not-An-Address "))
    }

    @Test
    fun `earlier spellings of an aster address are listed for lookup`() {
        val contact = PinIdentity.describe("Jane.Doe+News@astermail.org")

        assertEquals("janedoe@astermail.org", contact.id)
        assertEquals(PeerKind.BUILTIN, contact.kind)
        assertTrue(
            contact.spellings.containsAll(
                listOf(
                    "janedoe@astermail.org",
                    "jane.doe+news@astermail.org",
                    "janedoe+news@astermail.org",
                    "jane.doe@astermail.org",
                ),
            ),
        )
    }

    @Test
    fun `an outside contact keeps a single spelling`() {
        assertEquals(listOf("jane.doe@example.com"), PinIdentity.describe("Jane.Doe@example.com").spellings)
    }

    @Test
    fun `dotted spellings enumerate dot placements up to the limit`() {
        assertEquals(
            listOf("a.bc@astermail.org", "ab.c@astermail.org", "a.b.c@astermail.org"),
            PinIdentity.dotted_spellings("abc@astermail.org", 10),
        )
        assertEquals(2, PinIdentity.dotted_spellings("abcdef@astermail.org", 2).size)
        assertTrue(PinIdentity.dotted_spellings("a@astermail.org", 5).isEmpty())
    }
}
