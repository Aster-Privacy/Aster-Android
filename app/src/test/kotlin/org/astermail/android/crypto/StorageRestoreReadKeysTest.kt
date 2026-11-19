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
package org.astermail.android.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageRestoreReadKeysTest {

    @Test
    fun restore_read_keys_are_zeroed_after_use() {
        val seen = mutableListOf<ByteArray>()
        val sizes = StorageWriteKey.with_restore_read_keys(
            passphrase = byteArrayOf(1, 2, 3),
            kdf_version = CryptoNative.stretched_storage_kdf_version,
            derive = { _, version -> ByteArray(32) { (version ?: 0).toByte() } },
        ) { keys ->
            seen += keys
            keys.map { it.size }
        }
        assertEquals(listOf(32, 32), sizes)
        assertTrue(seen.all { key -> key.all { it.toInt() == 0 } })
    }

    @Test
    fun no_passphrase_gives_no_restore_keys() {
        val count = StorageWriteKey.with_restore_read_keys(null, null, { _, _ -> ByteArray(32) }) { it.size }
        assertEquals(0, count)
    }
}
