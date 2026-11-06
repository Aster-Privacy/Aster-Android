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

package org.astermail.android.notifications

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import okio.BufferedSource
import org.astermail.android.R
import org.astermail.android.contacts.ContactPhotoDirectory
import org.astermail.android.mail.AsterProfileResolverHolder
import org.astermail.android.mail.InboxItem
import org.astermail.android.mail.OwnAddressAvatars
import org.astermail.android.mail.is_aster_domain
import org.astermail.android.network.low_network_monitor
import org.astermail.android.security.SenderAuthState
import org.astermail.android.security.SenderTrustContext
import org.astermail.android.security.SenderTrustMessage
import org.astermail.android.security.SenderTrustStore
import org.astermail.android.security.is_shared_tenant_sender_domain
import org.astermail.android.security.sender_logo_domain
import org.astermail.android.security.sender_trust_for
import org.astermail.android.ui.mail.LogoTone
import org.astermail.android.ui.mail.aster_system_avatar_backdrop
import org.astermail.android.ui.mail.avatar_colors_for
import org.astermail.android.ui.mail.avatar_key_for
import org.astermail.android.ui.mail.classify_logo_pixels
import org.astermail.android.ui.mail.decode_avatar_model
import org.astermail.android.ui.mail.displayed_sender_email
import org.astermail.android.ui.mail.extract_domain
import org.astermail.android.ui.mail.favicon_recently_missed
import org.astermail.android.ui.mail.get_favicon_url
import org.astermail.android.ui.mail.get_root_domain
import org.astermail.android.ui.mail.inbox_item_trust_message
import org.astermail.android.ui.mail.is_aster_system_address
import org.astermail.android.ui.mail.is_definitive_favicon_miss
import org.astermail.android.ui.mail.is_system_local_part
import org.astermail.android.ui.mail.logo_backdrop_inset
import org.astermail.android.ui.mail.logo_contrast_backdrop
import org.astermail.android.ui.mail.note_favicon_hit
import org.astermail.android.ui.mail.note_favicon_definitive_miss
import org.astermail.android.ui.mail.read_skeleton_geometry
import org.astermail.android.ui.mail.sender_logo_backdrop
import java.text.BreakIterator
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

internal const val NOTIFICATION_LOGO_MAX_BYTES = 512 * 1024
internal const val NOTIFICATION_LOGO_DECODE_PX = 160
internal const val NOTIFICATION_LOGO_MAX_SOURCE_PX = 4096
internal const val NOTIFICATION_AVATAR_FETCH_MS = 2_500L
internal const val NOTIFICATION_AVATAR_RUN_BUDGET_MS = 3_000L
internal const val NOTIFICATION_AVATAR_CACHE_ENTRIES = 32
private const val NOTIFICATION_AVATAR_DP = 64
private const val NOTIFICATION_AVATAR_MIN_PX = 128
private const val NOTIFICATION_AVATAR_MAX_PX = 192
private const val SYSTEM_LOGO_SCALE = 0.78f
private const val INITIAL_TEXT_SCALE = 0.4f
private const val LOGO_TONE_SAMPLE_PX = 16
internal const val NOTIFICATION_LOGO_REJECTED_TTL_MS = 30L * 60L * 1000L
internal const val NOTIFICATION_LOGO_RETRY_TTL_MS = 60L * 1000L
private const val NOTIFICATION_LOGO_MISS_ENTRIES = 256

data class NotificationAvatarSettings(
    val show_pictures: Boolean,
    val low_network: Boolean,
)

data class NotificationAvatarFacts(
    val content_hidden: Boolean,
    val settings: NotificationAvatarSettings,
    val sender_email: String,
    val sender_name: String,
    val undecryptable: Boolean,
    val trust_known: Boolean,
    val identity_marks: Boolean,
    val has_warning: Boolean,
    val auth_state: SenderAuthState?,
    val system_sender: Boolean,
    val trust_context_ready: Boolean,
)

sealed interface NotificationAvatarPlan {
    data object AppIcon : NotificationAvatarPlan

