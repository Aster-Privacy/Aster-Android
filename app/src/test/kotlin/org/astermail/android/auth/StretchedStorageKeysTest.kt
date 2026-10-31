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

import org.astermail.android.crypto.CryptoNative
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class StretchedStorageKeysTest {

    private val passphrase = "correct horse battery staple".toByteArray(Charsets.UTF_8)
    private val legacy_hex = "cb6616b65e1ade4fdaeca1775010334af9a4be7bce0666ec8b3d765df01a14fd"
    private val stretched_hex = "23ccc31a0a5d3bf5fdd19437ba916c4e82691b16f9ec5f5d0dbc2a45f23862ee"

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun unhex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun vault(kdf_version: Any?): JSONObject {
        val vault_obj = JSONObject().put("identity_key", "k")
        if (kdf_version != null) vault_obj.put("kdf_version", kdf_version)
        return vault_obj
    }

    @Test
    fun `only a numeric version of exactly two selects the stretched key`() {
        assertEquals(1, read_storage_kdf_version(vault(null)))
        assertEquals(1, read_storage_kdf_version(vault(1)))
        assertEquals(2, read_storage_kdf_version(vault(2)))
        assertEquals(2, read_storage_kdf_version(vault(2L)))
        assertEquals(2, read_storage_kdf_version(vault(2.0)))
        assertEquals(2, read_storage_kdf_version(JSONObject("{\"kdf_version\":2}")))
        assertEquals(1, read_storage_kdf_version(vault("2")))
        assertEquals(1, read_storage_kdf_version(vault(2.5)))
        assertEquals(1, read_storage_kdf_version(vault(3)))
        assertEquals(1, read_storage_kdf_version(vault(0)))
    }

    @Test
    fun `legacy vault absorbs nothing and never derives a key`() {
        var derived = 0
        val absorbed = stretched_storage_keks_to_absorb(vault(1), passphrase, ::encode) { _, _ ->
            derived++
            ByteArray(32)
        }
        assertTrue(absorbed.isEmpty())
        assertEquals(0, derived)

        assertTrue(stretched_storage_keks_to_absorb(vault(null), passphrase, ::encode).isEmpty())
        assertTrue(stretched_storage_keks_to_absorb(vault(3), passphrase, ::encode).isEmpty())
    }

    @Test
    fun `stretched vault absorbs the stretched key then the legacy key`() {
        val absorbed = stretched_storage_keks_to_absorb(vault(2), passphrase, ::encode)
        assertEquals(listOf(encode(unhex(stretched_hex)), encode(unhex(legacy_hex))), absorbed)
    }

    @Test
    fun `absorbed keys are appended after the existing ones`() {
        val current = listOf("data", "old")
        assertEquals(listOf("data", "old", "v2", "v1"), append_storage_keks(current, listOf("v2", "old", "v1")))
        assertEquals(current, append_storage_keks(current, emptyList()))
        assertEquals(listOf("v2"), append_storage_keks(emptyList(), listOf("v2", "", "v2")))
    }

    @Test
    fun `legacy candidate opens data when the stretched key does not`() {
        val legacy_key = unhex(legacy_hex)
        val field = CryptoNative.encrypt_field("template".toByteArray(Charsets.UTF_8), legacy_key)
        val candidates = storage_key_candidates(passphrase, 2)

        assertEquals(unhex(stretched_hex).toList(), candidates[0].toList())
        assertEquals(legacy_key.toList(), candidates[1].toList())

        val opened = open_with_storage_key_candidates(candidates) { key ->
            CryptoNative.decrypt_field(field.ciphertext, field.nonce, key)
        }
        assertEquals("template", opened?.toString(Charsets.UTF_8))
    }

    @Test
    fun `stretched candidate opens data written with the stretched key`() {
        val field = CryptoNative.encrypt_field("note".toByteArray(Charsets.UTF_8), unhex(stretched_hex))
        val opened = open_with_storage_key_candidates(storage_key_candidates(passphrase, 2)) { key ->
            CryptoNative.decrypt_field(field.ciphertext, field.nonce, key)
        }
        assertEquals("note", opened?.toString(Charsets.UTF_8))
    }

    @Test
    fun `legacy vault offers only the legacy candidate`() {
        val candidates = storage_key_candidates(passphrase, 1)
        assertEquals(1, candidates.size)
        assertEquals(unhex(legacy_hex).toList(), candidates[0].toList())
    }

    @Test
    fun `no candidate opens data sealed with an unrelated key`() {
        val field = CryptoNative.encrypt_field("x".toByteArray(Charsets.UTF_8), ByteArray(32) { 9 })
        val opened = open_with_storage_key_candidates(storage_key_candidates(passphrase, 2) + listOf(ByteArray(16))) { key ->
            CryptoNative.decrypt_field(field.ciphertext, field.nonce, key)
        }
        assertNull(opened)
    }
}
