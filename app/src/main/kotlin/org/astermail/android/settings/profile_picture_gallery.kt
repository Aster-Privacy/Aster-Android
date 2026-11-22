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
import coil.ImageLoader
import coil.imageLoader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.astermail.android.AsterApplication
import org.astermail.android.security.LockdownStore

const val profile_picture_gallery_base_url = "https://app.astermail.org/gallery"
const val profile_picture_gallery_fallback_base_url = "https://aster-wallpapers.pages.dev"

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
private const val gallery_max_credit_length = 200
private val gallery_credit_control_characters =
    Regex("""[\u0000-\u001f\u007f-\u009f\u200e\u200f\u202a-\u202e\u2066-\u2069]""")
private val gallery_credit_whitespace =
    Regex("""[\s\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+""")

data class GalleryItem(
    val slug: String,
    val category: String,
    val credit: String? = null,
)

class GalleryUnavailableException : IOException("gallery unavailable in lockdown")

fun is_gallery_slug(slug: String): Boolean = gallery_slug_pattern.matches(slug)

fun gallery_thumb_url(slug: String, base_url: String = profile_picture_gallery_base_url): String =
    "$base_url/thumb/$slug.webp"

fun gallery_full_url(slug: String, base_url: String = profile_picture_gallery_base_url): String =
    "$base_url/full/$slug.webp"

fun parse_gallery_credit(value: JsonElement?): String? {
    val primitive = value as? JsonPrimitive ?: return null
    if (!primitive.isString) return null
    val credit = primitive.content
        .replace(gallery_credit_control_characters, " ")
        .replace(gallery_credit_whitespace, " ")
        .trim()
        .take(gallery_max_credit_length)
        .trim()
    return credit.ifEmpty { null }
}

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
        items.add(GalleryItem(slug, category, parse_gallery_credit(obj["credit"])))
    }
    return items
}

fun gallery_categories_present(items: List<GalleryItem>): List<String> {
    val present = items.mapTo(HashSet()) { it.category }
    return profile_picture_gallery_categories.filter { it in present }
}

private fun gallery_origin_only(base_urls: List<String>): Interceptor {
    val bases = base_urls.map { it.toHttpUrl() }
    return Interceptor { chain ->
        val url = chain.request().url
        if (bases.none { url.scheme == it.scheme && url.host == it.host && url.port == it.port }) {
            throw IOException("gallery host not allowed")
        }
        chain.proceed(chain.request().newBuilder().removeHeader("Authorization").build())
    }
}

fun gallery_base_urls(
    base_url: String = profile_picture_gallery_base_url,
    fallback_base_url: String? = profile_picture_gallery_fallback_base_url,
): List<String> = listOfNotNull(base_url, fallback_base_url?.takeIf { it != base_url })

private fun gallery_http_builder(base_urls: List<String>): OkHttpClient.Builder = OkHttpClient.Builder()
    .cookieJar(CookieJar.NO_COOKIES)
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(gallery_timeout_seconds, TimeUnit.SECONDS)
    .callTimeout(gallery_timeout_seconds, TimeUnit.SECONDS)
    .addInterceptor(gallery_origin_only(base_urls))

fun gallery_thumb_client(
    shared: OkHttpClient,
    base_url: String = profile_picture_gallery_base_url,
    fallback_base_url: String? = profile_picture_gallery_fallback_base_url,
): OkHttpClient = gallery_http_builder(gallery_base_urls(base_url, fallback_base_url))
    .dns(shared.dns)
    .dispatcher(shared.dispatcher)
    .connectionPool(shared.connectionPool)
    .build()

class ProfilePictureGallery(
    base_url: String = profile_picture_gallery_base_url,
    private val is_available: () -> Boolean,
    client: OkHttpClient? = null,
    fallback_base_url: String? = profile_picture_gallery_fallback_base_url,
) {
    private val base_urls: List<String> = gallery_base_urls(base_url, fallback_base_url)
    private val http: OkHttpClient = client ?: gallery_http_builder(base_urls).build()

    private val mutex = Mutex()
    private var cached: List<GalleryItem>? = null

    @Volatile
    private var active_base: String = base_urls.first()

    fun thumb_url(slug: String): String = gallery_thumb_url(slug, active_base)

    suspend fun load_manifest(): List<GalleryItem> = mutex.withLock {
        cached?.let { return@withLock it }
        if (!is_available()) throw GalleryUnavailableException()
        val (base, items) = withContext(Dispatchers.IO) {
            var last_error: IOException? = null
            for (base in base_urls) {
                if (!is_available()) throw GalleryUnavailableException()
                val found = try {
                    curate_gallery_items(parse_gallery_manifest(get("$base/manifest.json").decodeToString()))
                } catch (error: IOException) {
                    last_error = error
                    continue
                }
                if (found.isNotEmpty()) return@withContext base to found
                last_error = IOException("gallery manifest empty")
            }
            throw last_error ?: IOException("gallery manifest empty")
        }
        active_base = base
        cached = items
        items
    }

    suspend fun clear_cache() = mutex.withLock { cached = null }

    suspend fun fetch_image(slug: String): ByteArray {
        if (!is_gallery_slug(slug)) throw IOException("gallery image not allowed")
        if (!is_available()) throw GalleryUnavailableException()
        return withContext(Dispatchers.IO) {
            val bases = listOf(active_base) + base_urls.filter { it != active_base }
            var last_error: IOException? = null
            for (base in bases) {
                for (url in listOf(gallery_full_url(slug, base), gallery_thumb_url(slug, base))) {
                    if (!is_available()) throw GalleryUnavailableException()
                    try {
                        return@withContext get(url)
                    } catch (error: IOException) {
                        last_error = error
                    }
                }
            }
            throw last_error ?: IOException("gallery image unavailable")
        }
    }

    private fun get(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("http ${response.code}")
            val body = response.body
            if (body.contentType()?.subtype == "html") throw IOException("gallery page missing")
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

    @Volatile
    private var thumb_loader: ImageLoader? = null

    fun image_loader(context: Context): ImageLoader {
        thumb_loader?.let { return it }
        val app = context.applicationContext
        val shared_loader = app.imageLoader
        val shared_client = (app as? AsterApplication)?.image_http_client ?: return shared_loader
        return synchronized(this) {
            thumb_loader ?: shared_loader.newBuilder()
                .okHttpClient(gallery_thumb_client(shared_client))
                .build()
                .also { thumb_loader = it }
        }
    }

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
