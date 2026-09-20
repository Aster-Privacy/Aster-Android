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

package org.astermail.android.ui.theme

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

enum class RemoteThemeKind(val folder: String) {
    thumb("thumb"),
    full("full"),
}

object remote_theme_store {
    const val base_url = "https://aster-wallpapers.pages.dev"

    private const val cache_budget_bytes = 192L * 1024 * 1024
    private const val retry_backoff_ms = 20_000L
    private const val max_entry_bytes = 12L * 1024 * 1024

    private val locks = ConcurrentHashMap<String, Any>()
    private val failures = ConcurrentHashMap<String, Long>()

    private val in_flight = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = in_flight.asStateFlow()

    private val failed_keys = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = failed_keys.asStateFlow()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun key_of(slug: String, kind: RemoteThemeKind): String = "${kind.folder}/$slug"

    private fun root(context: Context): File = File(File(context.noBackupFilesDir, "theme"), "remote")

    fun file_for(context: Context, slug: String, kind: RemoteThemeKind): File =
        File(File(root(context), kind.folder), "$slug.webp")

    fun is_cached(context: Context, slug: String, kind: RemoteThemeKind): Boolean =
        file_for(context, slug, kind).let { it.isFile && it.length() > 0L }

    fun clear_failure(slug: String, kind: RemoteThemeKind) {
        val key = key_of(slug, kind)
        failures.remove(key)
        failed_keys.value = failed_keys.value - key
    }

    fun ensure(context: Context, slug: String, kind: RemoteThemeKind): File? {
        val target = file_for(context, slug, kind)
        if (target.isFile && target.length() > 0L) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        val key = key_of(slug, kind)
        failures[key]?.let { at ->
            if (System.currentTimeMillis() - at < retry_backoff_ms) return null
        }
        val lock = locks.getOrPut(key) { Any() }
        synchronized(lock) {
            if (target.isFile && target.length() > 0L) return target
            mark_started(key)
            try {
                download(target, "$base_url/${kind.folder}/$slug.webp")
                failures.remove(key)
                failed_keys.value = failed_keys.value - key
                prune(context)
                return target
            } catch (error: IOException) {
                failures[key] = System.currentTimeMillis()
                failed_keys.value = failed_keys.value + key
                return null
            } finally {
                mark_finished(key)
            }
        }
    }

    private fun mark_started(key: String) {
        in_flight.value = in_flight.value + key
    }

    private fun mark_finished(key: String) {
        in_flight.value = in_flight.value - key
    }

    private fun download(target: File, url: String) {
        target.parentFile?.mkdirs()
        val staging = File(target.parentFile, "${target.name}.part")
        staging.delete()
        val request = Request.Builder().url(url).header("Accept", "image/webp").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("http ${response.code}")
            val body = response.body
            val declared = body.contentLength()
            if (declared > max_entry_bytes) throw IOException("oversize $declared")
            staging.outputStream().use { sink -> body.byteStream().copyTo(sink) }
        }
        if (staging.length() <= 0L) {
            staging.delete()
            throw IOException("zero length")
        }
        if (staging.length() > max_entry_bytes) {
            staging.delete()
            throw IOException("oversize ${staging.length()}")
        }
        if (!staging.renameTo(target)) {
            staging.delete()
            throw IOException("rename failed")
        }
    }

    private fun prune(context: Context) {
        val entries = root(context).walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.toList()
        var total = entries.sumOf { it.length() }
        if (total <= cache_budget_bytes) return
        val busy = in_flight.value
        entries.sortedBy { it.lastModified() }.forEach { candidate ->
            if (total <= cache_budget_bytes) return
            val key = "${candidate.parentFile?.name}/${candidate.nameWithoutExtension}"
            if (key in busy) return@forEach
            val size = candidate.length()
            if (candidate.delete()) total -= size
        }
    }

    fun clear(context: Context) {
        root(context).deleteRecursively()
        failures.clear()
        failed_keys.value = emptySet()
    }
}
