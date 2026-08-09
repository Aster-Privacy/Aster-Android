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

class InboundEnvelopeDecryptorTest {

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
            javaClass.classLoader?.getResourceAsStream("inbound_envelope_vectors.json"),
        )
        vectors = JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @After
    fun tear_down() {
        unmockkAll()
    }

    private fun decode(value: String): ByteArray = java.util.Base64.getDecoder().decode(value)

    private fun b64url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun recipient_identity_jwk(): String {
        val scalar = decode(vectors.getString("recipient_p256_private_scalar_b64"))
        val point = decode(vectors.getString("recipient_p256_public_sec1_b64"))

        return JSONObject()
            .put("kty", "EC")
            .put("crv", "P-256")
            .put("d", b64url(scalar))
            .put("x", b64url(point.copyOfRange(1, 33)))
            .put("y", b64url(point.copyOfRange(33, 65)))
            .toString()
    }

    private fun real_key_set() = InboundRatchetKeySet(
        identity_jwk = recipient_identity_jwk(),
        pq_identity_secret_b64 = vectors.getString("recipient_ml_kem768_decapsulation_key_b64"),
    )

    private fun wrong_key_set() = InboundRatchetKeySet(
        identity_jwk = JSONObject()
            .put("kty", "EC")
            .put("crv", "P-256")
            .put("d", b64url(ByteArray(32) { (it + 1).toByte() }))
            .toString(),
        pq_identity_secret_b64 = null,
    )

    private fun expected_plaintext() = vectors.getString("plaintext_utf8").toByteArray()

    @Test
    fun decrypts_a_marker_0x03_envelope_through_the_production_path() {
        val block = vectors.getJSONObject("ecdh_compressed")

        val plain = InboundEnvelopeDecryptor.decrypt(
            block.getString("envelope_b64"),
            decode(block.getString("nonce_b64")),
            listOf(real_key_set()),
        )

        assertArrayEquals(expected_plaintext(), plain)
    }

    @Test
    fun decrypts_a_marker_0x04_envelope_through_the_production_path() {
        val block = vectors.getJSONObject("pq_hybrid")

        val plain = InboundEnvelopeDecryptor.decrypt(
            block.getString("envelope_b64"),
            decode(block.getString("nonce_b64")),
            listOf(real_key_set()),
        )

        assertArrayEquals(expected_plaintext(), plain)
    }

    @Test
    fun falls_through_earlier_key_sets_to_the_one_that_works() {
        val block = vectors.getJSONObject("ecdh_compressed")

        val plain = InboundEnvelopeDecryptor.decrypt(
            block.getString("envelope_b64"),
            decode(block.getString("nonce_b64")),
            listOf(wrong_key_set(), real_key_set()),
        )

        assertArrayEquals(expected_plaintext(), plain)
    }

    @Test
    fun returns_null_when_no_key_set_matches() {
        val block = vectors.getJSONObject("ecdh_compressed")

        assertNull(
            InboundEnvelopeDecryptor.decrypt(
                block.getString("envelope_b64"),
                decode(block.getString("nonce_b64")),
                listOf(wrong_key_set()),
            ),
        )
        assertNull(
            InboundEnvelopeDecryptor.decrypt(
                block.getString("envelope_b64"),
                decode(block.getString("nonce_b64")),
                emptyList(),
            ),
        )
    }

    @Test
    fun a_pq_envelope_needs_a_pq_secret() {
        val block = vectors.getJSONObject("pq_hybrid")
        val ecdh_only = InboundRatchetKeySet(
            identity_jwk = recipient_identity_jwk(),
            pq_identity_secret_b64 = null,
        )

        assertNull(
            InboundEnvelopeDecryptor.decrypt(
                block.getString("envelope_b64"),
                decode(block.getString("nonce_b64")),
                listOf(ecdh_only),
            ),
        )
        assertNotNull(
            InboundEnvelopeDecryptor.decrypt(
                block.getString("envelope_b64"),
                decode(block.getString("nonce_b64")),
                listOf(ecdh_only, real_key_set()),
            ),
        )
    }

    @Test
    fun rejects_a_tampered_ciphertext() {
        val block = vectors.getJSONObject("ecdh_compressed")
        val tampered = decode(block.getString("envelope_b64"))
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xff).toByte()

        assertNull(
            InboundEnvelopeDecryptor.decrypt_bytes(
                tampered,
                decode(block.getString("nonce_b64")),
                listOf(real_key_set()),
            ),
        )
    }

    @Test
    fun rejects_a_tampered_ephemeral_key() {
        val block = vectors.getJSONObject("ecdh_compressed")
        val tampered = decode(block.getString("envelope_b64"))
        tampered[10] = (tampered[10].toInt() xor 0xff).toByte()

        assertNull(
            InboundEnvelopeDecryptor.decrypt_bytes(
                tampered,
                decode(block.getString("nonce_b64")),
                listOf(real_key_set()),
            ),
        )
    }

    @Test
    fun rejects_unknown_markers_and_malformed_inputs() {
        val block = vectors.getJSONObject("ecdh_compressed")
        val envelope = decode(block.getString("envelope_b64"))
        val nonce = decode(block.getString("nonce_b64"))
        val keys = listOf(real_key_set())

        val unknown_marker = envelope.copyOf()
        unknown_marker[0] = 0x09

        assertNull(InboundEnvelopeDecryptor.decrypt_bytes(unknown_marker, nonce, keys))
        assertNull(InboundEnvelopeDecryptor.decrypt_bytes(ByteArray(0), nonce, keys))
        assertNull(InboundEnvelopeDecryptor.decrypt_bytes(envelope.copyOfRange(0, 40), nonce, keys))
        assertNull(InboundEnvelopeDecryptor.decrypt_bytes(envelope, ByteArray(11), keys))
        assertNull(InboundEnvelopeDecryptor.decrypt("not base64 at all!!", nonce, keys))
    }

    @Test
    fun header_lengths_match_the_backend_wire_layout() {
        assertEquals(66, InboundEnvelopeDecryptor.header_length(InboundEnvelopeDecryptor.ECDH_MARKER))
        assertEquals(
            66,
            InboundEnvelopeDecryptor.header_length(InboundEnvelopeDecryptor.ECDH_COMPRESSED_MARKER),
        )
        assertEquals(
            1154,
            InboundEnvelopeDecryptor.header_length(InboundEnvelopeDecryptor.PQ_HYBRID_MARKER),
        )
        assertNull(InboundEnvelopeDecryptor.header_length(0x01))
    }
}
