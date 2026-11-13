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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class folder_protection_test {

    @Test
    fun matches_web_vector_for_legacy_sixteen_byte_salt() {
        val hash = derive_folder_auth_hash("hunter2", "AAECAwQFBgcICQoLDA0ODw==", allow_legacy = true)
        assertEquals("geWlAsIItcYaDIqooSZKoFWSE+Ce2f8DzMva7yDKOnc=", hash)
    }

    @Test
    fun matches_web_vector_for_unicode_password_and_thirty_two_byte_salt() {
        val hash = derive_folder_auth_hash(
            "pässwörd 日本",
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
            allow_legacy = false,
        )
        assertEquals("qNl6TyjxX2IuEow3hNujGiQRO1OvkOm49XrdSz4O03A=", hash)
    }

    @Test
    fun wrong_password_produces_different_hash() {
        val a = derive_folder_auth_hash("hunter2", "AAECAwQFBgcICQoLDA0ODw==", allow_legacy = true)
        val b = derive_folder_auth_hash("hunter3", "AAECAwQFBgcICQoLDA0ODw==", allow_legacy = true)
        assertNotEquals(a, b)
    }

    @Test
    fun refuses_legacy_salt_when_not_allowed() {
        assertThrows(folder_kdf_downgrade_exception::class.java) {
            derive_folder_auth_hash("hunter2", "AAECAwQFBgcICQoLDA0ODw==", allow_legacy = false)
        }
    }

    @Test
    fun refuses_salts_of_any_other_length() {
        for (length in listOf(0, 1, 8, 15, 17, 31, 33, 64)) {
            val salt = java.util.Base64.getEncoder().encodeToString(ByteArray(length))
            assertThrows(IllegalArgumentException::class.java) {
                derive_folder_auth_hash("hunter2", salt, allow_legacy = true)
            }
        }
    }

    @Test
    fun new_folder_passwords_use_the_current_format() {
        val material = prepare_folder_password("hunter2")
        assertTrue(folder_salt_is_current(material.password_salt))
        assertFalse(folder_salt_is_current("AAECAwQFBgcICQoLDA0ODw=="))
    }
}
