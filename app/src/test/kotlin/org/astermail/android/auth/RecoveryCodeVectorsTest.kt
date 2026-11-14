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

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecoveryCodeVectorsTest {

    private lateinit var vectors: JSONObject

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }

        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("recovery_code_vectors.json"),
        )
        vectors = JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun hex_of(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun encoded(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    @Test
    fun code_verifier_matches_the_server() {
        val raw_hash = ByteArray(32) { 7 }
        val verifier_salt = ByteArray(16) { 3 }

        assertEquals(
            "06f99b085e73a17975cc61fe253dfddc21e4b683d81d204d5bcdf848a4c74dd4",
            hex_of(derive_code_verifier(raw_hash, verifier_salt)),
        )
        assertEquals(
            "4152563303030303030303030303030303030303" +
                "aa0037a7929d74b1589ea2085b171720fdf103f4d70a81a512583ce4df275146",
            hex_of(salted_recovery_verifier(raw_hash, verifier_salt)),
        )
    }

    @Test
    fun code_verifier_rejects_a_salt_of_the_wrong_length() {
        val failure = runCatching { derive_code_verifier(ByteArray(32) { 7 }, ByteArray(8) { 3 }) }

        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun stored_salt_is_read_only_from_the_salted_format() {
        val raw_hash = ByteArray(32) { 7 }
        val verifier_salt = ByteArray(16) { 3 }

        assertArrayEquals(
            verifier_salt,
            stored_verifier_salt(salted_recovery_verifier(raw_hash, verifier_salt)),
        )
        assertNull(stored_verifier_salt(raw_hash))
        assertNull(stored_verifier_salt(recovery_code_verifier(raw_hash)))
        assertNull(stored_verifier_salt(ByteArray(52) { 1 }))
    }

    @Test
    fun candidates_cover_each_distinct_usable_salt() {
        val code = "ASTER-AAAA-BBBB-CCCC-DDDD"
        val first = encoded(ByteArray(16) { 3 })
        val second = encoded(ByteArray(16) { 4 })
        val candidates = build_verifier_candidates(
            code,
            listOf(first, first, null, encoded(ByteArray(8) { 3 }), "not base64 !", second),
        )

        assertEquals(listOf(first, second), candidates.map { it.verifier_salt })
        assertEquals(
            encoded(derive_code_verifier(raw_recovery_code_hash(code), ByteArray(16) { 3 })),
            candidates[0].code_verifier,
        )
        assertTrue(build_verifier_candidates(code, emptyList()).isEmpty())
    }

    @Test
    fun used_codes_are_found_in_every_stored_form() {
        val plain = "ASTER-AAAA-BBBB-CCCC-DDDD"
        val tagged = "ASTER-EEEE-FFFF-GGGG-HHHH"
        val salted = "ASTER-JJJJ-KKKK-LLLL-MMMM"
        val unused = "ASTER-NNNN-PPPP-QQQQ-RRRR"
        val stored = setOf(
            encoded(raw_recovery_code_hash(plain)),
            encoded(recovery_code_verifier(raw_recovery_code_hash(tagged))),
            encoded(salted_recovery_verifier(raw_recovery_code_hash(salted), ByteArray(16) { 5 })),
        )

        assertEquals(
            setOf(plain, tagged, salted),
            used_recovery_codes(listOf(plain, tagged, salted, unused), stored),
        )
        assertTrue(used_recovery_codes(listOf(plain), emptySet()).isEmpty())
    }

    @Test
    fun shorter_codes_are_outdated() {
        assertTrue(is_outdated_recovery_code("ASTER-AAAA-BBBB-CCCC"))
        assertFalse(is_outdated_recovery_code("ASTER-AAAA-BBBB-CCCC-DDDD"))
        assertFalse(is_outdated_recovery_code("not a code"))
        assertTrue(has_outdated_recovery_codes(listOf("ASTER-AAAA-BBBB-CCCC-DDDD", "ASTER-AAAA-BBBB-CCCC")))
        assertFalse(has_outdated_recovery_codes(listOf("ASTER-AAAA-BBBB-CCCC-DDDD")))
        assertFalse(has_outdated_recovery_codes(null))
    }

    @Test
    fun set_size_matches_the_other_clients() {
        assertEquals(RECOVERY_CODE_SET_SIZE, vectors.getInt("set_size"))
    }

    @Test
    fun generated_codes_use_the_shared_alphabet() {
        val alphabet = vectors.getString("alphabet")
        val codes = generate_recovery_codes()

        assertEquals(RECOVERY_CODE_SET_SIZE, codes.size)
        assertEquals(codes.size, codes.toSet().size)

        for (code in codes) {
            assertTrue(code, is_valid_recovery_code(code))
            assertEquals(code, canonicalize_recovery_code(code))
            assertEquals(5, code.split("-").size)

            for (character in code.removePrefix("ASTER-").replace("-", "")) {
                assertTrue("$code uses $character", alphabet.contains(character))
            }
        }
    }

    @Test
    fun canonicalizes_and_hashes_every_shared_vector() {
        val cases = vectors.getJSONArray("vectors")
        assertTrue(cases.length() > 0)

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val input = case.getString("input")

            assertEquals(input, case.getString("canonical"), canonicalize_recovery_code(input))
            assertEquals(input, case.getBoolean("is_valid"), is_valid_recovery_code(input))

            if (case.has("code_hash")) {
                assertEquals(input, case.getString("code_hash"), hash_recovery_code(input))
            }
        }
    }

    @Test
    fun verifier_has_versioned_prefix_and_digest_length() {
        val raw_hash = ByteArray(32) { 0x07 }
        val verifier = recovery_code_verifier(raw_hash)

        assertEquals(36, verifier.size)
        assertArrayEquals(byteArrayOf(0x41, 0x52, 0x56, 0x32), verifier.copyOfRange(0, 4))
    }

    @Test
    fun verifier_is_deterministic_and_input_bound() {
        val raw_hash = ByteArray(32) { 0x07 }
        val other_hash = ByteArray(32) { 0x08 }

        assertArrayEquals(recovery_code_verifier(raw_hash), recovery_code_verifier(raw_hash.copyOf()))
        assertFalse(recovery_code_verifier(raw_hash).contentEquals(recovery_code_verifier(other_hash)))
        assertFalse(recovery_code_verifier(raw_hash).copyOfRange(4, 36).contentEquals(raw_hash))
    }

    @Test
    fun stored_hashes_cover_legacy_and_verifier_forms() {
        val code = generate_recovery_codes(1).first()
        val raw_hash = raw_recovery_code_hash(code)
        val stored = recovery_code_stored_hashes(code)

        assertEquals(2, stored.size)
        assertTrue(stored.contains(hash_recovery_code(code)))
        assertTrue(
            stored.contains(
                java.util.Base64.getEncoder().encodeToString(recovery_code_verifier(raw_hash)),
            ),
        )
    }
}
