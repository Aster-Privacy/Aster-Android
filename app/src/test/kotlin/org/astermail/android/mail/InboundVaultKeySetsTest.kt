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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InboundVaultKeySetsTest {

    private lateinit var vectors: JSONObject

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("inbound_envelope_vectors.json"))
        vectors = JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @After
    fun tear_down() {
        unmockkAll()
    }

    private fun decode(value: String): ByteArray = java.util.Base64.getDecoder().decode(value)

    private fun b64url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun recipient_jwk(): String {
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

    private fun stale_jwk(): String {
        val pair = java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val priv = pair.private as java.security.interfaces.ECPrivateKey
        val pub = pair.public as java.security.interfaces.ECPublicKey
        fun fixed(value: java.math.BigInteger): ByteArray {
            val raw = value.toByteArray()
            return ByteArray(32).also { out ->
                val take = minOf(raw.size, 32)
                System.arraycopy(raw, raw.size - take, out, 32 - take, take)
            }
        }
        return JSONObject()
            .put("kty", "EC")
            .put("crv", "P-256")
            .put("d", b64url(fixed(priv.s)))
            .put("x", b64url(fixed(pub.w.affineX)))
            .put("y", b64url(fixed(pub.w.affineY)))
            .toString()
    }

    private fun stale_pq_secret(): String = java.util.Base64.getEncoder().encodeToString(
        org.astermail.android.crypto.ratchet.RatchetCrypto.ml_kem_768_generate_keypair().secret_key,
    )

    private fun no_seed(seed: String): String? = null

    private fun seed_to_secret(seed: String): String? =
        if (seed == vectors.getString("recipient_ml_kem768_seed_b64")) {
            vectors.getString("recipient_ml_kem768_decapsulation_key_b64")
        } else {
            null
        }

    private fun open(name: String, key_sets: List<InboundRatchetKeySet>): ByteArray? {
        val block = vectors.getJSONObject(name)
        return InboundSealOpener.open(
            block.getString("envelope_b64"),
            decode(block.getString("nonce_b64")),
            key_sets,
            null,
        )
    }

    private fun plaintext() = vectors.getString("plaintext_utf8").toByteArray()

    @Test
    fun drifted_local_keys_open_new_mail_through_the_vault_keys() {
        val local = listOf(InboundRatchetKeySet(stale_jwk(), stale_pq_secret()))
        assertNull(open("pq_hybrid", local))

        val vault = JSONObject()
            .put("ratchet_identity_key", recipient_jwk())
            .put("ratchet_pq_identity_key", vectors.getString("recipient_ml_kem768_decapsulation_key_b64"))
        val fallback = fallback_inbound_key_sets(local, vault_inbound_key_sets(vault, ::no_seed))

        assertArrayEquals(plaintext(), open("pq_hybrid", fallback))
        assertArrayEquals(plaintext(), open("ecdh_compressed", fallback))
    }

    @Test
    fun a_drifted_pq_secret_alone_is_covered_by_the_vault_secret() {
        val local = listOf(InboundRatchetKeySet(recipient_jwk(), stale_pq_secret()))
        assertNull(open("pq_hybrid", local))

        val vault = JSONObject()
            .put("ratchet_identity_key", recipient_jwk())
            .put("ratchet_pq_identity_key", vectors.getString("recipient_ml_kem768_decapsulation_key_b64"))

        assertArrayEquals(
            plaintext(),
            open("pq_hybrid", fallback_inbound_key_sets(local, vault_inbound_key_sets(vault, ::no_seed))),
        )
    }

    @Test
    fun vault_previous_keys_with_only_a_seed_are_used() {
        val local = listOf(InboundRatchetKeySet(stale_jwk(), stale_pq_secret()))
        val vault = JSONObject()
            .put("ratchet_identity_key", stale_jwk())
            .put(
                "ratchet_previous_keys",
                JSONArray().put(
                    JSONObject()
                        .put("ratchet_identity_key", recipient_jwk())
                        .put("ratchet_pq_identity_seed", vectors.getString("recipient_ml_kem768_seed_b64")),
                ),
            )

        assertArrayEquals(
            plaintext(),
            open("pq_hybrid", fallback_inbound_key_sets(local, vault_inbound_key_sets(vault, ::seed_to_secret))),
        )
    }

    @Test
    fun matching_vault_keys_add_no_second_attempt() {
        val local = listOf(
            InboundRatchetKeySet(recipient_jwk(), vectors.getString("recipient_ml_kem768_decapsulation_key_b64")),
        )
        val vault = JSONObject()
            .put("ratchet_identity_key", local[0].identity_jwk)
            .put("ratchet_pq_identity_key", local[0].pq_identity_secret_b64)

        assertTrue(fallback_inbound_key_sets(local, vault_inbound_key_sets(vault, ::no_seed)).isEmpty())
    }

    @Test
    fun local_key_sets_keep_the_current_key_first_and_skip_empty_entries() {
        val previous = JSONArray()
            .put(JSONObject())
            .put(JSONObject().put("ratchet_identity_key", "old").put("ratchet_pq_identity_seed", "seed"))
        val sets = inbound_key_sets_from("current", " ", previous.toString()) { "expanded-$it" }

        assertEquals(
            listOf(InboundRatchetKeySet("current", null), InboundRatchetKeySet("old", "expanded-seed")),
            sets,
        )
    }
}
