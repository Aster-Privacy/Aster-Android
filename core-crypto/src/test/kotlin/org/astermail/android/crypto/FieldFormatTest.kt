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

import java.util.Base64
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldFormatTest {

    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { 7 }
    private val plaintext = "Weekly update".toByteArray(Charsets.UTF_8)
    private val web_v2_vector = "WA/DNwN294G2+XL05SuAr1wmnmo4UvyWn/yWnL0="
    private val web_v1_vector = "WA/DNwN294G2+XL05ViSxvqUHEsULJXX8cu5Xos="

    @org.junit.Before
    @After
    fun reset_switch() {
        FieldFormat.write_v2 = false
        FieldFormat.write_source = { false }
        BoundFieldRecords.bind(null)
    }

    private class memory_records : BoundFieldRecordStore {
        val saved = mutableMapOf<String, Set<String>>()
        override fun records(kind: String): Set<String>? = saved[kind]
        override fun save(kind: String, records: Set<String>): Boolean {
            saved[kind] = records
            return true
        }
    }

    @Test
    fun a_record_seen_bound_no_longer_opens_its_unbound_forms() {
        val bound = FieldFormat.seal(plaintext, key, nonce, "template", "content", "tpl-40", FieldFormat.V2)
        assertTrue(opens(bound, "content", "tpl-40"))
        val unbound = FieldFormat.seal(plaintext, key, nonce, "template", "content", "tpl-40", FieldFormat.V1)
        val null_bound = FieldFormat.seal(plaintext, key, nonce, "template", "content", null, FieldFormat.V2)
        assertFalse(opens(unbound, "content", "tpl-40"))
        assertFalse(opens(null_bound, "content", "tpl-40"))
    }

    @Test
    fun records_never_seen_bound_still_read_their_older_forms() {
        val unbound = FieldFormat.seal(plaintext, key, nonce, "template", "content", "tpl-41", FieldFormat.V1)
        assertTrue(opens(unbound, "content", "tpl-41"))
        assertFalse(BoundFieldRecords.is_bound("template", "tpl-41"))
    }

    @Test
    fun the_bound_record_list_survives_a_restart() {
        val store = memory_records()
        BoundFieldRecords.bind(store)
        val bound = FieldFormat.seal(plaintext, key, nonce, "template", "content", "tpl-42", FieldFormat.V2)
        assertTrue(opens(bound, "content", "tpl-42"))
        BoundFieldRecords.bind(store)
        val unbound = FieldFormat.seal(plaintext, key, nonce, "template", "content", "tpl-42", FieldFormat.V1)
        assertFalse(opens(unbound, "content", "tpl-42"))
        assertEquals(setOf("tpl-42"), store.saved["template"])
    }

    @Test
    fun the_write_switch_can_follow_the_server() {
        FieldFormat.write_source = { true }
        assertEquals(FieldFormat.V2, FieldFormat.write_version())
        FieldFormat.write_source = { throw IllegalStateException("offline") }
        assertEquals(FieldFormat.V1, FieldFormat.write_version())
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun opens(ciphertext: ByteArray, field: String, record_id: String?): Boolean =
        runCatching { FieldFormat.decrypt(ciphertext, nonce, key, "template", field, record_id) }.isSuccess

    @Test
    fun v2_fields_match_the_web_vector() {
        val sealed = FieldFormat.seal(plaintext, key, nonce, "template", "content", "tpl-1", FieldFormat.V2)
        assertEquals(web_v2_vector, b64(sealed))
    }

    @Test
    fun v1_fields_match_the_web_vector() {
        val sealed = FieldFormat.seal(plaintext, key, nonce, "template", "content", "tpl-1", FieldFormat.V1)
        assertEquals(web_v1_vector, b64(sealed))
    }

    @Test
    fun fields_written_by_older_versions_still_read() {
        val legacy = Base64.getDecoder().decode(web_v1_vector)
        val opened = FieldFormat.decrypt(legacy, nonce, key, "template", "content", "tpl-1")
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun web_v2_fields_read_on_android() {
        val bound = Base64.getDecoder().decode(web_v2_vector)
        assertArrayEquals(plaintext, FieldFormat.decrypt(bound, nonce, key, "template", "content", "tpl-1"))
    }

    @Test
    fun a_v2_field_moved_to_another_field_does_not_open() {
        val sealed = FieldFormat.seal(plaintext, key, nonce, "template", "content", "tpl-1", FieldFormat.V2)
        assertTrue(opens(sealed, "content", "tpl-1"))
        assertFalse(opens(sealed, "name", "tpl-1"))
    }

    @Test
    fun a_v2_field_moved_to_another_record_does_not_open() {
        val sealed = FieldFormat.seal(plaintext, key, nonce, "template", "content", "tpl-1", FieldFormat.V2)
        assertFalse(opens(sealed, "content", "tpl-2"))
    }

    @Test
    fun a_new_record_field_sealed_before_its_id_exists_still_reads() {
        val sealed = FieldFormat.seal(plaintext, key, nonce, "template", "name", null, FieldFormat.V2)
        assertTrue(opens(sealed, "name", "tpl-9"))
        assertFalse(opens(sealed, "content", "tpl-9"))
    }

    @Test
    fun writes_stay_v1_while_the_switch_is_off() {
        assertEquals(FieldFormat.V1, FieldFormat.write_version())
        val field = FieldFormat.encrypt(plaintext, key, "template", "name", "tpl-1")
        assertArrayEquals(plaintext, CryptoNative.decrypt_field(field.ciphertext, field.nonce, key))
    }

    @Test
    fun writes_are_bound_once_the_switch_is_on() {
        FieldFormat.write_v2 = true
        val field = FieldFormat.encrypt(plaintext, key, "template", "name", "tpl-1")
        assertFalse(runCatching { CryptoNative.decrypt_field(field.ciphertext, field.nonce, key) }.isSuccess)
        assertArrayEquals(
            plaintext,
            FieldFormat.decrypt(field.ciphertext, field.nonce, key, "template", "name", "tpl-1"),
        )
    }
}
