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

private val addy_domains = listOf(
    "addy.io",
    "anonaddy.me",
    "anonaddy.com",
    "4wrd.cc",
    "mailer.me",
    "addymail.com",
)

private const val max_display_name_length = 128
private const val max_header_value_length = 998

private val name_control_chars = Regex(
    "[\\u0000-\\u001f\\u007f-\\u009f\\u200b-\\u200f\\u202a-\\u202e\\u2066-\\u2069]",
)
private val whitespace_run = Regex("\\s+")
private val encoded_word_pattern = Regex("=\\?([^?\\s]+)\\?([bBqQ])\\?([^?\\s]*)\\?=")
private val encoded_word_gap = Regex("(=\\?[^?\\s]+\\?[bBqQ]\\?[^?\\s]*\\?=)\\s+(?==\\?)")
private val addy_dmarc_fail = Regex("\\bdmarc\\s*=\\s*fail\\b", RegexOption.IGNORE_CASE)

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
): String? {
    val values = raw_headers
        .filter { it.first.equals(name, ignoreCase = true) }
        .map { it.second.trim() }
        .filter { it.isNotEmpty() }
    if (values.isEmpty()) return null
    if (values.any { it != values[0] }) return null
    if (values[0].length > max_header_value_length) return null
    return values[0]
}

private fun decode_encoded_word(charset: String, encoding: String, text: String): String? {
    val bytes = try {
        if (encoding.equals("b", ignoreCase = true)) {
            java.util.Base64.getDecoder().decode(text)
        } else {
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '_') {
                    out.write(0x20)
                    i += 1
                } else if (c == '=' && i + 2 < text.length) {
                    out.write(text.substring(i + 1, i + 3).toInt(16))
                    i += 3
                } else {
                    out.write(c.code and 0xff)
                    i += 1
                }
            }
            out.toByteArray()
        }
    } catch (_: IllegalArgumentException) {
        return null
    }
    val decoder_charset = try {
        java.nio.charset.Charset.forName(charset.substringBefore('*'))
    } catch (_: Exception) {
        return null
    }
    return String(bytes, decoder_charset)
}

fun decode_header_words(value: String): String {
    val joined = encoded_word_gap.replace(value, "$1")
    return encoded_word_pattern.replace(joined) { match ->
        decode_encoded_word(
            match.groupValues[1],
            match.groupValues[2],
            match.groupValues[3],
        ) ?: match.value
    }
}

private fun clean_display_name(name: String?): String? {
    if (name.isNullOrBlank()) return null
    val decoded = decode_header_words(name)
        .replace(name_control_chars, " ")
        .replace(whitespace_run, " ")
        .trim()
        .let { quoted_name_pattern.replace(it, "$1") }
        .trim()
    if (decoded.isEmpty()) return null
    return if (decoded.length > max_display_name_length) {
        decoded.substring(0, max_display_name_length).trim()
    } else {
        decoded
    }
}

fun parse_original_sender(value: String?): ParsedEmailAddress? {
    if (value.isNullOrBlank()) return null
    val parsed = parse_email_address(value) ?: return null
    return ParsedEmailAddress(clean_display_name(parsed.name), parsed.email)
}

private fun addy_reports_dmarc_failure(raw_headers: List<Pair<String, String>>): Boolean =
    raw_headers.any {
        it.first.equals("X-AnonAddy-Authentication-Results", ignoreCase = true) &&
            addy_dmarc_fail.containsMatchIn(it.second)
    }

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
            val original = parse_original_sender(sl_original) ?: parse_original_sender(sl_envelope)
            if (original != null && !same_email(original.email, from_email)) {
                return ForwardedAliasInfo("simplelogin", original)
            }
        }
    }

    val addy_from_header = find_header(raw_headers, "X-AnonAddy-Original-From-Header")
    val addy_original = find_header(raw_headers, "X-AnonAddy-Original-Sender")
    if (
        (addy_from_header != null || addy_original != null) &&
        matches_forwarder(from_email, addy_domains) &&
        !addy_reports_dmarc_failure(raw_headers)
    ) {
        val original = parse_original_sender(addy_from_header) ?: parse_original_sender(addy_original)
        if (original != null && !same_email(original.email, from_email)) {
            return ForwardedAliasInfo("addy", original)
        }
    }

    return null
}

data class ForwardingAuthResults(
    val dkim_result: String? = null,
    val dmarc_result: String? = null,
    val sender_aligned: Boolean? = null,
)

private fun auth_status(raw: String?): String {
    val value = raw.orEmpty().trim().lowercase()
    return if (value == "missing") "" else value
}

fun is_forwarder_authenticated(auth: ForwardingAuthResults?): Boolean {
    val dkim = auth_status(auth?.dkim_result)
    val dmarc = auth_status(auth?.dmarc_result)
    if (auth?.sender_aligned == false) return false
    if (auth?.sender_aligned == true) return true
    if (dkim.isEmpty() && dmarc.isEmpty()) return true
    if (dmarc == "pass") return true
    if (dmarc.isNotEmpty() && dmarc != "none") return false
    return dkim == "pass"
}

fun forwarding_address(display_sender_email: String?, sender_email: String): String? {
    val shown = display_sender_email?.trim().orEmpty()
    val literal = sender_email.trim()
    if (shown.isEmpty() || literal.isEmpty()) return null
    if (same_email(shown, literal)) return null
    return literal
}

fun resolve_forwarding_display(
    from_email: String,
    raw_headers: List<Pair<String, String>>,
    auth: ForwardingAuthResults? = null,
): ForwardingDisplay? {
    if (!is_forwarder_authenticated(auth)) return null
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
