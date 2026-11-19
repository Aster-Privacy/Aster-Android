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

import java.util.Base64
import org.astermail.android.api.security.HardwareKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasskeyNameTest {
    private val storage_key = ByteArray(32) { 0x21 }
    private val other_key = ByteArray(32) { 0x22 }
    private val web_sealed = "an1:oKGio6SlpqeoqaqroN6xTEjIMXDGqcRN5FEyApQiDxyswGD8b2Nm9bfdloSnYQEXBibcbWTzOZ9SUg14iQ=="
    private val wrong_tag = "an1:oKGio6SlpqeoqaqrFeKThlIF4LakNnaGtT1xSjFjBl06R1n/hKGWeiDgPXgrv1aiqw=="

    @Test
    fun opens_name_sealed_by_web_client() {
        assertEquals("Work laptop", open_passkey_name(web_sealed, listOf(storage_key)))
    }

    @Test
    fun tries_every_key_until_one_opens() {
        assertEquals("Work laptop", open_passkey_name(web_sealed, listOf(other_key, storage_key)))
    }

    @Test
    fun returns_nothing_when_no_key_opens() {
        assertNull(open_passkey_name(web_sealed, listOf(other_key)))
        assertNull(open_passkey_name(web_sealed, emptyList()))
    }

    @Test
    fun rejects_text_without_the_name_tag() {
        assertNull(open_passkey_name(wrong_tag, listOf(other_key)))
    }

    @Test
    fun rejects_malformed_sealed_names() {
        assertNull(open_passkey_name("an1:not base64!", listOf(storage_key)))
        assertNull(open_passkey_name("an1:AAAA", listOf(storage_key)))
    }

    @Test
    fun keeps_plain_names() {
        assertEquals("Phone", open_passkey_name("Phone", listOf(storage_key)))
        assertNull(open_passkey_name(null, listOf(storage_key)))
    }

    @Test
    fun collects_data_kek_and_decrypt_keks_once() {
        val encoded = Base64.getEncoder().encodeToString(other_key)
        val keys = passkey_name_keys(storage_key.copyOf(), listOf(encoded, encoded, "short", Base64.getEncoder().encodeToString(storage_key)))
        assertEquals(2, keys.size)
    }

    @Test
    fun never_shows_a_sealed_name_it_could_not_open() {
        assertEquals("", HardwareKey(id = "k", name = web_sealed).display_name)
        assertEquals("Phone", HardwareKey(id = "k", name = "Phone").display_name)
    }
}
