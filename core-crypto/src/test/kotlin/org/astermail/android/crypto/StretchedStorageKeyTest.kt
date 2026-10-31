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
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StretchedStorageKeyTest {

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val ascii_passphrase = "correct horse battery staple".toByteArray(Charsets.UTF_8)
    private val unicode_passphrase = unhex("70c3a4737377c3b672642de697a5e69cac2df09f9491")

    @Test
    fun `legacy storage key vector is unchanged`() {
        assertEquals(
            "cb6616b65e1ade4fdaeca1775010334af9a4be7bce0666ec8b3d765df01a14fd",
            hex(CryptoNative.derive_storage_key(ascii_passphrase)),
        )
        assertEquals(
            "6fefa6d2d072f961580c3b4993d5c4ea5cc5af8fd8768ab1b3fffa0745bb9bd1",
            hex(CryptoNative.derive_storage_key(unicode_passphrase)),
        )
    }

    @Test
    fun `stretched storage key matches the web derivation`() {
        assertEquals(
            "23ccc31a0a5d3bf5fdd19437ba916c4e82691b16f9ec5f5d0dbc2a45f23862ee",
            hex(CryptoNative.derive_stretched_storage_key(ascii_passphrase)),
        )
        assertEquals(
            "c2e9e920261db2d63a07252fc0291ea9bca81e9a358d6d544f3d0d33ed2bc7a8",
            hex(CryptoNative.derive_stretched_storage_key(unicode_passphrase)),
        )
    }

    @Test
    fun `version selects the derivation`() {
        val legacy = CryptoNative.derive_storage_key(ascii_passphrase)
        val stretched = CryptoNative.derive_stretched_storage_key(ascii_passphrase)

        assertEquals(hex(stretched), hex(CryptoNative.derive_storage_key_for_version(ascii_passphrase, 2)))
        assertEquals(hex(legacy), hex(CryptoNative.derive_storage_key_for_version(ascii_passphrase, 1)))
        assertEquals(hex(legacy), hex(CryptoNative.derive_storage_key_for_version(ascii_passphrase, 0)))
        assertEquals(hex(legacy), hex(CryptoNative.derive_storage_key_for_version(ascii_passphrase, 3)))
        assertNotEquals(hex(legacy), hex(stretched))
    }

    @Test
    fun `stretched derivation leaves the passphrase intact`() {
        val passphrase = ascii_passphrase.copyOf()
        CryptoNative.derive_stretched_storage_key(passphrase)
        assertEquals(hex(ascii_passphrase), hex(passphrase))
    }
}
