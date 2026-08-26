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
import org.junit.Before
import org.junit.Test

class SkippedMessageKeyRetentionTest {

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

    private fun to_recipient(result: DoubleRatchet.EncryptResult): RatchetRecipientData =
        RatchetRecipientData(
            header = result.header,
            ciphertext = RatchetCrypto.b64_encode(result.ciphertext),
            nonce = RatchetCrypto.b64_encode(result.nonce),
        )

    @Test
    fun a_message_key_skipped_ten_days_ago_still_decrypts() {
        val shared_secret = ByteArray(32) { 7 }
        val prekey = RatchetCrypto.generate_p256_keypair()
        val prekey_public_b64 = RatchetCrypto.b64_encode(prekey.public_raw)

        val sender = DoubleRatchet.init_sender("conv", shared_secret, prekey_public_b64)

        val receiver = RatchetState(
            conversation_id = "conv",
            dh_keypair = RatchetDhKeyPair(
                public_key = prekey_public_b64,
                secret_key = RatchetCrypto.b64_encode(
                    RatchetCrypto.private_to_raw_d(prekey.private_key),
                ),
            ),
            dh_remote_public = null,
            root_key = RatchetCrypto.b64_encode(shared_secret),
        )

        val first = DoubleRatchet.encrypt(sender, "first")
        val second = DoubleRatchet.encrypt(sender, "second")
        val third = DoubleRatchet.encrypt(sender, "third")

        assertEquals("second", DoubleRatchet.decrypt(receiver, to_recipient(second)))
        assertEquals(1, receiver.skipped_message_keys.size)

        val ten_days_ago = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000
        val skipped = receiver.skipped_message_keys[0].copy(timestamp = ten_days_ago)
        receiver.skipped_message_keys[0] = skipped

        assertEquals("third", DoubleRatchet.decrypt(receiver, to_recipient(third)))
        assertEquals(1, receiver.skipped_message_keys.size)

        assertEquals("first", DoubleRatchet.decrypt(receiver, to_recipient(first)))
    }
}
