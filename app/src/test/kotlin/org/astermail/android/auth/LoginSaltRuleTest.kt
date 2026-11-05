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

import org.astermail.android.crypto.AuthSaltCollisionException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginSaltRuleTest {

    private val short_salt = ByteArray(16) { (it + 1).toByte() }
    private val other_short_salt = ByteArray(16) { (it + 40).toByte() }
    private val current_salt = ByteArray(32) { (it + 7).toByte() }

    private class FakeRecords : LoginSaltRecordSource {
        var records: List<LoginSaltRecord> = emptyList()
        override fun find(keys: List<String>): ByteArray? = LoginSaltRule.find_record(records, keys)
        override fun put(keys: List<String>, salt: ByteArray) {
            records = LoginSaltRule.put_records(records, keys, salt)
        }
        override fun remove(keys: List<String>) {
            records = LoginSaltRule.remove_records(records, keys)
        }
    }

    private fun refused(block: () -> Unit): LegacyLoginSaltException? =
        try {
            block()
            null
        } catch (e: LegacyLoginSaltException) {
            e
        }

    @Test
    fun `matching record allows a short v1 salt`() {
        val records = FakeRecords().apply { put(listOf("hash_a"), short_salt) }
        val recorded = records.find(listOf("hash_a"))
        assertTrue(LoginSaltRule.is_v1_proof_allowed(short_salt, 1, recorded, LoginSaltCheck.sign_in, false))
        assertTrue(LoginSaltRule.is_v1_proof_allowed(short_salt, 1, recorded, LoginSaltCheck.step_up, false))
    }

    @Test
    fun `mismatched record refuses a short v1 salt even for a known account`() {
        val records = FakeRecords().apply { put(listOf("hash_a"), short_salt) }
        val recorded = records.find(listOf("hash_a"))
        assertFalse(LoginSaltRule.is_v1_proof_allowed(other_short_salt, 1, recorded, LoginSaltCheck.sign_in, true))
        val error = refused {
            LoginSaltRule.require_v1_proof_allowed(other_short_salt, 1, recorded, LoginSaltCheck.sign_in, true)
        }
        assertEquals(LoginSaltCheck.sign_in, error?.check)
    }

    @Test
    fun `no record and a saved account allows sign in`() {
        assertTrue(LoginSaltRule.is_v1_proof_allowed(short_salt, 1, null, LoginSaltCheck.sign_in, true))
    }

    @Test
    fun `no record and an unknown account refuses sign in`() {
        val error = refused {
            LoginSaltRule.require_v1_proof_allowed(short_salt, 1, null, LoginSaltCheck.sign_in, false)
        }
        assertEquals(LoginSaltCheck.sign_in, error?.check)
    }

    @Test
    fun `step up without a record is refused`() {
        assertFalse(LoginSaltRule.is_v1_proof_allowed(short_salt, 1, null, LoginSaltCheck.step_up, true))
        val error = refused {
            LoginSaltRule.require_v1_proof_allowed(short_salt, 1, null, LoginSaltCheck.step_up, true)
        }
        assertEquals(LoginSaltCheck.step_up, error?.check)
    }

    @Test
    fun `kdf v2 is allowed at any salt length`() {
        for (length in listOf(1, 8, 16, 31, 32, 64)) {
            val salt = ByteArray(length) { it.toByte() }
            assertTrue(LoginSaltRule.is_v1_proof_allowed(salt, 2, null, LoginSaltCheck.sign_in, false))
            assertTrue(LoginSaltRule.is_v1_proof_allowed(salt, 2, null, LoginSaltCheck.step_up, false))
            assertTrue(LoginSaltRule.is_v1_proof_allowed(salt, 2, other_short_salt, LoginSaltCheck.step_up, false))
        }
    }

    @Test
    fun `a 32 byte salt is unaffected`() {
        assertTrue(LoginSaltRule.is_v1_proof_allowed(current_salt, 1, null, LoginSaltCheck.sign_in, false))
        assertTrue(LoginSaltRule.is_v1_proof_allowed(current_salt, 1, null, LoginSaltCheck.step_up, false))
        assertTrue(LoginSaltRule.is_v1_proof_allowed(current_salt, 1, short_salt, LoginSaltCheck.step_up, false))
        assertTrue(LoginSaltRule.is_v1_proof_allowed(ByteArray(48), 1, null, LoginSaltCheck.sign_in, false))
    }

    @Test
    fun `a 31 byte salt still counts as short`() {
        assertFalse(LoginSaltRule.is_v1_proof_allowed(ByteArray(31), 1, null, LoginSaltCheck.sign_in, false))
    }

    @Test
    fun `rotation is needed only for short v1 salts`() {
        assertTrue(LoginSaltRule.needs_rotation(1, short_salt))
        assertFalse(LoginSaltRule.needs_rotation(2, short_salt))
        assertFalse(LoginSaltRule.needs_rotation(1, current_salt))
    }

    @Test
    fun `refusal is an auth salt collision`() {
        val error = refused {
            LoginSaltRule.require_v1_proof_allowed(short_salt, 1, null, LoginSaltCheck.step_up, false)
        }
        assertTrue(error is AuthSaltCollisionException)
    }

    @Test
    fun `records round trip through serialization`() {
        val stored = LoginSaltRule.put_records(emptyList(), listOf("hash_a", "hash_b"), short_salt)
        val parsed = LoginSaltRule.parse_records(LoginSaltRule.serialize_records(stored))
        assertEquals(stored, parsed)
        assertArrayEquals(short_salt, LoginSaltRule.find_record(parsed, listOf("missing", "hash_b")))
    }

    @Test
    fun `put replaces the record for a key`() {
        val first = LoginSaltRule.put_records(emptyList(), listOf("hash_a"), short_salt)
        val second = LoginSaltRule.put_records(first, listOf("hash_a"), current_salt)
        assertEquals(1, second.size)
        assertArrayEquals(current_salt, LoginSaltRule.find_record(second, listOf("hash_a")))
    }

    @Test
    fun `remove drops the record`() {
        val records = FakeRecords().apply {
            put(listOf("hash_a"), short_salt)
            put(listOf("hash_b"), current_salt)
            remove(listOf("hash_a"))
        }
        assertNull(records.find(listOf("hash_a")))
        assertArrayEquals(current_salt, records.find(listOf("hash_b")))
    }

    @Test
    fun `records are capped and keep the most recent`() {
        var records: List<LoginSaltRecord> = emptyList()
        for (i in 0 until LoginSaltRule.max_login_salt_records + 10) {
            records = LoginSaltRule.put_records(records, listOf("hash_$i"), short_salt)
        }
        assertEquals(LoginSaltRule.max_login_salt_records, records.size)
        assertNull(LoginSaltRule.find_record(records, listOf("hash_0")))
        assertArrayEquals(short_salt, LoginSaltRule.find_record(records, listOf("hash_${LoginSaltRule.max_login_salt_records + 9}")))
    }

    @Test
    fun `malformed stored records are ignored`() {
        val good = LoginSaltRule.encode_salt(short_salt)
        val parsed = LoginSaltRule.parse_records("broken\n:$good\nhash a:$good\nhash_c:not base64!\nhash_d:$good")
        assertEquals(listOf(LoginSaltRecord("hash_d", good)), parsed)
    }
}
