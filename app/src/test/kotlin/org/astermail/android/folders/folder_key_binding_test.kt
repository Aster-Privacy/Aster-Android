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

package org.astermail.android.folders

import java.security.SecureRandom
import java.util.Base64
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class folder_key_binding_test {

    private val folder_a = "6F1C2B0E-7C55-4E5B-9A55-0E6C7B1D2A11"
    private val folder_b = "0b5a1e39-2f53-4d1b-8a54-d1d6a2f9c0aa"
    private val current_salt = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val legacy_salt = "AAECAwQFBgcICQoLDA0ODw=="

    private fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    @Test
    fun bound_wrap_opens_only_for_its_own_folder() {
        val key = random(32)
        val folder_key = random(32)
        val nonce = random(12)
        val wrapped = wrap_folder_key(folder_key, key, nonce, folder_a)

        assertArrayEquals(folder_key, unwrap_folder_key(wrapped, key, nonce, folder_a.lowercase()))
        assertThrows(Exception::class.java) { unwrap_folder_key(wrapped, key, nonce, folder_b) }
        assertThrows(Exception::class.java) { unwrap_folder_key(wrapped, key, nonce, null) }
    }

    @Test
    fun a_ready_account_writes_a_bound_wrap() {
        val material = prepare_folder_password("hunter2", folder_a, bound = true)
        assertEquals(FOLDER_KEY_VERSION_BOUND, material.folder_key_version)
        assertTrue(folder_salt_is_current(material.password_salt))

        val keys = derive_folder_keys("hunter2", material.password_salt, allow_legacy = false)
        assertEquals(material.password_hash, keys.password_hash)
        val level = accept_folder_unlock(
            folder_id = folder_a,
            pin = 0,
            salt_base64 = material.password_salt,
            folder_key_version = material.folder_key_version,
            encrypted_folder_key = material.encrypted_folder_key,
            folder_key_nonce = material.folder_key_nonce,
            encryption_key = keys.encryption_key,
            bound_writes_ready = true,
        )
        assertEquals(FOLDER_PIN_BOUND, level)

        assertThrows(Exception::class.java) {
            accept_folder_unlock(
                folder_id = folder_b,
                pin = 0,
                salt_base64 = material.password_salt,
                folder_key_version = material.folder_key_version,
                encrypted_folder_key = material.encrypted_folder_key,
                folder_key_nonce = material.folder_key_nonce,
                encryption_key = keys.encryption_key,
                bound_writes_ready = true,
            )
        }
    }

    @Test
    fun an_account_that_is_not_ready_writes_the_shared_wrap() {
        val material = prepare_folder_password("hunter2", folder_a, bound = false)
        assertEquals(FOLDER_KEY_VERSION_LEGACY, material.folder_key_version)
        assertTrue(folder_salt_is_current(material.password_salt))

        val keys = derive_folder_keys("hunter2", material.password_salt, allow_legacy = false)
        val decoder = Base64.getDecoder()
        unwrap_folder_key(
            decoder.decode(material.encrypted_folder_key),
            keys.encryption_key,
            decoder.decode(material.folder_key_nonce),
            null,
        )
        assertEquals(FOLDER_PIN_CURRENT_SALT, folder_pin_level_for_write(false))
        assertEquals(FOLDER_PIN_BOUND, folder_pin_level_for_write(true))
    }

    @Test
    fun a_v2_pin_refuses_v1_even_when_the_account_reads_as_not_ready() {
        assertThrows(folder_kdf_downgrade_exception::class.java) {
            accept_folder_unlock(folder_a, FOLDER_PIN_BOUND, current_salt, null, null, null, ByteArray(32), bound_writes_ready = false)
        }
        assertThrows(folder_kdf_downgrade_exception::class.java) {
            required_folder_key_format(FOLDER_PIN_BOUND, FOLDER_KEY_VERSION_LEGACY, salt_current = true)
        }
    }

    @Test
    fun readiness_only_gates_raising_the_pin_to_v2() {
        val format = folder_key_format(allow_legacy_salt = false, bound = true)
        assertEquals(FOLDER_PIN_CURRENT_SALT, folder_pin_level_after_unlock(format, salt_current = true, bound_writes_ready = false))
        assertEquals(FOLDER_PIN_BOUND, folder_pin_level_after_unlock(format, salt_current = true, bound_writes_ready = true))
        assertEquals(FOLDER_PIN_BOUND, folder_pin_level_for_write(false, FOLDER_PIN_BOUND))
    }

    @Test
    fun a_ready_account_refuses_v1_for_a_v2_folder() {
        assertThrows(folder_kdf_downgrade_exception::class.java) {
            required_folder_key_format(FOLDER_PIN_BOUND, FOLDER_KEY_VERSION_LEGACY, salt_current = true)
        }
        assertThrows(folder_kdf_downgrade_exception::class.java) {
            accept_folder_unlock(folder_a, FOLDER_PIN_BOUND, current_salt, null, null, null, ByteArray(32), bound_writes_ready = true)
        }
    }

    @Test
    fun pinned_folder_refuses_a_legacy_salt() {
        assertThrows(folder_kdf_downgrade_exception::class.java) {
            required_folder_key_format(FOLDER_PIN_CURRENT_SALT, FOLDER_KEY_VERSION_LEGACY, salt_current = false)
        }
        assertFalse(folder_pin_allows_legacy_salt(FOLDER_PIN_CURRENT_SALT))
        assertTrue(folder_pin_allows_legacy_salt(0))
    }

    @Test
    fun bound_version_requires_a_current_salt() {
        assertThrows(folder_kdf_downgrade_exception::class.java) {
            required_folder_key_format(0, FOLDER_KEY_VERSION_BOUND, salt_current = false)
        }
        assertThrows(folder_kdf_downgrade_exception::class.java) {
            accept_folder_unlock(folder_a, 0, legacy_salt, FOLDER_KEY_VERSION_BOUND, "AA==", "AA==", ByteArray(32), bound_writes_ready = false)
        }
    }

    @Test
    fun bound_version_without_a_wrap_is_refused() {
        assertThrows(folder_kdf_downgrade_exception::class.java) {
            accept_folder_unlock(folder_a, 0, current_salt, FOLDER_KEY_VERSION_BOUND, null, null, ByteArray(32), bound_writes_ready = false)
        }
    }

    @Test
    fun unknown_versions_are_refused() {
        assertEquals(FOLDER_KEY_VERSION_LEGACY, parse_folder_key_version(null))
        assertThrows(folder_key_version_exception::class.java) { parse_folder_key_version(3) }
        assertThrows(folder_key_version_exception::class.java) { parse_folder_key_version(0) }
    }

    @Test
    fun legacy_folders_still_unlock_before_any_pin() {
        assertEquals(0, accept_folder_unlock(folder_a, 0, legacy_salt, null, null, null, ByteArray(32), bound_writes_ready = false))
        assertEquals(
            FOLDER_PIN_CURRENT_SALT,
            accept_folder_unlock(folder_a, 0, current_salt, null, null, null, ByteArray(32), bound_writes_ready = false),
        )
    }

    @Test
    fun vault_pins_merge_by_maximum_per_folder() {
        val server = JSONObject().put(folder_a.lowercase(), 2).put(folder_b, 1).put("bad", "2")
        val next = JSONObject().put(folder_a, 1).put(folder_b, 2).put("zero", 0).put("high", 9)

        val merged = merge_folder_key_pins(server, next)!!

        assertEquals(2, merged.getInt(folder_a.lowercase()))
        assertEquals(2, merged.getInt(folder_b))
        assertEquals(2, merged.length())
        assertNull(merge_folder_key_pins(JSONObject(), null))
    }

    @Test
    fun recording_a_pin_only_rises() {
        val pins = JSONObject().put(folder_a.lowercase(), 2)
        assertNull(with_folder_key_pin(pins, folder_a, 1))
        assertNull(with_folder_key_pin(pins, folder_a, 2))
        assertEquals(1, with_folder_key_pin(pins, folder_b, 1)!!.getInt(folder_b))
        assertNull(with_folder_key_pin(pins, folder_b, 3))
        assertEquals(2, vault_folder_key_pin(JSONObject().put(FOLDER_KEY_PINS_FIELD, pins), folder_a))
    }

    @Test
    fun local_folder_pin_only_rises() {
        val store = SessionKeyStore(null)
        assertEquals(0, store.folder_key_pin(folder_a))
        assertTrue(store.raise_folder_key_pin(folder_a, 2))
        assertFalse(store.raise_folder_key_pin(folder_a.lowercase(), 1))
        assertEquals(2, store.folder_key_pin(folder_a.lowercase()))
        assertFalse(store.raise_folder_key_pin(folder_b, 3))
        assertEquals(0, store.folder_key_pin(folder_b))
    }
}
