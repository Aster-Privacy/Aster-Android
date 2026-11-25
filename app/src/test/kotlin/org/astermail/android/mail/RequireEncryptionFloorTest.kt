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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequireEncryptionFloorTest {

    @Test
    fun the_server_can_raise_but_never_lower_the_local_choice() {
        assertEquals(true, RequireEncryptionFloor.effective(local = true, server = false))
        assertEquals(true, RequireEncryptionFloor.effective(local = false, server = true))
        assertEquals(true, RequireEncryptionFloor.effective(local = true, server = null))
        assertEquals(false, RequireEncryptionFloor.effective(local = false, server = false))
        assertEquals(false, RequireEncryptionFloor.effective(local = null, server = false))
        assertNull(RequireEncryptionFloor.effective(local = null, server = null))
        assertNull(RequireEncryptionFloor.effective(local = false, server = null))
    }

    @Test
    fun a_server_false_never_clears_the_local_choice() {
        assertTrue(RequireEncryptionFloor.local_after_server_load(local = true, server = false))
        assertTrue(RequireEncryptionFloor.local_after_server_load(local = false, server = true))
        assertFalse(RequireEncryptionFloor.local_after_server_load(local = false, server = false))
    }
}
