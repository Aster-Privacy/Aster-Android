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

import org.astermail.android.BuildConfig
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.astermail.android.R
import org.astermail.android.api.BuildConfig as ApiBuildConfig
import org.astermail.android.api.auth.PublicProfile
import org.astermail.android.mail.AsterProfileResolverHolder
import org.astermail.android.mail.is_aster_domain

private val FAVICON_BASE = "${ApiBuildConfig.API_BASE_URL}/api/images/v1/favicon/"

private val TWO_PART_TLDS = setOf(
    "co.uk", "co.jp", "co.kr", "co.nz", "co.za", "co.in", "co.id", "co.th",
    "co.il", "co.ke", "co.tz", "co.ug", "co.ao", "co.bw", "co.cr", "co.ve",
    "com.au", "com.br", "com.cn", "com.mx", "com.sg", "com.hk", "com.tw",
    "com.ar", "com.co", "com.my", "com.ph", "com.pk", "com.tr", "com.ua",
    "com.vn", "com.eg", "com.ng", "com.pe", "com.ec", "com.bd", "com.kw",
    "com.sa", "com.qa", "com.om", "com.lb", "com.gt", "com.do", "com.uy",
    "net.au", "net.br", "net.cn", "net.nz", "org.au", "org.uk", "org.nz",
    "org.za", "org.br", "org.cn", "ac.uk", "ac.jp", "ac.kr", "ac.nz",
    "ac.za", "ac.in", "gov.uk", "gov.au", "gov.br", "gov.cn", "edu.au",
    "edu.cn", "edu.hk", "ne.jp", "or.jp", "or.kr",
)

fun get_root_domain(domain: String): String {
    val parts = domain.lowercase().split(".")
    if (parts.size <= 2) return domain.lowercase()
    val last_two = parts.takeLast(2).joinToString(".")
    return if (TWO_PART_TLDS.contains(last_two)) {
        parts.takeLast(3).joinToString(".")
    } else {
        last_two
    }
}

fun get_favicon_url(domain: String): String = "$FAVICON_BASE$domain"

private const val FAVICON_MISS_TTL_MS = 30L * 60L * 1000L
private const val FAVICON_TRANSIENT_TTL_MS = 20L * 1000L
private const val FAVICON_MAX_ATTEMPTS = 3
private const val FAVICON_RETRY_BASE_DELAY_MS = 700L

private val favicon_blocked_until = java.util.concurrent.ConcurrentHashMap<String, Long>()

private fun favicon_recently_missed(root_domain: String): Boolean {
    val blocked_until = favicon_blocked_until[root_domain] ?: return false
    if (System.currentTimeMillis() < blocked_until) return true
    favicon_blocked_until.remove(root_domain)
    return false
}

private fun mark_favicon_missed(root_domain: String, ttl_ms: Long) {
    favicon_blocked_until[root_domain] = System.currentTimeMillis() + ttl_ms
}

private fun clear_favicon_miss(root_domain: String) {
    favicon_blocked_until.remove(root_domain)
}

private fun http_status_of(error: Throwable?): Int? =
    (error as? coil.network.HttpException)?.response?.code

internal fun is_definitive_favicon_miss(error: Throwable?): Boolean =
    http_status_of(error).let { it == 404 || it == 410 }

internal fun is_cancelled_favicon_load(error: Throwable?): Boolean =
    error is java.util.concurrent.CancellationException

internal fun decode_avatar_model(url: String): Any {
    if (!url.startsWith("data:")) return url
    val comma = url.indexOf(',')
    if (comma < 0) return url
    val meta = url.substring(5, comma)
    val payload = url.substring(comma + 1)
    val bytes = if (meta.contains("base64", ignoreCase = true)) {
        decode_base64_payload(payload)
    } else {
        runCatching { android.net.Uri.decode(payload).toByteArray(Charsets.UTF_8) }.getOrNull()
    }
    return bytes?.takeIf { it.isNotEmpty() } ?: url
}

private fun decode_base64_payload(payload: String): ByteArray? {
    val cleaned = payload.filterNot { it.isWhitespace() }
    if (cleaned.isEmpty()) return null
    return runCatching { Base64.decode(cleaned, Base64.DEFAULT) }.getOrNull()
        ?: runCatching { Base64.decode(cleaned, Base64.URL_SAFE) }.getOrNull()
}

