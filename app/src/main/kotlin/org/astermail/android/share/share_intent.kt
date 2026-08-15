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

package org.astermail.android.share

import android.content.Context
import android.content.Intent
import android.net.MailTo
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import java.io.File
import org.astermail.android.billing.AttachmentLimits

private const val share_cache_dir_name = "shared_attachments"
private const val share_cache_ttl_ms = 24L * 60 * 60 * 1000

data class SharePayload(
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val stream_uris: List<Uri> = emptyList(),
) {
    val is_empty: Boolean
        get() = to.isEmpty() && cc.isEmpty() && bcc.isEmpty() &&
            subject.isBlank() && body.isBlank() && stream_uris.isEmpty()
}

data class SharedAttachment(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime_type: String,
)

sealed class AttachmentImport {
    data class Imported(val attachment: SharedAttachment) : AttachmentImport()

    data class TooLarge(val name: String) : AttachmentImport()

    data class Failed(val name: String) : AttachmentImport()
}

fun parse_share_intent(intent: Intent?): SharePayload? {
    if (intent == null) return null
    val payload = when (intent.action) {
        Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> parse_send_intent(intent)
        Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> parse_mailto_intent(intent)
        else -> null
    } ?: return null
    return payload.takeIf { !it.is_empty }
}

private fun parse_send_intent(intent: Intent): SharePayload {
    val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
    val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
    val to = address_list(intent, Intent.EXTRA_EMAIL)
    val cc = address_list(intent, Intent.EXTRA_CC)
    val bcc = address_list(intent, Intent.EXTRA_BCC)
    val streams = stream_uris(intent)
    val mailto = to.firstOrNull()?.let { first ->
        if (first.startsWith("mailto:", ignoreCase = true)) parse_mailto_uri(Uri.parse(first)) else null
    }
    if (mailto != null) {
        return mailto.copy(
            subject = subject.ifBlank { mailto.subject },
            body = body.ifBlank { mailto.body },
            stream_uris = streams,
        )
    }
    return SharePayload(
        to = to,
        cc = cc,
        bcc = bcc,
        subject = subject,
        body = body,
        stream_uris = streams,
    )
}

private fun stream_uris(intent: Intent): List<Uri> {
    val collected = mutableListOf<Uri>()
    val many = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    if (many != null) {
        collected.addAll(many.filterNotNull())
    } else {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
            collected.add(it)
        }
    }
    val clip = intent.clipData
    if (clip != null) {
        for (index in 0 until clip.itemCount) {
            clip.getItemAt(index).uri?.let { collected.add(it) }
        }
    }
    return collected.filter { is_attachable_uri(it) }.distinct()
}

private fun is_attachable_uri(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase()
    return scheme == "content" || scheme == "file"
}

private fun parse_mailto_intent(intent: Intent): SharePayload? {
    val data = intent.data ?: return null
    if (!data.scheme.equals("mailto", ignoreCase = true)) return null
    val from_uri = parse_mailto_uri(data) ?: return null
    val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
    val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
    val extra_to = address_list(intent, Intent.EXTRA_EMAIL)
    val extra_cc = address_list(intent, Intent.EXTRA_CC)
    val extra_bcc = address_list(intent, Intent.EXTRA_BCC)
    return from_uri.copy(
        to = (from_uri.to + extra_to).distinct(),
        cc = (from_uri.cc + extra_cc).distinct(),
        bcc = (from_uri.bcc + extra_bcc).distinct(),
        subject = from_uri.subject.ifBlank { subject },
        body = from_uri.body.ifBlank { body },
    )
}

private fun parse_mailto_uri(uri: Uri): SharePayload? {
    val parsed = runCatching { MailTo.parse(uri.toString()) }.getOrNull() ?: return null
    return SharePayload(
        to = split_addresses(parsed.to),
        cc = split_addresses(parsed.cc),
        bcc = split_addresses(mailto_query_value(uri, "bcc")),
        subject = parsed.subject.orEmpty(),
        body = parsed.body.orEmpty(),
    )
}

