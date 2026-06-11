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

import kotlinx.serialization.Serializable

@Serializable
data class MessageHeader(
    val dh_public: String,
    val previous_chain_length: Int,
    val message_number: Int,
    val v: Int? = null,
)

@Serializable
data class RatchetRecipientData(
    val ephemeral_key: String? = null,
    val header: MessageHeader,
    val ciphertext: String,
    val nonce: String,
    val pq_ciphertext: String? = null,
    val pq_key_id: Int? = null,
)

@Serializable
data class RatchetEnvelope(
    val type: String,
    val sender_identity_key: String,
    val recipients: Map<String, RatchetRecipientData>,
)

@Serializable
data class SkippedMessageKey(
    val dh_public: String,
    val message_number: Int,
    val message_key: String,
    val timestamp: Long,
)

@Serializable
data class RatchetDhKeyPair(
    val public_key: String,
    val secret_key: String,
)

@Serializable
data class BootstrapData(
    val ephemeral_key: String,
    val pq_ciphertext: String? = null,
    val pq_key_id: Int? = null,
)

@Serializable
data class RatchetState(
    val conversation_id: String,
    var dh_keypair: RatchetDhKeyPair,
    var dh_remote_public: String? = null,
    var root_key: String,
    var chain_key_send: String? = null,
    var chain_key_recv: String? = null,
    var send_message_number: Int = 0,
    var recv_message_number: Int = 0,
    var previous_chain_length: Int = 0,
    val skipped_message_keys: MutableList<SkippedMessageKey> = mutableListOf(),
    var version: Int = 1,
    var created_at: Long = 0L,
    var updated_at: Long = 0L,
    var bootstrap: BootstrapData? = null,
)

data class RatchetIdentity(
    val identity_private: java.security.PrivateKey,
    val identity_public_raw: ByteArray,
    val signed_prekey_private: java.security.PrivateKey,
    val signed_prekey_public_raw: ByteArray,
    val signed_prekey_public_key: java.security.PublicKey,
)
