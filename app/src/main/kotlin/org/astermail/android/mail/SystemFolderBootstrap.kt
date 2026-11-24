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

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.R
import org.astermail.android.api.labels.CreateLabelRequest
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.crypto.AesGcm
import org.astermail.android.storage.SessionKeyStore

data class SystemFolderSpec(val folder_type: String, val name_res: Int, val sort_order: Int)

val SYSTEM_FOLDER_SPECS: List<SystemFolderSpec> = listOf(
    SystemFolderSpec("inbox", R.string.folder_inbox, 0),
    SystemFolderSpec("sent", R.string.folder_sent, 1),
    SystemFolderSpec("drafts", R.string.folder_drafts, 2),
    SystemFolderSpec("trash", R.string.folder_trash, 3),
    SystemFolderSpec("spam", R.string.folder_spam, 4),
    SystemFolderSpec("archive", R.string.folder_archive, 5),
)

@Singleton
class SystemFolderBootstrap @Inject constructor(
    private val labels_api: LabelsApi,
    private val session_key_store: SessionKeyStore,
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    suspend fun ensure_system_folders(attempts: Int = DEFAULT_ATTEMPTS): Map<String, String> = mutex.withLock {
        val total = attempts.coerceAtLeast(1)
        var tokens: Map<String, String> = emptyMap()
        for (attempt in 0 until total) {
            val is_last = attempt == total - 1
            tokens = try {
                create_missing_folders()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (is_last) throw t
                tokens
            }
            if (has_every_system_folder(tokens)) return@withLock tokens
            if (session_key_store.get_identity_key().isNullOrBlank()) return@withLock tokens
            if (!is_last) delay(RETRY_DELAYS_MS[attempt.coerceAtMost(RETRY_DELAYS_MS.size - 1)])
        }
        tokens
    }

    private suspend fun create_missing_folders(): Map<String, String> {
        val existing = LinkedHashMap<String, String>()
        for (label in labels_api.list_labels(include_counts = false).labels) {
            if (label.label_token.isBlank() || existing.containsKey(label.folder_type)) continue
            if (SYSTEM_FOLDER_SPECS.none { it.folder_type == label.folder_type }) continue
            existing[label.folder_type] = label.label_token
        }
        val missing = SYSTEM_FOLDER_SPECS.filter { !existing.containsKey(it.folder_type) }
        if (missing.isEmpty()) return existing
        val identity_key = session_key_store.get_identity_key()?.takeIf { it.isNotBlank() }
            ?: return existing
        for (spec in missing) {
            val token = generate_token_b64()
            val name = encrypt_field(context.getString(spec.name_res), identity_key)
            val created = try {
                labels_api.create_label(
                    CreateLabelRequest(
                        label_token = token,
                        encrypted_name = name.ciphertext_b64,
                        name_nonce = name.nonce_b64,
                        folder_type = spec.folder_type,
                        sort_order = spec.sort_order,
                    ),
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
            if (created) existing[spec.folder_type] = token
        }
        return existing
    }

    private data class EncryptedField(val ciphertext_b64: String, val nonce_b64: String)

    private fun encrypt_field(plaintext: String, identity_key: String): EncryptedField {
        val data = plaintext.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive_field_key(identity_key)
        try {
            val ciphertext = AesGcm.encrypt(key, nonce, data)
            data.fill(0)
            return EncryptedField(encode_b64(ciphertext), encode_b64(nonce))
        } finally {
            key.fill(0)
        }
    }

    private fun derive_field_key(identity_key: String): ByteArray {
        val material = (identity_key + FIELD_VERSION).toByteArray(Charsets.UTF_8)
        val key = MessageDigest.getInstance("SHA-256").digest(material)
        material.fill(0)
        return key
    }

    private fun generate_token_b64(): String =
        encode_b64(ByteArray(16).also { SecureRandom().nextBytes(it) })

    private fun encode_b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    companion object {
        private const val FIELD_VERSION = "astermail-labels-v1"
        const val DEFAULT_ATTEMPTS = 3
        private val RETRY_DELAYS_MS = longArrayOf(2_000L, 8_000L)

        fun has_every_system_folder(tokens: Map<String, String>): Boolean =
            SYSTEM_FOLDER_SPECS.all { !tokens[it.folder_type].isNullOrBlank() }
    }
}
