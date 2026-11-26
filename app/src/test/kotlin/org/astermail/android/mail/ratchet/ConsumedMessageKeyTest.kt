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

class ConsumedMessageKeyTest {

    private val conversation = "conversation-token-abc"

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

    private fun skipped(dh_public: String, message_number: Int, timestamp: Long) =
        SkippedMessageKey(
            dh_public = dh_public,
            message_number = message_number,
            message_key = "key-$dh_public-$message_number",
            timestamp = timestamp,
        )

    private fun state(
        dh_public: String = "local-dh-pub",
        dh_remote_public: String = "remote-dh-pub",
        root_key: String = "root-key-epoch-1",
        recv_message_number: Int = 0,
        epoch: Int = 0,
        skipped_message_keys: List<SkippedMessageKey> = emptyList(),
        consumed_message_keys: List<ConsumedMessageKey> = emptyList(),
        updated_at: Long = 1_000L,
    ) = RatchetState(
        conversation_id = conversation,
        dh_keypair = RatchetDhKeyPair(public_key = dh_public, secret_key = "$dh_public-sec"),
        dh_remote_public = dh_remote_public,
        root_key = root_key,
        chain_key_send = "send-chain",
        chain_key_recv = "recv-chain",
        recv_message_number = recv_message_number,
        epoch = epoch,
        skipped_message_keys = skipped_message_keys.toMutableList(),
        version = 2,
        created_at = 1_000L,
        updated_at = updated_at,
        consumed_message_keys = consumed_message_keys.toMutableList(),
    )

    private fun ids(state: RatchetState): List<String> =
        state.skipped_message_keys.map { "${it.dh_public}:${it.message_number}" }

    private fun to_recipient(result: DoubleRatchet.EncryptResult): RatchetRecipientData =
        RatchetRecipientData(
            header = result.header,
            ciphertext = RatchetCrypto.b64_encode(result.ciphertext),
            nonce = RatchetCrypto.b64_encode(result.nonce),
        )

    @Test
    fun a_key_this_device_used_on_an_earlier_chain_is_not_imported_again() {
        val local = state(
            dh_remote_public = "newer-dh-pub",
            consumed_message_keys = listOf(ConsumedMessageKey("older-dh-pub", 1)),
        )
        val stale = state(
            dh_remote_public = "newer-dh-pub",
            skipped_message_keys = listOf(skipped("older-dh-pub", 1, 10), skipped("older-dh-pub", 2, 20)),
        )

        assertEquals(listOf("older-dh-pub:2"), ids(RatchetStateMerge.merge(local, stale)))
    }

    @Test
    fun a_key_this_device_used_is_not_imported_across_a_diverged_epoch() {
        val local = state(
            epoch = 1,
            updated_at = 5_000L,
            consumed_message_keys = listOf(ConsumedMessageKey("dh-b", 1)),
        )
        val newer = state(
            dh_public = "other-dh-pub",
            root_key = "root-key-epoch-2",
            epoch = 2,
            updated_at = 9_000L,
            skipped_message_keys = listOf(skipped("dh-b", 1, 20), skipped("dh-c", 4, 30)),
        )
        val older = newer.copy(epoch = 0, updated_at = 500L)

        for (remote in listOf(newer, older)) {
            val merged = RatchetStateMerge.merge(local, remote)
            assertEquals(listOf("dh-c:4"), ids(merged))
            assertEquals(listOf(ConsumedMessageKey("dh-b", 1)), merged.consumed_message_keys)
        }
    }

    @Test
    fun keys_this_device_never_had_are_still_imported() {
        val local = state(consumed_message_keys = listOf(ConsumedMessageKey("dh-a", 1)))
        val remote = state(skipped_message_keys = listOf(skipped("dh-a", 2, 10), skipped("dh-b", 1, 20)))

        assertEquals(listOf("dh-a:2", "dh-b:1"), ids(RatchetStateMerge.merge(local, remote)))
    }

