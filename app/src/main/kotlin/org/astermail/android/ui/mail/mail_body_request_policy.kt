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
