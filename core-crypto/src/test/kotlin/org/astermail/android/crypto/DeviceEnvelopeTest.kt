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

import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEnvelopeTest {

    private class TestDevice {
        val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
        val x25519_sk: ByteArray = RatchetCrypto.random_bytes(32)
        val recipient = DeviceEnvelope.DeviceRecipient(
            ed25519_pk = Ed25519PrivateKeyParameters(RatchetCrypto.random_bytes(32), 0)
                .generatePublicKey()
                .encoded,
            mlkem_pk = mlkem.public_key,
            x25519_pk = X25519PrivateKeyParameters(x25519_sk, 0).generatePublicKey().encoded,
        )

        fun open(envelope: ByteArray, as_recipient: DeviceEnvelope.DeviceRecipient = recipient) =
            DeviceEnvelope.open_secret_for_device(envelope, as_recipient, mlkem.secret_key, x25519_sk)
    }

    private fun from_hex(value: String): ByteArray {
        val out = ByteArray(value.length / 2)
        for (i in out.indices) {
            out[i] = value.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    private fun to_hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun xchacha20_poly1305_matches_reference_vector() {
        val key = from_hex(
            "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f",
        )
        val nonce = from_hex("404142434445464748494a4b4c4d4e4f5051525354555657")
        val plaintext = (
            "Ladies and Gentlemen of the class of '99: If I could offer you " +
                "only one tip for the future, sunscreen would be it."
            ).toByteArray(Charsets.UTF_8)
        val expected =
            "bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb" +
                "731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452" +
                "2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9" +
                "21f9664c97637da9768812f615c68b13b52ef7e62efbf45089db18f9c8a3f0e4" +
                "1e5f"

        val ciphertext = DeviceEnvelope.xchacha20_poly1305_encrypt(key, nonce, plaintext)

        assertEquals(expected, to_hex(ciphertext))
        assertArrayEquals(
            plaintext,
            DeviceEnvelope.xchacha20_poly1305_decrypt(key, nonce, ciphertext),
        )
    }

    @Test
    fun hkdf_matches_reference_vector() {
        val ikm = from_hex(
            "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff" +
                "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100",
        )
        val salt = from_hex("404142434445464748494a4b4c4d4e4f5051525354555657")
        val info = "astermail-device-enroll-v1".toByteArray(Charsets.UTF_8)

        assertEquals(
            "300248132e489bb8cf1bc1beeb1528a1bc160fa8f48210426408bf32ec2cdbb1",
            to_hex(hkdf_sha256(ikm, salt, info, 32)),
        )
    }

    @Test
    fun seal_round_trips_through_device_keys() {
        val plaintext = "correct horse battery staple".toByteArray(Charsets.UTF_8)

        for (version in DeviceEnvelope.ENVELOPE_VERSIONS) {
            val device = TestDevice()
            val envelope = DeviceEnvelope.seal_secret_for_device(
                secret = plaintext,
                recipient = device.recipient,
                version = version,
            )
            val header = if (version == DeviceEnvelope.ENVELOPE_V2) 4 else 0

            assertEquals(
                header +
                    DeviceEnvelope.X25519_PK_BYTES +
                    DeviceEnvelope.MLKEM_768_CT_BYTES +
                    DeviceEnvelope.XNONCE_BYTES +
                    plaintext.size +
                    DeviceEnvelope.POLY1305_TAG_BYTES,
                envelope.size,
            )
            assertEquals(
                version == DeviceEnvelope.ENVELOPE_V2,
                String(envelope.copyOfRange(0, 4), Charsets.US_ASCII) == "AEV2",
            )
            assertTrue(envelope.size >= DeviceEnvelope.MIN_ENVELOPE_BYTES)
            assertArrayEquals(plaintext, device.open(envelope))
        }
    }

    @Test
    fun seal_is_randomized_per_call() {
        val device = TestDevice()
        val plaintext = "pw".toByteArray(Charsets.UTF_8)

        for (version in DeviceEnvelope.ENVELOPE_VERSIONS) {
            val first = DeviceEnvelope.seal_secret_for_device(plaintext, device.recipient, version)
            val second = DeviceEnvelope.seal_secret_for_device(plaintext, device.recipient, version)

            assertNotEquals(to_hex(first), to_hex(second))
        }
    }

    @Test
    fun seal_rejects_wrong_sized_device_keys() {
        val device = TestDevice()
        val plaintext = "pw".toByteArray(Charsets.UTF_8)
        val bad_recipients = listOf(
            DeviceEnvelope.DeviceRecipient(
                device.recipient.ed25519_pk,
                ByteArray(DeviceEnvelope.MLKEM_768_PK_BYTES - 1),
                device.recipient.x25519_pk,
            ),
            DeviceEnvelope.DeviceRecipient(
                device.recipient.ed25519_pk,
                device.recipient.mlkem_pk,
                ByteArray(31),
            ),
            DeviceEnvelope.DeviceRecipient(
                ByteArray(31),
                device.recipient.mlkem_pk,
                device.recipient.x25519_pk,
            ),
        )

        for (version in DeviceEnvelope.ENVELOPE_VERSIONS) {
            for (recipient in bad_recipients) {
                assertThrows(DeviceEnvelope.InvalidDeviceKeyException::class.java) {
                    DeviceEnvelope.seal_secret_for_device(plaintext, recipient, version)
                }
            }
        }
    }

    @Test
    fun seal_rejects_an_unsupported_version() {
        val device = TestDevice()

        for (version in listOf(0, 3)) {
            assertThrows(IllegalArgumentException::class.java) {
                DeviceEnvelope.seal_secret_for_device(
                    "pw".toByteArray(Charsets.UTF_8),
                    device.recipient,
                    version,
                )
            }
        }
    }

    @Test
    fun tampered_envelope_fails_to_open() {
        for (version in DeviceEnvelope.ENVELOPE_VERSIONS) {
            val device = TestDevice()
            val envelope = DeviceEnvelope.seal_secret_for_device(
                "hunter2".toByteArray(Charsets.UTF_8),
                device.recipient,
                version,
            )
            val header = if (version == DeviceEnvelope.ENVELOPE_V2) 4 else 0
            val indices = listOf(
                0,
                header,
                header + DeviceEnvelope.X25519_PK_BYTES,
                header + DeviceEnvelope.MIN_ENVELOPE_BYTES - DeviceEnvelope.POLY1305_TAG_BYTES - 1,
                envelope.size - 1,
            )

            for (index in indices) {
                val tampered = envelope.copyOf()
                tampered[index] = (tampered[index].toInt() xor 0x01).toByte()

                assertThrows("byte $index", Exception::class.java) { device.open(tampered) }
            }
        }
    }

    @Test
    fun v2_envelope_is_bound_to_the_device_signing_key() {
        val device = TestDevice()
        val plaintext = "pw".toByteArray(Charsets.UTF_8)
        val swapped = DeviceEnvelope.DeviceRecipient(
            ed25519_pk = Ed25519PrivateKeyParameters(RatchetCrypto.random_bytes(32), 0)
                .generatePublicKey()
                .encoded,
            mlkem_pk = device.recipient.mlkem_pk,
            x25519_pk = device.recipient.x25519_pk,
        )
        val v2 = DeviceEnvelope.seal_secret_for_device(
            plaintext,
            device.recipient,
            DeviceEnvelope.ENVELOPE_V2,
        )
        val v1 = DeviceEnvelope.seal_secret_for_device(
            plaintext,
            device.recipient,
            DeviceEnvelope.ENVELOPE_V1,
        )

        assertThrows(Exception::class.java) { device.open(v2, swapped) }
        assertArrayEquals(plaintext, device.open(v1, swapped))
    }

    @Test
    fun v2_envelope_with_its_header_stripped_fails_to_open() {
        val device = TestDevice()
        val v2 = DeviceEnvelope.seal_secret_for_device(
            "pw".toByteArray(Charsets.UTF_8),
            device.recipient,
            DeviceEnvelope.ENVELOPE_V2,
        )

        assertThrows(Exception::class.java) { device.open(v2.copyOfRange(4, v2.size)) }
    }

    @Test
    fun envelope_for_another_device_fails_to_open() {
        val device = TestDevice()
        val other = TestDevice()

        for (version in DeviceEnvelope.ENVELOPE_VERSIONS) {
            val envelope = DeviceEnvelope.seal_secret_for_device(
                "pw".toByteArray(Charsets.UTF_8),
                device.recipient,
                version,
            )

            assertThrows(Exception::class.java) { other.open(envelope) }
        }
    }

    @Test
    fun short_envelope_fails_to_open() {
        val device = TestDevice()

        for (size in listOf(0, 3, 4 + DeviceEnvelope.MIN_ENVELOPE_BYTES - 1)) {
            val short = ByteArray(size)
            if (size >= 4) "AEV2".toByteArray(Charsets.US_ASCII).copyInto(short)

            assertThrows(IllegalArgumentException::class.java) { device.open(short) }
        }
    }

    @Test
    fun choose_envelope_version_prefers_v2_only_when_offered() {
        assertEquals(2, DeviceEnvelope.choose_envelope_version(listOf(1, 2)))
        assertEquals(2, DeviceEnvelope.choose_envelope_version(listOf(2)))
        assertEquals(1, DeviceEnvelope.choose_envelope_version(listOf(1)))
        assertEquals(1, DeviceEnvelope.choose_envelope_version(emptyList()))
        assertEquals(1, DeviceEnvelope.choose_envelope_version(null))
        assertEquals(1, DeviceEnvelope.choose_envelope_version(listOf(3)))
    }

    @Test
    fun base64url_round_trips_without_padding() {
        val bytes = RatchetCrypto.random_bytes(101)
        val encoded = DeviceEnvelope.base64url_encode(bytes)

        assertTrue(!encoded.contains("=") && !encoded.contains("+") && !encoded.contains("/"))
        assertArrayEquals(bytes, DeviceEnvelope.base64url_decode(encoded))
        assertArrayEquals(bytes, DeviceEnvelope.base64url_decode("$encoded=="))
    }
}
