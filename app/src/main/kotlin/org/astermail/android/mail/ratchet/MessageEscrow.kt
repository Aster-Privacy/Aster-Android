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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import org.astermail.android.api.ratchet.PlaintextEscrowEntry
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto

data class EscrowedPlaintext(
    val plaintext: String,
    val verified: Boolean,
    val unconfirmed_sender: Boolean,
)

data class BoundEscrowPayload(
    val sender: String,
    val conversation_id: String,
    val plaintext: String,
    val unconfirmed_sender: Boolean,
)

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

    fun upload_bound_in_background(
        dedupe_key: String,
        plaintext: String,
        sender: String,
        conversation_id: String,
        unconfirmed_sender: Boolean,
    ) {
        if (dedupe_key.isBlank() || plaintext.isEmpty()) return
        background.launch {
            runCatching { upload_bound(dedupe_key, plaintext, sender, conversation_id, unconfirmed_sender) }
        }
    }

    suspend fun upload(dedupe_key: String, plaintext: String): Boolean {
        if (dedupe_key.isBlank() || plaintext.isEmpty()) return false
        val plaintext_bytes = plaintext.toByteArray(Charsets.UTF_8)
        if (plaintext_bytes.size > max_plaintext_bytes) {
            plaintext_bytes.fill(0)
            return false
        }
        return post_sealed(dedupe_key, plaintext_bytes, aad_for(dedupe_key))
    }

    suspend fun upload_bound(
        dedupe_key: String,
        plaintext: String,
        sender: String,
        conversation_id: String,
        unconfirmed_sender: Boolean,
    ): Boolean {
        if (dedupe_key.isBlank() || plaintext.isEmpty()) return false
        if (plaintext.toByteArray(Charsets.UTF_8).size > max_plaintext_bytes) return false
        val payload = encode_bound_payload(sender, conversation_id, plaintext, unconfirmed_sender)
        val bound = post_sealed(bound_key(dedupe_key), payload, bound_aad_for(dedupe_key))
        val legacy = upload(dedupe_key, plaintext)
        return bound && legacy
    }

    private suspend fun post_sealed(row_key: String, payload: ByteArray, aad: ByteArray): Boolean {
        val bases = state_store.master_key_candidates()
        val escrow_key = try {
            bases.firstOrNull()?.let { derive_escrow_key(it) }
        } finally {
            bases.forEach { it.fill(0) }
        }
        if (escrow_key == null) {
            payload.fill(0)
            return false
        }
        val nonce = RatchetCrypto.random_bytes(nonce_length)
        val sealed = try {
            RatchetCrypto.aes_gcm_encrypt(payload, escrow_key, nonce, aad)
        } finally {
            escrow_key.fill(0)
            payload.fill(0)
        }
        return ratchet_api.upload_plaintext_escrow(
            PlaintextEscrowEntry(
                message_id = row_key,
                encrypted_plaintext = RatchetCrypto.b64_encode(sealed),
                plaintext_nonce = RatchetCrypto.b64_encode(nonce),
            ),
        )
    }

    suspend fun fetch(dedupe_key: String): String? {
        if (dedupe_key.isBlank()) return null
        val opened = open_row(dedupe_key, aad_for(dedupe_key)) ?: return null
        return try {
            String(opened, Charsets.UTF_8)
        } finally {
            opened.fill(0)
        }
    }

    suspend fun fetch_bound(dedupe_key: String, sender: String, conversation_id: String): EscrowedPlaintext? {
        if (dedupe_key.isBlank()) return null
        val opened = open_row(bound_key(dedupe_key), bound_aad_for(dedupe_key))
        val bound = opened?.let { bytes ->
            try {
                decode_bound_payload(bytes)
            } finally {
                bytes.fill(0)
            }
        }
        if (bound != null) {
            if (!payload_matches_binding(bound, sender, conversation_id)) return null
            return EscrowedPlaintext(
                plaintext = bound.plaintext,
                verified = true,
                unconfirmed_sender = bound.unconfirmed_sender,
            )
        }
        val legacy = fetch(dedupe_key) ?: return null
        return EscrowedPlaintext(plaintext = legacy, verified = false, unconfirmed_sender = true)
    }

    private suspend fun open_row(row_key: String, aad: ByteArray): ByteArray? {
        val entry = try {
            ratchet_api.fetch_plaintext_escrow(row_key)
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
                    open_bytes(ciphertext, escrow_key, nonce, aad)
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
        private const val bound_aad_prefix = "aster.escrow.v3\u0000"
        private const val bound_key_prefix = "v3:"
        private val header_json = Json { ignoreUnknownKeys = true }

        fun dedupe_key(message_id: String, header: MessageHeader): String =
            "$message_id:${header.dh_public}:${header.message_number}"

        fun bound_key(dedupe_key: String): String = bound_key_prefix + dedupe_key

        fun derive_escrow_key(base: ByteArray): ByteArray =
            RatchetCrypto.hkdf_sha256(
                ikm = base,
                salt = escrow_salt.toByteArray(Charsets.UTF_8),
                info = escrow_info.toByteArray(Charsets.UTF_8),
                length = 32,
            )

        fun aad_for(dedupe_key: String): ByteArray = (aad_prefix + dedupe_key).toByteArray(Charsets.UTF_8)

        fun bound_aad_for(dedupe_key: String): ByteArray =
            (bound_aad_prefix + dedupe_key).toByteArray(Charsets.UTF_8)

        fun normalize_sender(sender: String): String = sender.trim().lowercase(java.util.Locale.ROOT)

        fun encode_bound_payload(
            sender: String,
            conversation_id: String,
            plaintext: String,
            unconfirmed_sender: Boolean,
        ): ByteArray {
            val header = buildJsonObject {
                put("s", normalize_sender(sender))
                put("c", conversation_id)
                if (unconfirmed_sender) put("u", 1)
            }
            return "$header\n$plaintext".toByteArray(Charsets.UTF_8)
        }

        fun decode_bound_payload(bytes: ByteArray): BoundEscrowPayload? {
            val text = String(bytes, Charsets.UTF_8)
            val split = text.indexOf('\n')
            if (split < 0) return null
            val header = try {
                header_json.parseToJsonElement(text.substring(0, split)) as? JsonObject
            } catch (_: Throwable) {
                null
            } ?: return null
            val sender = (header["s"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val conversation_id = (header["c"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val flag = (header["u"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            return BoundEscrowPayload(
                sender = sender,
                conversation_id = conversation_id,
                plaintext = text.substring(split + 1),
                unconfirmed_sender = flag == 1.0,
            )
        }

        fun payload_matches_binding(payload: BoundEscrowPayload, sender: String, conversation_id: String): Boolean =
            payload.sender == normalize_sender(sender) && payload.conversation_id == conversation_id

        fun seal(plaintext: ByteArray, escrow_key: ByteArray, nonce: ByteArray, dedupe_key: String): ByteArray =
            RatchetCrypto.aes_gcm_encrypt(plaintext, escrow_key, nonce, aad_for(dedupe_key))

        fun open(ciphertext: ByteArray, escrow_key: ByteArray, nonce: ByteArray, dedupe_key: String): String? {
            val plaintext = open_bytes(ciphertext, escrow_key, nonce, aad_for(dedupe_key)) ?: return null
            return try {
                String(plaintext, Charsets.UTF_8)
            } finally {
                plaintext.fill(0)
            }
        }

        private fun open_bytes(ciphertext: ByteArray, escrow_key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray? =
            try {
                RatchetCrypto.aes_gcm_decrypt(ciphertext, escrow_key, nonce, aad)
            } catch (_: Throwable) {
                null
            }
    }
}
