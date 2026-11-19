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
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RatchetRetiredMergeTest {

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun skipped(side: String, count: Int): MutableList<SkippedMessageKey> =
        MutableList(count) { index ->
            SkippedMessageKey(
                dh_public = side,
                message_number = index,
                message_key = "$side-$index",
                timestamp = index.toLong(),
            )
        }

    private fun base(
        side: String,
        root_key: String = "root-base",
        chain_key_send: String? = "cks",
        chain_key_recv: String? = "ckr",
        send_message_number: Int = 0,
        recv_message_number: Int = 0,
        skipped_count: Int = 0,
        created_at: Long = 1L,
        updated_at: Long = 10L,
    ) = RatchetState(
        conversation_id = "conv-vectors",
        dh_keypair = RatchetDhKeyPair(public_key = "dh-pub", secret_key = "dh-priv"),
        dh_remote_public = "dh-remote",
        root_key = root_key,
        chain_key_send = chain_key_send,
        chain_key_recv = chain_key_recv,
        send_message_number = send_message_number,
        recv_message_number = recv_message_number,
        epoch = 1,
        skipped_message_keys = skipped(side, skipped_count),
        created_at = created_at,
        updated_at = updated_at,
        remote_identity_key = "identity-base",
    )

    @Test
    fun retired_copy_never_replaces_the_send_chain() {
        val local = base("local", send_message_number = 2)
        val remote = base(
            "remote",
            send_message_number = 7,
            chain_key_send = "cks-remote",
            chain_key_recv = "ckr-remote",
            recv_message_number = 9,
            updated_at = 50L,
        )
        val merged = RatchetStateMerge.merge_retired(local, remote)

        assertEquals("cks", merged.chain_key_send)
        assertEquals(2, merged.send_message_number)
        assertEquals("ckr", merged.chain_key_recv)
        assertEquals(0, merged.recv_message_number)
        assertEquals("root-base", merged.root_key)
        assertEquals(10L, merged.updated_at)
        assertEquals(7, RatchetStateMerge.retired_send_target(local, remote))
    }

    @Test
    fun retired_copy_with_other_keys_at_the_same_epoch_is_ignored() {
        val local = base("local", send_message_number = 1)
        val remote = base(
            "remote",
            root_key = "root-remote",
            chain_key_send = "cks-remote",
            send_message_number = 5,
            created_at = 9L,
            updated_at = 50L,
        )
        val merged = RatchetStateMerge.merge_retired(local, remote)

        assertEquals("root-base", merged.root_key)
        assertEquals("cks", merged.chain_key_send)
        assertEquals(1, merged.send_message_number)
        assertEquals(1, RatchetStateMerge.retired_send_target(local, remote))
    }

    @Test
    fun retired_copy_never_adds_skipped_keys() {
        val local = base("local", skipped_count = 1)
        val remote = base("remote", skipped_count = 3)
        val merged = RatchetStateMerge.merge_retired(local, remote)

        assertEquals(1, merged.skipped_message_keys.size)
        assertEquals(0, RatchetStateMerge.retired_send_target(local, remote))
    }

    @Test
    fun retired_copy_more_than_1000_ahead_moves_nothing() {
        val local = base("local", send_message_number = 0)
        val remote = base("remote", send_message_number = 1001, chain_key_send = "cks-remote")
        val merged = RatchetStateMerge.merge_retired(local, remote)

        assertEquals("cks", merged.chain_key_send)
        assertEquals(0, merged.send_message_number)
        assertEquals(0, RatchetStateMerge.retired_send_target(local, remote))
    }

    @Test
    fun retired_copy_behind_keeps_the_local_counter() {
        val local = base("local", send_message_number = 5)
        val remote = base("remote", send_message_number = 3, chain_key_send = "cks-remote")
        val merged = RatchetStateMerge.merge_retired(local, remote)

        assertEquals("cks", merged.chain_key_send)
        assertEquals(5, merged.send_message_number)
        assertEquals(5, RatchetStateMerge.retired_send_target(local, remote))
    }

    @Test
    fun catching_up_the_send_chain_matches_sending_those_messages() {
        val chain_key = RatchetCrypto.b64_encode(ByteArray(32) { (it * 7).toByte() })
        val sent = base("local", chain_key_send = chain_key)
        val caught_up = base("local", chain_key_send = chain_key)

        repeat(3) {
            val (next_chain_key, message_key) = DoubleRatchet.kdf_chain(RatchetCrypto.b64_decode(sent.chain_key_send!!))
            message_key.fill(0)
            sent.chain_key_send = RatchetCrypto.b64_encode(next_chain_key)
            sent.send_message_number += 1
        }
        DoubleRatchet.advance_send_chain(caught_up, 3)

        assertEquals(sent.chain_key_send, caught_up.chain_key_send)
        assertEquals(3, caught_up.send_message_number)

        DoubleRatchet.advance_send_chain(caught_up, 2)
        DoubleRatchet.advance_send_chain(caught_up, 3 + 1001)

        assertEquals(sent.chain_key_send, caught_up.chain_key_send)
        assertEquals(3, caught_up.send_message_number)
    }
}
