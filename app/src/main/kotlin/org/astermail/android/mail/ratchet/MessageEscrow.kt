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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.astermail.android.api.ratchet.PlaintextEscrowEntry
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto

@Singleton
class MessageEscrow @Inject constructor(
    private val state_store: RatchetStateStore,
    private val ratchet_api: RatchetApi,
) {

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun upload_in_background(dedupe_key: String, plaintext: String) {
        if (dedupe_key.isBlank() || plaintext.isEmpty()) return
        background.launch { runCatching { upload(dedupe_key, plaintext) } }
    }

    suspend fun upload(dedupe_key: String, plaintext: String): Boolean {
        if (dedupe_key.isBlank() || plaintext.isEmpty()) return false
        val plaintext_bytes = plaintext.toByteArray(Charsets.UTF_8)
        if (plaintext_bytes.size > max_plaintext_bytes) {
            plaintext_bytes.fill(0)
            return false
        }
        val bases = state_store.master_key_candidates()
        val escrow_key = try {
            bases.firstOrNull()?.let { derive_escrow_key(it) }
        } finally {
            bases.forEach { it.fill(0) }
        }
        if (escrow_key == null) {
            plaintext_bytes.fill(0)
            return false
        }
        val nonce = RatchetCrypto.random_bytes(nonce_length)
        val sealed = try {
            seal(plaintext_bytes, escrow_key, nonce, dedupe_key)
        } finally {
            escrow_key.fill(0)
            plaintext_bytes.fill(0)
        }
        return ratchet_api.upload_plaintext_escrow(
            PlaintextEscrowEntry(
                message_id = dedupe_key,
                encrypted_plaintext = RatchetCrypto.b64_encode(sealed),
                plaintext_nonce = RatchetCrypto.b64_encode(nonce),
            ),
        )
    }

    suspend fun fetch(dedupe_key: String): String? {
        if (dedupe_key.isBlank()) return null
        val entry = try {
            ratchet_api.fetch_plaintext_escrow(dedupe_key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return null
        val ciphertext: ByteArray
        val nonce: ByteArray
        try {
            ciphertext = RatchetCrypto.b64_decode(entry.encrypted_plaintext)
            nonce = RatchetCrypto.b64_decode(entry.plaintext_nonce)
        } catch (_: Throwable) {
            return null
        }
        if (nonce.size != nonce_length) return null
        val bases = state_store.master_key_candidates()
        try {
            for (base in bases) {
                val escrow_key = derive_escrow_key(base)
                val opened = try {
                    open(ciphertext, escrow_key, nonce, dedupe_key)
                } finally {
                    escrow_key.fill(0)
                }
                if (opened != null) return opened
            }
            return null
        } finally {
            bases.forEach { it.fill(0) }
        }
    }

    companion object {
        internal const val max_plaintext_bytes = 100 * 1024
        private const val nonce_length = 12
        private const val escrow_salt = "Aster_Mail_Plaintext_Escrow"
        private const val escrow_info = "plaintext_escrow_key"
        private const val aad_prefix = "aster.escrow.v2\u0000"

        fun dedupe_key(message_id: String, header: MessageHeader): String =
            "$message_id:${header.dh_public}:${header.message_number}"

        fun derive_escrow_key(base: ByteArray): ByteArray =
            RatchetCrypto.hkdf_sha256(
                ikm = base,
                salt = escrow_salt.toByteArray(Charsets.UTF_8),
                info = escrow_info.toByteArray(Charsets.UTF_8),
                length = 32,
            )

        fun aad_for(dedupe_key: String): ByteArray = (aad_prefix + dedupe_key).toByteArray(Charsets.UTF_8)

        fun seal(plaintext: ByteArray, escrow_key: ByteArray, nonce: ByteArray, dedupe_key: String): ByteArray =
            RatchetCrypto.aes_gcm_encrypt(plaintext, escrow_key, nonce, aad_for(dedupe_key))

        fun open(ciphertext: ByteArray, escrow_key: ByteArray, nonce: ByteArray, dedupe_key: String): String? =
            try {
                val plaintext = RatchetCrypto.aes_gcm_decrypt(ciphertext, escrow_key, nonce, aad_for(dedupe_key))
                try {
                    String(plaintext, Charsets.UTF_8)
                } finally {
                    plaintext.fill(0)
                }
            } catch (_: Throwable) {
                null
            }
    }
}
