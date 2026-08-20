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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

    @Test
    fun an_envelope_sealed_by_the_web_client_opens_here() {
        val opened = DeviceEnvelope.open_secret_for_device(
            bytes("envelope"),
            bytes("mlkem_decaps"),
            bytes("x25519_scalar"),
        )

        assertEquals(vector.getValue("plaintext"), String(opened, Charsets.UTF_8))
    }

    @Test
    fun an_envelope_sealed_here_uses_the_layout_the_desktop_app_expects() {
        val plaintext = vector.getValue("plaintext").toByteArray(Charsets.UTF_8)
        val sealed = DeviceEnvelope.seal_secret_for_device(
            secret = plaintext,
            device_mlkem_pk = bytes("mlkem_pk"),
            device_x25519_pk = bytes("x25519_pk"),
        )

        assertEquals(
            DeviceEnvelope.MIN_ENVELOPE_BYTES + plaintext.size,
            sealed.size,
        )
        assertArrayEquals(
            plaintext,
            DeviceEnvelope.open_secret_for_device(
                sealed,
                bytes("mlkem_decaps"),
                bytes("x25519_scalar"),
            ),
        )
    }
}
