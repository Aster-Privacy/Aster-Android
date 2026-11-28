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

package org.astermail.android.mail

import androidx.annotation.StringRes
import org.astermail.android.R

enum class TrustVerdict { phishing, suspicious }

enum class TrustReason(val code: String, @StringRes val line_res: Int) {
    impersonation("impersonation", R.string.trust_reason_impersonation),
    self_name("self-name", R.string.trust_reason_self_name),
    address_in_name("address-in-name", R.string.trust_reason_address_in_name),
    lookalike_sender("lookalike-sender", R.string.trust_reason_lookalike_sender),
    unverified_sender("unverified-sender", R.string.trust_reason_unverified_sender),
    deceptive_link("deceptive-link", R.string.trust_reason_deceptive_link),
    reported_link("reported-link", R.string.trust_reason_reported_link),
    form("form", R.string.trust_reason_form),
}

data class MessageTrust(
    val verdict: TrustVerdict,
    val score: Int?,
    val reasons: List<TrustReason>,
)

const val MESSAGE_TRUST_HEADER = "x-aster-trust"
private const val MAX_TRUST_REASONS = 4

fun parse_message_trust(raw_headers: List<Pair<String, String>>): MessageTrust? {
    val value = raw_headers.firstOrNull { it.first.trim().equals(MESSAGE_TRUST_HEADER, ignoreCase = true) }?.second
        ?: return null
    val fields = value.split(';').mapNotNull { part ->
        val key = part.substringBefore('=', "").trim().lowercase()
        if (key.isEmpty()) null else key to part.substringAfter('=').trim()
    }.toMap()
    val verdict = when (fields["verdict"]?.lowercase()) {
        "phishing" -> TrustVerdict.phishing
        "suspicious" -> TrustVerdict.suspicious
        else -> return null
    }
    val score = fields["score"]?.toIntOrNull()?.takeIf { it in 0..100 }
    val reasons = fields["reasons"].orEmpty()
        .split(',')
        .mapNotNull { code -> TrustReason.entries.firstOrNull { it.code == code.trim().lowercase() } }
        .distinct()
        .take(MAX_TRUST_REASONS)
    return MessageTrust(verdict, score, reasons)
}

private const val MAX_REPORT_HOSTS = 50
private val HREF_PATTERN = Regex("""href\s*=\s*["']?\s*(https?://[^"'\s>]+)""", RegexOption.IGNORE_CASE)
private val IPV4_PATTERN = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

fun phishing_report_hosts(html: String?): List<String> {
    if (html.isNullOrEmpty()) return emptyList()
    return HREF_PATTERN.findAll(html)
        .mapNotNull { match -> report_host_of(match.groupValues[1].replace("&amp;", "&")) }
        .distinct()
        .take(MAX_REPORT_HOSTS)
        .toList()
}

private fun report_host_of(url: String): String? {
    val authority = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfterLast('@').let { if (it.startsWith('[')) return null else it.substringBefore(':') }
        .trim()
        .trimEnd('.')
        .lowercase()
    if (host.isEmpty() || host == "localhost" || '.' !in host || IPV4_PATTERN.matches(host)) return null
    return runCatching { java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED).lowercase() }.getOrNull()
        ?.takeIf { it.isNotEmpty() }
}
