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
package org.astermail.android.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredLoginSaltsTest {

    private fun salt_of(seed: Int, size: Int = 16): ByteArray = ByteArray(size) { (it + seed).toByte() }

    @Test
    fun `retired salts are returned newest first for the signed in user only`() {
        val store = SessionKeyStore()
        store.put_user_id("user-a")
        store.retire_password_salts("user-a", listOf(salt_of(1), salt_of(2)))
        store.retire_password_salts("user-b", listOf(salt_of(9)))
        val salts = store.get_retired_password_salts()
        assertEquals(2, salts.size)
        assertArrayEquals(salt_of(2), salts[0])
        assertArrayEquals(salt_of(1), salts[1])
    }

    @Test
    fun `retired salts survive clearing the session`() {
        val store = SessionKeyStore()
        store.put_user_id("user-a")
        store.retire_password_salts("user-a", listOf(salt_of(4)))
        store.clear()
        assertTrue(store.get_retired_password_salts().isEmpty())
        store.put_user_id("user-a")
        assertArrayEquals(salt_of(4), store.get_retired_password_salts().single())
    }

    @Test
    fun `a repeated salt is kept once and moves to the newest position`() {
        var entries = RetiredLoginSalts.remember_all(emptyList(), "u", listOf(salt_of(1), salt_of(2)))
        entries = RetiredLoginSalts.remember(entries, "u", salt_of(1))
        val salts = RetiredLoginSalts.salts_for(entries, "u")
        assertEquals(2, salts.size)
        assertArrayEquals(salt_of(1), salts[0])
        assertArrayEquals(salt_of(2), salts[1])
    }

    @Test
    fun `each user keeps at most the per user limit and the list round trips`() {
        var entries = emptyList<String>()
        for (seed in 0 until RetiredLoginSalts.max_per_user + 3) {
            entries = RetiredLoginSalts.remember(entries, "u", salt_of(seed))
        }
        entries = RetiredLoginSalts.remember(entries, "other", salt_of(50))
        val parsed = RetiredLoginSalts.parse(RetiredLoginSalts.serialize(entries))
        val salts = RetiredLoginSalts.salts_for(parsed, "u")
        assertEquals(RetiredLoginSalts.max_per_user, salts.size)
        assertArrayEquals(salt_of(RetiredLoginSalts.max_per_user + 2), salts.first())
        assertArrayEquals(salt_of(3), salts.last())
        assertArrayEquals(salt_of(50), RetiredLoginSalts.salts_for(parsed, "other").single())
    }

    @Test
    fun `invalid salts and owners are ignored`() {
        val entries = RetiredLoginSalts.remember_all(
            emptyList(),
            "u",
            listOf(ByteArray(0), salt_of(1, RetiredLoginSalts.max_salt_bytes + 1)),
        )
        assertTrue(entries.isEmpty())
        assertTrue(RetiredLoginSalts.remember(emptyList(), "", salt_of(1)).isEmpty())
        assertTrue(RetiredLoginSalts.remember(emptyList(), "a\tb", salt_of(1)).isEmpty())
        assertTrue(RetiredLoginSalts.parse("garbage\n\n").isEmpty())
    }

    @Test
    fun `server salts decode from base64 and drop bad entries`() {
        val good = java.util.Base64.getEncoder().encodeToString(salt_of(7))
        val too_long = java.util.Base64.getEncoder().encodeToString(salt_of(1, RetiredLoginSalts.max_salt_bytes + 1))
        val decoded = RetiredLoginSalts.decode_base64(listOf("not base64 !!", "", too_long, good))
        assertEquals(1, decoded.size)
        assertArrayEquals(salt_of(7), decoded.single())
    }
}
