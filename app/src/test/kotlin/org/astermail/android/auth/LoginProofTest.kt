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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.astermail.android.crypto.AuthSaltCollisionException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginProofTest {

    private class FakePins(initial: List<String> = emptyList()) : LoginPinSource {
        var entries: List<String> = initial
        override fun is_pinned(keys: List<String>): Boolean = LoginProof.is_pinned(entries, keys)
        override fun pin(keys: List<String>) {
            entries = LoginProof.add_pins(entries, keys)
        }
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun sample_v1(): ByteArray = ByteArray(32) { (it * 7 + 3).toByte() }

    @Test
    fun derive_v2_matches_known_answer_for_zero_input() {
        assertEquals("jMxRO7LIMGjT0gRMvr9dAN9d9Fn7ymn9FtA/9eb54ew=", b64(LoginProof.derive_v2(ByteArray(32))))
    }

    @Test
    fun derive_v2_matches_known_answer_for_counting_input() {
        val ikm = ByteArray(32) { it.toByte() }
        assertEquals("PSD43cjGUexfKi8te9TGr49vnKozhILpIV2MPUOhTPc=", b64(LoginProof.derive_v2(ikm)))
    }

    @Test
    fun derive_v2_never_returns_its_input() {
        val v1 = sample_v1()
        val v2 = LoginProof.derive_v2(v1)
        assertEquals(32, v2.size)
        assertFalse(v1.contentEquals(v2))
    }

    @Test
    fun kdf_version_is_two_only_for_the_number_two() {
        assertEquals(1, LoginProof.read_kdf_version(null))
        assertEquals(1, LoginProof.read_kdf_version(JsonNull))
        assertEquals(1, LoginProof.read_kdf_version(JsonPrimitive(1)))
        assertEquals(1, LoginProof.read_kdf_version(JsonPrimitive("2")))
        assertEquals(1, LoginProof.read_kdf_version(JsonPrimitive(2.0)))
        assertEquals(1, LoginProof.read_kdf_version(JsonPrimitive(3)))
        assertEquals(1, LoginProof.read_kdf_version(JsonPrimitive(true)))
        assertEquals(1, LoginProof.read_kdf_version(buildJsonObject { put("v", JsonPrimitive(2)) }))
        assertEquals(2, LoginProof.read_kdf_version(JsonPrimitive(2)))
    }

    @Test
    fun kdf_two_always_picks_v2() {
        assertEquals(LoginProofChoice.v2, LoginProof.choose_proof(2, pinned = false))
        assertEquals(LoginProofChoice.v2, LoginProof.choose_proof(2, pinned = true))
    }

    @Test(expected = AuthSaltCollisionException::class)
    fun pinned_account_refuses_kdf_one() {
        LoginProof.choose_proof(1, pinned = true)
    }

    @Test
    fun unpinned_kdf_one_picks_v1() {
        assertEquals(LoginProofChoice.v1, LoginProof.choose_proof(1, pinned = false))
    }

    @Test
    fun pins_are_capped_and_most_recent_win() {
        var entries = emptyList<String>()
        for (i in 0 until 80) {
            entries = LoginProof.add_pins(entries, listOf(LoginProof.user_id_pin_key("user_$i")))
        }
        assertEquals(LoginProof.max_pinned_accounts, entries.size)
        assertFalse(LoginProof.is_pinned(entries, listOf("u:user_0")))
        assertTrue(LoginProof.is_pinned(entries, listOf("u:user_79")))
        entries = LoginProof.add_pins(entries, listOf("u:user_20"))
        assertEquals(LoginProof.max_pinned_accounts, entries.size)
        assertEquals("u:user_20", entries.last())
        assertEquals(1, entries.count { it == "u:user_20" })
    }

    @Test
    fun pins_round_trip_and_survive_corrupt_storage() {
        val entries = LoginProof.add_pins(emptyList(), LoginProof.pin_keys("abc", "hash1"))
        assertEquals(listOf("u:abc", "h:hash1"), entries)
        assertEquals(entries, LoginProof.parse_pins(LoginProof.serialize_pins(entries)))
        assertEquals(emptyList<String>(), LoginProof.parse_pins(null))
        assertEquals(emptyList<String>(), LoginProof.parse_pins(""))
        assertEquals(emptyList<String>(), LoginProof.parse_pins("\n\n   \n"))
        assertEquals(listOf("u:ok"), LoginProof.parse_pins("garbage\nu:ok\nx:bad\nh:\nu:has space\n\u0000"))
        assertEquals(emptyList<String>(), LoginProof.pin_keys(null, " "))
    }

    @Test
    fun sign_in_with_kdf_two_sends_v2_without_pinning_before_success() {
        val pins = FakePins()
        val v1 = sample_v1()
        val proof = LoginProof.select_sign_in_proof(v1, 2, listOf("h:hash1"), pins)
        assertArrayEquals(LoginProof.derive_v2(v1), proof.proof)
        assertNull(proof.enrollment)
        assertFalse(pins.is_pinned(listOf("h:hash1")))
    }

    @Test
    fun sign_in_with_kdf_one_sends_v1_and_carries_enrollment_without_pinning() {
        val pins = FakePins()
        val v1 = sample_v1()
        val proof = LoginProof.select_sign_in_proof(v1, 1, listOf("h:hash1"), pins)
        assertArrayEquals(v1, proof.proof)
        assertNotNull(proof.enrollment)
        val enrollment = proof.enrollment!!
        assertArrayEquals(v1, enrollment.v1)
        assertArrayEquals(LoginProof.derive_v2(v1), enrollment.v2)
        assertFalse(pins.is_pinned(listOf("h:hash1")))
        enrollment.wipe()
        assertTrue(enrollment.v1.all { it == 0.toByte() } && enrollment.v2.all { it == 0.toByte() })
    }

    @Test(expected = AuthSaltCollisionException::class)
    fun sign_in_downgrade_for_pinned_hash_is_refused() {
        val pins = FakePins(listOf("h:hash1"))
        LoginProof.select_sign_in_proof(sample_v1(), 1, listOf("h:hash1"), pins)
    }

    @Test
    fun enrollment_pins_only_on_success() = runTest {
        val keys = listOf("u:abc", "h:hash1")
        val failing = FakePins()
        val enrollment = PendingLoginEnrollment(sample_v1(), LoginProof.derive_v2(sample_v1()))
        assertFalse(LoginProof.flush_enrollment(enrollment, keys, failing) { _, _ -> throw IllegalStateException("offline") })
        assertFalse(failing.is_pinned(keys))
        assertFalse(LoginProof.flush_enrollment(enrollment, keys, failing) { _, _ -> false })
        assertFalse(failing.is_pinned(keys))
        val working = FakePins()
        assertTrue(LoginProof.flush_enrollment(enrollment, keys, working) { v1, v2 ->
            v2.contentEquals(LoginProof.derive_v2(v1))
        })
        assertTrue(working.is_pinned(listOf("u:abc")))
        assertTrue(working.is_pinned(listOf("h:hash1")))
    }

    @Test
    fun step_up_uses_v2_after_inline_enrollment() = runTest {
        val pins = FakePins()
        val v1 = sample_v1()
        val proof = LoginProof.select_step_up_proof(v1, 1, listOf("u:abc"), pins) { _, _ -> true }
        assertArrayEquals(LoginProof.derive_v2(v1), proof)
        assertTrue(pins.is_pinned(listOf("u:abc")))
    }

    @Test
    fun step_up_falls_back_to_v1_when_enrollment_fails() = runTest {
        val pins = FakePins()
        val v1 = sample_v1()
        val proof = LoginProof.select_step_up_proof(v1, 1, listOf("u:abc"), pins) { _, _ ->
            throw IllegalStateException("rate limited")
        }
        assertArrayEquals(v1, proof)
        assertFalse(pins.is_pinned(listOf("u:abc")))
    }

    @Test(expected = AuthSaltCollisionException::class)
    fun step_up_downgrade_for_pinned_user_is_refused() = runTest {
        val pins = FakePins(listOf("u:abc"))
        LoginProof.select_step_up_proof(sample_v1(), 1, listOf("u:abc", "h:hash1"), pins) { _, _ -> true }
    }
}
