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

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceEnvelopeWebVectorTest {

    private val vector: Map<String, String> = javaClass.classLoader!!
        .getResourceAsStream("device_envelope_web_vector.txt")!!
        .bufferedReader()
        .readLines()
        .filter { it.isNotBlank() }
        .associate { line ->
            val index = line.indexOf('=')
            line.substring(0, index) to line.substring(index + 1)
        }

    private fun bytes(key: String) = DeviceEnvelope.base64url_decode(vector.getValue(key))

    private fun recipient(ed25519_seed: Byte) = DeviceEnvelope.DeviceRecipient(
        ed25519_pk = Ed25519PrivateKeyParameters(ByteArray(32) { ed25519_seed }, 0)
            .generatePublicKey()
            .encoded,
        mlkem_pk = bytes("mlkem_pk"),
        x25519_pk = bytes("x25519_pk"),
    )

    private fun open(envelope: ByteArray, ed25519_seed: Byte = 0x11) =
        DeviceEnvelope.open_secret_for_device(
            envelope,
            recipient(ed25519_seed),
            bytes("mlkem_decaps"),
            bytes("x25519_scalar"),
        )

    @Test
    fun the_vector_signing_key_matches_the_other_clients() {
        assertArrayEquals(bytes("ed25519_pk"), recipient(0x11).ed25519_pk)
    }

    @Test
    fun an_envelope_sealed_by_the_web_client_opens_here() {
        for (key in listOf("envelope", "envelope_v2")) {
            assertEquals(vector.getValue("plaintext"), String(open(bytes(key)), Charsets.UTF_8))
        }
    }

    @Test
    fun the_v2_vector_is_bound_to_the_signing_key() {
        assertThrows(Exception::class.java) { open(bytes("envelope_v2"), 0x12) }
        assertEquals(
            vector.getValue("plaintext"),
            String(open(bytes("envelope"), 0x12), Charsets.UTF_8),
        )
    }

    @Test
    fun the_v2_vector_with_its_header_stripped_fails_to_open() {
        val v2 = bytes("envelope_v2")

        assertThrows(Exception::class.java) { open(v2.copyOfRange(4, v2.size)) }
    }

    @Test
    fun an_envelope_sealed_here_uses_the_layout_the_desktop_app_expects() {
        val plaintext = vector.getValue("plaintext").toByteArray(Charsets.UTF_8)

        for (version in DeviceEnvelope.ENVELOPE_VERSIONS) {
            val sealed = DeviceEnvelope.seal_secret_for_device(
                secret = plaintext,
                recipient = recipient(0x11),
                version = version,
            )
            val header = if (version == DeviceEnvelope.ENVELOPE_V2) 4 else 0

            assertEquals(
                header + DeviceEnvelope.MIN_ENVELOPE_BYTES + plaintext.size,
                sealed.size,
            )
            assertArrayEquals(plaintext, open(sealed))
        }
    }
}
