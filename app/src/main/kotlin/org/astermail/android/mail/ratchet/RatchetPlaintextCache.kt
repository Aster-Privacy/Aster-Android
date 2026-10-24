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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val flag_lock = Any()
    private val unauthenticated = MutableStateFlow(read_flagged(unauthenticated_key))
    private val unconfirmed = MutableStateFlow(read_flagged(unconfirmed_key))

    val unauthenticated_ids: StateFlow<Set<String>> = unauthenticated.asStateFlow()

    val unconfirmed_sender_ids: StateFlow<Set<String>> = unconfirmed.asStateFlow()

    fun is_sender_unauthenticated(message_id: String?): Boolean =
        !message_id.isNullOrBlank() && message_id in unauthenticated.value

    fun is_sender_unconfirmed(message_id: String?): Boolean =
        !message_id.isNullOrBlank() && message_id in unconfirmed.value

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
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } finally {
            keys.forEach { it.fill(0) }
        }
    }

    suspend fun put(
        message_id: String,
        plaintext: String,
        sender_unauthenticated: Boolean = false,
        unconfirmed_sender: Boolean = false,
    ) {
        if (message_id.isBlank()) return
        val key = state_store.derive_state_encryption_key()
        if (key == null) {
            mutex.withLock {
                if (sender_unauthenticated) flag_unauthenticated(message_id)
                set_flag(unconfirmed, unconfirmed_key, message_id, unconfirmed_sender)
            }
            return
        }
        try {
            val nonce = RatchetCrypto.random_bytes(12)
            val ciphertext = RatchetCrypto.aes_gcm_encrypt(plaintext.toByteArray(Charsets.UTF_8), key, nonce, null)
            val encoded = RatchetCrypto.b64_encode(nonce) + ":" + RatchetCrypto.b64_encode(ciphertext)
            mutex.withLock { store_bounded(message_id, encoded, sender_unauthenticated, unconfirmed_sender) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        } finally {
            key.fill(0)
        }
    }

    private fun store_bounded(
        message_id: String,
        encoded: String,
        sender_unauthenticated: Boolean,
        unconfirmed_sender: Boolean,
    ) = synchronized(flag_lock) {
        val retained = current_index().filter { it != message_id }.toMutableList()
        retained.add(message_id)
        val overflow = retained.size - max_entries
        val evicted = if (overflow > 0) retained.subList(0, overflow).toList() else emptyList()
        if (overflow > 0) repeat(overflow) { retained.removeAt(0) }

        val flagged = unauthenticated.value - evicted.toSet() - message_id
        val next_flagged = if (sender_unauthenticated) flagged + message_id else flagged
        val retained_unconfirmed = unconfirmed.value - evicted.toSet() - message_id
        val next_unconfirmed = if (unconfirmed_sender) retained_unconfirmed + message_id else retained_unconfirmed

        val editor = prefs.edit()
        evicted.forEach { editor.remove(key_for(it)) }
        editor.putString(key_for(message_id), encoded)
        editor.putString(index_key, retained.joinToString(index_separator))
        editor.putString(unauthenticated_key, next_flagged.joinToString(index_separator))
        editor.putString(unconfirmed_key, next_unconfirmed.joinToString(index_separator))
        editor.commit()
        unauthenticated.value = next_flagged
        unconfirmed.value = next_unconfirmed
    }

    private fun flag_unauthenticated(message_id: String) = synchronized(flag_lock) {
        val next_flagged = (unauthenticated.value - message_id + message_id).toList().takeLast(max_entries).toSet()
        prefs.edit().putString(unauthenticated_key, next_flagged.joinToString(index_separator)).commit()
        unauthenticated.value = next_flagged
    }

    private fun set_flag(flow: MutableStateFlow<Set<String>>, pref_key: String, message_id: String, flagged: Boolean) {
        synchronized(flag_lock) {
            val without = flow.value - message_id
            val next = if (flagged) (without + message_id).toList().takeLast(max_entries).toSet() else without
            if (next == flow.value) return
            prefs.edit().putString(pref_key, next.joinToString(index_separator)).commit()
            flow.value = next
        }
    }

    private fun read_flagged(pref_key: String): Set<String> =
        prefs.getString(pref_key, null)
            ?.split(index_separator)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    fun confirm_sender(message_id: String) {
        set_flag(unconfirmed, unconfirmed_key, message_id, false)
    }

    private fun current_index(): List<String> {
        val stored = prefs.getString(index_key, null)
        if (stored != null) {
            return stored.split(index_separator).filter { it.isNotEmpty() }
        }
        return prefs.all.keys
            .filter { it.startsWith(entry_prefix) }
            .map { it.removePrefix(entry_prefix) }
    }

    fun clear() = synchronized(flag_lock) {
        prefs.edit().clear().commit()
        unauthenticated.value = emptySet()
        unconfirmed.value = emptySet()
    }

    private fun key_for(message_id: String): String = entry_prefix + message_id

    companion object {
        private const val prefs_name = "aster_ratchet_plaintext_v1"
        private const val entry_prefix = "ratchet_plaintext_"
        private const val index_key = "aster_plaintext_index_v1"
        private const val unauthenticated_key = "aster_plaintext_unauthenticated_v1"
        private const val unconfirmed_key = "aster_plaintext_unconfirmed_sender_v1"
        private const val index_separator = "\u001F"
        internal const val max_entries = 2000
    }
}