    data class Initials(val key: String, val initial: String) : NotificationAvatarPlan

    data class SystemLogo(val fallback: Initials) : NotificationAvatarPlan

    data class Picture(
        val email: String,
        val root_domain: String?,
        val aster_domain: Boolean,
        val allow_profile_picture: Boolean,
        val allow_network: Boolean,
        val fallback: Initials,
    ) : NotificationAvatarPlan
}

private val trusted_auth_states = setOf(SenderAuthState.authenticated, SenderAuthState.internal)

fun notification_initial(name: String, email: String): String {
    val source = name.trim().ifEmpty { email.trim() }
    if (source.isEmpty()) return "?"
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(source)
    val end = iterator.next().takeIf { it != BreakIterator.DONE } ?: source.length
    val first = source.substring(0, end)
    if (first.isBlank() || first.any { Character.isISOControl(it) }) return "?"
    return first.uppercase(Locale.ROOT)
}

fun notification_initials_plan(name: String, email: String): NotificationAvatarPlan.Initials {
    val clean_email = email.trim()
    val clean_name = name.trim()
    return NotificationAvatarPlan.Initials(
        key = avatar_key_for(clean_email, clean_name),
        initial = notification_initial(clean_name, clean_email),
    )
}

fun plan_notification_avatar(facts: NotificationAvatarFacts): NotificationAvatarPlan {
    if (facts.content_hidden) return NotificationAvatarPlan.AppIcon
    if (!facts.settings.show_pictures) return NotificationAvatarPlan.AppIcon
    val email = facts.sender_email.trim()
    val initials = notification_initials_plan(facts.sender_name, email)
    if (facts.undecryptable) return initials
    if (!facts.trust_known || !facts.identity_marks || facts.has_warning) return initials
    if (facts.auth_state !in trusted_auth_states) return initials
    val domain = extract_domain(email)
    val root_domain = if (domain.isBlank()) "" else get_root_domain(domain)
    val aster_domain = root_domain.isNotBlank() && is_aster_domain(root_domain)
    if (aster_domain && facts.system_sender && is_system_local_part(email)) {
        return NotificationAvatarPlan.SystemLogo(initials)
    }
    if (!facts.trust_context_ready) return initials
    if (email.isEmpty() || !email.contains('@')) return initials
    if (domain.isNotBlank() && is_shared_tenant_sender_domain(domain)) return initials
    val logo_domain = if (domain.isBlank() || aster_domain) null else sender_logo_domain(domain)
    return NotificationAvatarPlan.Picture(
        email = email,
        root_domain = logo_domain,
        aster_domain = aster_domain,
        allow_profile_picture = !facts.settings.low_network,
        allow_network = logo_domain != null && !facts.settings.low_network,
        fallback = initials,
    )
}

fun notification_avatar_facts(
    message: SenderTrustMessage?,
    shown_email: String,
    shown_name: String,
    undecryptable: Boolean,
    system_sender: Boolean,
    settings: NotificationAvatarSettings,
    content_hidden: Boolean,
    trust_context: SenderTrustContext,
    trust_context_ready: Boolean,
): NotificationAvatarFacts {
    val trust = if (undecryptable) null else runCatching { sender_trust_for(message, trust_context) }.getOrNull()
    return NotificationAvatarFacts(
        content_hidden = content_hidden,
        settings = settings,
        sender_email = trust?.address?.takeIf { it.isNotBlank() } ?: shown_email,
        sender_name = shown_name,
        undecryptable = undecryptable,
        trust_known = trust != null,
        identity_marks = trust?.show_identity_marks == true,
        has_warning = trust?.warning != null,
        auth_state = trust?.auth_state,
        system_sender = system_sender,
        trust_context_ready = trust_context_ready,
    )
}

fun inbox_item_system_sender(item: InboxItem): Boolean {
    if (!item.raw_item.system_origin || item.raw_item.is_external) return false
    if (!is_aster_system_address(item.sender_email)) return false
    val shown = displayed_sender_email(item.display_sender_email, item.sender_email)
    return shown.trim().lowercase(Locale.ROOT) == item.sender_email.trim().lowercase(Locale.ROOT)
}

