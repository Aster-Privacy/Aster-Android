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
package org.astermail.android.crypto.opaque

import java.math.BigInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LoginV3VectorTest {

    private val vector: Map<String, String> = javaClass.classLoader!!
        .getResourceAsStream("login_v3_vectors.txt")!!
        .bufferedReader()
        .readLines()
        .filter { it.isNotBlank() }
        .associate { line ->
            val index = line.indexOf('=')
            line.substring(0, index) to line.substring(index + 1)
        }

    private fun unhex(text: String): ByteArray =
        ByteArray(text.length / 2) { index -> text.substring(index * 2, index * 2 + 2).toInt(16).toByte() }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun bytes(key: String) = unhex(vector.getValue(key))

    private val password get() = bytes("password")

    @Test
    fun the_base_point_encodes_to_the_published_generator() {
        assertEquals(
            "e2f2ae0a6abc4e71a884a961c500515f58e30b6aa582dd8db6a65945e08d2d76",
            hex(Ristretto255.encode(Ristretto255.base_point)),
        )
    }

    @Test
    fun small_multiples_of_the_base_point_match_the_published_encodings() {
        val expected = listOf(
            "6a493210f7499cd17fecb510ae0cea23a110e8d5b901f8acadd3095c73a3b919",
            "94741f5d5d52755ece4f23f044ee27d5d1ea1e2bd196b462166b16152a9d0259",
            "da80862773358b466ffadfe0b3293ab3d9fd53c5ea6c955358f568322daf6a57",
        )
        expected.forEachIndexed { index, encoding ->
            val multiple = Ristretto255.multiply(Ristretto255.base_point, BigInteger.valueOf(index + 2L))
            assertEquals(encoding, hex(Ristretto255.encode(multiple)))
        }
    }

    @Test
    fun an_encoded_element_decodes_back_to_the_same_encoding() {
        val encoded = bytes("registration_request")
        val decoded = Ristretto255.decode(encoded)
        assertNotNull(decoded)
        assertArrayEquals(encoded, Ristretto255.encode(decoded!!))
    }

    @Test
    fun non_canonical_and_off_curve_encodings_are_refused() {
        val refused = listOf(
            "edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
            "0100000000000000000000000000000000000000000000000000000000000000",
            "26948d35ca62e643e26a83177332e6b6afeb9d08e4268b650f1f5bbd8d81d371",
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        )
        for (encoding in refused) assertNull(encoding, Ristretto255.decode(unhex(encoding)))
        assertNull(Ristretto255.decode(ByteArray(31)))
    }

    @Test
    fun the_multiple_by_the_group_order_is_the_identity() {
        val element = Ristretto255.multiply(Ristretto255.base_point, Ristretto255.group_order)
        assertEquals(true, Ristretto255.is_identity(element))
    }

    @Test
    fun the_registration_request_matches_the_shared_vector() {
        val start = LoginV3Client.registration_start(password, bytes("registration_start_seed"))
        assertEquals(vector.getValue("registration_request"), hex(start.message))
    }

    @Test
    fun the_registration_upload_matches_the_shared_vector() {
        val start = LoginV3Client.registration_start(password, bytes("registration_start_seed"))
        val upload = LoginV3Client.registration_finish(
            start,
            password,
            bytes("registration_response"),
            bytes("registration_finish_seed"),
        )
        assertEquals(vector.getValue("registration_upload"), hex(upload))
    }

    @Test
    fun the_credential_request_matches_the_shared_vector() {
        val start = LoginV3Client.login_start(password, bytes("login_start_seed"))
        assertEquals(vector.getValue("credential_request"), hex(start.message))
    }

    @Test
    fun the_credential_finalization_matches_the_shared_vector() {
        val start = LoginV3Client.login_start(password, bytes("login_start_seed"))
        val finalization = LoginV3Client.login_finish(start, password, bytes("credential_response"))
        assertEquals(vector.getValue("credential_finalization"), hex(finalization))
    }

    @Test
    fun a_wrong_password_is_rejected() {
        val wrong = "correct horse battery stapler".toByteArray(Charsets.UTF_8)
        val start = LoginV3Client.login_start(password, bytes("login_start_seed"))
        assertThrows(LoginV3RejectedException::class.java) {
            LoginV3Client.login_finish(start, wrong, bytes("credential_response"))
        }
    }

    @Test
    fun the_response_for_an_unknown_account_is_rejected() {
        val start = LoginV3Client.login_start(password, bytes("login_start_seed"))
        assertThrows(LoginV3RejectedException::class.java) {
            LoginV3Client.login_finish(start, password, bytes("unknown_account_credential_response"))
        }
    }

    @Test
    fun a_tampered_server_proof_is_rejected() {
        val response = bytes("credential_response")
        response[response.size - 1] = (response[response.size - 1].toInt() xor 1).toByte()
        val start = LoginV3Client.login_start(password, bytes("login_start_seed"))
        assertThrows(LoginV3RejectedException::class.java) {
            LoginV3Client.login_finish(start, password, response)
        }
    }

    @Test
    fun a_response_of_the_wrong_length_is_malformed() {
        val start = LoginV3Client.login_start(password, bytes("login_start_seed"))
        assertThrows(LoginV3MalformedException::class.java) {
            LoginV3Client.login_finish(start, password, bytes("credential_response").copyOf(319))
        }
        val registration = LoginV3Client.registration_start(password, bytes("registration_start_seed"))
        assertThrows(LoginV3MalformedException::class.java) {
            LoginV3Client.registration_finish(registration, password, ByteArray(63), ByteArray(32))
        }
    }

    @Test
    fun a_registration_start_cannot_finish_a_sign_in() {
        val start = LoginV3Client.registration_start(password, bytes("registration_start_seed"))
        assertThrows(LoginV3MalformedException::class.java) {
            LoginV3Client.login_finish(start, password, bytes("credential_response"))
        }
    }

    @Test
    fun different_seeds_give_different_requests() {
        val first = LoginV3Client.login_start(password, ByteArray(32) { 1 })
        val second = LoginV3Client.login_start(password, ByteArray(32) { 2 })
        assertEquals(false, first.message.contentEquals(second.message))
    }
}
