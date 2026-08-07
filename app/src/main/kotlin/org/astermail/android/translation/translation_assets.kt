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
    private const val ASSET_DIR = "bergamot"
    private const val MODEL_RELAY_BASE = "https://relay.astermail.org/models/bergamot/v1"
    private const val CACHE_DIR = "bergamot_models"
    private const val MAX_CACHE_BYTES = 600L * 1024L * 1024L
    private const val MAX_MODEL_BYTES = 128L * 1024L * 1024L
    private const val EVICTION_GRACE_MS = 60_000L

    private val model_lock = Any()

    fun serve(
        context: Context,
        host: String?,
        path: String?,
        allow_models: Boolean,
    ): WebResourceResponse? {
        if (host != CONTENT_HOST || path == null) return null
        return when {
            path.startsWith(ASSET_PREFIX) -> serve_asset(context, path.removePrefix(ASSET_PREFIX))
            path.startsWith(MODEL_PREFIX) ->
                if (allow_models) serve_model(context, path.removePrefix(MODEL_PREFIX)) else null
            else -> null
        }
    }

    private fun serve_asset(context: Context, name: String): WebResourceResponse? {
        val safe = sanitize(name) ?: return null
        return try {
            val stream = context.assets.open("$ASSET_DIR/$safe")
            response(mime_for(safe), stream.readBytes())
        } catch (_: Throwable) {
            null
        }
    }

    private fun serve_model(context: Context, relative: String): WebResourceResponse? {
        val safe = sanitize(relative) ?: return null
        val cache_root = File(context.filesDir, CACHE_DIR)
        val target = File(cache_root, safe)
        synchronized(model_lock) {
            if (!target.exists() || target.length() == 0L) {
                if (!download(safe, target)) return null
                trim_cache(cache_root)
            }
            target.setLastModified(System.currentTimeMillis())
        }
        return try {
            response(mime_for(safe), target.readBytes())
        } catch (_: Throwable) {
            null
        }
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
            if (file.delete()) total -= size
        }
    }

    private fun download(relative: String, target: File): Boolean {
        val url = "$MODEL_RELAY_BASE/$relative"
        var connection: HttpURLConnection? = null
        return try {
            target.parentFile?.mkdirs()
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                requestMethod = "GET"
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
            if (connection.contentLengthLong > MAX_MODEL_BYTES) return false
            val tmp = File(target.parentFile, target.name + ".part")
            val copied = connection.inputStream.use { input ->
                tmp.outputStream().use { output -> copy_capped(input, output) }
            }
            if (!copied) {
                tmp.delete()
                return false
            }
            tmp.renameTo(target)
        } catch (_: Throwable) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun copy_capped(input: java.io.InputStream, output: java.io.OutputStream): Boolean {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            total += read
            if (total > MAX_MODEL_BYTES) return false
            output.write(buffer, 0, read)
        }
    }

    fun clear_cache(context: Context): Boolean {
        return File(context.filesDir, CACHE_DIR).deleteRecursively()
    }

    fun cached_bytes(context: Context): Long {
        val root = File(context.filesDir, CACHE_DIR)
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun sanitize(name: String): String? {
        if (name.isBlank()) return null
        if (name.contains("..") || name.startsWith("/")) return null
        return name
    }

    private fun mime_for(name: String): String = when {
        name.endsWith(".js") -> "text/javascript"
        name.endsWith(".wasm") -> "application/wasm"
        name.endsWith(".json") -> "application/json"
        name.endsWith(".spm") -> "application/octet-stream"
        name.endsWith(".bin") -> "application/octet-stream"
        else -> "application/octet-stream"
    }

    private fun response(mime: String, bytes: ByteArray): WebResourceResponse {
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "no-store",
        )
        return WebResourceResponse(
            mime,
            "UTF-8",
            200,
            "OK",
            headers,
            ByteArrayInputStream(bytes),
        )
    }
}