@Composable
fun SenderAvatar(
    email: String,
    name: String = "",
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    profile_picture_url: String? = null,
) {
    val context = LocalContext.current
    if (!profile_picture_url.isNullOrBlank()) {
        val seed_fb = avatar_seed_for(email, name)
        val (bg_fb, fg_fb) = avatar_colors_for(seed_fb)
        var loaded_pp by remember(profile_picture_url) { mutableStateOf(false) }
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(if (loaded_pp) Color.Transparent else bg_fb),
            contentAlignment = Alignment.Center,
        ) {
            if (!loaded_pp) {
                Text(
                    text = initial_for(name, email),
                    color = fg_fb,
                    style = avatar_initial_style(size),
                )
            }
            val model = remember(profile_picture_url) { decode_avatar_model(profile_picture_url) }
            val request = remember(profile_picture_url, model) {
                ImageRequest.Builder(context)
                    .data(model)
                    .memoryCacheKey(profile_picture_url)
                    .placeholderMemoryCacheKey(profile_picture_url)
                    .diskCacheKey(profile_picture_url)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = name.ifBlank { null },
                contentScale = ContentScale.Crop,
                onState = { state ->
                    loaded_pp = state is coil.compose.AsyncImagePainter.State.Success
                },
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
        return
    }

    val seed = avatar_seed_for(email, name)
    val (bg, fg) = avatar_colors_for(seed)
    val domain = remember(email) { extract_domain(email) }
    val root_domain = remember(domain) { get_root_domain(domain) }

    if (root_domain.isBlank()) {
        initials_circle(name, email, bg, fg, size, modifier)
        return
    }

    if (is_aster_domain(root_domain)) {
        if (is_system_email(email)) {
            AsterSystemAvatar(size = size, modifier = modifier)
            return
        }
        AsterDomainAvatar(email = email, name = name, size = size, modifier = modifier)
        return
    }

    if (remember(root_domain) { favicon_recently_missed(root_domain) }) {
        initials_circle(name, email, bg, fg, size, modifier)
        return
    }

    val url = remember(root_domain) { "$FAVICON_BASE$root_domain" }
    var loaded by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableStateOf(0) }
    var pending_attempt by remember(url) { mutableStateOf(0) }

    LaunchedEffect(url, pending_attempt) {
        if (pending_attempt <= attempt) return@LaunchedEffect
        kotlinx.coroutines.delay(FAVICON_RETRY_BASE_DELAY_MS * pending_attempt)
        attempt = pending_attempt
    }

    Box(
        modifier = modifier.size(size).clip(CircleShape).background(if (loaded) Color.White else bg),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = initial_for(name, email),
                color = fg,
                style = avatar_initial_style(size),
            )
        }
        val request = remember(url, attempt) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(url)
                .placeholderMemoryCacheKey(url)
                .diskCacheKey("favicon:$root_domain")
                .setParameter("retry_attempt", attempt, memoryCacheKey = null)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { state ->
                loaded = state is coil.compose.AsyncImagePainter.State.Success
                if (loaded) clear_favicon_miss(root_domain)
                if (state is coil.compose.AsyncImagePainter.State.Error) {
                    val error = state.result.throwable
                    when {
                        is_cancelled_favicon_load(error) -> Unit
                        is_definitive_favicon_miss(error) -> mark_favicon_missed(root_domain, FAVICON_MISS_TTL_MS)
                        pending_attempt < FAVICON_MAX_ATTEMPTS -> pending_attempt += 1
                        else -> mark_favicon_missed(root_domain, FAVICON_TRANSIENT_TTL_MS)
                    }
                }
            },
            modifier = Modifier.size(size).clip(CircleShape),
        )
    }
}

private val SYSTEM_LOCAL_PARTS = setOf(
    "mailer-daemon",
    "mail-daemon",
    "maildaemon",
    "postmaster",
    "no-reply",
    "noreply",
    "do-not-reply",
    "donotreply",
    "abuse",
    "system",
    "updates",
    "mail",
    "support",
    "security",
    "billing",
    "info",
    "hello",
    "team",
    "notifications",
    "newsletter",
    "marketing",
    "announce",
    "announcements",
)

