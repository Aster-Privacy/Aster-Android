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

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.astermail.android.api.labels.CreateLabelRequest
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.crypto.AesGcm
import org.astermail.android.storage.SessionKeyStore

@Singleton
class ImportFolderResolver @Inject constructor(
    private val labels_api: LabelsApi,
    private val session_key_store: SessionKeyStore,
) {
    suspend fun resolve(folder_names: List<String>): Map<String, String> {
        val wanted = folder_names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_IMPORT_FOLDERS)
        if (wanted.isEmpty()) return emptyMap()
        val identity_key = session_key_store.get_identity_key()?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val labels = labels_api.list_labels(include_counts = false).labels
        val by_name = LinkedHashMap<String, String>()
        var next_sort = 0
        for (label in labels) {
            if (label.label_token.isBlank()) continue
            next_sort = maxOf(next_sort, label.sort_order + 1)
            val name = decrypt_name(label, identity_key) ?: continue
            by_name.putIfAbsent(name.lowercase(Locale.ROOT), label.label_token)
        }
        val resolved = LinkedHashMap<String, String>()
        for (name in wanted) {
            val existing = by_name[name.lowercase(Locale.ROOT)]
            if (existing != null) {
                resolved[name] = existing
                continue
            }
            val token = generate_token_b64()
            val encrypted = encrypt_field(name, identity_key)
            val created = try {
                labels_api.create_label(
                    CreateLabelRequest(
                        label_token = token,
                        encrypted_name = encrypted.first,
                        name_nonce = encrypted.second,
                        folder_type = "folder",
                        sort_order = next_sort,
                    ),
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
            if (created) {
                next_sort += 1
                resolved[name] = token
                by_name[name.lowercase(Locale.ROOT)] = token
            }
        }
        return resolved
    }

    private fun decrypt_name(label: LabelItem, identity_key: String): String? {
        val encrypted = label.encrypted_name?.takeIf { it.isNotBlank() } ?: return null
        val nonce = label.name_nonce?.takeIf { it.isNotBlank() } ?: return null
        val ciphertext = runCatching { Base64.decode(encrypted, Base64.DEFAULT) }.getOrNull() ?: return null
        val nonce_bytes = runCatching { Base64.decode(nonce, Base64.DEFAULT) }.getOrNull() ?: return null
        for (version in FIELD_VERSIONS) {
            val key = derive_field_key(identity_key, version)
            try {
                val plain = AesGcm.decrypt(key, nonce_bytes, ciphertext)
                return String(plain, Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
            } catch (_: Throwable) {
            } finally {
                key.fill(0)
            }
        }
        return null
    }

    private fun encrypt_field(plaintext: String, identity_key: String): Pair<String, String> {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive_field_key(identity_key, FIELD_VERSIONS.first())
        try {
            val ciphertext = AesGcm.encrypt(key, nonce, plaintext.toByteArray(Charsets.UTF_8))
            return encode_b64(ciphertext) to encode_b64(nonce)
        } finally {
            key.fill(0)
        }
    }

    private fun derive_field_key(identity_key: String, version: String): ByteArray {
        val material = (identity_key + version).toByteArray(Charsets.UTF_8)
        val key = MessageDigest.getInstance("SHA-256").digest(material)
        material.fill(0)
        return key
    }

    private fun generate_token_b64(): String = encode_b64(ByteArray(16).also { SecureRandom().nextBytes(it) })

    private fun encode_b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    companion object {
        private val FIELD_VERSIONS = listOf("astermail-labels-v1", "astermail-tags-v1")
    }
}
