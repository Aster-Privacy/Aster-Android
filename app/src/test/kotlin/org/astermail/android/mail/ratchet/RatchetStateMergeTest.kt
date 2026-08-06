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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatchetStateMergeTest {

    private val conversation = "conversation-token-abc"

    private fun skipped(dh_public: String, message_number: Int, timestamp: Long) =
        SkippedMessageKey(
            dh_public = dh_public,
            message_number = message_number,
            message_key = "key-$dh_public-$message_number",
            timestamp = timestamp,
        )

    private fun state(
        dh_public: String = "local-dh-pub",
        root_key: String = "root-key-epoch-1",
        chain_key_send: String? = "send-chain-0",
        chain_key_recv: String? = "recv-chain-0",
        send_message_number: Int = 0,
        recv_message_number: Int = 0,
        previous_chain_length: Int = 0,
        epoch: Int = 0,
        skipped_message_keys: List<SkippedMessageKey> = emptyList(),
        bootstrap: BootstrapData? = null,
        updated_at: Long = 1_000L,
    ) = RatchetState(
        conversation_id = conversation,
        dh_keypair = RatchetDhKeyPair(public_key = dh_public, secret_key = "$dh_public-sec"),
        dh_remote_public = "remote-dh-pub",
        root_key = root_key,
        chain_key_send = chain_key_send,
        chain_key_recv = chain_key_recv,
        send_message_number = send_message_number,
        recv_message_number = recv_message_number,
        previous_chain_length = previous_chain_length,
        epoch = epoch,
        skipped_message_keys = skipped_message_keys.toMutableList(),
        version = 2,
        created_at = 1_000L,
        updated_at = updated_at,
        bootstrap = bootstrap,
    )

    @Test
    fun keeps_the_furthest_send_chain() {
        val local = state(chain_key_send = "send-chain-5", send_message_number = 5)
        val remote = state(chain_key_send = "send-chain-2", send_message_number = 2)

        assertEquals("send-chain-5", RatchetStateMerge.merge(local, remote).chain_key_send)
        assertEquals("send-chain-5", RatchetStateMerge.merge(remote, local).chain_key_send)
        assertEquals(5, RatchetStateMerge.merge(remote, local).send_message_number)
    }

    @Test
    fun keeps_the_earliest_receive_chain() {
        val local = state(chain_key_recv = "recv-chain-7", recv_message_number = 7)
        val remote = state(chain_key_recv = "recv-chain-3", recv_message_number = 3)

        assertEquals("recv-chain-3", RatchetStateMerge.merge(local, remote).chain_key_recv)
        assertEquals("recv-chain-3", RatchetStateMerge.merge(remote, local).chain_key_recv)
        assertEquals(3, RatchetStateMerge.merge(local, remote).recv_message_number)
    }

    @Test
    fun adopts_a_receive_chain_the_other_device_established_first() {
        val local = state(chain_key_recv = null, recv_message_number = 0)
        val remote = state(chain_key_recv = "recv-chain-4", recv_message_number = 4)

        val merged = RatchetStateMerge.merge(local, remote)

        assertEquals("recv-chain-4", merged.chain_key_recv)
        assertEquals(4, merged.recv_message_number)
    }

    @Test
    fun unions_skipped_message_keys_from_both_devices() {
        val local = state(
            skipped_message_keys = listOf(skipped("dh-a", 1, 10), skipped("dh-a", 2, 20)),
        )
        val remote = state(
            skipped_message_keys = listOf(skipped("dh-a", 2, 20), skipped("dh-b", 1, 30)),
        )

        val merged = RatchetStateMerge.merge(local, remote)

        assertEquals(
            listOf("dh-a:1", "dh-a:2", "dh-b:1"),
            merged.skipped_message_keys.map { "${it.dh_public}:${it.message_number}" },
        )
    }

    @Test
    fun never_drops_a_skipped_key_only_one_side_has() {
        val local = state(skipped_message_keys = listOf(skipped("dh-a", 9, 90)))
        val remote = state()

        assertEquals(1, RatchetStateMerge.merge(remote, local).skipped_message_keys.size)
    }

    @Test
    fun carries_skipped_keys_across_a_diverged_dh_epoch() {
        val local = state(
            skipped_message_keys = listOf(skipped("dh-a", 1, 10)),
            epoch = 1,
            updated_at = 5_000L,
        )
        val remote = state(
            dh_public = "other-dh-pub",
            root_key = "root-key-epoch-2",
            chain_key_send = "send-chain-epoch-2",
            skipped_message_keys = listOf(skipped("dh-b", 1, 20)),
            epoch = 2,
            updated_at = 9_000L,
        )

        val merged = RatchetStateMerge.merge(local, remote)

        assertEquals("root-key-epoch-2", merged.root_key)
        assertEquals(2, merged.skipped_message_keys.size)
        assertEquals(9_000L, merged.updated_at)
    }

    @Test
    fun keeps_the_higher_epoch_even_when_the_other_side_was_written_later() {
        val local = state(
            root_key = "root-key-epoch-3",
            epoch = 3,
            updated_at = 2_000L,
        )
        val remote = state(
            dh_public = "other-dh-pub",
            root_key = "root-key-epoch-2",
            epoch = 2,
            updated_at = 9_000L,
        )

        assertEquals("root-key-epoch-3", RatchetStateMerge.merge(local, remote).root_key)
        assertEquals("root-key-epoch-3", RatchetStateMerge.merge(remote, local).root_key)
    }

    @Test
    fun falls_back_to_the_newer_write_when_both_sides_report_the_same_epoch() {
        val local = state(epoch = 4, updated_at = 2_000L)
        val remote = state(
            dh_public = "other-dh-pub",
            root_key = "root-key-epoch-other",
            epoch = 4,
            updated_at = 9_000L,
        )

        assertEquals("root-key-epoch-other", RatchetStateMerge.merge(local, remote).root_key)
    }

    @Test
    fun resolves_a_full_tie_the_same_way_on_both_devices() {
        val local = state(epoch = 4, updated_at = 7_000L)
        val remote = state(
            dh_public = "other-dh-pub",
            root_key = "root-key-zzz",
            epoch = 4,
            updated_at = 7_000L,
        )

        assertEquals(
            RatchetStateMerge.merge(local, remote).root_key,
            RatchetStateMerge.merge(remote, local).root_key,
        )
    }

    @Test
    fun keeps_the_highest_epoch_when_both_devices_are_on_the_same_chain() {
        val local = state(epoch = 2)
        val remote = state(epoch = 5)

        assertEquals(5, RatchetStateMerge.merge(local, remote).epoch)
    }

    @Test
    fun keeps_a_bootstrap_known_to_only_one_device() {
        val local = state()
        val remote = state(bootstrap = BootstrapData(ephemeral_key = "eph-key"))

        assertEquals(
            "eph-key",
            RatchetStateMerge.merge(local, remote).bootstrap?.ephemeral_key,
        )
    }

    @Test
    fun refuses_to_merge_states_from_different_conversations() {
        val local = state()
        val other = state().copy(conversation_id = "a-different-conversation")

        assertEquals(local, RatchetStateMerge.merge(local, other))
        assertNull(RatchetStateMerge.merge(local, other).bootstrap)
    }
}
