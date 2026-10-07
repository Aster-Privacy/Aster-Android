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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetMessageKeyOnlyTest {

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
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun genuine_header(): MessageHeader {
        val prekey = RatchetCrypto.generate_p256_keypair()
        val sender = DoubleRatchet.init_sender(
            "conv",
            ByteArray(32) { 7 },
            RatchetCrypto.b64_encode(prekey.public_raw),
        )
        return DoubleRatchet.encrypt(sender, "real message").header
    }

    private fun sealed_under(key: ByteArray, text: String, header: MessageHeader): RatchetRecipientData {
        val nonce = RatchetCrypto.random_bytes(12)
        val ciphertext = RatchetCrypto.aes_gcm_encrypt(text.toByteArray(Charsets.UTF_8), key, nonce, null)
        return RatchetRecipientData(
            header = header,
            ciphertext = RatchetCrypto.b64_encode(ciphertext),
            nonce = RatchetCrypto.b64_encode(nonce),
        )
    }

    @Test
    fun `a stripped version message never opens under a key other than its message key`() {
        val account_key = ByteArray(32) { 3 }
        val forged = sealed_under(account_key, "account secret", genuine_header().copy(v = null))

        val outcome = runCatching {
            DoubleRatchet.decrypt_with_message_key(forged, ByteArray(32) { 9 })
        }

        assertTrue(outcome.isFailure)
    }

    @Test
    fun `a genuine version one message still opens under its own message key`() {
        val message_key = ByteArray(32) { 5 }
        val sealed = sealed_under(message_key, "legacy mail", genuine_header().copy(v = null))

        assertEquals("legacy mail", DoubleRatchet.decrypt_with_message_key(sealed, message_key))
    }
}
