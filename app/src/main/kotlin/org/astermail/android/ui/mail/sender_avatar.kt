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
import org.astermail.android.security.is_shared_tenant_sender_domain
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
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.astermail.android.R
import org.astermail.android.api.BuildConfig as ApiBuildConfig
import org.astermail.android.api.auth.PublicProfile
import org.astermail.android.contacts.ContactPhotoDirectory
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_state
import org.astermail.android.mail.AsterProfileResolverHolder
import org.astermail.android.mail.OwnAddressAvatars
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

internal val aster_system_avatar_backdrop = Color.White

fun inbox_logo_root_domain(domain: String): String =
    if (domain.isBlank() || is_shared_tenant_sender_domain(domain)) "" else get_root_domain(domain)

private const val FAVICON_MISS_TTL_MS = 30L * 60L * 1000L
private const val FAVICON_TRANSIENT_TTL_MS = 20L * 1000L
private const val FAVICON_MAX_ATTEMPTS = 3
private const val FAVICON_RETRY_BASE_DELAY_MS = 700L

private val favicon_blocked_until = java.util.concurrent.ConcurrentHashMap<String, Long>()

object favicon_miss_store {
    private const val prefs_name = "aster_favicon_misses"
    private const val max_entries = 500
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun attach(context: android.content.Context) {
        val store = context.applicationContext.getSharedPreferences(prefs_name, android.content.Context.MODE_PRIVATE)
        prefs = store
        val now = System.currentTimeMillis()
        val expired = mutableListOf<String>()
        for ((domain, value) in store.all) {
            val until = value as? Long ?: continue
            if (until > now) favicon_blocked_until.putIfAbsent(domain, until) else expired.add(domain)
        }
        if (expired.isNotEmpty()) store.edit().apply { expired.forEach { remove(it) } }.apply()
    }

    internal fun remember_miss(root_domain: String, until: Long) {
        val store = prefs ?: return
        if (store.all.size >= max_entries) return
        store.edit().putLong(root_domain, until).apply()
    }

    internal fun forget(root_domain: String) {
        val store = prefs ?: return
        if (store.contains(root_domain)) store.edit().remove(root_domain).apply()
    }
}

internal fun note_favicon_hit(root_domain: String) = clear_favicon_miss(root_domain)

internal fun note_favicon_definitive_miss(root_domain: String) = mark_favicon_definitive_miss(root_domain)

private fun mark_favicon_definitive_miss(root_domain: String) {
    val until = System.currentTimeMillis() + FAVICON_MISS_TTL_MS
    favicon_blocked_until[root_domain] = until
    favicon_miss_store.remember_miss(root_domain, until)
}

internal fun favicon_recently_missed(root_domain: String): Boolean {
    val blocked_until = favicon_blocked_until[root_domain] ?: return false
    if (System.currentTimeMillis() < blocked_until) return true
    favicon_blocked_until.remove(root_domain)
    return false
}

private fun mark_favicon_missed(root_domain: String, ttl_ms: Long) {
    favicon_blocked_until[root_domain] = System.currentTimeMillis() + ttl_ms
}

private fun clear_favicon_miss(root_domain: String) {
    if (favicon_blocked_until.remove(root_domain) != null) favicon_miss_store.forget(root_domain)
}

private fun http_status_of(error: Throwable?): Int? =
    (error as? coil.network.HttpException)?.response?.code

internal fun is_definitive_favicon_miss(error: Throwable?): Boolean =
    http_status_of(error).let { it == 400 || it == 404 || it == 410 }

internal fun is_cancelled_favicon_load(error: Throwable?): Boolean =
    error is java.util.concurrent.CancellationException

private const val decoded_avatar_cache_bytes = 4 * 1024 * 1024

private val decoded_avatar_cache = object : android.util.LruCache<String, ByteArray>(decoded_avatar_cache_bytes) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size
}

internal fun cached_avatar_model(url: String): Any? {
    if (!url.startsWith("data:")) return url
    return decoded_avatar_cache.get(url)
}

