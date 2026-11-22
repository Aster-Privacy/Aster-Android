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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class InboundSealOpenerTest {

    private lateinit var legacy: JSONObject

    private lateinit var bound: JSONObject

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            val text = firstArg<String>()
            if (secondArg<Int>() and android.util.Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlDecoder().decode(text.trimEnd('='))
            } else {
                java.util.Base64.getDecoder().decode(text)
            }
        }
        legacy = load("inbound_envelope_vectors.json")
        bound = load("inbound_bound_envelope_vectors.json")
    }

    @After
    fun tear_down() {
        unmockkAll()
    }

    private fun load(name: String): JSONObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(name))
        return JSONObject(stream.bufferedReader().use { it.readText() })
    }

    private fun decode(value: String): ByteArray = java.util.Base64.getDecoder().decode(value)

    private fun b64url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun key_set(vectors: JSONObject): InboundRatchetKeySet {
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

    private fun key_sets() = listOf(key_set(bound), key_set(legacy))

    private fun owner() = bound.getString("bound_owner_user_id")

    private fun open(vectors: JSONObject, name: String): ByteArray? {
        val block = vectors.getJSONObject(name)
        return InboundSealOpener.open(
            block.getString("envelope_b64"),
            decode(block.getString("nonce_b64")),
            key_sets(),
            owner(),
        )
    }

    private fun plaintext(vectors: JSONObject) = vectors.getString("plaintext_utf8").toByteArray()

    @Test
    fun legacy_seals_stay_readable_after_a_bound_seal_was_opened() {
        assertArrayEquals(plaintext(bound), open(bound, "pq_hybrid_bound"))
        assertArrayEquals(plaintext(bound), open(bound, "ecdh_bound"))

        repeat(3) {
            assertArrayEquals(plaintext(legacy), open(legacy, "pq_hybrid"))
            assertArrayEquals(plaintext(legacy), open(legacy, "ecdh_compressed"))
        }
    }

    @Test
    fun legacy_and_bound_seals_open_in_any_order() {
        assertArrayEquals(plaintext(legacy), open(legacy, "pq_hybrid"))
        assertArrayEquals(plaintext(bound), open(bound, "pq_hybrid_bound"))
        assertArrayEquals(plaintext(legacy), open(legacy, "pq_hybrid"))
        assertArrayEquals(plaintext(bound), open(bound, "ecdh_bound"))
    }

    @Test
    fun a_bound_seal_still_refuses_another_account() {
        val block = bound.getJSONObject("pq_hybrid_bound")
        assertNull(
            InboundSealOpener.open(
                block.getString("envelope_b64"),
                decode(block.getString("nonce_b64")),
                key_sets(),
                "11111111-1111-1111-1111-111111111111",
            ),
        )
    }

    @Test
    fun a_bound_seal_relabeled_as_legacy_stays_refused() {
        val block = bound.getJSONObject("pq_hybrid_bound")
        val relabeled = decode(block.getString("envelope_b64")).also { it[0] = InboundEnvelopeDecryptor.PQ_HYBRID_MARKER }
        assertNull(
            InboundSealOpener.open(
                java.util.Base64.getEncoder().encodeToString(relabeled),
                decode(block.getString("nonce_b64")),
                key_sets(),
                owner(),
            ),
        )
    }
}
