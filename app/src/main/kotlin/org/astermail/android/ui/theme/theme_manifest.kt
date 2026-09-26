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
import androidx.compose.ui.graphics.Color
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.astermail.android.design.ColorThemeId
import org.json.JSONObject

object theme_manifest {
    private const val manifest_max_bytes = 2 * 1024 * 1024
    private const val refresh_interval_ms = 12L * 60L * 60L * 1000L

    private val catalog_state = MutableStateFlow(bundled_theme_backgrounds)
    val catalog: StateFlow<List<ThemeBackground>> = catalog_state.asStateFlow()

    private val grouped_state = MutableStateFlow(group(bundled_theme_backgrounds))
    val categories: StateFlow<List<Pair<ThemeCategory, List<ThemeBackground>>>> = grouped_state.asStateFlow()

    @Volatile private var loaded = false

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private fun group(items: List<ThemeBackground>): List<Pair<ThemeCategory, List<ThemeBackground>>> =
        ThemeCategory.entries
            .map { category -> category to items.filter { it.category == category } }
            .filter { it.second.isNotEmpty() }

    private fun cache_file(context: Context): File =
        File(File(context.noBackupFilesDir, "theme"), "manifest.json")

    private fun publish(items: List<ThemeBackground>) {
        if (items.isEmpty()) return
        catalog_state.value = items
        grouped_state.value = group(items)
    }

    suspend fun load_cached(context: Context) {
        withContext(Dispatchers.IO) {
            val cached = cache_file(context)
            if (cached.isFile) {
                runCatching { parse(cached.readText()) }.getOrNull()?.let(::publish)
            }
        }
    }

    suspend fun load(context: Context) {
        if (loaded) return
        loaded = true
        withContext(Dispatchers.IO) {
            val cached = cache_file(context)
            if (cached.isFile) {
                runCatching { parse(cached.readText()) }.getOrNull()?.let(::publish)
            }
            val stale = !cached.isFile || System.currentTimeMillis() - cached.lastModified() > refresh_interval_ms
            if (stale) refresh(context)
        }
    }

    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        val body = runCatching { fetch() }.getOrNull() ?: return@withContext false
        val parsed = runCatching { parse(body) }.getOrNull() ?: return@withContext false
        if (parsed.isEmpty()) return@withContext false
        val cached = cache_file(context)
        cached.parentFile?.mkdirs()
        runCatching { cached.writeText(body) }
        publish(parsed)
        true
    }

    private fun fetch(): String {
        val request = Request.Builder()
            .url("${remote_theme_store.base_url}/manifest.json")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("http ${response.code}")
            val body = response.body
            if (body.contentLength() > manifest_max_bytes) throw IOException("oversize")
            return body.string()
        }
    }

    internal fun parse(raw: String): List<ThemeBackground> {
        val root = JSONObject(raw)
        val items = root.optJSONArray("items") ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<ThemeBackground>(items.length())
        for (index in 0 until items.length()) {
            val entry = items.optJSONObject(index) ?: continue
            val slug = entry.optString("slug").trim()
            if (slug.isEmpty() || !slug.all { it.isDigit() || it in 'a'..'z' || it == '_' }) continue
            if (!seen.add(slug)) continue
            val category = ThemeCategory.entries.firstOrNull {
                it.name == entry.optString("category") && it != ThemeCategory.yours
            } ?: continue
            val credit = entry.optString("credit").trim()
            if (credit.isEmpty()) continue
            out += ThemeBackground(
                id = slug,
                category = category,
                color_theme = ColorThemeId.from_key(entry.optString("accent")),
                tint = parse_tint(entry.optString("tint")),
                credit = credit,
            )
        }
        return out
    }

    private fun parse_tint(raw: String): Color {
        val hex = raw.removePrefix("#").removePrefix("0x")
        if (hex.length != 8) return fallback_tint
        val value = hex.toULongOrNull(16) ?: return fallback_tint
        return Color(value.toLong())
    }

    private val fallback_tint = Color(0xFF1E1E1E)
}
