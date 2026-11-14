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

    private fun to_private_jwk(kp: RatchetCrypto.EcKeyPair): String {
        val d = RatchetCrypto.private_to_raw_d(kp.private_key)
        val x = kp.public_raw.copyOfRange(1, 33)
        val y = kp.public_raw.copyOfRange(33, 65)
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"${b64url(x)}\",\"y\":\"${b64url(y)}\",\"d\":\"${b64url(d)}\"}"
    }

    private fun b64url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun session(): Pair<RatchetState, RatchetState> {
        val prekey = RatchetCrypto.generate_p256_keypair()
        val prekey_public = RatchetCrypto.b64_encode(prekey.public_raw)
        val shared = ByteArray(32) { 7 }
        val sender = DoubleRatchet.init_sender("conv", shared, prekey_public)
        val receiver = X3dh.init_receiver_state("conv", shared, to_private_jwk(prekey), prekey_public)
        return sender to receiver
    }

    private fun bound_message(sender: RatchetState, text: String): RatchetRecipientData {
        val sealed = DoubleRatchet.encrypt(sender, text)
        return RatchetRecipientData(
            header = sealed.header,
            ciphertext = RatchetCrypto.b64_encode(sealed.ciphertext),
            nonce = RatchetCrypto.b64_encode(sealed.nonce),
        )
    }

    private fun unbound_message(sender: RatchetState, text: String, version: Int?): RatchetRecipientData {
        val sealed = DoubleRatchet.encrypt(sender, "discarded")
        val nonce = RatchetCrypto.random_bytes(12)
        val ciphertext = RatchetCrypto.aes_gcm_encrypt(
            text.toByteArray(Charsets.UTF_8),
            sealed.message_key,
            nonce,
            null,
        )
        return RatchetRecipientData(
            header = sealed.header.copy(v = version),
            ciphertext = RatchetCrypto.b64_encode(ciphertext),
            nonce = RatchetCrypto.b64_encode(nonce),
        )
    }

    @Test
    fun `a new session accepts an unbound header`() {
        val (sender, receiver) = session()

        assertEquals("old client", DoubleRatchet.decrypt(receiver, unbound_message(sender, "old client", null)))
        assertEquals(false, receiver.header_bound)
    }

    @Test
    fun `a bound message pins the session to bound headers`() {
        val (sender, receiver) = session()

        assertEquals("first", DoubleRatchet.decrypt(receiver, bound_message(sender, "first")))
        assertEquals(true, receiver.header_bound)
    }

    @Test
    fun `a pinned session refuses an unbound header and keeps its state`() {
        val (sender, receiver) = session()
        DoubleRatchet.decrypt(receiver, bound_message(sender, "first"))
        val before = receiver.copy(skipped_message_keys = receiver.skipped_message_keys.toMutableList())

        for (version in listOf<Int?>(null, 1)) {
            val downgraded = unbound_message(sender.copy(), "downgraded", version)
            assertTrue(runCatching { DoubleRatchet.decrypt(receiver, downgraded) }.isFailure)
        }

        assertEquals(before, receiver)
        assertEquals("second", DoubleRatchet.decrypt(receiver, bound_message(sender, "second")))
    }

    @Test
    fun `a failed bound message does not pin the session`() {
        val (sender, receiver) = session()
        val genuine = bound_message(sender, "first")
        val tampered = genuine.copy(nonce = RatchetCrypto.b64_encode(ByteArray(12)))

        assertTrue(runCatching { DoubleRatchet.decrypt(receiver, tampered) }.isFailure)
        assertEquals(false, receiver.header_bound)
    }

    @Test
    fun `an out of order bound message pins the session`() {
        val (sender, receiver) = session()
        val first = bound_message(sender, "first")
        val second = bound_message(sender, "second")
        DoubleRatchet.decrypt(receiver, second)
        receiver.header_bound = false

        assertEquals("first", DoubleRatchet.decrypt(receiver, first))
        assertEquals(true, receiver.header_bound)
    }

    @Test
    fun `a header version outside one byte is refused before serializing`() {
        val header = genuine_header()

        for (version in listOf(-1, 256, 258, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertTrue(runCatching { DoubleRatchet.serialize_header_for_ad(header.copy(v = version)) }.isFailure)
        }
        assertTrue(runCatching { DoubleRatchet.serialize_header_for_ad(header.copy(v = 255)) }.isSuccess)
        assertTrue(runCatching { DoubleRatchet.serialize_header_for_ad(header.copy(v = 0)) }.isSuccess)
    }

    @Test
    fun `a header version that wraps to a valid byte fails to decrypt and keeps the state`() {
        val (sender, receiver) = session()
        val genuine = bound_message(sender, "first")
        val before = receiver.copy(skipped_message_keys = receiver.skipped_message_keys.toMutableList())

        val wrapped = genuine.copy(header = genuine.header.copy(v = 258))

        assertTrue(runCatching { DoubleRatchet.decrypt(receiver, wrapped) }.isFailure)
        assertEquals(before, receiver)
        assertEquals("first", DoubleRatchet.decrypt(receiver, genuine))
    }

    @Test
    fun `a header version outside one byte fails to open under its message key`() {
        val (sender, _) = session()
        val sealed = DoubleRatchet.encrypt(sender, "keyed")
        val recipient = RatchetRecipientData(
            header = sealed.header.copy(v = 258),
            ciphertext = RatchetCrypto.b64_encode(sealed.ciphertext),
            nonce = RatchetCrypto.b64_encode(sealed.nonce),
        )

        assertTrue(runCatching { DoubleRatchet.decrypt_with_message_key(recipient, sealed.message_key) }.isFailure)
        assertEquals(
            "keyed",
            DoubleRatchet.decrypt_with_message_key(recipient.copy(header = sealed.header), sealed.message_key),
        )
    }
}
