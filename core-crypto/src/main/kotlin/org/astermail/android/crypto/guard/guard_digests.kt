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

package org.astermail.android.crypto.guard

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID

enum class GuardActionCode(val wire: Int) {
    ENROLL(1),
    APPROVE(2),
    DENY(3),
    CANCEL(4),
    SKIP(5),
    VETO(6),
    PANIC(7),
    REMOVE_DEVICE(8),
    SWITCHES(9),
    JOIN(10),
    RESUME(11),
}

object GuardDigests {
    private val keys_label = "aster-guard-keys-v1".toByteArray(Charsets.US_ASCII)
    private val approve_label = "aster-guard-approve-v1".toByteArray(Charsets.US_ASCII)
    private val id_label = "aster-guard-id-v1".toByteArray(Charsets.US_ASCII)
    private val switches_label = "aster-guard-switches-v1".toByteArray(Charsets.US_ASCII)
    private val number_label = "aster-guard-number-v1".toByteArray(Charsets.US_ASCII)
    private val action_label = "aster-guard-action-v1".toByteArray(Charsets.US_ASCII)

    const val ACTION_MESSAGE_LEN = 102
    const val NUMBER_MIN = 10
    const val NUMBER_MAX = 99

    val ABSENT_ACTION_PK: ByteArray get() = ByteArray(33)

    val PANIC_SUBJECT: ByteArray get() = ByteArray(32)

    fun uuid_bytes(id: UUID?): ByteArray {
        if (id == null) return ByteArray(16)
        return ByteBuffer.allocate(16)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
            .array()
    }

    private fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        for (p in parts) md.update(p)
        return md.digest()
    }

    private fun u32_le(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    fun keys_digest(ed25519: ByteArray, mlkem: ByteArray, x25519: ByteArray, action_pk: ByteArray): ByteArray {
        require(action_pk.size == 33) { "action key must be 33 bytes" }
        return sha256(keys_label, ed25519, mlkem, x25519, action_pk)
    }

    fun approve_subject(request_id: UUID?, keys_digest: ByteArray, number: Int): ByteArray {
        require(keys_digest.size == 32) { "keys digest must be 32 bytes" }
        require(number in 0..255) { "number out of range" }
        return sha256(approve_label, uuid_bytes(request_id), keys_digest, byteArrayOf(number.toByte()))
    }

    fun id_subject(id: UUID): ByteArray = sha256(id_label, uuid_bytes(id))

    fun switches_subject(switches: Int, hold_scopes: Int): ByteArray =
        sha256(switches_label, u32_le(switches), u32_le(hold_scopes))

    fun number_hash(request_id: UUID, number: Int): ByteArray {
        require(number in NUMBER_MIN..NUMBER_MAX) { "number out of range" }
        return sha256(number_label, uuid_bytes(request_id), byteArrayOf(number.toByte()))
    }

    fun action_message(code: GuardActionCode, user_id: UUID, nonce: ByteArray, subject: ByteArray): ByteArray {
        require(nonce.size == 32) { "nonce must be 32 bytes" }
        require(subject.size == 32) { "subject must be 32 bytes" }
        val out = ByteBuffer.allocate(ACTION_MESSAGE_LEN)
        out.put(action_label)
        out.put(code.wire.toByte())
        out.put(uuid_bytes(user_id))
        out.put(nonce)
        out.put(subject)
        return out.array()
    }

    fun compress_p256(uncompressed: ByteArray): ByteArray {
        require(uncompressed.size == 65 && uncompressed[0] == 0x04.toByte()) { "expected an uncompressed point" }
        val out = ByteArray(33)
        out[0] = (0x02 or (uncompressed[64].toInt() and 1)).toByte()
        System.arraycopy(uncompressed, 1, out, 1, 32)
        return out
    }
}
