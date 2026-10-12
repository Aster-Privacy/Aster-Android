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
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class X3dhSenderRoundTripTest {

    private lateinit var sender_identity: RatchetCrypto.EcKeyPair
    private lateinit var receiver_identity: RatchetCrypto.EcKeyPair
    private lateinit var receiver_spk: RatchetCrypto.EcKeyPair
    private lateinit var kem: RatchetCrypto.MlKemKeyPair

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            val flags = secondArg<Int>()
            if (flags and android.util.Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
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

        sender_identity = RatchetCrypto.generate_p256_keypair()
        receiver_identity = RatchetCrypto.generate_p256_keypair()
        receiver_spk = RatchetCrypto.generate_p256_keypair()
        kem = RatchetCrypto.ml_kem_768_generate_keypair()
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun to_private_jwk(kp: RatchetCrypto.EcKeyPair): String {
        val d = RatchetCrypto.private_to_raw_d(kp.private_key)
        val x = kp.public_raw.copyOfRange(1, 33)
        val y = kp.public_raw.copyOfRange(33, 65)
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"${RatchetCrypto.b64url_encode(x)}\",\"y\":\"${RatchetCrypto.b64url_encode(y)}\",\"d\":\"${RatchetCrypto.b64url_encode(d)}\"}"
    }

    private fun send(
        version: Int,
        pq_prekey: Pair<Int, ByteArray>? = null,
        pq_identity: ByteArray? = null,
        signature_covers_pq_identity: Boolean = false,
    ): X3dh.SenderResult = X3dh.perform_sender(
        sender_identity_jwk = to_private_jwk(sender_identity),
        recipient_identity_raw = receiver_identity.public_raw,
        recipient_signed_prekey_raw = receiver_spk.public_raw,
        recipient_pq_prekey = pq_prekey,
        recipient_pq_identity = pq_identity,
        signature_covers_pq_identity = signature_covers_pq_identity,
        x3dh_version = version,
    )

    private fun receive(sent: X3dh.SenderResult, version: Int?): ByteArray {
        val pq_shared = sent.pq_ciphertext?.let { RatchetCrypto.ml_kem_768_decapsulate(it, kem.secret_key) }
        return X3dh.perform_receiver(
            receiver_identity_jwk = to_private_jwk(receiver_identity),
            receiver_signed_prekey_jwk = to_private_jwk(receiver_spk),
            sender_identity_raw = sender_identity.public_raw,
            sender_ephemeral_raw = sent.ephemeral_public_raw,
            pq_shared_secret = pq_shared,
            pq_from_identity = sent.pq_mode == X3dh.PqMode.IDENTITY,
            x3dh_version = version,
            pq_ciphertext = sent.pq_ciphertext,
        )
    }

    @Test
    fun negotiation_picks_transcript_binding_only_when_advertised() {
        assertEquals(X3dh.VERSION_LEGACY, X3dh.negotiated_sender_version(null))
        assertEquals(X3dh.VERSION_LEGACY, X3dh.negotiated_sender_version(0))
        assertEquals(X3dh.VERSION_LEGACY, X3dh.negotiated_sender_version(1))
        assertEquals(X3dh.VERSION_TRANSCRIPT_BOUND, X3dh.negotiated_sender_version(2))
        assertEquals(X3dh.VERSION_TRANSCRIPT_BOUND, X3dh.negotiated_sender_version(3))
    }

    @Test
    fun classical_legacy_round_trips() {
        val sent = send(X3dh.VERSION_LEGACY)
        assertEquals(X3dh.VERSION_LEGACY, sent.x3dh_version)
        assertArrayEquals(sent.shared_secret, receive(sent, null))
        assertArrayEquals(sent.shared_secret, receive(sent, X3dh.VERSION_LEGACY))
    }

    @Test
    fun classical_transcript_bound_round_trips() {
        val sent = send(X3dh.VERSION_TRANSCRIPT_BOUND)
        assertEquals(X3dh.VERSION_TRANSCRIPT_BOUND, sent.x3dh_version)
        assertArrayEquals(sent.shared_secret, receive(sent, X3dh.VERSION_TRANSCRIPT_BOUND))
        assertFalse(sent.shared_secret.contentEquals(receive(sent, X3dh.VERSION_LEGACY)))
    }

    @Test
    fun post_quantum_onetime_round_trips_in_both_versions() {
        for (version in listOf(X3dh.VERSION_LEGACY, X3dh.VERSION_TRANSCRIPT_BOUND)) {
            val sent = send(version, pq_prekey = 7 to kem.public_key)
            assertNotNull(sent.pq_ciphertext)
            assertEquals(X3dh.PqMode.ONETIME, sent.pq_mode)
            assertEquals(version, sent.x3dh_version)
            assertArrayEquals(sent.shared_secret, receive(sent, version))
        }
    }

    @Test
    fun post_quantum_identity_round_trips_in_both_versions() {
        for (version in listOf(X3dh.VERSION_LEGACY, X3dh.VERSION_TRANSCRIPT_BOUND)) {
            val sent = send(version, pq_identity = kem.public_key, signature_covers_pq_identity = true)
            assertEquals(X3dh.PqMode.IDENTITY, sent.pq_mode)
            assertEquals(X3dh.PQ_IDENTITY_KEY_ID, sent.pq_key_id)
            assertArrayEquals(sent.shared_secret, receive(sent, version))
        }
    }

    @Test
    fun transcript_bound_post_quantum_rejects_a_legacy_receiver() {
        val sent = send(X3dh.VERSION_TRANSCRIPT_BOUND, pq_prekey = 7 to kem.public_key)
        assertFalse(sent.shared_secret.contentEquals(receive(sent, X3dh.VERSION_LEGACY)))
    }
}
