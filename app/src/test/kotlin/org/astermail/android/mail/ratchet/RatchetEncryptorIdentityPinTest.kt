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

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetEncryptorIdentityPinTest {

    private val sender_email = "hello@astermail.org"
    private val recipient_email = "kchaos@aster.cx"

    private lateinit var state_store: RatchetStateStore
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var ratchet_api: RatchetApi
    private lateinit var syncer: RatchetStateSyncer
    private lateinit var keys_api: KeysApi
    private lateinit var identity_pins: RatchetIdentityPinStore

    private lateinit var recipient_identity_b64: String
    private lateinit var bundle: PrekeyBundleResponse
    private lateinit var conversation_id: String

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        }
        every { Base64.decode(any<String>(), any()) } answers {
            val value = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0) {
                var padded = value
                val mod = padded.length % 4
                if (mod != 0) padded += "=".repeat(4 - mod)
                java.util.Base64.getUrlDecoder().decode(padded)
            } else {
                java.util.Base64.getDecoder().decode(value)
            }
        }

        val sender_identity_kp = RatchetCrypto.generate_p256_keypair()
        val recipient_identity_kp = RatchetCrypto.generate_p256_keypair()
        val recipient_spk_kp = RatchetCrypto.generate_p256_keypair()

        recipient_identity_b64 = RatchetCrypto.b64_encode(recipient_identity_kp.public_raw)
        conversation_id = X3dh.derive_conversation_id(sender_email, recipient_email)

        bundle = PrekeyBundleResponse(
            user_id = "u_1",
            kem_identity_key = recipient_identity_b64,
            signed_prekey = RatchetCrypto.b64_encode(recipient_spk_kp.public_raw),
            signed_prekey_signature = "",
        )

        state_store = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        ratchet_api = mockk(relaxed = true)
        syncer = mockk(relaxed = true)
        keys_api = mockk(relaxed = true)
        identity_pins = mockk(relaxed = true)

        coEvery { state_store.load(any()) } returns null
        every { session_key_store.get_ratchet_identity_public_b64() } returns
            RatchetCrypto.b64_encode(sender_identity_kp.public_raw)
        every { session_key_store.get_ratchet_identity_jwk() } returns to_private_jwk(sender_identity_kp)
        coEvery { ratchet_api.fetch_prekey_bundle(any(), any()) } returns bundle
        every { identity_pins.is_prekey_binding_verified(any()) } returns false
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun to_private_jwk(kp: RatchetCrypto.EcKeyPair): String {
        val d = RatchetCrypto.private_to_raw_d(kp.private_key)
        val x = kp.public_raw.copyOfRange(1, 33)
        val y = kp.public_raw.copyOfRange(33, 65)
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"${RatchetCrypto.b64url_encode(x)}\",\"y\":\"${RatchetCrypto.b64url_encode(y)}\",\"d\":\"${RatchetCrypto.b64url_encode(d)}\"}"
    }

    private fun new_encryptor(): RatchetEncryptor = RatchetEncryptor(
        state_store,
        session_key_store,
        ratchet_api,
        syncer,
        ConversationLocks(),
        keys_api,
        identity_pins,
    )

    @Test
    fun `swapped prekey bundle identity blocks the send`() = runTest {
        every { identity_pins.evaluate(conversation_id, recipient_identity_b64) } returns
            IdentityPinOutcome.CHANGED

        var thrown: Throwable? = null
        try {
            new_encryptor().encrypt_envelope(
                sender_email,
                listOf(recipient_email),
                "hello",
                allow_non_post_quantum = true,
            )
        } catch (t: Throwable) {
            thrown = t
        }

        assertTrue(thrown is RatchetIdentityPinException)
        assertEquals(recipient_email, (thrown as RatchetIdentityPinException).recipient)
        coVerify(exactly = 0) { state_store.save(any()) }
        coVerify(exactly = 1) {
            identity_pins.flag_identity_change(conversation_id, recipient_email, recipient_identity_b64, any())
        }
    }

    @Test
    fun `first contact bundle is pinned and sent`() = runTest {
        every { identity_pins.evaluate(conversation_id, recipient_identity_b64) } returns
            IdentityPinOutcome.FIRST_CONTACT

        val envelope = new_encryptor().encrypt_envelope(
            sender_email,
            listOf(recipient_email),
            "hello",
            allow_non_post_quantum = true,
        )

        assertNotNull(envelope)
        coVerify(exactly = 1) { identity_pins.pin_if_absent(conversation_id, recipient_identity_b64) }
    }

    @Test
    fun `pinned identity that still matches is sent`() = runTest {
        every { identity_pins.evaluate(conversation_id, recipient_identity_b64) } returns
            IdentityPinOutcome.UNCHANGED

        val envelope = new_encryptor().encrypt_envelope(
            sender_email,
            listOf(recipient_email),
            "hello",
            allow_non_post_quantum = true,
        )

        assertNotNull(envelope)
        coVerify(exactly = 0) { identity_pins.flag_identity_change(any(), any(), any(), any()) }
    }
}
