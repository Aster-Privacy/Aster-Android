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

package org.astermail.android.mail.ratchet

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class X3dhV2VectorTest {

    private lateinit var vectors: JSONObject

    private fun b64(name: String): ByteArray =
        java.util.Base64.getDecoder().decode(vectors.getString(name))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun derive(version: Int?, pq_shared: ByteArray?, from_identity: Boolean): String =
        hex(
            X3dh.perform_receiver(
                receiver_identity_jwk = vectors.getString("receiver_identity_jwk"),
                receiver_signed_prekey_jwk = vectors.getString("receiver_signed_prekey_jwk"),
                sender_identity_raw = b64("sender_identity_raw"),
                sender_ephemeral_raw = b64("sender_ephemeral_raw"),
                pq_shared_secret = pq_shared,
                pq_from_identity = from_identity,
                x3dh_version = version,
                pq_ciphertext = b64("kem_ciphertext"),
            ),
        )

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
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
            javaClass.classLoader?.getResourceAsStream("x3dh_v2_vectors.json"),
        )
        vectors = JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    @Test
    fun identity_key_derivation_matches_the_shared_public_key() {
        val derived = RatchetCrypto.p256_public_raw_from_private_jwk(
            vectors.getString("receiver_identity_jwk"),
        )

        assertEquals(
            java.util.Base64.getEncoder().encodeToString(derived),
            vectors.getString("receiver_identity_raw"),
        )
    }

    @Test
    fun kem_material_matches_the_other_clients() {
        val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(b64("kem_seed"))
        val shared = RatchetCrypto.ml_kem_768_decapsulate(b64("kem_ciphertext"), pair.secret_key)

        assertEquals(vectors.getString("kem_shared_secret"), hex(shared))
    }

    @Test
    fun legacy_classical_secret_matches() {
        assertEquals(
            vectors.getString("classical_v1"),
            derive(X3dh.VERSION_LEGACY, null, false),
        )
    }

    @Test
    fun absent_version_is_treated_as_legacy() {
        assertEquals(vectors.getString("classical_v1"), derive(null, null, false))
    }

    @Test
    fun transcript_bound_classical_secret_matches() {
        assertEquals(
            vectors.getString("classical_v2"),
            derive(X3dh.VERSION_TRANSCRIPT_BOUND, null, false),
        )
    }

    @Test
    fun legacy_post_quantum_secret_matches() {
        val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(b64("kem_seed"))
        val shared = RatchetCrypto.ml_kem_768_decapsulate(b64("kem_ciphertext"), pair.secret_key)

        assertEquals(
            vectors.getString("pq_v1"),
            derive(X3dh.VERSION_LEGACY, shared, false),
        )
    }

    @Test
    fun transcript_bound_post_quantum_secret_matches() {
        val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(b64("kem_seed"))
        val shared = RatchetCrypto.ml_kem_768_decapsulate(b64("kem_ciphertext"), pair.secret_key)

        assertEquals(
            vectors.getString("pq_v2"),
            derive(X3dh.VERSION_TRANSCRIPT_BOUND, shared, false),
        )
    }

    @Test
    fun transcript_bound_identity_lane_secret_matches() {
        val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(b64("kem_seed"))
        val shared = RatchetCrypto.ml_kem_768_decapsulate(b64("kem_ciphertext"), pair.secret_key)

        assertEquals(
            vectors.getString("pq_identity_v2"),
            derive(X3dh.VERSION_TRANSCRIPT_BOUND, shared, true),
        )
    }

    @Test
    fun every_version_and_lane_is_domain_separated() {
        val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(b64("kem_seed"))
        val shared = RatchetCrypto.ml_kem_768_decapsulate(b64("kem_ciphertext"), pair.secret_key)

        val derived = listOf(
            derive(X3dh.VERSION_LEGACY, null, false),
            derive(X3dh.VERSION_TRANSCRIPT_BOUND, null, false),
            derive(X3dh.VERSION_LEGACY, shared, false),
            derive(X3dh.VERSION_TRANSCRIPT_BOUND, shared, false),
            derive(X3dh.VERSION_TRANSCRIPT_BOUND, shared, true),
        )

        assertEquals(derived.size, derived.toSet().size)
        assertNotEquals(derived[0], derived[1])
    }
}
