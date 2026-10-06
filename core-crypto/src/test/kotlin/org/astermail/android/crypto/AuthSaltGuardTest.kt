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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSaltGuardTest {

    private val vault_salt = ByteArray(16) { (it + 1).toByte() }
    private val vault = vault_salt + ByteArray(64) { 9 }

    @Test
    fun `a salt equal to the cached vault salt is refused before the hash is derived`() {
        var derived = 0
        assertThrows(AuthSaltCollisionException::class.java) {
            AuthSaltGuard.derive_with_usable_auth_salt(vault_salt.copyOf(), vault, emptyList()) {
                derived += 1
                ByteArray(32)
            }
        }
        assertEquals(0, derived)
    }

    @Test
    fun `a salt equal to a remembered vault salt is refused without a cached vault`() {
        var derived = 0
        assertThrows(AuthSaltCollisionException::class.java) {
            AuthSaltGuard.derive_with_usable_auth_salt(vault_salt.copyOf(), null, listOf(ByteArray(16), vault_salt)) {
                derived += 1
                ByteArray(32)
            }
        }
        assertEquals(0, derived)
        assertTrue(AuthSaltGuard.collides_with_remembered_salts(vault_salt.copyOf(), listOf(vault_salt)))
    }

    @Test
    fun `an unrelated salt of either length derives the hash`() {
        val short_salt = ByteArray(16) { 0x55 }
        val long_salt = ByteArray(32) { 0x66 }
        val expected = ByteArray(32) { 7 }
        assertArrayEquals(
            expected,
            AuthSaltGuard.derive_with_usable_auth_salt(short_salt, vault, listOf(vault_salt)) { expected },
        )
        assertArrayEquals(
            expected,
            AuthSaltGuard.derive_with_usable_auth_salt(long_salt, vault, listOf(vault_salt)) { expected },
        )
        assertFalse(AuthSaltGuard.collides_with_remembered_salts(long_salt, listOf(vault_salt)))
    }

    @Test
    fun `a salt shorter than the minimum is refused`() {
        assertThrows(AuthSaltCollisionException::class.java) {
            AuthSaltGuard.require_usable_auth_salt(ByteArray(4), null)
        }
    }
}
