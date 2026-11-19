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

import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.crypto.ratchet.RecoveryLane
import org.astermail.android.storage.SessionKeyStore

class MemoryLaneReplayVault(
    val records: MutableMap<String, LaneReplayRecord> = LinkedHashMap(),
) : LaneReplayVault(mockk(relaxed = true), SessionKeyStore(null)) {
    val queued = ArrayList<Pair<String, LaneReplayRecord>>()

    override suspend fun record(sender_email: String): LaneReplayRecord =
        records[sender_email] ?: LaneReplay.EMPTY

    override fun queue(sender_email: String, record: LaneReplayRecord) {
        queued += sender_email to record
        records[sender_email] = LaneReplay.merge(record, records[sender_email])
    }
}

object LegacyLaneFixture {
    private const val SENDER_LANE_LABEL = "aster.ratchet.recovery.lane.sender.v1"
    private const val LANE_KEY_BYTES = 32
    private const val NONCE_BYTES = 12

    private fun frame_and_digest(parts: List<String>): ByteArray {
        val framed = ByteArrayOutputStream()
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            framed.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
            framed.write(bytes)
        }
        return RatchetCrypto.sha256(framed.toByteArray())
    }

    fun seal(
        message_key: String,
        conversation_id: String,
        sender_identity_public: String,
        sender_identity_jwk: String,
        recipient_identity_public: String,
        recipient_identity_jwk: String,
    ): RecoveryLane.Data {
        val lane = RecoveryLane.seal_unbound(
            message_key,
            conversation_id,
            sender_identity_public,
            RecoveryLane.RecipientKeys(recipient_identity_public, null),
        )
        val dh_secret = RatchetCrypto.ecdh(
            RatchetCrypto.parse_p256_private_jwk(recipient_identity_jwk),
            RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(lane.epk)),
        )
        val static_secret = RatchetCrypto.ecdh(
            RatchetCrypto.parse_p256_private_jwk(sender_identity_jwk),
            RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(recipient_identity_public)),
        )
        val info = frame_and_digest(
            listOf(
                SENDER_LANE_LABEL,
                conversation_id,
                sender_identity_public,
                recipient_identity_public,
                "",
                lane.epk,
                "",
            ),
        )
        val key = RatchetCrypto.hkdf_sha256(
            dh_secret + static_secret,
            RatchetCrypto.sha256(SENDER_LANE_LABEL.toByteArray(Charsets.UTF_8)),
            info,
            LANE_KEY_BYTES,
        )
        val nonce = RatchetCrypto.random_bytes(NONCE_BYTES)
        val sct = RatchetCrypto.aes_gcm_encrypt(message_key.toByteArray(Charsets.UTF_8), key, nonce, info)
        return lane.copy(sct = RatchetCrypto.b64_encode(sct), snonce = RatchetCrypto.b64_encode(nonce))
    }
}
