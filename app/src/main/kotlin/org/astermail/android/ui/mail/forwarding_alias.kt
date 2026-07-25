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

data class ParsedEmailAddress(
    val name: String?,
    val email: String,
)

data class ForwardedAliasInfo(
    val service: String,
    val original: ParsedEmailAddress,
)

data class ForwardingDisplay(
    val display_sender_name: String,
    val display_sender_email: String,
    val forwarding_service: String,
)

private val simplelogin_domains = listOf(
    "simplelogin.co",
    "simplelogin.com",
    "simplelogin.fr",
    "simplelogin.io",
    "slmail.me",
    "slmails.com",
    "silomails.com",
    "aleeas.com",
    "8alias.com",
    "8shield.net",
    "dralias.com",
)

private val addy_domains = listOf("addy.io", "anonaddy.me", "anonaddy.com")

private val email_address_pattern = Regex(
    "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?" +
        "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$",
)

private val angle_address_pattern = Regex("^(.*?)<\\s*([^<>\\s]+)\\s*>\\s*$")
private val quoted_name_pattern = Regex("^\"(.*)\"$")
private val bare_leading_junk = Regex("^[<\\s\"]+")
private val bare_trailing_junk = Regex("[>\\s\"]+$")

fun parse_email_address(value: String): ParsedEmailAddress? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null

    val angle_match = angle_address_pattern.find(trimmed)
    if (angle_match != null) {
        val raw_name = quoted_name_pattern
            .replace(angle_match.groupValues[1].trim(), "$1")
            .trim()
        val email = angle_match.groupValues[2].trim()
        if (!email_address_pattern.matches(email)) return null
        return ParsedEmailAddress(raw_name.takeIf { it.isNotEmpty() }, email)
    }

    val bare = trimmed
        .replace(bare_leading_junk, "")
        .replace(bare_trailing_junk, "")
    if (!email_address_pattern.matches(bare)) return null
    return ParsedEmailAddress(null, bare)
}

private fun domain_of(email: String): String {
    val at = email.lastIndexOf('@')
    return if (at >= 0) email.substring(at + 1).lowercase() else ""
}

private fun matches_forwarder(email: String, domains: List<String>): Boolean {
    val domain = domain_of(email)
    return domains.any { domain == it || domain.endsWith(".$it") }
}

private fun find_header(
    raw_headers: List<Pair<String, String>>,
    name: String,
): String? = raw_headers
    .firstOrNull { it.first.equals(name, ignoreCase = true) }
    ?.second
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

private fun same_email(a: String, b: String): Boolean =
    a.trim().lowercase() == b.trim().lowercase()

fun detect_forwarded_alias(
    raw_headers: List<Pair<String, String>>,
    from_email: String,
): ForwardedAliasInfo? {
    if (raw_headers.isEmpty() || from_email.isBlank()) return null

    val sl_type = find_header(raw_headers, "X-SimpleLogin-Type")
    val sl_original = find_header(raw_headers, "X-SimpleLogin-Original-From")
    val sl_envelope = find_header(raw_headers, "X-SimpleLogin-Envelope-From")

    if (
        (sl_original != null || sl_envelope != null) &&
        matches_forwarder(from_email, simplelogin_domains)
    ) {
        val is_forward = sl_type == null || sl_type.contains("forward", ignoreCase = true)
        if (is_forward) {
            val original = parse_email_address(sl_original ?: sl_envelope.orEmpty())
            if (original != null && !same_email(original.email, from_email)) {
                return ForwardedAliasInfo("simplelogin", original)
            }
        }
    }

    val addy_original = find_header(raw_headers, "X-AnonAddy-Original-Sender")
    if (addy_original != null && matches_forwarder(from_email, addy_domains)) {
        val original = parse_email_address(addy_original)
        if (original != null && !same_email(original.email, from_email)) {
            return ForwardedAliasInfo("addy", original)
        }
    }

    return null
}

fun resolve_forwarding_display(
    from_email: String,
    raw_headers: List<Pair<String, String>>,
): ForwardingDisplay? {
    val forwarded = detect_forwarded_alias(raw_headers, from_email) ?: return null
    val fallback_name = forwarded.original.email.substringBefore('@')
    return ForwardingDisplay(
        display_sender_name = forwarded.original.name
            ?: fallback_name.ifBlank { forwarded.original.email },
        display_sender_email = forwarded.original.email,
        forwarding_service = forwarded.service,
    )
}

fun displayed_sender_name(display_sender_name: String?, sender_name: String): String =
    display_sender_name ?: sender_name

fun displayed_sender_email(display_sender_email: String?, sender_email: String): String =
    display_sender_email ?: sender_email