private fun mailto_query_value(uri: Uri, key: String): String {
    val ssp = uri.encodedSchemeSpecificPart ?: return ""
    val query = ssp.substringAfter('?', "")
    if (query.isBlank()) return ""
    return query.split('&')
        .mapNotNull { pair ->
            val name = pair.substringBefore('=', "")
            if (!name.equals(key, ignoreCase = true)) return@mapNotNull null
            runCatching { Uri.decode(pair.substringAfter('=', "")) }.getOrNull()
        }
        .filter { it.isNotBlank() }
        .joinToString(",")
}

private fun address_list(intent: Intent, key: String): List<String> {
    val array = intent.getStringArrayExtra(key)
    if (array != null) return array.flatMap { split_addresses(it) }
    val single = intent.getCharSequenceExtra(key)?.toString()
    return split_addresses(single)
}

private fun split_addresses(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(',', ';')
        .map { it.trim().removePrefix("mailto:").trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

fun import_shared_attachment(context: Context, uri: Uri): AttachmentImport {
    val resolver = context.contentResolver
    var name = ""
    var size = 0L
    runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name_index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val size_index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (name_index >= 0) name = cursor.getString(name_index).orEmpty()
                if (size_index >= 0 && !cursor.isNull(size_index)) size = cursor.getLong(size_index)
            }
        }
    }
    if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    if (name.isBlank()) name = "attachment"
    name = sanitize_file_name(name)
    if (!is_attachable_uri(uri) || is_private_app_file(context, uri)) return AttachmentImport.Failed(name)
    if (size == 0L) {
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                size = descriptor.length.takeIf { it > 0 } ?: 0L
            }
        }
    }
    if (size > AttachmentLimits.max_bytes()) return AttachmentImport.TooLarge(name)
    val mime = resolver.getType(uri) ?: guess_mime_from_name(name)
    name = ensure_extension(name, mime)
    val target_dir = File(context.cacheDir, share_cache_dir_name)
    if (!target_dir.exists() && !target_dir.mkdirs()) return AttachmentImport.Failed(name)
    prune_share_cache(target_dir)
    val target = File(target_dir, System.nanoTime().toString() + "_" + name)
    val copied = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching -1L
        target.length()
    }.getOrDefault(-1L)
    if (copied < 0) {
        target.delete()
        return AttachmentImport.Failed(name)
    }
    if (copied > AttachmentLimits.max_bytes()) {
        target.delete()
        return AttachmentImport.TooLarge(name)
    }
    val provider_uri = runCatching {
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
    }.getOrNull()
    if (provider_uri == null) {
        target.delete()
        return AttachmentImport.Failed(name)
    }
    return AttachmentImport.Imported(
        SharedAttachment(uri = provider_uri, name = name, size = copied, mime_type = mime),
    )
}

private fun is_private_app_file(context: Context, uri: Uri): Boolean {
    if (uri.scheme.equals("content", ignoreCase = true)) {
        return uri.authority == context.packageName + ".fileprovider"
    }
    if (!uri.scheme.equals("file", ignoreCase = true)) return false
    val path = runCatching { File(uri.path.orEmpty()).canonicalPath }.getOrNull() ?: return true
    val private_roots = listOfNotNull(
        runCatching { context.dataDir.canonicalPath }.getOrNull(),
        runCatching { context.filesDir.canonicalPath }.getOrNull(),
        runCatching { context.cacheDir.canonicalPath }.getOrNull(),
        runCatching { context.noBackupFilesDir.canonicalPath }.getOrNull(),
    )
    return private_roots.any { root -> path == root || path.startsWith(root + File.separator) }
}

private fun guess_mime_from_name(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension.isBlank()) return "application/octet-stream"
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}

private fun ensure_extension(name: String, mime: String): String {
    if (name.substringAfterLast('.', "").isNotBlank()) return name
    val extension = android.webkit.MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(mime.substringBefore(';').trim().lowercase())
    if (extension.isNullOrBlank()) return name
    return name + "." + extension
}

private fun sanitize_file_name(raw: String): String {
    val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trimStart('.')
    val safe = cleaned.ifBlank { "attachment" }
    return if (safe.length > 120) safe.takeLast(120) else safe
}

private fun prune_share_cache(dir: File) {
    val cutoff = System.currentTimeMillis() - share_cache_ttl_ms
    runCatching {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }
}