@Composable
internal fun remember_avatar_model(url: String): Any? {
    val state = remember(url) { mutableStateOf(cached_avatar_model(url)) }
    if (state.value == null) {
        LaunchedEffect(url) {
            state.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                decode_avatar_model(url)
            }
        }
    }
    return state.value
}

internal fun decode_avatar_model(url: String): Any {
    if (!url.startsWith("data:")) return url
    decoded_avatar_cache.get(url)?.let { return it }
    val decoded = decode_data_avatar(url)
    if (decoded is ByteArray) decoded_avatar_cache.put(url, decoded)
    return decoded
}

private fun decode_data_avatar(url: String): Any {
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

private const val AVATAR_PENDING_MAX_MS = 1200L

internal fun avatar_shows_initials(loaded: Boolean, failed: Boolean, wait_expired: Boolean): Boolean =
    !loaded && (failed || wait_expired)

@Composable
private fun remember_avatar_wait_expired(key: Any?, active: Boolean): Boolean {
    val expired_state = remember(key) { mutableStateOf(false) }
    if (active && !expired_state.value) {
        LaunchedEffect(key) {
            kotlinx.coroutines.delay(AVATAR_PENDING_MAX_MS)
            expired_state.value = true
        }
    }
    return expired_state.value
}

@Composable
private fun remember_avatar_colors(email: String, name: String, profile_color: String?): Pair<Color, Color> =
    remember(email, name, profile_color) { avatar_colors_for(avatar_key_for(email, name), profile_color) }

@Composable
private fun Modifier.avatar_fill(loaded: Boolean, loaded_color: Color, show_initials: Boolean, initials_color: Color): Modifier {
    val pending_state = shimmer_state(animated = false)
    return when {
        loaded -> this.background(loaded_color)
        show_initials -> this.background(initials_color)
        else -> this.shimmer(pending_state, CircleShape)
    }
}

internal fun avatar_already_cached(context: android.content.Context, key: String): Boolean =
    runCatching {
        coil.Coil.imageLoader(context).memoryCache?.get(coil.memory.MemoryCache.Key(key)) != null
    }.getOrDefault(false)

@Composable
fun SenderAvatar(
    email: String,
    name: String = "",
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    profile_picture_url: String? = null,
    sender_authenticated: Boolean = false,
    profile_color: String? = null,
    use_peer_profile_color: Boolean = true,
    use_contact_photo: Boolean = true,
    plain: Boolean = false,
) {
    if (plain) {
        val (plain_bg, plain_fg) = remember_avatar_colors(email, name, null)
        initials_circle(name, email, plain_bg, plain_fg, size, modifier)
        return
    }
    if (use_contact_photo && !remember(email, sender_authenticated) { is_aster_system_sender(email, sender_authenticated) }) {
        val contact_photo = remember_contact_photo(email)
        if (contact_photo != null) {
            ContactPhotoAvatar(photo = contact_photo, name = name, size = size, modifier = modifier)
            return
        }
    }
    val context = LocalContext.current
    val low_network = org.astermail.android.network.low_network_active()
    val remote_avatars_allowed = org.astermail.android.api.network.should_load_remote_avatar(low_network)
    val own_avatars = own_address_avatar_entries()
    val own_picture = remember(email, own_avatars) { OwnAddressAvatars.get(email) }
    val resolved_profile_picture = profile_picture_url?.takeIf { it.isNotBlank() } ?: own_picture
    if (!resolved_profile_picture.isNullOrBlank() && remote_avatars_allowed) {
        val (bg_fb, fg_fb) = remember_avatar_colors(email, name, profile_color)
        var loaded_pp by remember(resolved_profile_picture) {
            mutableStateOf(avatar_already_cached(context, resolved_profile_picture))
        }
        var failed_pp by remember(resolved_profile_picture) { mutableStateOf(false) }
        val pp_wait_expired = remember_avatar_wait_expired(resolved_profile_picture, active = !loaded_pp)
        val pp_initials = avatar_shows_initials(loaded_pp, failed_pp, pp_wait_expired)
        var pp_drawable by remember(resolved_profile_picture) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
        val pp_tone = remember_logo_tone(resolved_profile_picture, pp_drawable)
        val pp_backdrop = logo_contrast_backdrop(pp_tone, AsterMaterial.colors.is_dark)
        val pp_inset = if (loaded_pp && pp_backdrop != null) size * logo_backdrop_inset else 0.dp
        Box(
            modifier = modifier.size(size).clip(CircleShape).avatar_fill(loaded_pp, pp_backdrop ?: Color.Transparent, pp_initials, bg_fb),
            contentAlignment = Alignment.Center,
        ) {
            if (pp_initials) {
                Text(
                    text = initial_for(name, email),
                    color = fg_fb,
                    style = avatar_initial_style(size),
                )
            }
            val model = remember_avatar_model(resolved_profile_picture)
            if (model != null) {
                val request = remember(resolved_profile_picture, model) {
                    ImageRequest.Builder(context)
                        .data(model)
                        .memoryCacheKey(resolved_profile_picture)
                        .placeholderMemoryCacheKey(resolved_profile_picture)
                        .diskCacheKey(resolved_profile_picture)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = name.ifBlank { null },
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        loaded_pp = state is coil.compose.AsyncImagePainter.State.Success
                        if (state is coil.compose.AsyncImagePainter.State.Success) pp_drawable = state.result.drawable
                        if (state is coil.compose.AsyncImagePainter.State.Error && !is_cancelled_favicon_load(state.result.throwable)) failed_pp = true
                    },
                    modifier = Modifier.size(size).padding(pp_inset).clip(CircleShape),
                )
            }
        }
        return
    }

    val (bg, fg) = remember_avatar_colors(email, name, profile_color)
    val domain = remember(email) { extract_domain(email) }
    val root_domain = remember(domain) { inbox_logo_root_domain(domain) }

    if (root_domain.isBlank()) {
        initials_circle(name, email, bg, fg, size, modifier)
        return
    }

    if (is_aster_domain(root_domain)) {
        if (sender_authenticated && is_system_local_part(email)) {
            AsterSystemAvatar(size = size, modifier = modifier)
            return
        }
        AsterDomainAvatar(
            email = email,
            name = name,
            size = size,
            modifier = modifier,
            profile_color = profile_color,
            use_peer_profile_color = use_peer_profile_color,
        )
        return
    }

    if (!org.astermail.android.api.network.should_load_sender_logo(low_network)) {
        initials_circle(name, email, bg, fg, size, modifier)
        return
    }

    if (remember(root_domain) { favicon_recently_missed(root_domain) }) {
        initials_circle(name, email, bg, fg, size, modifier)
        return
    }

    val url = remember(root_domain) { "$FAVICON_BASE$root_domain" }
    var loaded by remember(url) { mutableStateOf(avatar_already_cached(context, url)) }
    var attempt by remember(url) { mutableStateOf(0) }
    var pending_attempt by remember(url) { mutableStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }
    val wait_expired = remember_avatar_wait_expired(url, active = !loaded)
    val show_initials = avatar_shows_initials(loaded, failed, wait_expired)
    var logo_drawable by remember(url) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    val logo_tone = remember_logo_tone(url, logo_drawable)
    val is_dark = AsterMaterial.colors.is_dark
    val logo_inset = if (loaded && logo_contrast_backdrop(logo_tone, is_dark) != null) size * logo_backdrop_inset else 0.dp

    if (pending_attempt > attempt) {
        LaunchedEffect(url, pending_attempt) {
            kotlinx.coroutines.delay(FAVICON_RETRY_BASE_DELAY_MS * pending_attempt)
            attempt = pending_attempt
        }
    }

    Box(
        modifier = modifier.size(size).clip(CircleShape).avatar_fill(loaded, sender_logo_backdrop(logo_tone, is_dark), show_initials, bg),
        contentAlignment = Alignment.Center,
    ) {
        if (show_initials) {
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
                if (state is coil.compose.AsyncImagePainter.State.Success) logo_drawable = state.result.drawable
                if (loaded) clear_favicon_miss(root_domain)
                if (state is coil.compose.AsyncImagePainter.State.Error) {
                    val error = state.result.throwable
                    when {
                        is_cancelled_favicon_load(error) -> Unit
                        is_definitive_favicon_miss(error) -> {
                            failed = true
                            mark_favicon_definitive_miss(root_domain)
                        }
                        pending_attempt < FAVICON_MAX_ATTEMPTS -> pending_attempt += 1
                        else -> {
                            failed = true
                            mark_favicon_missed(root_domain, FAVICON_TRANSIENT_TTL_MS)
                        }
                    }
                }
            },
            modifier = Modifier.size(size).padding(logo_inset).clip(CircleShape),
        )
    }
}

