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
import org.junit.Before
import org.junit.Test

class X3dhSignedPqTargetTest {

    private val onetime_key_id = 77

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            val flags = secondArg<Int>()
            if (flags and android.util.Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(firstArg())
            } else {
                java.util.Base64.getEncoder().encodeToString(firstArg())
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
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun send(
        onetime_public: ByteArray?,
        identity_public: ByteArray?,
        signature_covers_pq_identity: Boolean,
    ): X3dh.SenderResult {
        val sender = RatchetCrypto.generate_p256_keypair()
        val recipient_identity = RatchetCrypto.generate_p256_keypair()
        val recipient_signed_prekey = RatchetCrypto.generate_p256_keypair()
        return X3dh.perform_sender(
            sender_identity_jwk = RatchetCrypto.p256_private_to_jwk(sender.private_key),
            recipient_identity_raw = recipient_identity.public_raw,
            recipient_signed_prekey_raw = recipient_signed_prekey.public_raw,
            recipient_pq_prekey = onetime_public?.let { onetime_key_id to it },
            recipient_pq_identity = identity_public,
            signature_covers_pq_identity = signature_covers_pq_identity,
        )
    }

    @Test
    fun `a signature that covers the pq identity key ignores the unsigned one-time prekey`() {
        val onetime = RatchetCrypto.ml_kem_768_generate_keypair()
        val identity = RatchetCrypto.ml_kem_768_generate_keypair()

        val result = send(onetime.public_key, identity.public_key, signature_covers_pq_identity = true)

        assertEquals(X3dh.PqMode.IDENTITY, result.pq_mode)
        assertEquals(X3dh.PQ_IDENTITY_KEY_ID, result.pq_key_id)
    }

    @Test
    fun `the one-time prekey is kept when the signature does not cover the pq identity key`() {
        val onetime = RatchetCrypto.ml_kem_768_generate_keypair()
        val identity = RatchetCrypto.ml_kem_768_generate_keypair()

        val result = send(onetime.public_key, identity.public_key, signature_covers_pq_identity = false)

        assertEquals(X3dh.PqMode.ONETIME, result.pq_mode)
        assertEquals(onetime_key_id, result.pq_key_id)
    }

    @Test
    fun `the one-time prekey is kept when the bundle has no usable pq identity key`() {
        val onetime = RatchetCrypto.ml_kem_768_generate_keypair()

        val truncated = send(onetime.public_key, ByteArray(12), signature_covers_pq_identity = true)
        val missing = send(onetime.public_key, null, signature_covers_pq_identity = true)

        assertEquals(X3dh.PqMode.ONETIME, truncated.pq_mode)
        assertEquals(X3dh.PqMode.ONETIME, missing.pq_mode)
        assertArrayEquals(
            intArrayOf(onetime_key_id, onetime_key_id),
            intArrayOf(truncated.pq_key_id ?: -2, missing.pq_key_id ?: -2),
        )
    }
}
