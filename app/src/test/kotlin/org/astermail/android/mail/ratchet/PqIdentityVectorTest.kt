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

import java.security.MessageDigest
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PqIdentityVectorTest {

    private val counting_seed = ByteArray(64) { it.toByte() }

    private val expected_public_sha256 =
        "0b7934c83125c788995e2ba6bd761e33046b3e40571be53e023309a29f398cc9"

    private val expected_secret_sha256 =
        "dac268bde6a8dd238e9887117d6b664e7a7a9350ad6b7c08a948e504809572a5"

    private fun sha256_hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }

    @Test
    fun seed_derives_the_same_keypair_as_the_other_clients() {
        val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(counting_seed)

        assertEquals(1184, pair.public_key.size)
        assertEquals(2400, pair.secret_key.size)
        assertEquals(expected_public_sha256, sha256_hex(pair.public_key))
        assertEquals(expected_secret_sha256, sha256_hex(pair.secret_key))
    }

    @Test
    fun generated_keypair_round_trips_through_encapsulation() {
        val pair = RatchetCrypto.ml_kem_768_generate_keypair()

        assertEquals(64, pair.seed.size)

        val encapsulation = RatchetCrypto.ml_kem_768_encapsulate(pair.public_key)
        val shared = RatchetCrypto.ml_kem_768_decapsulate(
            encapsulation.ciphertext,
            pair.secret_key,
        )

        assertEquals(1088, encapsulation.ciphertext.size)
        assertTrue(encapsulation.shared_secret.contentEquals(shared))
    }

    @Test
    fun generated_keypair_matches_its_own_seed() {
        val pair = RatchetCrypto.ml_kem_768_generate_keypair()
        val rederived = RatchetCrypto.ml_kem_768_keypair_from_seed(pair.seed)

        assertTrue(pair.public_key.contentEquals(rederived.public_key))
        assertTrue(pair.secret_key.contentEquals(rederived.secret_key))
    }
}