fun is_system_email(email: String): Boolean {
    val at_idx = email.indexOf('@')
    val local = if (at_idx >= 0) email.substring(0, at_idx) else email
    return SYSTEM_LOCAL_PARTS.contains(local.trim().lowercase())
}

@Composable
private fun AsterSystemAvatar(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.aster_favicon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size * 0.78f),
        )
    }
}

@Composable
private fun AsterDomainAvatar(
    email: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolver = AsterProfileResolverHolder.shared
    val lower_email = remember(email) { email.trim().lowercase() }

    LaunchedEffect(lower_email, resolver) {
        resolver?.request(lower_email)
    }

    val fallback_profiles = remember { MutableStateFlow(emptyMap<String, PublicProfile?>()) }
    val profiles_flow = resolver?.profiles ?: fallback_profiles
    val own_profile_flow = remember(profiles_flow, lower_email) {
        profiles_flow
            .map { it[lower_email] }
            .distinctUntilChanged()
    }
    val profile by own_profile_flow.collectAsStateWithLifecycle(
        initialValue = profiles_flow.value[lower_email],
    )

    val resolved_pic = profile?.profile_picture?.takeIf { it.isNotBlank() }
    val (aster_bg, aster_fg) = avatar_colors_for(avatar_seed_for(email, name), profile?.profile_color)
    if (resolved_pic != null) {
        var loaded by remember(resolved_pic) { mutableStateOf(false) }
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(if (loaded) Color.Transparent else aster_bg),
            contentAlignment = Alignment.Center,
        ) {
            if (!loaded) {
                Text(
                    text = initial_for(name, email),
                    color = aster_fg,
                    style = avatar_initial_style(size),
                )
            }
            val model = remember(resolved_pic) { decode_avatar_model(resolved_pic) }
            val request = remember(resolved_pic, model) {
                ImageRequest.Builder(context)
                    .data(model)
                    .memoryCacheKey(resolved_pic)
                    .placeholderMemoryCacheKey(resolved_pic)
                    .diskCacheKey(resolved_pic)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = name.ifBlank { null },
                contentScale = ContentScale.Crop,
                onState = { state ->
                    loaded = state is coil.compose.AsyncImagePainter.State.Success
                },
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
        return
    }

    initials_circle(name, email, aster_bg, aster_fg, size, modifier)
}

@Composable
private fun initials_circle(
    name: String,
    email: String,
    bg: Color,
    fg: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial_for(name, email),
            color = fg,
            style = avatar_initial_style(size),
        )
    }
}

@Composable
fun StackedAvatars(
    participants: List<Pair<String, String>>,
    size: Dp = 40.dp,
    max_visible: Int = 3,
    modifier: Modifier = Modifier,
) {
    val list = if (participants.isEmpty()) emptyList() else participants
    if (list.size <= 1) {
        val first = list.firstOrNull() ?: ("" to "")
        val (nm, em) = first
        androidx.compose.runtime.key(em.lowercase().ifBlank { nm.lowercase() }) {
            SenderAvatar(email = em, name = nm, size = size, modifier = modifier)
        }
        return
    }
    val take = list.take(max_visible.coerceAtMost(3))
    val each_size = if (take.size == 2) (size.value * 0.66f).dp else (size.value * 0.48f).dp
    val alignments = when (take.size) {
        2 -> listOf(Alignment.TopStart, Alignment.BottomEnd)
        else -> listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomCenter)
    }
    Box(modifier = modifier.size(width = size, height = (size.value * 1.25f).dp)) {
        take.forEachIndexed { idx, (nm, em) ->
            androidx.compose.runtime.key(em.lowercase().ifBlank { nm.lowercase() }) {
                SenderAvatar(
                    email = em,
                    name = nm,
                    size = each_size,
                    modifier = Modifier.align(alignments[idx]),
                )
            }
        }
    }
}

private fun extract_domain(email: String): String {
    val at_idx = email.indexOf('@')
    if (at_idx < 0 || at_idx == email.length - 1) return ""
    return email.substring(at_idx + 1).trim().lowercase()
}
