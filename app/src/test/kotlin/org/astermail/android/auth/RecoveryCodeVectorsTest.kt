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
import org.junit.Assert.assertEquals
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
}