fun inbox_item_avatar_plan(
    item: InboxItem,
    shown_name: String,
    settings: NotificationAvatarSettings,
    content_hidden: Boolean,
    trust_context: SenderTrustContext = SenderTrustStore.context.value,
    trust_context_ready: Boolean = SenderTrustStore.contacts_ready(),
): NotificationAvatarPlan = runCatching {
    plan_notification_avatar(
        notification_avatar_facts(
            message = inbox_item_trust_message(item),
            shown_email = displayed_sender_email(item.display_sender_email, item.sender_email),
            shown_name = shown_name,
            undecryptable = item.is_undecryptable,
            system_sender = inbox_item_system_sender(item),
            settings = settings,
            content_hidden = content_hidden,
            trust_context = trust_context,
            trust_context_ready = trust_context_ready,
        ),
    )
}.getOrElse { NotificationAvatarPlan.AppIcon }

fun push_avatar_plan(
    shown_email: String,
    shown_name: String,
    settings: NotificationAvatarSettings,
): NotificationAvatarPlan = runCatching {
    plan_notification_avatar(
        NotificationAvatarFacts(
            content_hidden = false,
            settings = settings,
            sender_email = shown_email,
            sender_name = shown_name,
            undecryptable = false,
            trust_known = false,
            identity_marks = false,
            has_warning = false,
            auth_state = null,
            system_sender = false,
            trust_context_ready = false,
        ),
    )
}.getOrElse { NotificationAvatarPlan.AppIcon }

fun merge_avatar_settings(
    cached: Pair<Boolean, Boolean>?,
    skeleton_show_avatar: Boolean?,
    monitor_low_network: Boolean?,
): NotificationAvatarSettings {
    val show_pictures = cached?.first ?: skeleton_show_avatar ?: false
    val low_network = monitor_low_network ?: true
    return NotificationAvatarSettings(
        show_pictures = show_pictures,
        low_network = low_network || cached?.second == true,
    )
}

fun notification_avatar_settings(context: Context, cached: Pair<Boolean, Boolean>?): NotificationAvatarSettings =
    merge_avatar_settings(
        cached = cached,
        skeleton_show_avatar = runCatching { read_skeleton_geometry(context).show_avatar }.getOrNull(),
        monitor_low_network = runCatching { low_network_monitor.is_active(context) }.getOrNull(),
    )

class NotificationAvatarBudget(
    private val total_ms: Long = NOTIFICATION_AVATAR_RUN_BUDGET_MS,
    private val per_fetch_ms: Long = NOTIFICATION_AVATAR_FETCH_MS,
) {
    private var spent_ms = 0L

    @Synchronized
    fun allowance(): Long = minOf(per_fetch_ms, (total_ms - spent_ms).coerceAtLeast(0L))

    @Synchronized
    fun spend(elapsed_ms: Long) {
        spent_ms += elapsed_ms.coerceAtLeast(0L)
    }
}

class NotificationAvatarLru<V : Any>(private val max_entries: Int = NOTIFICATION_AVATAR_CACHE_ENTRIES) {
    private val entries = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > max_entries
    }

    @Synchronized
    fun get(key: String): V? = entries[key]

    @Synchronized
    fun put(key: String, value: V) {
        entries[key] = value
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun clear() = entries.clear()
}

fun read_capped_bytes(source: BufferedSource, max_bytes: Int): ByteArray? {
    if (source.request(max_bytes.toLong() + 1L)) return null
    val bytes = source.readByteArray()
    return bytes.takeIf { it.isNotEmpty() }
}

fun is_acceptable_logo_mime(mime: String?): Boolean {
    val base = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) ?: return true
    if (base.isEmpty()) return true
    if (base == "application/octet-stream") return true
    return base.startsWith("image/") && !base.contains("svg") && !base.contains("xml")
}

