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

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SecurePrefs

@Singleton
class RatchetPlaintextCache @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
    private val state_store: RatchetStateStore,
) {

    private val prefs = SecurePrefs.open(context, prefs_name)
    private val mutex = Mutex()

    suspend fun get(message_id: String): String? {
        if (message_id.isBlank()) return null
        val stored = mutex.withLock { prefs.getString(key_for(message_id), null) } ?: return null
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2) return null
        val keys = state_store.state_encryption_key_candidates()
        if (keys.isEmpty()) return null
        return try {
            val nonce = RatchetCrypto.b64_decode(parts[0])
            val ciphertext = RatchetCrypto.b64_decode(parts[1])
            keys.firstNotNullOfOrNull { candidate ->
                try {
                    String(RatchetCrypto.aes_gcm_decrypt(ciphertext, candidate, nonce, null), Charsets.UTF_8)
                } catch (_: Throwable) {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        } finally {
            keys.forEach { it.fill(0) }
        }
    }

    suspend fun put(message_id: String, plaintext: String) {
        if (message_id.isBlank()) return
        val key = state_store.derive_state_encryption_key() ?: return
        try {
            val nonce = RatchetCrypto.random_bytes(12)
            val ciphertext = RatchetCrypto.aes_gcm_encrypt(plaintext.toByteArray(Charsets.UTF_8), key, nonce, null)
            val encoded = RatchetCrypto.b64_encode(nonce) + ":" + RatchetCrypto.b64_encode(ciphertext)
            mutex.withLock { prefs.edit().putString(key_for(message_id), encoded).apply() }
        } catch (_: Throwable) {
        } finally {
            key.fill(0)
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key_for(message_id: String): String = "ratchet_plaintext_$message_id"

    companion object {
        private const val prefs_name = "aster_ratchet_plaintext_v1"
    }
}
