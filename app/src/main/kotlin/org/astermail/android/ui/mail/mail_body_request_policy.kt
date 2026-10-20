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

internal const val MAIL_CONTENT_HOST = "mail-content.invalid"

internal const val REMOTE_IMAGE_PROXY_HOST = "app.astermail.org"

internal const val REMOTE_IMAGE_PROXY_PATH = "/api/images/v1/proxy"

internal enum class MailBodyRequestAction { LOCAL, PROXY, BLOCK }

internal fun mail_body_request_action(
    scheme: String?,
    host: String?,
    path: String?,
    proxied_url: String?,
    allow_external: Boolean,
): MailBodyRequestAction {
    if (host == MAIL_CONTENT_HOST) return MailBodyRequestAction.LOCAL
    if (scheme == "data" || scheme == "cid" || scheme == "about") return MailBodyRequestAction.LOCAL
    if (!allow_external) return MailBodyRequestAction.BLOCK
    if (scheme != "https") return MailBodyRequestAction.BLOCK
    if (host != REMOTE_IMAGE_PROXY_HOST) return MailBodyRequestAction.BLOCK
    if (path != REMOTE_IMAGE_PROXY_PATH) return MailBodyRequestAction.BLOCK
    if (proxied_url.isNullOrBlank()) return MailBodyRequestAction.BLOCK
    return MailBodyRequestAction.PROXY
}

internal fun proxy_redirect_target(current_url: String, location: String?): String? {
    if (location.isNullOrBlank()) return null
    val resolved = try {
        java.net.URI(current_url).resolve(location.trim())
    } catch (_: Throwable) {
        return null
    }
    if (resolved.scheme?.lowercase() != "https") return null
    if (resolved.host?.lowercase() != REMOTE_IMAGE_PROXY_HOST) return null
    if (resolved.port != -1 && resolved.port != 443) return null
    if (resolved.rawUserInfo != null) return null
    if (resolved.path != REMOTE_IMAGE_PROXY_PATH) return null
    val query = resolved.rawQuery ?: return null
    if (query.split('&').none { it.startsWith("url=") && it.length > 4 }) return null
    return resolved.toString()
}

internal fun proxy_status_is_retryable(code: Int): Boolean =
    code == 408 || code == 502 || code == 503 || code == 504
