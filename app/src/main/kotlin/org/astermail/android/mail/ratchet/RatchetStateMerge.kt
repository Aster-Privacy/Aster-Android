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

import org.astermail.android.crypto.ratchet.RatchetCrypto

object RatchetStateMerge {

    private const val MAX_MERGED_SKIPPED_KEYS = 1000
    private const val MAX_RETIRED_SEND_CATCH_UP = 1000

    private fun skipped_key_id(key: SkippedMessageKey): String =
        "${key.dh_public}:${key.message_number}"

    private fun merge_skipped_keys(
        a: List<SkippedMessageKey>,
        b: List<SkippedMessageKey>,
    ): MutableList<SkippedMessageKey> {
        val by_id = linkedMapOf<String, SkippedMessageKey>()

        for (key in a + b) {
            val id = skipped_key_id(key)
            val existing = by_id[id]
            if (existing == null || key.timestamp > existing.timestamp) {
                by_id[id] = key
            }
        }

        val merged = by_id.values.sortedBy { it.timestamp }
        return merged
            .takeLast(MAX_MERGED_SKIPPED_KEYS)
            .toMutableList()
    }

    private fun consumed_locally(local: RatchetState, key: SkippedMessageKey, local_ids: Set<String>): Boolean =
        local.chain_key_recv != null &&
            key.dh_public == local.dh_remote_public &&
            key.message_number < local.recv_message_number &&
            skipped_key_id(key) !in local_ids

    private fun remote_keys_still_pending(local: RatchetState, remote: RatchetState): List<SkippedMessageKey> {
        val local_ids = local.skipped_message_keys.mapTo(HashSet()) { skipped_key_id(it) }
        return remote.skipped_message_keys.filterNot { consumed_locally(local, it, local_ids) }
    }

    fun shares_epoch(a: RatchetState, b: RatchetState): Boolean =
        a.conversation_id == b.conversation_id && same_epoch(a, b)

    private fun same_epoch(a: RatchetState, b: RatchetState): Boolean =
        a.dh_keypair.public_key == b.dh_keypair.public_key &&
            a.dh_remote_public == b.dh_remote_public &&
            constant_time_equals(a.root_key, b.root_key)

    private fun constant_time_equals(a: String, b: String): Boolean {
        val left = a.toByteArray(Charsets.UTF_8)
        val right = b.toByteArray(Charsets.UTF_8)
        if (left.size != right.size) return false
        var diff = 0
        for (i in left.indices) diff = diff or (left[i].toInt() xor right[i].toInt())
        return diff == 0
    }

    private fun root_key_tie_break(state: RatchetState): String =
        java.util.Base64.getEncoder()
            .encodeToString(RatchetCrypto.sha256(state.root_key.toByteArray(Charsets.UTF_8)))

    private fun pick_newer_epoch(local: RatchetState, remote: RatchetState): RatchetState = when {
        local.epoch != remote.epoch -> if (local.epoch > remote.epoch) local else remote
        local.updated_at != remote.updated_at -> if (local.updated_at > remote.updated_at) local else remote
        else -> if (root_key_tie_break(local) > root_key_tie_break(remote)) local else remote
    }

    private fun pick_winner(local: RatchetState, remote: RatchetState, remote_versioned: Boolean): RatchetState {
        if (remote_versioned) return pick_newer_epoch(local, remote)
        if (remote.created_at <= 0L || local.created_at <= 0L || remote.created_at < local.created_at) return local
        if (remote.epoch != local.epoch) return if (remote.epoch > local.epoch) remote else local
        return if (remote.updated_at > local.updated_at) remote else local
    }

    fun merge(local: RatchetState, remote: RatchetState, remote_versioned: Boolean = true): RatchetState {
        if (local.conversation_id != remote.conversation_id) return local

        val skipped = merge_skipped_keys(local.skipped_message_keys, remote_keys_still_pending(local, remote))
        val local_skipped = merge_skipped_keys(local.skipped_message_keys, emptyList())
        val updated_at = maxOf(local.updated_at, remote.updated_at)

        if (!same_epoch(local, remote)) {
            val winner = pick_winner(local, remote, remote_versioned)
            val keep_local_skipped = !remote_versioned && winner === local
            return winner.copy(
                skipped_message_keys = if (keep_local_skipped) local_skipped else skipped,
                updated_at = updated_at,
                remote_identity_key = local.remote_identity_key ?: remote.remote_identity_key,
                header_bound = local.header_bound || remote.header_bound,
            )
        }

        val send_ahead =
            if (local.send_message_number >= remote.send_message_number) local else remote

        val recv_source = when {
            local.chain_key_recv == null -> remote
            else -> local
        }
        val merged_skipped = if (remote_versioned || local.chain_key_recv == null) skipped else local_skipped

        return local.copy(
            chain_key_send = send_ahead.chain_key_send,
            send_message_number = send_ahead.send_message_number,
            chain_key_recv = recv_source.chain_key_recv,
            recv_message_number = recv_source.recv_message_number,
            previous_chain_length = maxOf(local.previous_chain_length, remote.previous_chain_length),
            epoch = maxOf(local.epoch, remote.epoch),
            bootstrap = local.bootstrap ?: remote.bootstrap,
            remote_identity_key = local.remote_identity_key ?: remote.remote_identity_key,
            skipped_message_keys = merged_skipped,
            updated_at = updated_at,
            header_bound = local.header_bound || remote.header_bound,
        )
    }

    fun merge_retired(local: RatchetState, remote: RatchetState): RatchetState {
        if (local.conversation_id != remote.conversation_id) return local
        return local.copy(
            skipped_message_keys = merge_skipped_keys(local.skipped_message_keys, emptyList()),
        )
    }

    fun retired_send_target(local: RatchetState, remote: RatchetState): Int {
        val current = local.send_message_number
        if (local.chain_key_send == null || !shares_epoch(local, remote)) return current
        val target = remote.send_message_number
        return if (target > current && target - current <= MAX_RETIRED_SEND_CATCH_UP) target else current
    }
}
