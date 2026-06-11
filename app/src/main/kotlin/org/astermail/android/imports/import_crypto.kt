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

package org.astermail.android.imports

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.api.imports.ImportedEmailData

private const val IMPORT_KEY_VERSION = "astermail-import-v1"
private const val NONCE_LENGTH = 12
private const val GCM_TAG_BITS = 128

@Serializable
data class RawHeader(val name: String, val value: String)

@Serializable
data class ImportEnvelope(
    val message_id: String,
    val from: String,
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val sent_at: String,
    val date: String,
    val body_html: String? = null,
    val body_text: String? = null,
    val html_body: String? = null,
    val text_body: String? = null,
    val attachment_count: Int = 0,
    val source: String,
    val imported_at: String,
    val reply_to: String? = null,
    val list_unsubscribe: String? = null,
    val list_unsubscribe_post: String? = null,
    val raw_headers: List<RawHeader>? = null,
)

private val json = Json {
    encodeDefaults = true
    explicitNulls = false
}

fun derive_import_key(identity_key: String): SecretKeySpec {
    val material = (identity_key + IMPORT_KEY_VERSION).toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(material)
    material.fill(0)
    return SecretKeySpec(digest, "AES")
}

fun hash_message_id(message_id: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(message_id.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(digest, Base64.NO_WRAP)
}

fun encrypt_envelope(
    envelope: ImportEnvelope,
    key: SecretKeySpec,
    received_at: String,
    thread_token: String? = null,
    item_type: String? = null,
    folder_token: String? = null,
    content_hash: String? = null,
): ImportedEmailData {
    val nonce = ByteArray(NONCE_LENGTH).also { SecureRandom().nextBytes(it) }
    val plaintext = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
    val ciphertext = cipher.doFinal(plaintext)
    plaintext.fill(0)

    return ImportedEmailData(
        message_id_hash = hash_message_id(envelope.message_id),
        encrypted_envelope = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        envelope_nonce = Base64.encodeToString(nonce, Base64.NO_WRAP),
        folder_token = folder_token,
        content_hash = content_hash,
        item_type = item_type,
        received_at = received_at,
        thread_token = thread_token,
    )
}
