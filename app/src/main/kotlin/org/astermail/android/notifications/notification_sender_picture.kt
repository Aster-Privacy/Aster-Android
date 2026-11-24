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

import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.mail.is_aster_domain
import org.astermail.android.security.SenderTrustMessage

internal enum class SenderPictureSource { Inline, AsterHost, CachedOnly }

internal fun sender_picture_source(url: String, api_host: String): SenderPictureSource? {
    val trimmed = url.trim()
    if (trimmed.startsWith("data:image/", ignoreCase = true)) return SenderPictureSource.Inline
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')?.takeIf { it.isNotBlank() } ?: return null
    val api = api_host.trim().lowercase(Locale.ROOT)
    val own_host = (api.isNotEmpty() && host == api) ||
        generateSequence(host) { it.substringAfter('.', "").takeIf { rest -> rest.contains('.') } }
            .any { is_aster_domain(it) }
    return if (own_host) SenderPictureSource.AsterHost else SenderPictureSource.CachedOnly
}

internal suspend fun <T> within_avatar_budget(
    budget: NotificationAvatarBudget,
    clock: () -> Long = System::currentTimeMillis,
    block: suspend () -> T?,
): T? {
    val allowance = budget.allowance()
    if (allowance <= 0L) return null
    val started = clock()
    return try {
        withTimeoutOrNull(allowance) { block() }
    } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
    } catch (_: Throwable) {
        null
    } finally {
        budget.spend(clock() - started)
    }
}

internal suspend fun <T> resolve_sender_picture(
    known_url: () -> String?,
    lookup_url: suspend () -> String?,
    load: suspend (url: String, allow_network: Boolean) -> T?,
    api_host: String,
    budget: NotificationAvatarBudget,
    clock: () -> Long = System::currentTimeMillis,
): T? {
    val known = runCatching { known_url() }.getOrNull()?.takeIf { it.isNotBlank() }
    val url = known
        ?: within_avatar_budget(budget, clock) { lookup_url() }?.takeIf { it.isNotBlank() }
        ?: return null
    val source = sender_picture_source(url, api_host) ?: return null
    return within_avatar_budget(budget, clock) { load(url, source == SenderPictureSource.AsterHost) }
}

internal suspend fun <T> sender_icon_or_initials(picture: suspend () -> T?, initials: () -> T?): T? {
    val loaded = try {
        picture()
    } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
    } catch (_: Throwable) {
        null
    }
    return loaded ?: runCatching { initials() }.getOrNull()
}

internal suspend fun warm_avatar_trust(
    settings: NotificationAvatarSettings,
    content_hidden: Boolean,
    budget: NotificationAvatarBudget,
    ready: () -> Boolean,
    warm: suspend () -> Boolean,
): Boolean {
    if (content_hidden || !settings.show_pictures) return false
    if (runCatching { ready() }.getOrDefault(false)) return true
    within_avatar_budget(budget) { warm() }
    return runCatching { ready() }.getOrDefault(false)
}

internal fun push_trust_message(
    item_type: String?,
    is_external: Boolean?,
    sender_name: String?,
    sender_email: String?,
    display_sender_name: String?,
    display_sender_email: String?,
    sender_verification: String?,
): SenderTrustMessage? {
    if (item_type != "received" || is_external != false) return null
    val email = sender_email?.trim()?.takeIf { it.contains('@') } ?: return null
    return SenderTrustMessage(
        sender_name = sender_name,
        sender_email = email,
        display_sender_name = display_sender_name,
        display_sender_email = display_sender_email,
        is_external = false,
        sender_verification = sender_verification,
    )
}
