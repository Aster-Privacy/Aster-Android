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

package org.astermail.android.settings

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.astermail.android.security.LockdownStore

const val profile_picture_gallery_base_url = "https://aster-wallpapers.pages.dev"

val profile_picture_gallery_categories: List<String> = listOf(
    "space",
    "night_sky",
    "water",
    "planets",
    "landscapes",
    "forest",
    "cities",
    "aurora",
    "mountains",
    "ocean",
    "desert",
)

private const val gallery_max_items = 2000
private const val gallery_timeout_seconds = 15L
private const val gallery_max_image_bytes = 5L * 1024 * 1024
private val gallery_slug_pattern = Regex("^[a-z0-9][a-z0-9_-]{0,80}$")
private val gallery_json = Json { ignoreUnknownKeys = true }

data class GalleryItem(
    val slug: String,
    val category: String,
)

class GalleryUnavailableException : IOException("gallery unavailable in lockdown")

fun is_gallery_slug(slug: String): Boolean = gallery_slug_pattern.matches(slug)

fun gallery_thumb_url(slug: String, base_url: String = profile_picture_gallery_base_url): String =
    "$base_url/thumb/$slug.webp"

fun parse_gallery_manifest(payload: String): List<GalleryItem> {
    val root = runCatching { gallery_json.parseToJsonElement(payload) }.getOrNull() as? JsonObject
        ?: return emptyList()
    val raw = root["items"] as? JsonArray ?: return emptyList()
    val seen = HashSet<String>()
    val items = ArrayList<GalleryItem>()
    for (entry in raw.take(gallery_max_items)) {
        val obj = entry as? JsonObject ?: continue
        val slug = (obj["slug"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
        val category = (obj["category"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
        if (!is_gallery_slug(slug)) continue
        if (category !in profile_picture_gallery_categories || slug in seen) continue
        seen.add(slug)
        items.add(GalleryItem(slug, category))
    }
    return items
}

fun gallery_categories_present(items: List<GalleryItem>): List<String> {
    val present = items.mapTo(HashSet()) { it.category }
    return profile_picture_gallery_categories.filter { it in present }
}

class ProfilePictureGallery(
    private val base_url: String = profile_picture_gallery_base_url,
    private val is_available: () -> Boolean,
    client: OkHttpClient? = null,
) {
    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(gallery_timeout_seconds, TimeUnit.SECONDS)
        .callTimeout(gallery_timeout_seconds, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private var cached: List<GalleryItem>? = null

    suspend fun load_manifest(): List<GalleryItem> = mutex.withLock {
        cached?.let { return@withLock it }
        if (!is_available()) throw GalleryUnavailableException()
        val items = withContext(Dispatchers.IO) {
            val body = get("$base_url/manifest.json").decodeToString()
            parse_gallery_manifest(body)
        }
        if (items.isEmpty()) throw IOException("gallery manifest empty")
        cached = items
        items
    }

    suspend fun clear_cache() = mutex.withLock { cached = null }

    suspend fun fetch_image(slug: String): ByteArray {
        if (!is_gallery_slug(slug)) throw IOException("gallery image not allowed")
        if (!is_available()) throw GalleryUnavailableException()
        return withContext(Dispatchers.IO) { get(gallery_thumb_url(slug, base_url)) }
    }

    private fun get(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("http ${response.code}")
            val body = response.body
            if (body.contentLength() > gallery_max_image_bytes) throw IOException("oversize")
            val out = java.io.ByteArrayOutputStream()
            body.byteStream().use { stream ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > gallery_max_image_bytes) throw IOException("oversize")
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray()
        }
    }
}

fun profile_picture_gallery_available_now(context: Context): Boolean =
    !LockdownStore.is_enabled(context.applicationContext)

object profile_picture_gallery_holder {

    @Volatile
    private var instance: ProfilePictureGallery? = null

    fun get(context: Context): ProfilePictureGallery {
        instance?.let { return it }
        val app = context.applicationContext
        return synchronized(this) {
            instance ?: ProfilePictureGallery(
                is_available = { profile_picture_gallery_available_now(app) },
            ).also { instance = it }
        }
    }
}
