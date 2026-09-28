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

package org.astermail.android.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeDetectionTest {

    @Test
    fun `recovery codes are told apart from backup codes`() {
        assertTrue(looks_like_recovery_code("ASTER-7KQ2-M9XD-4HPT-WN3C"))
        assertTrue(looks_like_recovery_code("aster 7kq2 m9xd 4hpt"))
        assertTrue(looks_like_recovery_code("ASTER-7KQ2-M9XD"))
    }

    @Test
    fun `backup codes and partial input are not recovery codes`() {
        assertFalse(looks_like_recovery_code("ABCD-EFGH-JKMN"))
        assertFalse(looks_like_recovery_code("ABCD-EFGH"))
        assertFalse(looks_like_recovery_code("ASTER"))
        assertFalse(looks_like_recovery_code("ASTE-RABC-DEFG"))
    }
}
