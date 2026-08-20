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
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEnvelopeTest {

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
        val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
        val device_x25519_sk = RatchetCrypto.random_bytes(32)
        val device_x25519_pk = X25519PrivateKeyParameters(device_x25519_sk, 0)
            .generatePublicKey()
            .encoded
        val plaintext = "correct horse battery staple".toByteArray(Charsets.UTF_8)

        val envelope = DeviceEnvelope.seal_secret_for_device(
            secret = plaintext,
            device_mlkem_pk = mlkem.public_key,
            device_x25519_pk = device_x25519_pk,
        )

        assertEquals(
            DeviceEnvelope.X25519_PK_BYTES +
                DeviceEnvelope.MLKEM_768_CT_BYTES +
                DeviceEnvelope.XNONCE_BYTES +
                plaintext.size +
                DeviceEnvelope.POLY1305_TAG_BYTES,
            envelope.size,
        )
        assertTrue(envelope.size >= DeviceEnvelope.MIN_ENVELOPE_BYTES)
        assertArrayEquals(
            plaintext,
            DeviceEnvelope.open_secret_for_device(envelope, mlkem.secret_key, device_x25519_sk),
        )
    }

    @Test
    fun seal_is_randomized_per_call() {
        val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
        val device_x25519_sk = RatchetCrypto.random_bytes(32)
        val device_x25519_pk = X25519PrivateKeyParameters(device_x25519_sk, 0)
            .generatePublicKey()
            .encoded
        val plaintext = "pw".toByteArray(Charsets.UTF_8)

        val first = DeviceEnvelope.seal_secret_for_device(plaintext, mlkem.public_key, device_x25519_pk)
        val second = DeviceEnvelope.seal_secret_for_device(plaintext, mlkem.public_key, device_x25519_pk)

        assertNotEquals(to_hex(first), to_hex(second))
    }

    @Test
    fun seal_rejects_wrong_sized_device_keys() {
        val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
        val plaintext = "pw".toByteArray(Charsets.UTF_8)

        assertThrows(DeviceEnvelope.InvalidDeviceKeyException::class.java) {
            DeviceEnvelope.seal_secret_for_device(
                plaintext,
                ByteArray(DeviceEnvelope.MLKEM_768_PK_BYTES - 1),
                ByteArray(32),
            )
        }
        assertThrows(DeviceEnvelope.InvalidDeviceKeyException::class.java) {
            DeviceEnvelope.seal_secret_for_device(plaintext, mlkem.public_key, ByteArray(31))
        }
    }

    @Test
    fun tampered_envelope_fails_to_open() {
        val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
        val device_x25519_sk = RatchetCrypto.random_bytes(32)
        val device_x25519_pk = X25519PrivateKeyParameters(device_x25519_sk, 0)
            .generatePublicKey()
            .encoded

        val envelope = DeviceEnvelope.seal_secret_for_device(
            "hunter2".toByteArray(Charsets.UTF_8),
            mlkem.public_key,
            device_x25519_pk,
        )
        envelope[envelope.size - 1] = (envelope[envelope.size - 1].toInt() xor 0x01).toByte()

        assertThrows(Exception::class.java) {
            DeviceEnvelope.open_secret_for_device(envelope, mlkem.secret_key, device_x25519_sk)
        }
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
