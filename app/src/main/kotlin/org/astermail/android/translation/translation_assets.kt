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

package org.astermail.android.translation

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object TranslationAssets {
    const val CONTENT_HOST = "mail-content.invalid"
    const val ASSET_PREFIX = "/bergamot/"
    const val MODEL_PREFIX = "/models/bergamot/v1/"
    const val REGISTRY_NAME = "registry.json"
    const val BUNDLED_REGISTRY_SHA256 = "e40d8bf8686a2ed186f6dff665b602ad934d37b267e9c2bb800c3a543a161c29"
    private const val ASSET_DIR = "bergamot"
    private const val MODEL_RELAY_HOST = "relay.astermail.org"
    private const val MODEL_RELAY_BASE = "https://$MODEL_RELAY_HOST/models/bergamot/v1"
    private const val MAX_REDIRECTS = 3
    private const val CACHE_DIR = "bergamot_models"
    private const val MAX_CACHE_BYTES = 600L * 1024L * 1024L
    private const val MAX_MODEL_BYTES = 128L * 1024L * 1024L
    private const val EVICTION_GRACE_MS = 60_000L
    private const val MAX_REGISTRY_BYTES = 1L * 1024L * 1024L
    private const val ENGINE_ORIGIN = "https://$CONTENT_HOST"

    private val model_lock = Any()
    private val verified_files = HashSet<String>()

    @Volatile
    private var registry_cache: Map<String, String>? = null

    fun serve(
        context: Context,
        host: String?,
        path: String?,
    ): WebResourceResponse? {
        if (host != CONTENT_HOST || path == null) return null
        return when {
            path.startsWith(ASSET_PREFIX) -> serve_asset(context, path.removePrefix(ASSET_PREFIX))
            path.startsWith(MODEL_PREFIX) -> serve_model(context, path.removePrefix(MODEL_PREFIX))
            else -> null
        }
    }

    private fun serve_asset(context: Context, name: String): WebResourceResponse? {
        val safe = sanitize(name) ?: return null
        if (safe == REGISTRY_NAME) return null
        return try {
            val bytes = context.assets.open("$ASSET_DIR/$safe").use { it.readBytes() }
            response(mime_for(safe), bytes)
        } catch (_: Throwable) {
            null
        }
    }

    private fun serve_registry(context: Context): WebResourceResponse? {
        val bytes = bundled_registry_bytes(context) ?: return null
        return response(mime_for(REGISTRY_NAME), bytes)
    }

    private fun bundled_registry_bytes(context: Context): ByteArray? {
        val bytes = try {
            context.assets.open("$ASSET_DIR/$REGISTRY_NAME").use { it.readBytes() }
        } catch (_: Throwable) {
            return null
        }
        if (bytes.size > MAX_REGISTRY_BYTES) return null
        if (sha256_hex(bytes) != BUNDLED_REGISTRY_SHA256) return null
        return bytes
    }

    internal fun registry_hashes(context: Context): Map<String, String>? {
        registry_cache?.let { return it }
        val bytes = bundled_registry_bytes(context) ?: return null
        val parsed = read_registry_hashes(String(bytes, Charsets.UTF_8)) ?: return null
        registry_cache = parsed
        return parsed
    }

    private fun serve_model(context: Context, relative: String): WebResourceResponse? {
        val safe = sanitize(relative) ?: return null
        if (safe == REGISTRY_NAME) return serve_registry(context)
        val expected = registry_hashes(context)?.get(safe) ?: return null
        val cache_root = File(context.filesDir, CACHE_DIR)
        val target = File(cache_root, safe)
        synchronized(model_lock) {
            if (target.exists() && !cached_file_valid(target, safe, expected)) {
                target.delete()
                verified_files.remove(safe)
            }
            if (!target.exists() || target.length() == 0L) {
                if (!fetch_model(context, safe, target, expected)) return null
                verified_files.add(safe)
                trim_cache(cache_root)
            }
            target.setLastModified(System.currentTimeMillis())
        }
        return try {
            if (target.length() > MAX_MODEL_BYTES) return null
            response(mime_for(safe), target.inputStream())
        } catch (_: Throwable) {
            null
        }
    }

    private fun cached_file_valid(target: File, safe: String, expected: String): Boolean {
        if (verified_files.contains(safe)) return true
        if (target.length() == 0L || target.length() > MAX_MODEL_BYTES) return false
        val ok = runCatching {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            hex(digest.digest()) == expected
        }.getOrDefault(false)
        if (ok) verified_files.add(safe)
        return ok
    }

    private fun trim_cache(cache_root: File) {
        if (!cache_root.exists()) return
        val files = cache_root.walkTopDown().filter { it.isFile }.toMutableList()
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        val now = System.currentTimeMillis()
        val evictable = files
            .filter { now - it.lastModified() > EVICTION_GRACE_MS }
            .sortedBy { it.lastModified() }
        for (file in evictable) {
            if (total <= MAX_CACHE_BYTES) break
            val size = file.length()
            if (file.delete()) {
                total -= size
                verified_files.remove(file.relativeTo(cache_root).invariantSeparatorsPath)
            }
        }
    }

    internal fun pack_of(name: String): String? {
        val pack = name.substringBefore('/', "")
        if (pack.length != 4 || !pack.all { it in 'a'..'z' }) return null
        return pack
    }

    private fun fetch_model(
        context: Context,
        safe: String,
        target: File,
        expected: String,
    ): Boolean {
        val pack = pack_of(safe) ?: return false
        if (!TranslationDownloadPolicy.pack_consent_granted(context, pack)) return false
        if (TranslationDownloadPolicy.download_blocked(context)) return false
        return download(safe, target, expected)
    }

    internal fun read_registry_hashes(text: String): Map<String, String>? {
        return try {
            val root = org.json.JSONObject(text)
            val hashes = HashMap<String, String>()
            for (pair in root.keys()) {
                val entry = root.optJSONObject(pair) ?: continue
                for (kind in entry.keys()) {
                    val file = entry.optJSONObject(kind) ?: continue
                    val name = file.optString("name")
                    val hash = file.optString("expectedSha256Hash").lowercase()
                    if (name.isNotBlank() && sanitize(name) == name && is_sha256_hex(hash)) {
                        hashes[name] = hash
                    }
                }
            }
            if (hashes.isEmpty()) null else hashes
        } catch (_: Throwable) {
            null
        }
    }

    internal fun is_sha256_hex(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun download(relative: String, target: File, expected_hash: String): Boolean {
        val url = "$MODEL_RELAY_BASE/$relative"
        var connection: HttpURLConnection? = null
        val tmp = File(target.parentFile, target.name + ".part")
        return try {
            target.parentFile?.mkdirs()
            connection = open_pinned_connection(url) ?: return false
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
            if (connection.contentLengthLong > MAX_MODEL_BYTES) return false
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val copied = connection.inputStream.use { input ->
                tmp.outputStream().use { output -> copy_capped(input, output, digest) }
            }
            if (!copied) return false
            if (hex(digest.digest()) != expected_hash) return false
            tmp.renameTo(target)
        } catch (_: Throwable) {
            false
        } finally {
            connection?.disconnect()
            if (tmp.exists()) tmp.delete()
        }
    }

    internal fun sha256_hex(bytes: ByteArray): String =
        hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) out.append("%02x".format(byte))
        return out.toString()
    }

    private fun open_pinned_connection(initial_url: String): HttpURLConnection? {
        var current = URL(initial_url)
        var hops = 0
        while (hops <= MAX_REDIRECTS) {
            if (!current.protocol.equals("https", ignoreCase = true)) return null
            if (!current.host.equals(MODEL_RELAY_HOST, ignoreCase = true)) return null
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                requestMethod = "GET"
                instanceFollowRedirects = false
            }
            val code = connection.responseCode
            val is_redirect = code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            if (!is_redirect) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) return null
            current = runCatching { URL(current, location) }.getOrNull() ?: return null
            hops++
        }
        return null
    }

    private fun copy_capped(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        digest: java.security.MessageDigest,
    ): Boolean {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            total += read
            if (total > MAX_MODEL_BYTES) return false
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
    }

    fun packs_cached(context: Context, packs: List<String>): Boolean {
        if (packs.isEmpty()) return true
        val cache_root = File(context.filesDir, CACHE_DIR)
        val names = registry_hashes(context)?.keys ?: return false
        return packs.all { pack ->
            val files = names.filter { it.startsWith("$pack/") }
            files.isNotEmpty() && files.all { File(cache_root, it).length() > 0L }
        }
    }

    fun clear_cache(context: Context): Boolean {
        synchronized(model_lock) {
            verified_files.clear()
            return File(context.filesDir, CACHE_DIR).deleteRecursively()
        }
    }

    fun cached_bytes(context: Context): Long {
        val root = File(context.filesDir, CACHE_DIR)
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    internal fun sanitize(name: String): String? {
        if (name.isBlank()) return null
        if (name.contains("..") || name.startsWith("/") || name.contains('\\')) return null
        if (name.any { it.code < 0x20 }) return null
        if (!name.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' || it == '/' }) return null
        return name
    }

    private fun mime_for(name: String): String = when {
        name.endsWith(".js") -> "text/javascript"
        name.endsWith(".wasm") -> "application/wasm"
        name.endsWith(".json") -> "application/json"
        else -> "application/octet-stream"
    }

    private fun response(mime: String, bytes: ByteArray): WebResourceResponse =
        response(mime, ByteArrayInputStream(bytes))

    private fun response(mime: String, stream: java.io.InputStream): WebResourceResponse {
        val headers = mapOf(
            "Access-Control-Allow-Origin" to ENGINE_ORIGIN,
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
        )
        return WebResourceResponse(
            mime,
            "UTF-8",
            200,
            "OK",
            headers,
            stream,
        )
    }
}
