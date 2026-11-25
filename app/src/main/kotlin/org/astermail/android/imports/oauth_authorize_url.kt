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

package org.astermail.android.imports

private val oauth_authorize_hosts = mapOf(
    "google" to "accounts.google.com",
    "microsoft" to "login.microsoftonline.com",
    "yahoo" to "api.login.yahoo.com",
)

internal fun is_expected_authorize_url(provider: String, url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val expected_host = oauth_authorize_hosts[provider.lowercase()] ?: return false
    val uri = try {
        java.net.URI(url.trim())
    } catch (_: Throwable) {
        return false
    }
    if (!uri.isAbsolute || uri.isOpaque) return false
    if (uri.scheme?.lowercase() != "https") return false
    if (uri.rawUserInfo != null) return false
    if (uri.port != -1 && uri.port != 443) return false
    return uri.host?.lowercase() == expected_host
}
