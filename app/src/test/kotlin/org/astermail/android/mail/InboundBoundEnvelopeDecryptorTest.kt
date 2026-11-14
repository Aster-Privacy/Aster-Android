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

package org.astermail.android.mail

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class InboundBoundEnvelopeDecryptorTest {

    private lateinit var vectors: JSONObject

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            val text = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and android.util.Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlDecoder().decode(text.trimEnd('='))
            } else {
                java.util.Base64.getDecoder().decode(text)
            }
        }

        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("inbound_bound_envelope_vectors.json"),
        )
        vectors = JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @After
    fun tear_down() {
        unmockkAll()
    }

    private val other_owner = "11111111-1111-1111-1111-111111111111"

    private fun decode(value: String): ByteArray = java.util.Base64.getDecoder().decode(value)

    private fun b64url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun key_set(): InboundRatchetKeySet {
        val scalar = decode(vectors.getString("recipient_p256_private_scalar_b64"))
        val point = decode(vectors.getString("recipient_p256_public_sec1_b64"))

        return InboundRatchetKeySet(
            identity_jwk = JSONObject()
                .put("kty", "EC")
                .put("crv", "P-256")
                .put("d", b64url(scalar))
                .put("x", b64url(point.copyOfRange(1, 33)))
                .put("y", b64url(point.copyOfRange(33, 65)))
                .toString(),
            pq_identity_secret_b64 = vectors.getString("recipient_ml_kem768_decapsulation_key_b64"),
        )
    }

    private fun owner() = vectors.getString("bound_owner_user_id")

    private fun envelope(name: String) = decode(vectors.getJSONObject(name).getString("envelope_b64"))

    private fun nonce(name: String) = decode(vectors.getJSONObject(name).getString("nonce_b64"))

    private fun open(name: String, enc: ByteArray, owner_user_id: String?) =
        InboundEnvelopeDecryptor.decrypt_bytes(enc, nonce(name), listOf(key_set()), owner_user_id)

    private fun expected_plaintext() = vectors.getString("plaintext_utf8").toByteArray()

    @Test
    fun bound_envelopes_carry_the_new_markers() {
        assertEquals(0x05.toByte(), envelope("ecdh_bound")[0])
        assertEquals(0x06.toByte(), envelope("pq_hybrid_bound")[0])
        assertEquals(66, InboundEnvelopeDecryptor.header_length(0x05))
        assertEquals(1154, InboundEnvelopeDecryptor.header_length(0x06))
    }

    @Test
    fun decrypts_a_marker_0x05_envelope_for_its_owner() {
        assertArrayEquals(expected_plaintext(), open("ecdh_bound", envelope("ecdh_bound"), owner()))
    }

    @Test
    fun decrypts_a_marker_0x06_envelope_for_its_owner() {
        assertArrayEquals(expected_plaintext(), open("pq_hybrid_bound", envelope("pq_hybrid_bound"), owner()))
    }

    @Test
    fun decrypts_a_bound_envelope_through_the_base64_entry_point() {
        val block = vectors.getJSONObject("ecdh_bound")

        val plain = InboundEnvelopeDecryptor.decrypt(
            block.getString("envelope_b64"),
            decode(block.getString("nonce_b64")),
            listOf(key_set()),
            owner(),
        )

        assertArrayEquals(expected_plaintext(), plain)
    }

    @Test
    fun a_bound_envelope_does_not_open_for_another_account() {
        assertNull(open("ecdh_bound", envelope("ecdh_bound"), other_owner))
        assertNull(open("pq_hybrid_bound", envelope("pq_hybrid_bound"), other_owner))
    }

    @Test
    fun a_bound_envelope_does_not_open_without_an_account() {
        for (missing in listOf(null, "", "user-1")) {
            assertNull(open("ecdh_bound", envelope("ecdh_bound"), missing))
            assertNull(open("pq_hybrid_bound", envelope("pq_hybrid_bound"), missing))
        }
    }

    @Test
    fun a_bound_envelope_cannot_be_relabeled_as_unbound() {
        val ecdh = envelope("ecdh_bound").also { it[0] = 0x03 }
        val pq = envelope("pq_hybrid_bound").also { it[0] = 0x04 }

        assertNull(open("ecdh_bound", ecdh, owner()))
        assertNull(open("ecdh_bound", ecdh, null))
        assertNull(open("pq_hybrid_bound", pq, owner()))
        assertNull(open("pq_hybrid_bound", pq, null))
    }

    @Test
    fun a_bound_envelope_fails_when_its_ciphertext_is_altered() {
        val ecdh = envelope("ecdh_bound")
        ecdh[ecdh.size - 1] = (ecdh[ecdh.size - 1].toInt() xor 0x01).toByte()

        assertNull(open("ecdh_bound", ecdh, owner()))
    }

    @Test
    fun account_ids_parse_to_sixteen_bytes() {
        val bytes = InboundEnvelopeDecryptor.owner_bytes(owner())

        assertNotNull(bytes)
        assertEquals(16, bytes!!.size)
        assertEquals(0x3f.toByte(), bytes.first())
        assertEquals(0x5e.toByte(), bytes.last())
        assertArrayEquals(bytes, InboundEnvelopeDecryptor.owner_bytes("3F2B8C1E7A4D4E9B9C605D1A2B3C4D5E"))
    }

    @Test
    fun malformed_account_ids_are_refused() {
        val malformed = listOf(
            null,
            "",
            "user-1",
            "3f2b8c1e-7a4d-4e9b-9c60-5d1a2b3c4d",
            "+f2b8c1e-7a4d-4e9b-9c60-5d1a2b3c4d5e",
            "zz2b8c1e-7a4d-4e9b-9c60-5d1a2b3c4d5e",
            "\uFF13f2b8c1e-7a4d-4e9b-9c60-5d1a2b3c4d5e",
            "3f2b8c1e-7a4d-4e9b-9c60-5d1a2b3c4d5e00",
        )

        for (value in malformed) {
            assertNull(InboundEnvelopeDecryptor.owner_bytes(value))
        }
    }
}
