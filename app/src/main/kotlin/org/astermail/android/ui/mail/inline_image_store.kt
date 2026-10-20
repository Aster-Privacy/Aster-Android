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

package org.astermail.android.ui.mail

internal const val INLINE_IMAGE_PATH_PREFIX = "/__aster_inline/"

internal const val INLINE_IMAGE_URL_PREFIX =
    "https://${org.astermail.android.translation.TranslationAssets.CONTENT_HOST}$INLINE_IMAGE_PATH_PREFIX"

internal class InlineImageEntry(val content_type: String, val bytes: ByteArray)

internal class InlineImageReloader(
    val content_id: String,
    val content_type: String,
    val load: () -> ByteArray?,
)

internal object InlineImageStore {

    private const val STORE_MAX_TOTAL_BYTES = 32 * 1024 * 1024

    private val entries = object : LinkedHashMap<String, InlineImageEntry>(16, 0.75f, true) {}

    private const val STORE_MAX_RELOADERS = 512

    private val reloaders = object : LinkedHashMap<String, InlineImageReloader>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, InlineImageReloader>?): Boolean =
            size > STORE_MAX_RELOADERS
    }

    private var total_bytes = 0

    @Synchronized
    fun register_reloader(key: String, reloader: InlineImageReloader) {
        reloaders[key] = reloader
    }

    fun entry_or_reload(key: String): InlineImageEntry? {
        get(key)?.let { return it }
        val reloader = synchronized(this) { reloaders[key] } ?: return null
        val bytes = runCatching { reloader.load() }.getOrNull() ?: return null
        if (bytes.isEmpty() || content_key(reloader.content_id, bytes) != key) return null
        put(key, reloader.content_type, bytes)
        return get(key) ?: InlineImageEntry(reloader.content_type, bytes)
    }

    @Synchronized
    fun put(key: String, content_type: String, bytes: ByteArray) {
        val existing = entries.remove(key)
        if (existing != null) total_bytes -= existing.bytes.size
        entries[key] = InlineImageEntry(content_type, bytes)
        total_bytes += bytes.size
        val iterator = entries.entries.iterator()
        while (total_bytes > STORE_MAX_TOTAL_BYTES && iterator.hasNext()) {
            val oldest = iterator.next()
            if (oldest.key == key) continue
            total_bytes -= oldest.value.bytes.size
            iterator.remove()
        }
    }

    @Synchronized
    fun get(key: String): InlineImageEntry? = entries[key]

    @Synchronized
    fun clear() {
        entries.values.forEach { it.bytes.fill(0) }
        entries.clear()
        reloaders.clear()
        total_bytes = 0
    }

    fun url_for(key: String): String = INLINE_IMAGE_URL_PREFIX + key

    fun key_for_path(path: String?): String? {
        if (path == null || !path.startsWith(INLINE_IMAGE_PATH_PREFIX)) return null
        return path.removePrefix(INLINE_IMAGE_PATH_PREFIX).takeIf { it.isNotBlank() }
    }

    fun entry_for_source(src: String): InlineImageEntry? {
        if (!src.startsWith(INLINE_IMAGE_URL_PREFIX)) return null
        val key = src.removePrefix(INLINE_IMAGE_URL_PREFIX).substringBefore('?').takeIf { it.isNotBlank() }
            ?: return null
        return entry_or_reload(key)
    }

    fun content_key(content_id: String, bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(content_id.toByteArray(Charsets.UTF_8))
        digest.update(bytes)
        return digest.digest().take(16).joinToString("") { byte -> "%02x".format(byte) }
    }
}

internal fun inline_image_response(path: String?): android.webkit.WebResourceResponse? {
    val key = InlineImageStore.key_for_path(path) ?: return null
    val entry = InlineImageStore.entry_or_reload(key)
        ?: return android.webkit.WebResourceResponse(
            "text/plain",
            null,
            404,
            "Not Found",
            mapOf("Cache-Control" to "no-store"),
            java.io.ByteArrayInputStream(ByteArray(0)),
        )
    return android.webkit.WebResourceResponse(
        entry.content_type,
        null,
        200,
        "OK",
        mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
        java.io.ByteArrayInputStream(entry.bytes),
    )
}
