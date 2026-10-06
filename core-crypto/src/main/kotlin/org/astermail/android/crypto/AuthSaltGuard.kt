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

class AuthSaltCollisionException(message: String) : SecurityException(message)

object AuthSaltGuard {
    private const val vault_salt_bytes = 16
    private const val min_auth_salt_bytes = 8

    fun vault_salt_prefix(encrypted_vault: ByteArray): ByteArray? {
        if (encrypted_vault.size <= vault_salt_bytes) return null
        return encrypted_vault.copyOfRange(0, vault_salt_bytes)
    }

    fun collides_with_vault_salt(auth_salt: ByteArray, encrypted_vault: ByteArray?): Boolean {
        if (encrypted_vault == null) return false
        val prefix = vault_salt_prefix(encrypted_vault) ?: return false
        if (auth_salt.size != prefix.size) return false
        return constant_time_equals(auth_salt, prefix)
    }

    fun collides_with_remembered_salts(auth_salt: ByteArray, remembered_vault_salts: Collection<ByteArray>): Boolean {
        var collides = false
        for (remembered in remembered_vault_salts) {
            if (constant_time_equals(auth_salt, remembered)) collides = true
        }
        return collides
    }

    fun require_usable_auth_salt(
        auth_salt: ByteArray,
        encrypted_vault: ByteArray?,
        remembered_vault_salts: Collection<ByteArray> = emptyList(),
    ) {
        if (auth_salt.size < min_auth_salt_bytes) {
            throw AuthSaltCollisionException("auth salt is shorter than $min_auth_salt_bytes bytes")
        }
        if (collides_with_vault_salt(auth_salt, encrypted_vault)) {
            throw AuthSaltCollisionException("auth salt equals the vault key salt")
        }
        if (collides_with_remembered_salts(auth_salt, remembered_vault_salts)) {
            throw AuthSaltCollisionException("auth salt equals a vault key salt seen on this device")
        }
    }

    fun derive_with_usable_auth_salt(
        auth_salt: ByteArray,
        encrypted_vault: ByteArray?,
        remembered_vault_salts: Collection<ByteArray>,
        derive: (ByteArray) -> ByteArray,
    ): ByteArray {
        require_usable_auth_salt(auth_salt, encrypted_vault, remembered_vault_salts)
        return derive(auth_salt)
    }

    fun constant_time_equals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
