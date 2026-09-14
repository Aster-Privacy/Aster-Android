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

import org.astermail.android.api.send.ExternalAttachmentPayload

internal const val DRAFT_MAX_SIZE_BYTES = 52_428_800L
internal const val DRAFT_MAX_ATTACHMENT_COUNT = 100
internal const val DRAFT_ATTACHMENTS_KEY = "attachments"
private const val DRAFT_CIPHER_OVERHEAD_BYTES = 44L

data class DraftAttachmentFile(
    val path: String,
    val name: String,
    val size_bytes: Long,
    val mime_type: String,
)

class E2eEncryptionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class AttachmentPrepareException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

internal fun draft_size_label(size_bytes: Long): String = when {
    size_bytes < 1024L -> "$size_bytes B"
    size_bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", size_bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", size_bytes / (1024.0 * 1024.0))
}

internal fun draft_attachments_json(attachments: List<ExternalAttachmentPayload>): org.json.JSONArray =
    org.json.JSONArray().apply {
        attachments.forEach { att ->
            put(
                org.json.JSONObject().apply {
                    put("id", java.util.UUID.randomUUID().toString())
                    put("name", att.filename)
                    put("size", draft_size_label(att.size_bytes))
                    put("size_bytes", att.size_bytes)
                    put("mime_type", att.content_type)
                    put("data_base64", att.data)
                    att.content_id?.takeIf { it.isNotBlank() }?.let { put("content_id", it) }
                },
            )
        }
    }

internal fun parse_draft_attachments(obj: org.json.JSONObject): List<ExternalAttachmentPayload> {
    val array = obj.optJSONArray(DRAFT_ATTACHMENTS_KEY) ?: return emptyList()
    val result = ArrayList<ExternalAttachmentPayload>(array.length())
    for (index in 0 until array.length()) {
        val entry = array.optJSONObject(index) ?: continue
        val data = entry.optString("data_base64", "")
        if (data.isBlank()) continue
        val name = entry.optString("name", "").ifBlank { entry.optString("filename", "") }
        val content_id = entry.optString("content_id", "").takeIf { it.isNotBlank() }
        result.add(
            ExternalAttachmentPayload(
                data = data,
                filename = name.ifBlank { "attachment" },
                content_type = entry.optString("mime_type", "").ifBlank { "application/octet-stream" },
                size_bytes = entry.optLong("size_bytes", 0L).takeIf { it > 0L } ?: (data.length * 3L / 4L),
                content_id = content_id,
            ),
        )
    }
    return result
}

internal fun parse_draft_attachments(json_str: String): List<ExternalAttachmentPayload> =
    runCatching { parse_draft_attachments(org.json.JSONObject(json_str)) }.getOrDefault(emptyList())

internal fun encrypted_draft_length(plaintext_utf8_bytes: Long): Long =
    4L * ((plaintext_utf8_bytes + DRAFT_CIPHER_OVERHEAD_BYTES + 2L) / 3L)

internal fun utf8_length(text: String): Long {
    var total = 0L
    var index = 0
    while (index < text.length) {
        val ch = text[index]
        total += when {
            ch.code < 0x80 -> 1
            ch.code < 0x800 -> 2
            Character.isHighSurrogate(ch) && index + 1 < text.length && Character.isLowSurrogate(text[index + 1]) -> {
                index++
                4
            }
            else -> 3
        }
        index++
    }
    return total
}

internal fun draft_attachments_may_fit(attachments: List<ExternalAttachmentPayload>): Boolean {
    if (attachments.isEmpty()) return true
    if (attachments.size > DRAFT_MAX_ATTACHMENT_COUNT) return false
    val data_chars = attachments.sumOf { it.data.length.toLong() }
    return encrypted_draft_length(data_chars) <= DRAFT_MAX_SIZE_BYTES
}

internal fun draft_envelope_fits(envelope_json: String): Boolean =
    encrypted_draft_length(utf8_length(envelope_json)) <= DRAFT_MAX_SIZE_BYTES

internal fun armored_public_key_from_private(armored_private_key: String?): String? {
    val source = armored_private_key?.trim().orEmpty()
    if (!source.startsWith("-----BEGIN PGP PRIVATE KEY")) return null
    return runCatching {
        val secret_ring = org.bouncycastle.openpgp.PGPSecretKeyRing(
            org.bouncycastle.openpgp.PGPUtil.getDecoderStream(source.byteInputStream()),
            org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator(),
        )
        val out = java.io.ByteArrayOutputStream()
        org.bouncycastle.bcpg.ArmoredOutputStream(out).use { armored ->
            secret_ring.publicKeys.forEach { it.encode(armored) }
        }
        String(out.toByteArray(), Charsets.US_ASCII)
    }.getOrNull()?.takeIf { it.contains("BEGIN PGP PUBLIC KEY") }
}

internal fun normalize_own_address(address: String): String = address.trim().lowercase()

internal fun safe_draft_file_name(index: Int, name: String): String {
    val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().take(120)
    return "${index}_${cleaned.ifBlank { "attachment" }}"
}

internal fun materialize_draft_attachments(
    dir: java.io.File,
    attachments: List<ExternalAttachmentPayload>,
): List<DraftAttachmentFile> {
    if (dir.exists()) dir.deleteRecursively()
    if (attachments.isEmpty()) return emptyList()
    dir.mkdirs()
    return attachments.mapIndexedNotNull { index, att ->
        runCatching {
            val bytes = android.util.Base64.decode(att.data, android.util.Base64.DEFAULT)
            val file = java.io.File(dir, safe_draft_file_name(index, att.filename))
            file.writeBytes(bytes)
            DraftAttachmentFile(
                path = file.absolutePath,
                name = att.filename,
                size_bytes = bytes.size.toLong(),
                mime_type = att.content_type,
            )
        }.getOrNull()
    }
}
