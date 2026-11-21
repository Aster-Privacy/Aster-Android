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

package org.astermail.android.crypto.guard

import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GuardDigestsTest {
    private val user_id = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    private val request_id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
    private val nonce = ByteArray(32) { it.toByte() }
    private val subject = ByteArray(32) { (0xa0 + it).toByte() }
    private val ed = ByteArray(32) { 0x11 }
    private val mlkem = ByteArray(1184) { 0x22 }
    private val x = ByteArray(32) { 0x33 }
    private val action_pk = byteArrayOf(0x02) + ByteArray(32) { 0x44 }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun sha(b: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(b))
    private val kd get() = GuardDigests.keys_digest(ed, mlkem, x, action_pk)
    private val kd0 get() = GuardDigests.keys_digest(ed, mlkem, x, GuardDigests.ABSENT_ACTION_PK)

    @Test
    fun keys_digest_vectors() {
        assertEquals("c8b92c7e16a9bb2edf93c93983240c55a375fc29d50746cf6cdf6d439d41110b", hex(kd))
        assertEquals("834f8d3bc8320d7e2a92e33f00f200de037ce2806695c27d38902e9cfb8269e3", hex(kd0))
    }

    @Test
    fun approve_subject_vectors() {
        assertEquals("ac8e5a52e5e4d55ac8fc07619a5c7cc6be147d749a4dc5f93f943f3a1d9026bc", hex(GuardDigests.approve_subject(request_id, kd, 42)))
        assertEquals("3e5170b45bb76f27bdc4fb9d9ca6c5110b000fc15558d1f75c2df11e0a7ecd3b", hex(GuardDigests.approve_subject(null, kd0, 0)))
        assertEquals("374d2364cb371fda52fdf948e42c21153c3cfd338217f63f713265c3964a8151", hex(GuardDigests.approve_subject(null, kd, 0)))
    }

    @Test
    fun id_and_switches_subject_vectors() {
        assertEquals("16a862dba0000cddd0258465be215033594566e1c8b7eefe25c8106f48072484", hex(GuardDigests.id_subject(request_id)))
        assertEquals("0868cdee63b96db6e43215df13b03e4dafe3d69747fcfec3640fe3a2024599a2", hex(GuardDigests.switches_subject(9, 5)))
        assertEquals("fd4b2ece22272e8146d498ef0ddd8ca36fc19a0d990f3b963124e044ea2a2937", hex(GuardDigests.switches_subject(0, 0)))
    }

    @Test
    fun number_hash_vectors() {
        assertEquals("7d633c75e8df5c05c248e56bf09b4b596b0260a9ac609b993f3d7a2959f69d77", hex(GuardDigests.number_hash(request_id, 42)))
        assertEquals("c3ef3cf2523deccb046c661829841da962a6a7f9f3094d728381f37aa4c7d354", hex(GuardDigests.number_hash(request_id, 43)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun number_hash_rejects_nine() {
        GuardDigests.number_hash(request_id, 9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun number_hash_rejects_one_hundred() {
        GuardDigests.number_hash(request_id, 100)
    }

    @Test
    fun rescue_rotate_subject_vector() {
        assertEquals("c258331a421bf62c2379f975c6fb6115b160a2f39104302166d95b40f536ab01", hex(GuardDigests.rescue_rotate_subject("BCDFGHJKMN23456789BCDFGHJ")))
    }

    @Test
    fun panic_subject_and_absent_key_are_zero() {
        assertArrayEquals(ByteArray(32), GuardDigests.PANIC_SUBJECT)
        assertArrayEquals(ByteArray(33), GuardDigests.ABSENT_ACTION_PK)
    }

    @Test
    fun action_message_vectors() {
        val expected = mapOf(
            GuardActionCode.ENROLL to "9c3e758429f0a2b53fbca00e281d9ff1c2ae43a3c180963a34fda347a6770073",
            GuardActionCode.APPROVE to "da02e9769a6ca4363bb3f4caa2c01a01d3ad1db8782bbc4cddfde9bfa1e4a605",
            GuardActionCode.DENY to "5b387eaa2b15a32b9b34694c93aa42095940f982c7cb948ac0d1bd029d04ef07",
            GuardActionCode.CANCEL to "f66d62e35a19499f547b6684975985e2e75d2591c5cbffb81e3941e625ce9708",
            GuardActionCode.SKIP to "d1947e8e3d8e669af8ac94a0434ba3c2d19f16ca1f35ad99b6cc551e084f54f0",
            GuardActionCode.VETO to "56eaf57e429231612860a3a65d20af0cf05b1f9a55dc7a24044a053aedb0c1ef",
            GuardActionCode.PANIC to "7fb2d5d79cac706addcb119ab80d0b2846f1677f933c37d75f77560acc97d334",
            GuardActionCode.REMOVE_DEVICE to "7cbd1f37de6fd39223117aee9ba1ed785bff00687f26e59ffa7a183d4a1a6048",
            GuardActionCode.SWITCHES to "145d3ac91b28bb6d3b84994acb8de784bbe28b7a73ddd34f57b044329af4a1ea",
            GuardActionCode.JOIN to "302d82406e44010289df1573d6ff620f67dc3144d5c472ec82e9d9ef6083b084",
            GuardActionCode.RESUME to "e0875eea1b941c547e40cec50c6f092809f157c8722ddc2c58c80d53c21ab016",
            GuardActionCode.ROTATE_RESCUE to "e78ec94b6c9c767af6a5c682164c1874c6ccc65c095adb5d980221d822f7d125",
        )
        assertEquals(12, GuardActionCode.entries.size)
        for ((code, digest) in expected) {
            val msg = GuardDigests.action_message(code, user_id, nonce, subject)
            assertEquals(102, msg.size)
            assertEquals(code.name, digest, sha(msg))
        }
    }

    @Test
    fun compress_p256_even_and_odd_y() {
        val px = ByteArray(32) { (it + 1).toByte() }
        val even = byteArrayOf(0x04) + px + ByteArray(32) { if (it == 31) 0x10 else 0x55 }
        val odd = byteArrayOf(0x04) + px + ByteArray(32) { if (it == 31) 0x11 else 0x55 }
        assertArrayEquals(byteArrayOf(0x02) + px, GuardDigests.compress_p256(even))
        assertArrayEquals(byteArrayOf(0x03) + px, GuardDigests.compress_p256(odd))
    }
}
