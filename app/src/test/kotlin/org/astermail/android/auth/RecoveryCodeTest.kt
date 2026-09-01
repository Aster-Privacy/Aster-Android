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

package org.astermail.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {

    private val four_segment = "ASTER-ABCD-EFGH-JKLM-NPQR"
    private val three_segment = "ASTER-ABCD-EFGH-JKLM"

    @Test
    fun `exactly formatted codes are unchanged`() {
        assertEquals(four_segment, canonicalize_recovery_code(four_segment))
        assertEquals(three_segment, canonicalize_recovery_code(three_segment))
    }

    @Test
    fun `separator variations normalize to the canonical form`() {
        val variants = listOf(
            "ASTER ABCD EFGH JKLM NPQR",
            "ASTERABCDEFGHJKLMNPQR",
            "aster abcd efgh jklm npqr",
            "ASTER–ABCD–EFGH–JKLM–NPQR",
            "  ASTER-ABCD-EFGH-JKLM-NPQR  ",
        )

        for (variant in variants) {
            assertEquals(four_segment, canonicalize_recovery_code(variant))
        }
    }

    @Test
    fun `web issued four segment codes are accepted`() {
        assertTrue(is_valid_recovery_code(four_segment))
        assertTrue(is_valid_recovery_code(three_segment))
        assertTrue(is_valid_recovery_code("ASTER ABCD EFGH JKLM NPQR"))
    }

    @Test
    fun `malformed codes are rejected`() {
        assertFalse(is_valid_recovery_code("ASTER-ABCD-EFGH"))
        assertFalse(is_valid_recovery_code("NOTACODE"))
        assertFalse(is_valid_recovery_code(""))
    }
}
