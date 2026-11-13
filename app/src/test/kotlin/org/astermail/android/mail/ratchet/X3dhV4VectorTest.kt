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
import kotlinx.serialization.json.Json
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class X3dhV4VectorTest {

    private lateinit var vectors: JSONObject

    private val wire_json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    private fun b64(name: String): ByteArray =
        java.util.Base64.getDecoder().decode(vectors.getString(name))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun kem_shared(): ByteArray {
        val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(b64("kem_seed"))
        return RatchetCrypto.ml_kem_768_decapsulate(b64("kem_ciphertext"), pair.secret_key)
    }

    private fun derive(
        version: Int?,
        pq_shared: ByteArray?,
        from_identity: Boolean,
        one_time_jwk: String? = vectors.getString("receiver_one_time_prekey_jwk"),
        one_time_id: Int? = vectors.getInt("one_time_prekey_id"),
    ): String = hex(
        X3dh.perform_receiver(
            receiver_identity_jwk = vectors.getString("receiver_identity_jwk"),
            receiver_signed_prekey_jwk = vectors.getString("receiver_signed_prekey_jwk"),
            sender_identity_raw = b64("sender_identity_raw"),
            sender_ephemeral_raw = b64("sender_ephemeral_raw"),
            pq_shared_secret = pq_shared,
            pq_from_identity = from_identity,
            x3dh_version = version,
            pq_ciphertext = b64("kem_ciphertext"),
            one_time_prekey_jwk = one_time_jwk,
            one_time_prekey_id = one_time_id,
        ),
    )

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

        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("x3dh_v4_vectors.json"),
        )
        vectors = JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    @Test
    fun one_time_prekey_derivation_matches_the_shared_public_key() {
        val derived = RatchetCrypto.p256_public_raw_from_private_jwk(
            vectors.getString("receiver_one_time_prekey_jwk"),
        )

        assertEquals(
            vectors.getString("receiver_one_time_prekey_raw"),
            java.util.Base64.getEncoder().encodeToString(derived),
        )
    }

    @Test
    fun fourth_agreement_matches_the_shared_value() {
        val dh4 = RatchetCrypto.ecdh(
            RatchetCrypto.parse_p256_private_jwk(vectors.getString("receiver_one_time_prekey_jwk")),
            RatchetCrypto.parse_p256_public_raw(b64("sender_ephemeral_raw")),
        )

        assertEquals(vectors.getString("dh4"), hex(dh4))
    }

    @Test
    fun one_time_prekey_id_encodes_as_four_big_endian_bytes() {
        assertEquals(
            vectors.getString("one_time_prekey_id_bytes"),
            hex(X3dh.encode_one_time_prekey_id(vectors.getInt("one_time_prekey_id"))),
        )
        assertEquals("7fffffff", hex(X3dh.encode_one_time_prekey_id(Int.MAX_VALUE)))
        assertEquals("00000001", hex(X3dh.encode_one_time_prekey_id(1)))
    }

    @Test
    fun one_time_prekey_id_range_is_enforced() {
        assertTrue(X3dh.is_valid_one_time_prekey_id(1))
        assertTrue(X3dh.is_valid_one_time_prekey_id(Int.MAX_VALUE))
        assertFalse(X3dh.is_valid_one_time_prekey_id(0))
        assertFalse(X3dh.is_valid_one_time_prekey_id(-1))
        assertFalse(X3dh.is_valid_one_time_prekey_id(null))
        assertThrows(IllegalArgumentException::class.java) { X3dh.encode_one_time_prekey_id(0) }
    }

    @Test
    fun signed_prekey_id_matches_the_shared_value() {
        assertEquals(
            vectors.getString("signed_prekey_id"),
            X3dh.signed_prekey_id(b64("receiver_signed_prekey_raw")),
        )
    }

    @Test
    fun one_time_bound_secrets_match_the_other_clients() {
        val shared = kem_shared()

        assertEquals(vectors.getString("classical_v4"), derive(X3dh.VERSION_ONE_TIME_BOUND, null, false))
        assertEquals(vectors.getString("pq_v4"), derive(X3dh.VERSION_ONE_TIME_BOUND, shared, false))
        assertEquals(vectors.getString("pq_identity_v4"), derive(X3dh.VERSION_ONE_TIME_BOUND, shared, true))
    }

    @Test
    fun earlier_versions_are_unchanged() {
        val shared = kem_shared()

        assertEquals(vectors.getString("classical_v1"), derive(null, null, false, null, null))
        assertEquals(vectors.getString("classical_v1"), derive(X3dh.VERSION_LEGACY, null, false, null, null))
        assertEquals(vectors.getString("pq_v1"), derive(X3dh.VERSION_LEGACY, shared, false, null, null))
        assertEquals(vectors.getString("classical_v2"), derive(X3dh.VERSION_TRANSCRIPT_BOUND, null, false, null, null))
        assertEquals(vectors.getString("pq_v2"), derive(X3dh.VERSION_TRANSCRIPT_BOUND, shared, false, null, null))
        assertEquals(vectors.getString("pq_identity_v2"), derive(X3dh.VERSION_TRANSCRIPT_BOUND, shared, true, null, null))
        assertEquals(vectors.getString("classical_v3"), derive(X3dh.VERSION_KEY_BOUND, null, false, null, null))
        assertEquals(vectors.getString("pq_v3"), derive(X3dh.VERSION_KEY_BOUND, shared, false, null, null))
        assertEquals(vectors.getString("pq_identity_v3"), derive(X3dh.VERSION_KEY_BOUND, shared, true, null, null))
    }

    @Test
    fun earlier_versions_ignore_a_supplied_one_time_prekey() {
        val shared = kem_shared()

        assertEquals(vectors.getString("classical_v1"), derive(X3dh.VERSION_LEGACY, null, false))
        assertEquals(vectors.getString("classical_v2"), derive(X3dh.VERSION_TRANSCRIPT_BOUND, null, false))
        assertEquals(vectors.getString("classical_v3"), derive(X3dh.VERSION_KEY_BOUND, null, false))
        assertEquals(vectors.getString("pq_v3"), derive(X3dh.VERSION_KEY_BOUND, shared, false))
        assertEquals(vectors.getString("pq_identity_v3"), derive(X3dh.VERSION_KEY_BOUND, shared, true))
    }

    @Test
    fun the_receiver_refuses_without_the_one_time_private_key() {
        assertThrows(IllegalArgumentException::class.java) {
            derive(X3dh.VERSION_ONE_TIME_BOUND, null, false, one_time_jwk = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            derive(X3dh.VERSION_ONE_TIME_BOUND, kem_shared(), true, one_time_jwk = null)
        }
    }

    @Test
    fun the_receiver_refuses_without_a_valid_one_time_prekey_id() {
        for (bad_id in listOf(null, 0, -5)) {
            assertThrows(IllegalArgumentException::class.java) {
                derive(X3dh.VERSION_ONE_TIME_BOUND, null, false, one_time_id = bad_id)
            }
        }
    }

    @Test
    fun the_receiver_refuses_a_malformed_sender_key() {
        assertThrows(Throwable::class.java) {
            X3dh.perform_receiver(
                receiver_identity_jwk = vectors.getString("receiver_identity_jwk"),
                receiver_signed_prekey_jwk = vectors.getString("receiver_signed_prekey_jwk"),
                sender_identity_raw = b64("sender_identity_raw").copyOf(64),
                sender_ephemeral_raw = b64("sender_ephemeral_raw"),
                x3dh_version = X3dh.VERSION_ONE_TIME_BOUND,
                one_time_prekey_jwk = vectors.getString("receiver_one_time_prekey_jwk"),
                one_time_prekey_id = vectors.getInt("one_time_prekey_id"),
            )
        }
    }

    @Test
    fun a_different_one_time_prekey_id_changes_the_secret() {
        val other = derive(X3dh.VERSION_ONE_TIME_BOUND, null, false, one_time_id = 16909061)

        assertFalse(other == vectors.getString("classical_v4"))
    }

    @Test
    fun an_unknown_version_above_four_is_treated_as_legacy() {
        assertEquals(vectors.getString("classical_v1"), derive(5, null, false))
        assertEquals(vectors.getString("classical_v1"), derive(99, null, false, null, null))
    }

    @Test
    fun the_write_ceiling_stays_below_the_one_time_bound_version() {
        assertEquals(3, X3dh.VERSION_MAX)
        assertEquals(4, X3dh.READ_VERSION_MAX)
        assertEquals(X3dh.VERSION_KEY_BOUND, X3dh.negotiated_sender_version(4))
        assertEquals(X3dh.VERSION_KEY_BOUND, X3dh.negotiated_sender_version(99))
        assertEquals(2, X3dh.receive_floor(4))
    }

    private fun sample_recipient(version: Int?, opk_id: Int?, spk_id: String?) = RatchetRecipientData(
        ephemeral_key = "ZXBo",
        header = MessageHeader(dh_public = "ZGg=", previous_chain_length = 0, message_number = 0),
        ciphertext = "Y3Q=",
        nonce = "bm9uY2U=",
        x3dh_v = version,
        opk_id = opk_id,
        spk_id = spk_id,
    )

    @Test
    fun wire_fields_are_absent_below_version_four() {
        for (version in listOf(null, 1, 2, 3)) {
            val encoded = wire_json.encodeToString(RatchetRecipientData.serializer(), sample_recipient(version, null, null))
            assertFalse(encoded.contains("opk_id"))
            assertFalse(encoded.contains("spk_id"))
        }
        val bootstrap = wire_json.encodeToString(BootstrapData.serializer(), BootstrapData(ephemeral_key = "ZXBo", x3dh_version = 3))
        assertFalse(bootstrap.contains("one_time_prekey_id"))
        assertFalse(bootstrap.contains("signed_prekey_id"))
    }

    @Test
    fun wire_fields_round_trip_at_version_four() {
        val encoded = wire_json.encodeToString(RatchetRecipientData.serializer(), sample_recipient(4, 16909060, "c5ea95322278e438"))
        val parsed = JSONObject(encoded)

        assertEquals(4, parsed.getInt("x3dh_v"))
        assertEquals(16909060, parsed.getInt("opk_id"))
        assertEquals("c5ea95322278e438", parsed.getString("spk_id"))

        val decoded = wire_json.decodeFromString(RatchetRecipientData.serializer(), encoded)
        assertEquals(16909060, decoded.opk_id)
        assertEquals("c5ea95322278e438", decoded.spk_id)
    }

    @Test
    fun older_wire_data_decodes_without_the_new_fields() {
        val decoded = wire_json.decodeFromString(
            RatchetRecipientData.serializer(),
            """{"header":{"dh_public":"ZGg=","previous_chain_length":0,"message_number":0},"ciphertext":"Y3Q=","nonce":"bm9uY2U=","x3dh_v":3}""",
        )

        assertNull(decoded.opk_id)
        assertNull(decoded.spk_id)
    }

    private class Party {
        val sender_identity = RatchetCrypto.generate_p256_keypair()
        val receiver_identity = RatchetCrypto.generate_p256_keypair()
        val receiver_spk = RatchetCrypto.generate_p256_keypair()
        val receiver_opk = RatchetCrypto.generate_p256_keypair()
        val kem = RatchetCrypto.ml_kem_768_generate_keypair()

        fun send(
            version: Int = X3dh.VERSION_KEY_BOUND,
            advertised: Int? = 4,
            one_time: Pair<Int, ByteArray>? = 77 to receiver_opk.public_raw,
            pq_prekey: Pair<Int, ByteArray>? = null,
            pq_identity: ByteArray? = null,
        ): X3dh.SenderResult = X3dh.perform_sender(
            sender_identity_jwk = RatchetCrypto.p256_private_to_jwk(sender_identity.private_key),
            recipient_identity_raw = receiver_identity.public_raw,
            recipient_signed_prekey_raw = receiver_spk.public_raw,
            recipient_pq_prekey = pq_prekey,
            recipient_pq_identity = pq_identity,
            signature_covers_pq_identity = pq_identity != null,
            x3dh_version = version,
            recipient_one_time_prekey = one_time,
            advertised_x3dh_version = advertised,
        )

        fun receive(sent: X3dh.SenderResult, with_one_time: Boolean = true): ByteArray {
            val pq_shared = sent.pq_ciphertext?.let { RatchetCrypto.ml_kem_768_decapsulate(it, kem.secret_key) }
            return X3dh.perform_receiver(
                receiver_identity_jwk = RatchetCrypto.p256_private_to_jwk(receiver_identity.private_key),
                receiver_signed_prekey_jwk = RatchetCrypto.p256_private_to_jwk(receiver_spk.private_key),
                sender_identity_raw = sender_identity.public_raw,
                sender_ephemeral_raw = sent.ephemeral_public_raw,
                pq_shared_secret = pq_shared,
                pq_from_identity = sent.pq_mode == X3dh.PqMode.IDENTITY,
                x3dh_version = sent.x3dh_version,
                pq_ciphertext = sent.pq_ciphertext,
                one_time_prekey_jwk = if (with_one_time) {
                    RatchetCrypto.p256_private_to_jwk(receiver_opk.private_key)
                } else {
                    null
                },
                one_time_prekey_id = sent.one_time_prekey_id,
            )
        }
    }

    @Test
    fun the_sender_writes_version_four_when_every_condition_holds() {
        val party = Party()
        val sent = party.send()

        assertEquals(X3dh.VERSION_ONE_TIME_BOUND, sent.x3dh_version)
        assertEquals(77, sent.one_time_prekey_id)
        assertEquals(X3dh.signed_prekey_id(party.receiver_spk.public_raw), sent.signed_prekey_id)
        assertArrayEquals(sent.shared_secret, party.receive(sent))
        assertThrows(IllegalArgumentException::class.java) { party.receive(sent, with_one_time = false) }
    }

    @Test
    fun version_four_round_trips_in_both_post_quantum_lanes() {
        val party = Party()

        val onetime = party.send(pq_prekey = 7 to party.kem.public_key)
        assertEquals(X3dh.VERSION_ONE_TIME_BOUND, onetime.x3dh_version)
        assertEquals(X3dh.PqMode.ONETIME, onetime.pq_mode)
        assertArrayEquals(onetime.shared_secret, party.receive(onetime))

        val identity = party.send(pq_identity = party.kem.public_key)
        assertEquals(X3dh.VERSION_ONE_TIME_BOUND, identity.x3dh_version)
        assertEquals(X3dh.PqMode.IDENTITY, identity.pq_mode)
        assertArrayEquals(identity.shared_secret, party.receive(identity))
    }

    private fun assert_stays_below_four(sent: X3dh.SenderResult, expected: Int, party: Party) {
        assertEquals(expected, sent.x3dh_version)
        assertNull(sent.one_time_prekey_id)
        assertNull(sent.signed_prekey_id)
        assertArrayEquals(sent.shared_secret, party.receive(sent, with_one_time = false))
    }

    @Test
    fun the_sender_stays_at_three_when_the_bundle_does_not_advertise_four() {
        val party = Party()

        assert_stays_below_four(party.send(advertised = 3), 3, party)
        assert_stays_below_four(party.send(advertised = null), 3, party)
    }

    @Test
    fun the_sender_stays_at_three_without_a_usable_one_time_prekey() {
        val party = Party()
        val off_curve = ByteArray(65) { if (it == 0) 0x04 else 0x01 }

        assert_stays_below_four(party.send(one_time = null), 3, party)
        assert_stays_below_four(party.send(one_time = 0 to party.receiver_opk.public_raw), 3, party)
        assert_stays_below_four(party.send(one_time = -3 to party.receiver_opk.public_raw), 3, party)
        assert_stays_below_four(party.send(one_time = 77 to party.receiver_opk.public_raw.copyOf(64)), 3, party)
        assert_stays_below_four(
            party.send(one_time = 77 to party.receiver_opk.public_raw.copyOf().also { it[0] = 0x02 }),
            3,
            party,
        )
        assert_stays_below_four(party.send(one_time = 77 to off_curve), 3, party)
    }

    @Test
    fun the_sender_never_reaches_four_without_transcript_binding() {
        val party = Party()

        assert_stays_below_four(party.send(version = X3dh.VERSION_LEGACY), 1, party)
        assert_stays_below_four(party.send(version = X3dh.VERSION_TRANSCRIPT_BOUND, advertised = 3), 2, party)
    }

    @Test
    fun the_sender_default_arguments_keep_the_earlier_behavior() {
        val party = Party()
        val sent = X3dh.perform_sender(
            sender_identity_jwk = RatchetCrypto.p256_private_to_jwk(party.sender_identity.private_key),
            recipient_identity_raw = party.receiver_identity.public_raw,
            recipient_signed_prekey_raw = party.receiver_spk.public_raw,
            x3dh_version = X3dh.VERSION_KEY_BOUND,
        )

        assert_stays_below_four(sent, 3, party)
    }
}
