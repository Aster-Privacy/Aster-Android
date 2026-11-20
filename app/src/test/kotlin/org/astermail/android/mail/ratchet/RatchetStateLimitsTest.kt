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
import kotlinx.serialization.json.Json
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RatchetStateLimitsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    private fun sender_state(): RatchetState {
        val prekey = RatchetCrypto.generate_p256_keypair()
        return DoubleRatchet.init_sender("conv", ByteArray(32) { 7 }, RatchetCrypto.b64_encode(prekey.public_raw))
    }

    private fun container(state: RatchetState): String =
        """{"state":${json.encodeToString(RatchetState.serializer(), state)},"conversation_id":"conv","version":3}"""

    @Test
    fun an_exhausted_sending_chain_refuses_to_encrypt() {
        val state = sender_state()
        state.send_message_number = Int.MAX_VALUE
        try {
            DoubleRatchet.encrypt(state, "hello")
            fail("expected the sending chain to be refused")
        } catch (expected: IllegalStateException) {
            assertEquals("sending chain is exhausted", expected.message)
        }
        assertEquals(Int.MAX_VALUE, state.send_message_number)
    }

    @Test
    fun a_header_counter_at_the_limit_is_refused_even_with_a_matching_skipped_key() {
        val state = sender_state()
        val remote = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        val header = MessageHeader(
            dh_public = remote,
            previous_chain_length = 0,
            message_number = Int.MAX_VALUE,
            v = 2,
        )
        val message_key = ByteArray(32) { 3 }
        val nonce = ByteArray(12) { 5 }
        val ciphertext = RatchetCrypto.aes_gcm_encrypt(
            "hello".toByteArray(Charsets.UTF_8),
            message_key,
            nonce,
            DoubleRatchet.serialize_header_for_ad(header),
        )
        state.skipped_message_keys.add(
            SkippedMessageKey(
                dh_public = remote,
                message_number = Int.MAX_VALUE,
                message_key = RatchetCrypto.b64_encode(message_key),
                timestamp = System.currentTimeMillis(),
            ),
        )
        val recipient = RatchetRecipientData(
            header = header,
            ciphertext = RatchetCrypto.b64_encode(ciphertext),
            nonce = RatchetCrypto.b64_encode(nonce),
        )
        try {
            DoubleRatchet.decrypt(state, recipient)
            fail("expected the header to be refused")
        } catch (expected: IllegalStateException) {
            assertEquals("malformed ratchet header", expected.message)
        }
        assertEquals(1, state.skipped_message_keys.size)
    }

    @Test
    fun a_fresh_state_is_well_formed() {
        assertTrue(DoubleRatchet.is_well_formed(sender_state()))
    }

    @Test
    fun a_saved_state_with_a_negative_counter_is_malformed() {
        val state = sender_state()
        state.recv_message_number = -1
        assertFalse(DoubleRatchet.is_well_formed(state))
        assertEquals(
            RatchetStateContainerResult.Malformed,
            RatchetStateContainer.decode(container(state), "conv", floor = 0L, opened_bound = false),
        )
    }

    @Test
    fun a_saved_state_with_a_negative_epoch_is_malformed() {
        val state = sender_state()
        state.epoch = -2
        assertEquals(
            RatchetStateContainerResult.Malformed,
            RatchetStateContainer.decode(container(state), "conv", floor = 0L, opened_bound = false),
        )
    }

    @Test
    fun a_saved_state_with_too_many_skipped_keys_is_malformed() {
        val state = sender_state()
        repeat(2001) { index ->
            state.skipped_message_keys.add(
                SkippedMessageKey(dh_public = "a", message_number = index, message_key = "k", timestamp = 1L),
            )
        }
        assertEquals(
            RatchetStateContainerResult.Malformed,
            RatchetStateContainer.decode(container(state), "conv", floor = 0L, opened_bound = false),
        )
    }
}
