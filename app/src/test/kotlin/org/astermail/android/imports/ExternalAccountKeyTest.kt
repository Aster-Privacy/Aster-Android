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

package org.astermail.android.imports

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalAccountKeyTest {

    private val data_kek = ByteArray(32) { 1 }
    private val passphrase = "correct horse".toByteArray()
    private val derived = ByteArray(32) { 2 }

    @Test
    fun prefers_data_kek_without_fetching_passphrase() {
        val key = external_account_master_key(data_kek, { error("must not fetch password") }) { error("must not derive") }

        assertArrayEquals(data_kek, key)
    }

    @Test
    fun derives_from_passphrase_and_wipes_it() {
        val key = external_account_master_key(null, { passphrase }) { derived }

        assertArrayEquals(derived, key)
        assertArrayEquals(ByteArray(passphrase.size), passphrase)
    }

    @Test
    fun ignores_and_wipes_malformed_data_kek() {
        val malformed = ByteArray(16) { 1 }
        val key = external_account_master_key(malformed, { passphrase }) { derived }

        assertArrayEquals(derived, key)
        assertArrayEquals(ByteArray(16), malformed)
    }

    @Test
    fun wipes_passphrase_when_derivation_fails() {
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            external_account_master_key(null, { passphrase }) { error("derivation failed") }
        }
        assertArrayEquals(ByteArray(passphrase.size), passphrase)
    }

    @Test
    fun returns_null_without_key_material() {
        assertNull(external_account_master_key(null, { null }) { error("must not derive") })
    }
}
