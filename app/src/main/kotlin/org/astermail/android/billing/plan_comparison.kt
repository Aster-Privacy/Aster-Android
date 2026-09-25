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

package org.astermail.android.billing

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal const val PLAN_COMPARISON_SCHEMA = 1
internal const val PLAN_COMPARISON_BASE_URL = "https://astermail.org/plan-comparison"

internal data class plan_comparison_value(val included: Boolean, val text: String?)

internal data class plan_comparison_plan(val code: String, val name: String, val family: Boolean)

internal data class plan_comparison_row(
    val id: String,
    val label: String,
    val tip: String?,
    val values: Map<String, plan_comparison_value>,
) {
    fun value_for(code: String): plan_comparison_value = values[code] ?: plan_comparison_value(false, null)
}

internal data class plan_comparison_group(val id: String, val title: String, val rows: List<plan_comparison_row>)

internal data class plan_comparison_feed(
    val locale: String,
    val plans: List<plan_comparison_plan>,
    val groups: List<plan_comparison_group>,
) {
    fun plan(code: String): plan_comparison_plan? = plans.firstOrNull { it.code == code }

    fun row(id: String): plan_comparison_row? = groups.firstNotNullOfOrNull { group -> group.rows.firstOrNull { it.id == id } }

    fun groups_for(codes: List<String>, include_family: Boolean): List<plan_comparison_group> =
        groups.filter { include_family || it.id != PLAN_COMPARISON_FAMILY_GROUP }
            .map { group -> group.copy(rows = group.rows.filter { row -> codes.any { row.values.containsKey(it) } }) }
            .filter { it.rows.isNotEmpty() }

    val row_count: Int get() = groups.sumOf { it.rows.size }
}

internal const val PLAN_COMPARISON_FAMILY_GROUP = "family"

internal val plan_comparison_web_locales = listOf(
    "en", "ar", "fr", "de", "he", "id", "ja", "ko", "pt", "zh-CN", "es-419", "es", "th", "zh-TW", "vi", "hi",
)

internal val plan_comparison_bundled_locales = setOf("en", "ar", "de", "es", "fr", "hi", "ja", "ko", "pt", "zh-CN")

private val latin_american_regions = setOf(
    "419", "AR", "BO", "CL", "CO", "CR", "CU", "DO", "EC", "GT", "HN", "MX", "NI", "PA", "PE", "PR", "PY", "SV", "US", "UY", "VE",
)

internal fun plan_comparison_locale(locale: Locale): String {
    val language = when (locale.language) {
        "iw" -> "he"
        "in" -> "id"
        else -> locale.language
    }
    val region = locale.country.uppercase(Locale.ROOT)
    val candidate = when (language) {
        "zh" -> when {
            locale.script.equals("Hant", ignoreCase = true) -> "zh-TW"
            region in setOf("TW", "HK", "MO") -> "zh-TW"
            else -> "zh-CN"
        }
        "es" -> if (region in latin_american_regions) "es-419" else "es"
        else -> language
    }
    return if (candidate in plan_comparison_web_locales) candidate else "en"
}

private const val max_text_length = 400
private const val max_plans = 12
private const val max_groups = 32
private const val max_rows = 400

