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

package org.astermail.android.crypto.guard

import java.security.SecureRandom
import java.util.UUID
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GuardContextSealTest {
    private val request_id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
    private val device_id = UUID.fromString("a1a2a3a4-b1b2-c1c2-d1d2-e1e2e3e4e5e6")

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private val mlkem = RatchetCrypto.ml_kem_768_generate_keypair()
    private val x_sk = X25519PrivateKeyParameters(SecureRandom())
    private val x_sk_bytes = x_sk.encoded
    private val x_pk = x_sk.generatePublicKey().encoded

    @Test
    fun aad_vectors() {
        assertEquals(
            "61737465722d67756172642d636f6e746578742d763100112233445566778899aabbccddeeffa1a2a3a4b1b2c1c2d1d2e1e2e3e4e5e6",
            hex(GuardContextSeal.context_aad(request_id, device_id)),
        )
        assertEquals(
            "61737465722d67756172642d636f64652d7365637265742d7631a1a2a3a4b1b2c1c2d1d2e1e2e3e4e5e6",
            hex(GuardContextSeal.code_secret_aad(device_id)),
        )
    }

    @Test
    fun seal_round_trip() {
        val aad = GuardContextSeal.context_aad(request_id, device_id)
        val plain = "context".toByteArray()
        val sealed = GuardContextSeal.seal(plain, aad, x_pk, mlkem.public_key)
        assertEquals(GuardContextSeal.HEADER_LEN + plain.size + 16, sealed.size)
        assertArrayEquals(plain, GuardContextSeal.open(sealed, aad, x_sk_bytes, mlkem.secret_key))
    }

    @Test(expected = GuardSealException::class)
    fun open_with_flipped_aad_throws() {
        val aad = GuardContextSeal.code_secret_aad(device_id)
        val sealed = GuardContextSeal.seal(ByteArray(20) { 7 }, aad, x_pk, mlkem.public_key)
        val bad = aad.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        GuardContextSeal.open(sealed, bad, x_sk_bytes, mlkem.secret_key)
    }

    @Test(expected = GuardSealException::class)
    fun open_with_wrong_version_throws() {
        val aad = GuardContextSeal.code_secret_aad(device_id)
        val sealed = GuardContextSeal.seal(ByteArray(20) { 7 }, aad, x_pk, mlkem.public_key)
        sealed[0] = 0x02
        GuardContextSeal.open(sealed, aad, x_sk_bytes, mlkem.secret_key)
    }

    @Test(expected = GuardSealException::class)
    fun open_short_input_throws() {
        GuardContextSeal.open(ByteArray(40), ByteArray(0), x_sk_bytes, mlkem.secret_key)
    }
}
