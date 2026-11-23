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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityKeyEventsTest {

    @Test
    fun `storing a new identity key signals its arrival`() {
        val store = SessionKeyStore()
        val before = store.identity_key_events().value

        store.put_identity_key("identity-a")

        assertEquals(before + 1, store.identity_key_events().value)
        assertEquals("identity-a", store.get_identity_key())
    }

    @Test
    fun `storing the same identity key again does not signal`() {
        val store = SessionKeyStore()
        store.put_identity_key("identity-a")
        val after_first = store.identity_key_events().value

        store.put_identity_key("identity-a")

        assertEquals(after_first, store.identity_key_events().value)
    }

    @Test
    fun `clearing the session signals that the identity key is gone`() {
        val store = SessionKeyStore()
        store.put_identity_key("identity-a")
        val after_put = store.identity_key_events().value

        store.clear()

        assertEquals(after_put + 1, store.identity_key_events().value)
        assertNull(store.get_identity_key())
    }
}
