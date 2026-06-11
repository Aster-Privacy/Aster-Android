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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.astermail.android.crypto.ratchet.RatchetCrypto

object DoubleRatchet {

    private val ad_prefix = "astermail-ratchet-header-v2".toByteArray(Charsets.UTF_8)
    private val kdf_info_root = "Aster Mail_Root_KDF".toByteArray(Charsets.UTF_8)
    private val kdf_info_chain = "Aster Mail_Chain_KDF".toByteArray(Charsets.UTF_8)
    private const val max_skip = 1000
    private const val skipped_key_ttl_ms = 7L * 24 * 60 * 60 * 1000

    fun serialize_header_for_ad(header: MessageHeader): ByteArray {
        val dh_public = RatchetCrypto.b64_decode(header.dh_public)
        val total = ad_prefix.size + 1 + dh_public.size + 8
        val buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        buf.put(ad_prefix)
        buf.put((header.v ?: 1).toByte())
        buf.put(dh_public)
        buf.putInt(header.previous_chain_length)
        buf.putInt(header.message_number)
        return buf.array()
    }

    fun kdf_root(root_key: ByteArray, dh_output: ByteArray): Pair<ByteArray, ByteArray> {
        val out = RatchetCrypto.hkdf_sha256(dh_output, root_key, kdf_info_root, 64)
        return out.copyOfRange(0, 32) to out.copyOfRange(32, 64)
    }

    fun kdf_chain(chain_key: ByteArray): Pair<ByteArray, ByteArray> {
        val out = RatchetCrypto.hkdf_sha256(chain_key, ByteArray(32), kdf_info_chain, 64)
        return out.copyOfRange(0, 32) to out.copyOfRange(32, 64)
    }

    fun decrypt(state: RatchetState, recipient: RatchetRecipientData): String {
        try_skipped(state, recipient)?.let {
            state.updated_at = System.currentTimeMillis()
            return it
        }

        val work = clone_state(state)
        val header = recipient.header
        if (work.dh_remote_public == null || header.dh_public != work.dh_remote_public) {
            skip_message_keys(work, header.previous_chain_length)
            dh_ratchet_step(work, header.dh_public)
        }
        skip_message_keys(work, header.message_number)

        val chain_key = RatchetCrypto.b64_decode(work.chain_key_recv ?: throw IllegalStateException("no chain_key_recv"))
        val (new_chain_key, message_key) = kdf_chain(chain_key)

        val ad = if ((header.v ?: 1) >= 2) serialize_header_for_ad(header) else null
        val ciphertext = RatchetCrypto.b64_decode(recipient.ciphertext)
        val nonce = RatchetCrypto.b64_decode(recipient.nonce)
        val message_key_aes = message_key.copyOfRange(0, 32)
        val plaintext = RatchetCrypto.aes_gcm_decrypt(ciphertext, message_key_aes, nonce, ad)

        work.chain_key_recv = RatchetCrypto.b64_encode(new_chain_key)
        work.recv_message_number += 1
        work.updated_at = System.currentTimeMillis()
        cleanup_old_skipped(work)

        commit_state(state, work)
        return String(plaintext, Charsets.UTF_8)
    }

    private fun try_skipped(state: RatchetState, recipient: RatchetRecipientData): String? {
        val header = recipient.header
        val idx = state.skipped_message_keys.indexOfFirst {
            it.dh_public == header.dh_public && it.message_number == header.message_number
        }
        if (idx == -1) return null
        val skipped = state.skipped_message_keys[idx]
        val message_key = RatchetCrypto.b64_decode(skipped.message_key)
        val ad = if ((header.v ?: 1) >= 2) serialize_header_for_ad(header) else null
        val ciphertext = RatchetCrypto.b64_decode(recipient.ciphertext)
        val nonce = RatchetCrypto.b64_decode(recipient.nonce)
        val plaintext = RatchetCrypto.aes_gcm_decrypt(ciphertext, message_key.copyOfRange(0, 32), nonce, ad)
        state.skipped_message_keys.removeAt(idx)
        return String(plaintext, Charsets.UTF_8)
    }

    private fun skip_message_keys(state: RatchetState, until: Int) {
        val chain_key_b64 = state.chain_key_recv ?: return
        val remote_public = state.dh_remote_public ?: return
        if (until - state.recv_message_number > max_skip) {
            throw IllegalStateException("too many skipped messages (${until - state.recv_message_number})")
        }
        var chain_key = RatchetCrypto.b64_decode(chain_key_b64)
        while (state.recv_message_number < until) {
            val (next_chain_key, message_key) = kdf_chain(chain_key)
            state.skipped_message_keys.add(
                SkippedMessageKey(
                    dh_public = remote_public,
                    message_number = state.recv_message_number,
                    message_key = RatchetCrypto.b64_encode(message_key),
                    timestamp = System.currentTimeMillis(),
                ),
            )
            chain_key.fill(0)
            chain_key = next_chain_key
            state.recv_message_number += 1
        }
        state.chain_key_recv = RatchetCrypto.b64_encode(chain_key)
        cleanup_old_skipped(state)
    }

