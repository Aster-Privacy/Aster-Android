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

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InboundEnvelopeVectorsTest {

    private lateinit var vectors: JSONObject

    private val eph_key_len = 65
    private val ml_kem_ct_len = 1088
    private val ecdh_info = "aster-inbound-v1".toByteArray()
    private val pq_hybrid_info = "aster-inbound-pq-v1".toByteArray()

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
        unmockkStatic(android.util.Base64::class)
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

    private fun inflate_zlib(compressed: ByteArray): ByteArray {
        val inflater = java.util.zip.Inflater()
        try {
            inflater.setInput(compressed)
            val out = java.io.ByteArrayOutputStream(compressed.size)
            val buffer = ByteArray(1 shl 16)
            while (!inflater.finished()) {
                val produced = inflater.inflate(buffer)
                if (produced == 0 && !inflater.finished()) {
                    throw IllegalStateException("truncated zlib stream")
                }
                out.write(buffer, 0, produced)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun decrypt_ecdh(enc: ByteArray, nonce: ByteArray): ByteArray {
        val ephemeral_public = RatchetCrypto.parse_p256_public_raw(
            enc.copyOfRange(1, 1 + eph_key_len),
        )
        val identity_private = RatchetCrypto.parse_p256_private_jwk(recipient_identity_jwk())
        val shared_secret = RatchetCrypto.ecdh(identity_private, ephemeral_public)
        val aes_key = RatchetCrypto.hkdf_sha256(shared_secret, ByteArray(0), ecdh_info, 32)
        val plaintext = RatchetCrypto.aes_gcm_decrypt(
            enc.copyOfRange(1 + eph_key_len, enc.size),
            aes_key,
            nonce,
        )
        return inflate_zlib(plaintext)
    }

    private fun decrypt_pq_hybrid(enc: ByteArray, nonce: ByteArray): ByteArray {
        val ephemeral_public = RatchetCrypto.parse_p256_public_raw(
            enc.copyOfRange(1, 1 + eph_key_len),
        )
        val identity_private = RatchetCrypto.parse_p256_private_jwk(recipient_identity_jwk())
        val ecdh_shared = RatchetCrypto.ecdh(identity_private, ephemeral_public)

        val ml_kem_shared = RatchetCrypto.ml_kem_768_decapsulate(
            enc.copyOfRange(1 + eph_key_len, 1 + eph_key_len + ml_kem_ct_len),
            decode(vectors.getString("recipient_ml_kem768_decapsulation_key_b64")),
        )

        val ikm = ByteArray(64)
        System.arraycopy(ecdh_shared, 0, ikm, 0, 32)
        System.arraycopy(ml_kem_shared, 0, ikm, 32, 32)

        val aes_key = RatchetCrypto.hkdf_sha256(ikm, ByteArray(0), pq_hybrid_info, 32)
        val plaintext = RatchetCrypto.aes_gcm_decrypt(
            enc.copyOfRange(1 + eph_key_len + ml_kem_ct_len, enc.size),
            aes_key,
            nonce,
        )
        return inflate_zlib(plaintext)
    }

    @Test
    fun backend_wire_layout_matches_the_client_offsets() {
        val ecdh = decode(vectors.getJSONObject("ecdh_compressed").getString("envelope_b64"))
        val pq = decode(vectors.getJSONObject("pq_hybrid").getString("envelope_b64"))

        assertEquals(0x03, ecdh[0].toInt() and 0xff)
        assertEquals(0x04, ecdh[1].toInt() and 0xff)
        assertEquals(0x04, pq[0].toInt() and 0xff)
        assertEquals(0x04, pq[1].toInt() and 0xff)
        assertEquals(
            12,
            decode(vectors.getJSONObject("ecdh_compressed").getString("nonce_b64")).size,
        )
        assertTrue(pq.size > 1 + eph_key_len + ml_kem_ct_len + 16)
    }

    @Test
    fun decrypts_a_marker_0x03_envelope_produced_by_the_backend() {
        val block = vectors.getJSONObject("ecdh_compressed")
        val plain = decrypt_ecdh(
            decode(block.getString("envelope_b64")),
            decode(block.getString("nonce_b64")),
        )

        assertArrayEquals(vectors.getString("plaintext_utf8").toByteArray(), plain)
    }

    @Test
    fun decrypts_a_marker_0x04_envelope_produced_by_the_backend() {
        val block = vectors.getJSONObject("pq_hybrid")
        val plain = decrypt_pq_hybrid(
            decode(block.getString("envelope_b64")),
            decode(block.getString("nonce_b64")),
        )

        assertArrayEquals(vectors.getString("plaintext_utf8").toByteArray(), plain)
    }

    @Test(expected = Exception::class)
    fun rejects_an_envelope_whose_ciphertext_was_tampered_with() {
        val block = vectors.getJSONObject("ecdh_compressed")
        val tampered = decode(block.getString("envelope_b64"))

        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xff).toByte()

        decrypt_ecdh(tampered, decode(block.getString("nonce_b64")))
    }

    @Test
    fun empty_salt_hkdf_matches_the_backend_zero_salt_form() {
        val ikm = ByteArray(32) { it.toByte() }

        assertArrayEquals(
            RatchetCrypto.hkdf_sha256(ikm, ByteArray(0), ecdh_info, 32),
            RatchetCrypto.hkdf_sha256(ikm, ByteArray(32), ecdh_info, 32),
        )
    }
}
