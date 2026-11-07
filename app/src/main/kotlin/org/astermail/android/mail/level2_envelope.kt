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

package org.astermail.android.mail

import java.security.MessageDigest
import java.util.Base64
import org.astermail.android.crypto.EnvelopeV2
import org.astermail.android.crypto.StorageWriteKey
import org.astermail.android.storage.SessionKeyStore

object LevelTwoEnvelope {

    fun marker_b64(): String = Base64.getEncoder().encodeToString(EnvelopeV2.marker())

    fun is_marker_b64(nonce_b64: String?): Boolean {
        if (nonce_b64.isNullOrBlank()) return false
        val decoded = runCatching { Base64.getMimeDecoder().decode(nonce_b64.trim()) }.getOrNull() ?: return false
        return EnvelopeV2.is_marker(decoded)
    }

    fun read_keys(store: SessionKeyStore): List<ByteArray> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ByteArray>()
        val candidates = listOfNotNull(store.get_data_kek()) +
            store.get_decrypt_keks().mapNotNull(StorageWriteKey::decode_key)
        for (key in candidates) {
            if (key.size != StorageWriteKey.KEY_LENGTH || !seen.add(StorageWriteKey.encode_key(key))) {
                key.fill(0)
                continue
            }
            out.add(key)
        }
        return out
    }

    fun open_b64(sealed_b64: String?, keys: List<ByteArray>): ByteArray? {
        if (sealed_b64.isNullOrBlank()) return null
        val blob = runCatching { Base64.getMimeDecoder().decode(sealed_b64.trim()) }.getOrNull() ?: return null
        return EnvelopeV2.open(blob, keys)
    }

    fun open_with_store(sealed_b64: String?, store: SessionKeyStore): ByteArray? {
        val keys = read_keys(store)
        return try {
            open_b64(sealed_b64, keys)
        } finally {
            keys.forEach { it.fill(0) }
        }
    }

    fun write_key(store: SessionKeyStore): ByteArray? {
        if (!store.is_level2_admitted()) return null
        return StorageWriteKey.write_key(store)
    }

    fun stored_sha256_hex(sealed_b64: String?): String? {
        if (sealed_b64.isNullOrBlank()) return null
        val raw = runCatching { Base64.getMimeDecoder().decode(sealed_b64.trim()) }.getOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
    }

    fun seal_b64(plaintext: ByteArray, data_kek: ByteArray): String =
        Base64.getEncoder().encodeToString(EnvelopeV2.seal(plaintext, data_kek))
}