internal fun parse_plan_comparison(raw: String): plan_comparison_feed? = runCatching {
    val root = JSONObject(raw)
    if (root.optInt("schema", -1) != PLAN_COMPARISON_SCHEMA) return null
    val locale = root.optString("locale").trim().takeIf { it in plan_comparison_web_locales } ?: return null
    val plans_json = root.optJSONArray("plans") ?: return null
    val plans = ArrayList<plan_comparison_plan>()
    for (index in 0 until minOf(plans_json.length(), max_plans)) {
        val entry = plans_json.optJSONObject(index) ?: continue
        val code = entry.optString("code").trim()
        if (!is_safe_id(code) || plans.any { it.code == code }) continue
        val name = clean_text(entry.optString("name")) ?: continue
        plans += plan_comparison_plan(code = code, name = name, family = entry.optBoolean("family", false))
    }
    if (plans.isEmpty()) return null
    val codes = plans.map { it.code }.toSet()
    val groups_json = root.optJSONArray("groups") ?: return null
    val groups = ArrayList<plan_comparison_group>()
    var total_rows = 0
    for (group_index in 0 until minOf(groups_json.length(), max_groups)) {
        val group = groups_json.optJSONObject(group_index) ?: continue
        val group_id = group.optString("id").trim()
        if (!is_safe_id(group_id)) continue
        val title = clean_text(group.optString("title")) ?: continue
        val rows_json = group.optJSONArray("rows") ?: continue
        val rows = ArrayList<plan_comparison_row>()
        for (row_index in 0 until rows_json.length()) {
            if (total_rows >= max_rows) break
            val row = rows_json.optJSONObject(row_index) ?: continue
            val row_id = row.optString("id").trim()
            if (!is_safe_id(row_id)) continue
            val label = clean_text(row.optString("label")) ?: continue
            val tip = if (row.isNull("tip")) null else clean_text(row.optString("tip"))
            val values_json = row.optJSONObject("values") ?: continue
            val values = LinkedHashMap<String, plan_comparison_value>()
            for (code in codes) {
                if (!values_json.has(code)) continue
                values[code] = when (val value = values_json.opt(code)) {
                    is Boolean -> plan_comparison_value(included = value, text = null)
                    is String -> clean_text(value)?.let { plan_comparison_value(included = true, text = it) }
                        ?: plan_comparison_value(included = false, text = null)
                    else -> plan_comparison_value(included = false, text = null)
                }
            }
            if (values.isEmpty()) continue
            rows += plan_comparison_row(id = row_id, label = label, tip = tip, values = values)
            total_rows += 1
        }
        if (rows.isNotEmpty()) groups += plan_comparison_group(id = group_id, title = title, rows = rows)
    }
    if (groups.isEmpty()) return null
    plan_comparison_feed(locale = locale, plans = plans, groups = groups)
}.getOrNull()

private fun is_safe_id(value: String): Boolean =
    value.isNotEmpty() && value.length <= 64 && value.all { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }

private fun clean_text(value: String?): String? =
    value?.filterNot { Character.isISOControl(it) }?.trim()?.takeIf { it.isNotEmpty() }?.take(max_text_length)

object plan_comparison_store {
    private const val feed_max_bytes = 512L * 1024L
    private const val refresh_interval_ms = 6L * 60L * 60L * 1000L

    private val feed_state = MutableStateFlow<plan_comparison_feed?>(null)
    internal val feed: StateFlow<plan_comparison_feed?> = feed_state.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var loaded_locale: String? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun cache_file(context: Context, locale: String): File =
        File(File(context.noBackupFilesDir, "plan_comparison"), "$locale.json")

    private fun bundled(context: Context, locale: String): plan_comparison_feed? {
        val asset_locale = if (locale in plan_comparison_bundled_locales) locale else "en"
        return runCatching {
            context.assets.open("plan_comparison/$asset_locale.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let(::parse_plan_comparison)
    }

    suspend fun load(context: Context) {
        val locale = plan_comparison_locale(context.resources.configuration.locales[0] ?: Locale.getDefault())
        mutex.withLock {
            if (loaded_locale == locale && feed_state.value != null) return
            loaded_locale = locale
            withContext(Dispatchers.IO) {
                val cached = cache_file(context, locale)
                val from_cache = if (cached.isFile) runCatching { parse_plan_comparison(cached.readText()) }.getOrNull()?.takeIf { it.locale == locale } else null
                val initial = from_cache ?: bundled(context, locale)
                if (initial != null) feed_state.value = initial
                val stale = from_cache == null || System.currentTimeMillis() - cached.lastModified() > refresh_interval_ms
                if (stale) refresh(context, locale)
            }
        }
    }

    private fun refresh(context: Context, locale: String) {
        val body = runCatching { fetch(locale) }.getOrNull() ?: return
        val parsed = parse_plan_comparison(body)?.takeIf { it.locale == locale } ?: return
        val cached = cache_file(context, locale)
        cached.parentFile?.mkdirs()
        runCatching { cached.writeText(body) }
        feed_state.value = parsed
    }

    private fun fetch(locale: String): String {
        val request = Request.Builder()
            .url("$PLAN_COMPARISON_BASE_URL/$locale.json")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("http ${response.code}")
            val body = response.body
            if (body.contentLength() > feed_max_bytes) throw IOException("oversize")
            val bytes = body.byteStream().use { stream -> stream.readNBytesCompat(feed_max_bytes + 1) }
            if (bytes.size > feed_max_bytes) throw IOException("oversize")
            return String(bytes, Charsets.UTF_8)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (total < limit) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }
}
