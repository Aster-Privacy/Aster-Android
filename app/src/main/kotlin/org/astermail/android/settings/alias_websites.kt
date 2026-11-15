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

import java.net.IDN
import java.net.URI
import java.util.Locale

internal const val ALIAS_WEBSITE_MAX_URL_LENGTH = 200
internal const val ALIAS_WEBSITE_MAX_HOSTNAME_LENGTH = 253
internal const val ALIAS_WEBSITE_MAX_LABEL_LENGTH = 63

private val UNSAFE_WEBSITE_CHARACTERS = Regex("[\\x00-\\x1f\\x7f\\s]")
private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
private val URL_PARTS = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#\\\\]*)(.*)$")
private val AUTHORITY_PARTS = Regex("^([^:\\[\\]]+)(?::([0-9]{1,5}))?$")
private val USERINFO_AUTHORITY = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:[\\\\/]*([^\\\\/?#]*)")
private val HOST_LABEL_PATTERN = Regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$")

private fun with_default_scheme(raw: String): String? {
    val cleaned = raw.replace(UNSAFE_WEBSITE_CHARACTERS, "")
    if (cleaned.isEmpty()) return null
    val with_scheme = if (SCHEME_PREFIX.containsMatchIn(cleaned)) cleaned else "https://$cleaned"
    if (with_scheme.length > ALIAS_WEBSITE_MAX_URL_LENGTH) return null
    return with_scheme
}

private fun has_userinfo(url: String): Boolean =
    USERINFO_AUTHORITY.find(url)?.groupValues?.get(1)?.contains('@') ?: false

internal fun normalize_alias_website_url(raw: String): String? {
    val with_scheme = with_default_scheme(raw) ?: return null
    if (has_userinfo(with_scheme)) return null
    return try {
        val uri = URI(with_scheme)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") return null
        if (uri.rawUserInfo != null) return null
        val host = uri.host
        if (host.isNullOrBlank() || !host.contains(".")) return null
        with_scheme
    } catch (e: Exception) {
        null
    }
}

internal fun is_plausible_alias_website_host(hostname: String): Boolean {
    val host = hostname.lowercase(Locale.ROOT).removeSuffix(".")
    if (host.isEmpty() || host.length > ALIAS_WEBSITE_MAX_HOSTNAME_LENGTH) return false
    val labels = host.split(".")
    if (labels.size < 2) return false
    if (labels.any { it.length > ALIAS_WEBSITE_MAX_LABEL_LENGTH || !HOST_LABEL_PATTERN.matches(it) }) {
        return false
    }
    return labels.last() in IANA_TLDS
}

private fun to_ascii_host(host: String): String? = try {
    IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT).removeSuffix(".")
} catch (e: IllegalArgumentException) {
    null
}

private fun is_default_port(scheme: String, port: Int): Boolean =
    (scheme == "https" && port == 443) || (scheme == "http" && port == 80)

internal fun validate_alias_website_input(raw: String): String? {
    val with_scheme = with_default_scheme(raw) ?: return null
    val parts = URL_PARTS.matchEntire(with_scheme) ?: return null
    val scheme = parts.groupValues[1].lowercase(Locale.ROOT)
    if (scheme != "https" && scheme != "http") return null
    val authority = parts.groupValues[2]
    if (authority.contains('@')) return null
    val authority_parts = AUTHORITY_PARTS.matchEntire(authority) ?: return null
    val host = to_ascii_host(authority_parts.groupValues[1]) ?: return null
    if (!is_plausible_alias_website_host(host)) return null
    val port_text = authority_parts.groupValues[2]
    val port_suffix = if (port_text.isEmpty()) {
        ""
    } else {
        val port = port_text.toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        if (is_default_port(scheme, port)) "" else ":$port"
    }
    val rest = parts.groupValues[3]
    val path_suffix = if (rest == "/") "" else rest
    val canonical = "$scheme://$host$port_suffix$path_suffix"
    if (canonical.length > ALIAS_WEBSITE_MAX_URL_LENGTH) return null
    return try {
        val uri = URI(canonical)
        if (uri.host != host || uri.rawUserInfo != null) null else canonical
    } catch (e: Exception) {
        null
    }
}