    fun init_sender(
        conversation_id: String,
        shared_secret: ByteArray,
        remote_signed_prekey_raw_b64: String,
    ): RatchetState {
        val remote_pub = RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(remote_signed_prekey_raw_b64))
        val sending_kp = RatchetCrypto.generate_p256_keypair()
        val dh_output = RatchetCrypto.ecdh(sending_kp.private_key, remote_pub)
        val (root_after, chain_send) = kdf_root(shared_secret, dh_output)
        val now = System.currentTimeMillis()
        return RatchetState(
            conversation_id = conversation_id,
            dh_keypair = RatchetDhKeyPair(
                public_key = RatchetCrypto.b64_encode(sending_kp.public_raw),
                secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(sending_kp.private_key)),
            ),
            dh_remote_public = remote_signed_prekey_raw_b64,
            root_key = RatchetCrypto.b64_encode(root_after),
            chain_key_send = RatchetCrypto.b64_encode(chain_send),
            chain_key_recv = null,
            send_message_number = 0,
            recv_message_number = 0,
            previous_chain_length = 0,
            version = 1,
            created_at = now,
            updated_at = now,
        )
    }

    data class EncryptResult(
        val header: MessageHeader,
        val ciphertext: ByteArray,
        val nonce: ByteArray,
    )

    fun encrypt(state: RatchetState, plaintext: String): EncryptResult {
        val chain_send_b64 = state.chain_key_send
            ?: throw IllegalStateException("no chain_key_send (ratchet not initialized for sending)")
        val chain_key = RatchetCrypto.b64_decode(chain_send_b64)
        val (next_chain_key, message_key) = kdf_chain(chain_key)

        val header = MessageHeader(
            dh_public = state.dh_keypair.public_key,
            previous_chain_length = state.previous_chain_length,
            message_number = state.send_message_number,
            v = null,
        )
        val nonce = RatchetCrypto.random_bytes(12)
        val message_key_aes = message_key.copyOfRange(0, 32)
        val ciphertext = RatchetCrypto.aes_gcm_encrypt(plaintext.toByteArray(Charsets.UTF_8), message_key_aes, nonce, null)

        state.chain_key_send = RatchetCrypto.b64_encode(next_chain_key)
        state.send_message_number += 1
        state.updated_at = System.currentTimeMillis()
        chain_key.fill(0)
        return EncryptResult(header, ciphertext, nonce)
    }

    private fun parse_secret_key(secret_key: String): java.security.PrivateKey {
        return if (RatchetCrypto.looks_like_jwk(secret_key)) {
            RatchetCrypto.parse_p256_private_jwk(secret_key)
        } else {
            RatchetCrypto.raw_d_to_private(RatchetCrypto.b64_decode(secret_key))
        }
    }

    private fun dh_ratchet_step(state: RatchetState, new_remote_dh_public_b64: String) {
        state.previous_chain_length = state.send_message_number
        state.send_message_number = 0
        state.recv_message_number = 0
        state.dh_remote_public = new_remote_dh_public_b64

        val remote_pub = RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(new_remote_dh_public_b64))
        val current_secret = parse_secret_key(state.dh_keypair.secret_key)

        val dh_output_recv = RatchetCrypto.ecdh(current_secret, remote_pub)
        val root_in = RatchetCrypto.b64_decode(state.root_key)
        val (root_after_recv, chain_recv) = kdf_root(root_in, dh_output_recv)
        state.root_key = RatchetCrypto.b64_encode(root_after_recv)
        state.chain_key_recv = RatchetCrypto.b64_encode(chain_recv)

        val new_kp = RatchetCrypto.generate_p256_keypair()
        state.dh_keypair = RatchetDhKeyPair(
            public_key = RatchetCrypto.b64_encode(new_kp.public_raw),
            secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(new_kp.private_key)),
        )

        val dh_output_send = RatchetCrypto.ecdh(new_kp.private_key, remote_pub)
        val (root_after_send, chain_send) = kdf_root(root_after_recv, dh_output_send)
        state.root_key = RatchetCrypto.b64_encode(root_after_send)
        state.chain_key_send = RatchetCrypto.b64_encode(chain_send)
    }

    private fun cleanup_old_skipped(state: RatchetState) {
        val cutoff = System.currentTimeMillis() - skipped_key_ttl_ms
        state.skipped_message_keys.removeAll { it.timestamp < cutoff }
    }

    private fun clone_state(state: RatchetState): RatchetState {
        return state.copy(
            dh_keypair = state.dh_keypair.copy(),
            skipped_message_keys = state.skipped_message_keys.toMutableList(),
        )
    }

    private fun commit_state(target: RatchetState, source: RatchetState) {
        target.dh_keypair = source.dh_keypair
        target.dh_remote_public = source.dh_remote_public
        target.root_key = source.root_key
        target.chain_key_send = source.chain_key_send
        target.chain_key_recv = source.chain_key_recv
        target.send_message_number = source.send_message_number
        target.recv_message_number = source.recv_message_number
        target.previous_chain_length = source.previous_chain_length
        target.skipped_message_keys.clear()
        target.skipped_message_keys.addAll(source.skipped_message_keys)
        target.updated_at = source.updated_at
    }
}