@Composable
private fun remember_contact_photo(email: String): ImageBitmap? {
    val version = contact_photo_directory_version()
    var photo by remember(email) { mutableStateOf(ContactPhotoDirectory.cached(email)) }
    LaunchedEffect(email, version) {
        photo = ContactPhotoDirectory.photo_for(email)
    }
    return photo
}

@Composable
private fun ContactPhotoAvatar(
    photo: ImageBitmap,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = photo,
            contentDescription = name.ifBlank { null },
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    }
}

internal fun is_aster_system_sender(email: String, sender_authenticated: Boolean): Boolean {
    if (!sender_authenticated || !is_system_local_part(email)) return false
    val domain = extract_domain(email)
    return domain.isNotBlank() && is_aster_domain(get_root_domain(domain))
}

internal val SYSTEM_LOCAL_PARTS = setOf(
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

internal fun is_system_local_part(email: String): Boolean {
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
        modifier = modifier.size(size).clip(CircleShape).background(aster_system_avatar_backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.aster_wordmark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = size * 0.72f, height = size * 0.18f),
        )
    }
}

@Composable
private fun AsterDomainAvatar(
    email: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    profile_color: String? = null,
    use_peer_profile_color: Boolean = true,
) {
    val context = LocalContext.current
    val low_network = org.astermail.android.network.low_network_active()
    val resolver = AsterProfileResolverHolder.shared
    val lower_email = remember(email) { email.trim().lowercase() }

    LaunchedEffect(lower_email, resolver, low_network) {
        if (org.astermail.android.api.network.should_load_remote_avatar(low_network)) {
            resolver?.request(lower_email)
        }
    }

    val profile_entry = remember_profile_entry(resolver?.profiles, lower_email)
    val profile = profile_entry.profile
    val profile_known = profile_entry.known
    val profile_wait_expired = remember_avatar_wait_expired(lower_email, active = !profile_known)

    val resolved_pic = profile?.profile_picture
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { org.astermail.android.api.network.should_load_remote_avatar(low_network) }
    val chosen_profile_color = profile_color?.takeIf { it.isNotEmpty() }
        ?: profile?.profile_color?.takeIf { use_peer_profile_color }
    val (aster_bg, aster_fg) = remember_avatar_colors(email, name, chosen_profile_color)
    if (resolved_pic != null) {
        var loaded by remember(resolved_pic) {
            mutableStateOf(avatar_already_cached(context, resolved_pic))
        }
        var pic_failed by remember(resolved_pic) { mutableStateOf(false) }
        val pic_wait_expired = remember_avatar_wait_expired(resolved_pic, active = !loaded)
        val pic_initials = avatar_shows_initials(loaded, pic_failed, pic_wait_expired)
        var pic_drawable by remember(resolved_pic) { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
        val pic_tone = remember_logo_tone(resolved_pic, pic_drawable)
        val pic_backdrop = logo_contrast_backdrop(pic_tone, AsterMaterial.colors.is_dark)
        val pic_inset = if (loaded && pic_backdrop != null) size * logo_backdrop_inset else 0.dp
        Box(
            modifier = modifier.size(size).clip(CircleShape).avatar_fill(loaded, pic_backdrop ?: Color.Transparent, pic_initials, aster_bg),
            contentAlignment = Alignment.Center,
        ) {
            if (pic_initials) {
                Text(
                    text = initial_for(name, email),
                    color = aster_fg,
                    style = avatar_initial_style(size),
                )
            }
            val model = remember_avatar_model(resolved_pic)
            if (model != null) {
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
                        if (state is coil.compose.AsyncImagePainter.State.Success) pic_drawable = state.result.drawable
                        if (state is coil.compose.AsyncImagePainter.State.Error && !is_cancelled_favicon_load(state.result.throwable)) pic_failed = true
                    },
                    modifier = Modifier.size(size).padding(pic_inset).clip(CircleShape),
                )
            }
        }
        return
    }

    val awaiting_profile = resolver != null &&
        !profile_known &&
        !profile_wait_expired &&
        org.astermail.android.api.network.should_load_remote_avatar(low_network)
    if (awaiting_profile) {
        Box(modifier = modifier.size(size).clip(CircleShape).avatar_fill(false, Color.Transparent, false, aster_bg))
        return
    }
    initials_circle(name, email, aster_bg, aster_fg, size, modifier)
}

