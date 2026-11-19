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
package org.astermail.android.crypto.storage_rotation

import org.astermail.android.crypto.StorageWriteKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ConstantSweepTriggerTest {

    @Test
    fun `an account that never swept is due`() {
        assertEquals(true, constants_sweep_due(prefs_available = true, done = false, use_pending = false, first_in_process = false))
    }

    @Test
    fun `a done account is skipped until a constant read is flagged`() {
        assertEquals(false, constants_sweep_due(prefs_available = true, done = true, use_pending = false, first_in_process = false))
        assertEquals(true, constants_sweep_due(prefs_available = true, done = true, use_pending = true, first_in_process = false))
    }

    @Test
    fun `without prefs the sweep runs once per process`() {
        assertEquals(true, constants_sweep_due(prefs_available = false, done = false, use_pending = false, first_in_process = true))
        assertEquals(false, constants_sweep_due(prefs_available = false, done = false, use_pending = false, first_in_process = false))
    }

    @Test
    fun `a constant hit is never promoted ahead of the real keys`() {
        val real = ByteArray(32) { 5 }
        val constant = StorageWriteKey.public_mail_kek_raws().first()
        val sealed = gcm_seal(constant, "x".toByteArray(), null)
        val keys = mutableListOf(real, constant.copyOf())

        val opened = open_with_any(keys, decode_b64(sealed.ciphertext)!!, decode_b64(sealed.nonce)!!)

        assertEquals("x", opened?.toString(Charsets.UTF_8))
        assertEquals(true, keys.first().contentEquals(real))
    }
}