fun logo_sample_size(width: Int, height: Int, target_px: Int): Int {
    if (width <= 0 || height <= 0 || target_px <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= target_px && height / (sample * 2) >= target_px) sample *= 2
    return sample
}

fun is_acceptable_logo_bounds(width: Int, height: Int): Boolean =
    width in 1..NOTIFICATION_LOGO_MAX_SOURCE_PX && height in 1..NOTIFICATION_LOGO_MAX_SOURCE_PX

class NotificationLogoRejected : IllegalStateException("logo rejected")

private fun decode_logo_bytes(bytes: ByteArray, target_px: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (!is_acceptable_logo_bounds(bounds.outWidth, bounds.outHeight)) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = logo_sample_size(bounds.outWidth, bounds.outHeight, target_px)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun notification_logo_decoder(target_px: Int) = Decoder.Factory { result, options, _ ->
    Decoder {
        if (!is_acceptable_logo_mime(result.mimeType)) throw NotificationLogoRejected()
        val bytes = read_capped_bytes(result.source.source(), NOTIFICATION_LOGO_MAX_BYTES)
            ?: throw NotificationLogoRejected()
        val bitmap = decode_logo_bytes(bytes, target_px) ?: throw NotificationLogoRejected()
        DecodeResult(drawable = BitmapDrawable(options.context.resources, bitmap), isSampled = true)
    }
}

class NotificationLogoMisses(
    private val max_entries: Int = NOTIFICATION_LOGO_MISS_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val blocked_until = ConcurrentHashMap<String, Long>()

    fun is_blocked(root_domain: String): Boolean {
        val until = blocked_until[root_domain] ?: return false
        if (clock() < until) return true
        blocked_until.remove(root_domain)
        return false
    }

    fun note(root_domain: String, ttl_ms: Long) {
        if (blocked_until.size >= max_entries) blocked_until.clear()
        blocked_until[root_domain] = clock() + ttl_ms
    }

    fun forget(root_domain: String) {
        blocked_until.remove(root_domain)
    }

    fun clear() = blocked_until.clear()
}

internal fun record_notification_logo_miss(
    misses: NotificationLogoMisses,
    root_domain: String,
    error: Throwable?,
    shared_definitive_miss: (String) -> Unit = ::note_favicon_definitive_miss,
) {
    when {
        is_definitive_favicon_miss(error) -> {
            shared_definitive_miss(root_domain)
            misses.note(root_domain, NOTIFICATION_LOGO_REJECTED_TTL_MS)
        }
        error is NotificationLogoRejected -> misses.note(root_domain, NOTIFICATION_LOGO_REJECTED_TTL_MS)
        else -> misses.note(root_domain, NOTIFICATION_LOGO_RETRY_TTL_MS)
    }
}

internal suspend fun <T> guarded_avatar_icon(
    content_hidden: Boolean,
    plan: () -> NotificationAvatarPlan,
    render: suspend (NotificationAvatarPlan) -> T?,
    release_claim: () -> Unit,
): T? {
    if (content_hidden) return null
    return try {
        render(plan())
    } catch (cancelled: CancellationException) {
        release_claim()
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

object NotificationAvatars {
    private val rendered = NotificationAvatarLru<Bitmap>()
    private val logo_misses = NotificationLogoMisses()

    fun clear() {
        rendered.clear()
        logo_misses.clear()
    }

    suspend fun large_icon(context: Context, plan: NotificationAvatarPlan, budget: NotificationAvatarBudget): Bitmap? =
        try {
            render(context.applicationContext, plan, budget)
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            fallback_icon(context, plan)
        } catch (_: Throwable) {
            fallback_icon(context, plan)
        }

    fun offline_icon(context: Context, plan: NotificationAvatarPlan): Bitmap? = runCatching {
        when (plan) {
            is NotificationAvatarPlan.Initials -> initials_bitmap(context.applicationContext, plan)
            else -> null
        }
    }.getOrNull()

    private fun fallback_icon(context: Context, plan: NotificationAvatarPlan): Bitmap? = runCatching {
        when (plan) {
            NotificationAvatarPlan.AppIcon -> null
            is NotificationAvatarPlan.Initials -> initials_bitmap(context.applicationContext, plan)
            is NotificationAvatarPlan.SystemLogo -> initials_bitmap(context.applicationContext, plan.fallback)
            is NotificationAvatarPlan.Picture -> initials_bitmap(context.applicationContext, plan.fallback)
        }
    }.getOrNull()

    private suspend fun render(context: Context, plan: NotificationAvatarPlan, budget: NotificationAvatarBudget): Bitmap? =
        when (plan) {
            NotificationAvatarPlan.AppIcon -> null
            is NotificationAvatarPlan.Initials -> initials_bitmap(context, plan)
            is NotificationAvatarPlan.SystemLogo -> system_logo_bitmap(context) ?: initials_bitmap(context, plan.fallback)
            is NotificationAvatarPlan.Picture -> picture_bitmap(context, plan, budget) ?: initials_bitmap(context, plan.fallback)
        }

    private suspend fun picture_bitmap(
        context: Context,
        plan: NotificationAvatarPlan.Picture,
        budget: NotificationAvatarBudget,
    ): Bitmap? {
        contact_photo_bitmap(context, plan.email)?.let { return it }
        if (plan.allow_profile_picture) profile_picture_bitmap(context, plan.email)?.let { return it }
        val root_domain = plan.root_domain ?: return null
        return favicon_bitmap(context, root_domain, plan.allow_network, budget)
    }

    private fun contact_photo_bitmap(context: Context, email: String): Bitmap? {
        val photo = runCatching { ContactPhotoDirectory.cached(email) }.getOrNull() ?: return null
        val source = runCatching { software_bitmap(photo.asAndroidBitmap()) }.getOrNull() ?: return null
        return circle_bitmap(context, source, backdrop = 0, inset = 0f)
    }

    private suspend fun profile_picture_bitmap(context: Context, email: String): Bitmap? {
        val lower = email.trim().lowercase(Locale.ROOT)
        val own = runCatching { OwnAddressAvatars.get(lower) }.getOrNull()
        val aster = runCatching {
            AsterProfileResolverHolder.shared?.profiles?.value?.get(lower)?.profile_picture
        }.getOrNull()
        val url = (own ?: aster)?.takeIf { it.isNotBlank() } ?: return null
        val request = ImageRequest.Builder(context)
            .data(decode_avatar_model(url))
            .diskCacheKey(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.DISABLED)
            .allowHardware(false)
            .size(NOTIFICATION_LOGO_DECODE_PX)
            .decoderFactory(notification_logo_decoder(NOTIFICATION_LOGO_DECODE_PX))
            .build()
        val result = coil.Coil.imageLoader(context).execute(request) as? SuccessResult ?: return null
        val source = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
        return toned_circle(context, source)
    }

    private suspend fun favicon_bitmap(
        context: Context,
        root_domain: String,
        allow_network: Boolean,
        budget: NotificationAvatarBudget,
    ): Bitmap? {
        val dark = is_dark(context)
        val cache_key = "logo:$root_domain:$dark"
        rendered.get(cache_key)?.let { return it }
        if (favicon_recently_missed(root_domain) || logo_misses.is_blocked(root_domain)) return null
        val allowance = budget.allowance()
        if (allowance <= 0L) return null
        val request = ImageRequest.Builder(context)
            .data(get_favicon_url(root_domain))
            .diskCacheKey("favicon:$root_domain")
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(if (allow_network) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .allowHardware(false)
            .size(NOTIFICATION_LOGO_DECODE_PX)
            .decoderFactory(notification_logo_decoder(NOTIFICATION_LOGO_DECODE_PX))
            .build()
        val started = System.currentTimeMillis()
        val result = try {
            withTimeoutOrNull(allowance) { coil.Coil.imageLoader(context).execute(request) }
        } finally {
            budget.spend(System.currentTimeMillis() - started)
        }
        when (result) {
            is SuccessResult -> {
                val source = (result.drawable as? BitmapDrawable)?.bitmap ?: return null
                note_favicon_hit(root_domain)
                logo_misses.forget(root_domain)
                val circle = toned_circle(context, source) ?: return null
                rendered.put(cache_key, circle)
                return circle
            }
            is ErrorResult -> {
                if (allow_network) record_notification_logo_miss(logo_misses, root_domain, result.throwable)
                return null
            }
            else -> {
                if (allow_network) record_notification_logo_miss(logo_misses, root_domain, null)
                return null
            }
        }
    }

    private fun toned_circle(context: Context, source: Bitmap): Bitmap? {
        val software = software_bitmap(source) ?: return null
        val tone = logo_tone_of(software)
        val dark = is_dark(context)
        val backdrop = sender_logo_backdrop(tone, dark).toArgb()
        val inset = if (logo_contrast_backdrop(tone, dark) != null) logo_backdrop_inset else 0f
        return circle_bitmap(context, software, backdrop, inset)
    }

    private fun system_logo_bitmap(context: Context): Bitmap? {
        val cache_key = "system"
        rendered.get(cache_key)?.let { return it }
        val size = avatar_px(context)
        val logo_px = (size * SYSTEM_LOGO_SCALE).toInt().coerceAtLeast(1)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, R.drawable.aster_favicon, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = logo_sample_size(bounds.outWidth, bounds.outHeight, logo_px)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.aster_favicon, options) ?: return null
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = aster_system_avatar_backdrop.toArgb()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        val offset = (size - logo_px) / 2
        canvas.drawBitmap(logo, null, Rect(offset, offset, offset + logo_px, offset + logo_px), paint)
        rendered.put(cache_key, output)
        return output
    }

    private fun initials_bitmap(context: Context, plan: NotificationAvatarPlan.Initials): Bitmap {
        val size = avatar_px(context)
        val (background, foreground) = avatar_colors_for(plan.key)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = background.toArgb()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, fill)
        val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        text.color = foreground.toArgb()
        text.textSize = size * INITIAL_TEXT_SCALE
        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.textAlign = Paint.Align.CENTER
        val bounds = Rect()
        text.getTextBounds(plan.initial, 0, plan.initial.length, bounds)
        canvas.drawText(plan.initial, size / 2f, size / 2f - bounds.exactCenterY(), text)
        return output
    }

    private fun circle_bitmap(context: Context, source: Bitmap, backdrop: Int, inset: Float): Bitmap {
        val size = avatar_px(context)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val center = size / 2f
        if (android.graphics.Color.alpha(backdrop) != 0) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            fill.color = backdrop
            canvas.drawCircle(center, center, center, fill)
        }
        val radius = center * (1f - 2f * inset)
        val scale = maxOf(2f * radius / source.width, 2f * radius / source.height)
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(center - source.width * scale / 2f, center - source.height * scale / 2f)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(matrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = shader
        canvas.drawCircle(center, center, radius, paint)
        return output
    }

    private fun software_bitmap(source: Bitmap): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        if (source.config != Bitmap.Config.HARDWARE) return source
        return source.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun logo_tone_of(source: Bitmap): LogoTone? = runCatching {
        val scaled = Bitmap.createScaledBitmap(source, LOGO_TONE_SAMPLE_PX, LOGO_TONE_SAMPLE_PX, true)
        val pixels = IntArray(LOGO_TONE_SAMPLE_PX * LOGO_TONE_SAMPLE_PX)
        scaled.getPixels(pixels, 0, LOGO_TONE_SAMPLE_PX, 0, 0, LOGO_TONE_SAMPLE_PX, LOGO_TONE_SAMPLE_PX)
        classify_logo_pixels(pixels)
    }.getOrNull()

    private fun avatar_px(context: Context): Int =
        (NOTIFICATION_AVATAR_DP * context.resources.displayMetrics.density).toInt()
            .coerceIn(NOTIFICATION_AVATAR_MIN_PX, NOTIFICATION_AVATAR_MAX_PX)

    private fun is_dark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