    @Test
    fun a_key_this_device_still_holds_is_kept_when_another_device_used_it() {
        val local = state(skipped_message_keys = listOf(skipped("dh-a", 1, 10)))
        val remote = state(consumed_message_keys = listOf(ConsumedMessageKey("dh-a", 1)))

        val merged = RatchetStateMerge.merge(local, remote)

        assertEquals(listOf("dh-a:1"), ids(merged))
        assertEquals(listOf(ConsumedMessageKey("dh-a", 1)), merged.consumed_message_keys)
    }

    @Test
    fun an_unversioned_remote_does_not_add_to_the_record() {
        val local = state()
        val remote = state(consumed_message_keys = listOf(ConsumedMessageKey("dh-a", 1)))

        assertTrue(RatchetStateMerge.merge(local, remote, remote_versioned = false).consumed_message_keys.isEmpty())
    }

    @Test
    fun the_record_stays_bounded() {
        val many = (0 until DoubleRatchet.max_consumed_message_keys + 50).map { ConsumedMessageKey("dh", it) }
        val merged = RatchetStateMerge.merge(state(consumed_message_keys = many), state())

        assertEquals(DoubleRatchet.max_consumed_message_keys, merged.consumed_message_keys.size)
        assertEquals(ConsumedMessageKey("dh", 50), merged.consumed_message_keys.first())
    }

    @Test
    fun decrypting_records_the_key_and_a_stale_copy_cannot_reopen_the_message() {
        val shared_secret = ByteArray(32) { 7 }
        val prekey = RatchetCrypto.generate_p256_keypair()
        val prekey_public_b64 = RatchetCrypto.b64_encode(prekey.public_raw)
        val sender = DoubleRatchet.init_sender(conversation, shared_secret, prekey_public_b64)
        val receiver = RatchetState(
            conversation_id = conversation,
            dh_keypair = RatchetDhKeyPair(
                public_key = prekey_public_b64,
                secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(prekey.private_key)),
            ),
            dh_remote_public = null,
            root_key = RatchetCrypto.b64_encode(shared_secret),
        )

        val first = DoubleRatchet.encrypt(sender, "first")
        val second = DoubleRatchet.encrypt(sender, "second")
        assertEquals("second", DoubleRatchet.decrypt(receiver, to_recipient(second)))
        val stale = receiver.copy(
            dh_keypair = receiver.dh_keypair.copy(),
            skipped_message_keys = receiver.skipped_message_keys.toMutableList(),
            consumed_message_keys = receiver.consumed_message_keys.toMutableList(),
        )
        assertEquals("first", DoubleRatchet.decrypt(receiver, to_recipient(first)))

        val chain = first.header.dh_public
        assertEquals(
            listOf(ConsumedMessageKey(chain, 1), ConsumedMessageKey(chain, 0)),
            receiver.consumed_message_keys,
        )
        assertTrue(receiver.skipped_message_keys.isEmpty())

        val moved_on = receiver.copy(
            dh_remote_public = "a-later-chain",
            root_key = "a-later-root",
            epoch = receiver.epoch + 1,
            skipped_message_keys = mutableListOf(),
            consumed_message_keys = receiver.consumed_message_keys.toMutableList(),
        )
        assertTrue(RatchetStateMerge.merge(moved_on, stale).skipped_message_keys.isEmpty())
    }

    @Test
    fun a_stored_state_without_the_record_still_loads() {
        val legacy = "{\"state\":{\"conversation_id\":\"conv-a\"," +
            "\"dh_keypair\":{\"public_key\":\"pub\",\"secret_key\":\"sec\"},\"root_key\":\"root\"," +
            "\"skipped_message_keys\":[{\"dh_public\":\"dh\",\"message_number\":1,\"message_key\":\"k\",\"timestamp\":1}]}," +
            "\"conversation_id\":\"conv-a\",\"sync_version\":3}"

        val decoded = RatchetStateContainer.decode(legacy, "conv-a", floor = 0L, opened_bound = false)

        val accepted = decoded as RatchetStateContainerResult.Accepted
        assertTrue(accepted.state.consumed_message_keys.isEmpty())
        assertEquals(1, accepted.state.skipped_message_keys.size)
    }
}
