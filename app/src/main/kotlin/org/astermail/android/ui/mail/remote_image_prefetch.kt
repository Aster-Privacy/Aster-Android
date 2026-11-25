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

import android.content.Context
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.astermail.android.api.network.low_network_state
import org.astermail.android.api.network.should_render_plain_text
import org.astermail.android.api.preferences.UserPreferences

internal const val PREFETCH_MAX_MESSAGES = 20
internal const val PREFETCH_MAX_IMAGES_PER_MESSAGE = 30
internal const val PREFETCH_INTERVAL_MS = 2_000L
internal const val PREFETCH_MAX_CONSECUTIVE_FAILURES = 3

internal enum class prefetch_outcome { stored, skipped, failed }

internal fun arrival_prefetch_allowed(
    settings: remote_image_cache_settings,
    preferences: UserPreferences?,
    unmetered: Boolean,
    low_network: Boolean = false,
): Boolean =
    settings.keep_images &&
        settings.prefetch_on_arrival &&
        unmetered &&
        preferences != null &&
        !should_render_plain_text(preferences.html_rendering_mode, low_network) &&
        effective_remote_image_mode(preferences) == REMOTE_IMAGES_ALWAYS

internal fun prefetch_image_urls(html: String?, limit: Int = PREFETCH_MAX_IMAGES_PER_MESSAGE): List<String> {
    if (html.isNullOrBlank()) return emptyList()
    return EmailHtmlSanitizer.analyze_trackers(html).image_urls.asSequence()
        .map { it.trim() }
        .filter { source ->
            val lower = source.lowercase()
            lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("//")
        }
        .map { proxied_image_target(it, REMOTE_IMAGE_PROXY_BASE) }
        .filter { is_image_proxy_url(it) }
        .filterNot { EmailHtmlSanitizer.is_tracker_url(remote_image_cache_key(it) ?: it) }
        .distinctBy { remote_image_cache_key(it) }
        .take(limit)
        .toList()
}

internal suspend fun prefetch_remote_images(
    bodies: List<String?>,
    allowed: () -> Boolean,
    is_cached: (String) -> Boolean,
    fetch: suspend (String) -> prefetch_outcome,
    max_messages: Int = PREFETCH_MAX_MESSAGES,
    max_images_per_message: Int = PREFETCH_MAX_IMAGES_PER_MESSAGE,
    interval_ms: Long = PREFETCH_INTERVAL_MS,
    max_consecutive_failures: Int = PREFETCH_MAX_CONSECUTIVE_FAILURES,
    pause: suspend (Long) -> Unit = { delay(it) },
): Int {
    if (!runCatching(allowed).getOrDefault(false)) return 0
    val targets = bodies.asSequence()
        .take(max_messages)
        .flatMap { prefetch_image_urls(it, max_images_per_message).asSequence() }
        .distinctBy { remote_image_cache_key(it) }
        .toList()
    var stored = 0
    var failures = 0
    var requested = false
    for (url in targets) {
        if (!runCatching(allowed).getOrDefault(false)) break
        if (runCatching { is_cached(url) }.getOrDefault(true)) continue
        if (requested) pause(interval_ms)
        if (!runCatching(allowed).getOrDefault(false)) break
        requested = true
        when (runCatching { fetch(url) }.getOrDefault(prefetch_outcome.failed)) {
            prefetch_outcome.stored -> {
                stored++
                failures = 0
            }
            prefetch_outcome.skipped -> failures = 0
            prefetch_outcome.failed -> {
                failures++
                if (failures >= max_consecutive_failures) break
            }
        }
    }
    return stored
}

internal object arrival_image_prefetch {
    private val running = AtomicBoolean(false)

    private fun on_unmetered_network(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !manager.isActiveNetworkMetered
    }.getOrDefault(false)

    private fun allowed_now(context: Context, preferences: () -> UserPreferences?, account: String): Boolean =
        remote_image_cache.account_id(context) == account &&
            arrival_prefetch_allowed(
                settings = remote_image_cache.current_settings(context),
                preferences = preferences(),
                unmetered = on_unmetered_network(context),
                low_network = low_network_state.extend_timeouts(),
            )

    private fun fetch_and_store(context: Context, account: String, url: String): prefetch_outcome {
        val generation = remote_image_cache.generation(context) ?: return prefetch_outcome.skipped
        val response = fetch_proxied_image(context, url) ?: return prefetch_outcome.failed
        response.use { resp ->
            if (resp.code != 200) return prefetch_outcome.failed
            val body = resp.body ?: return prefetch_outcome.failed
            if (body.contentLength() > REMOTE_IMAGE_CACHE_MAX_ENTRY_BYTES) return prefetch_outcome.skipped
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    if (out.size() + read > REMOTE_IMAGE_CACHE_MAX_ENTRY_BYTES) return prefetch_outcome.skipped
                    out.write(chunk, 0, read)
                }
            }
            if (remote_image_cache.account_id(context) != account) return prefetch_outcome.skipped
            val saved = remote_image_cache.remember(
                context,
                account,
                url,
                resp.code,
                resp.header("Content-Type"),
                out.toByteArray(),
                expected_generation = generation,
            )
            return if (saved) prefetch_outcome.stored else prefetch_outcome.skipped
        }
    }

    suspend fun on_bodies_arrived(context: Context, preferences: () -> UserPreferences?, bodies: List<String?>): Int {
        if (bodies.isEmpty()) return 0
        val app_context = context.applicationContext
        val account = remote_image_cache.account_id(app_context) ?: return 0
        if (!allowed_now(app_context, preferences, account)) return 0
        if (!running.compareAndSet(false, true)) return 0
        return try {
            withContext(Dispatchers.IO) {
                prefetch_remote_images(
                    bodies = bodies,
                    allowed = { allowed_now(app_context, preferences, account) },
                    is_cached = { remote_image_cache.contains(app_context, account, it) },
                    fetch = { url -> fetch_and_store(app_context, account, url) },
                )
            }
        } finally {
            running.set(false)
        }
    }
}