@androidx.compose.runtime.Immutable
private data class AvatarProfileEntry(val profile: PublicProfile?, val known: Boolean)

private val unknown_avatar_profile = AvatarProfileEntry(profile = null, known = false)

private fun avatar_profile_entry(profiles: Map<String, PublicProfile?>, lower_email: String): AvatarProfileEntry =
    if (profiles.containsKey(lower_email)) AvatarProfileEntry(profiles[lower_email], known = true) else unknown_avatar_profile

@Composable
private fun remember_profile_entry(
    profiles_flow: StateFlow<Map<String, PublicProfile?>>?,
    lower_email: String,
): AvatarProfileEntry {
    if (profiles_flow == null) return unknown_avatar_profile
    val entry_flow = remember(profiles_flow, lower_email) {
        profiles_flow
            .map { avatar_profile_entry(it, lower_email) }
            .distinctUntilChanged()
    }
    val entry by entry_flow.collectAsStateWithLifecycle(
        initialValue = avatar_profile_entry(profiles_flow.value, lower_email),
    )
    return entry
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
    authenticated_system_email: String? = null,
    modifier: Modifier = Modifier,
    plain: Boolean = false,
) {
    val list = if (participants.isEmpty()) emptyList() else participants
    if (list.size <= 1) {
        val first = list.firstOrNull() ?: ("" to "")
        val (nm, em) = first
        androidx.compose.runtime.key(em.lowercase().ifBlank { nm.lowercase() }) {
            SenderAvatar(
                email = em,
                name = nm,
                size = size,
                sender_authenticated = matches_authenticated_system(em, authenticated_system_email),
                modifier = modifier,
                plain = plain,
            )
        }
        return
    }
    val take = list.take(max_visible.coerceAtMost(3))
    val each_size = if (take.size == 2) (size.value * 0.66f).dp else (size.value * 0.48f).dp
    val alignments = when (take.size) {
        2 -> listOf(Alignment.TopStart, Alignment.BottomEnd)
        else -> listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomCenter)
    }
    Box(modifier = modifier.size(size)) {
        take.forEachIndexed { idx, (nm, em) ->
            androidx.compose.runtime.key(em.lowercase().ifBlank { nm.lowercase() }) {
                SenderAvatar(
                    email = em,
                    name = nm,
                    size = each_size,
                    sender_authenticated = matches_authenticated_system(em, authenticated_system_email),
                    modifier = Modifier.align(alignments[idx]),
                    plain = plain,
                )
            }
        }
    }
}

internal fun matches_authenticated_system(email: String, authenticated_system_email: String?): Boolean {
    if (authenticated_system_email.isNullOrBlank()) return false
    return email.trim().lowercase() == authenticated_system_email.trim().lowercase()
}

private val FAVICON_DOMAIN_PATTERN =
    Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,}$")

internal fun is_valid_favicon_domain(domain: String): Boolean =
    domain.length <= 253 && FAVICON_DOMAIN_PATTERN.matches(domain)

internal fun extract_domain(email: String): String {
    val at_idx = email.lastIndexOf('@')
    if (at_idx < 0 || at_idx == email.length - 1) return ""
    val domain = email.substring(at_idx + 1).trim().trimEnd('>', '"', '\'', ' ').trim().lowercase()
    return if (is_valid_favicon_domain(domain)) domain else ""
}
