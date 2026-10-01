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

package org.astermail.android.security

import java.net.IDN
import java.util.Locale

// Summary of the SPF, DKIM and DMARC results that Aster's mail servers record
// when a message arrives (spf_result, dkim_result and dmarc_result). Only
// those server-side results are used: an Authentication-Results header inside
// the message could have been written by the sender. The web client applies
// the same rules.

enum class EmailAuthCheck { spf, dkim, dmarc }

enum class EmailAuthStatus { pass, fail, none, missing, other }

enum class EmailAuthVerdict { authenticated, unverified, partial, failed }

data class EmailAuthCheckResult(
    val check: EmailAuthCheck,
    val status: EmailAuthStatus,
    val value: String,
)

data class EmailAuthSummary(
    val verdict: EmailAuthVerdict,
    val checks: List<EmailAuthCheckResult>,
)

private const val max_shown_auth_value = 32

private fun auth_check_result(check: EmailAuthCheck, raw: String?): EmailAuthCheckResult {
    val value = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val status = when (value) {
        "", "missing" -> EmailAuthStatus.missing
        "pass" -> EmailAuthStatus.pass
        "fail", "hardfail" -> EmailAuthStatus.fail
        "none" -> EmailAuthStatus.none
        else -> EmailAuthStatus.other
    }
    val shown = when (status) {
        EmailAuthStatus.missing -> ""
        EmailAuthStatus.other -> cap_code_points(value.uppercase(Locale.ROOT), max_shown_auth_value)
        else -> value
    }
    return EmailAuthCheckResult(check, status, shown)
}

private fun cap_code_points(text: String, limit: Int): String =
    if (text.codePointCount(0, text.length) <= limit) text else text.substring(0, text.offsetByCodePoints(0, limit))

// Authenticated when DMARC passed on top of SPF or DKIM. Failed when a check
// failed and DMARC did not pass. Inconclusive when a check gave an unusual
// result (softfail, neutral, temperror...), or when DMARC passed without an
// SPF or DKIM pass: the server accepted the domain, for example through
// another DKIM signature, so a failed check alone does not make the message
// a spoof. Not fully verified when checks were simply absent, which is
// common for legitimate mail from domains without a DMARC policy. Null when
// the server recorded nothing.
fun summarize_email_authentication(
    spf_result: String?,
    dkim_result: String?,
    dmarc_result: String?,
): EmailAuthSummary? {
    val spf = auth_check_result(EmailAuthCheck.spf, spf_result)
    val dkim = auth_check_result(EmailAuthCheck.dkim, dkim_result)
    val dmarc = auth_check_result(EmailAuthCheck.dmarc, dmarc_result)
    val checks = listOf(spf, dkim, dmarc)
    if (checks.all { it.status == EmailAuthStatus.missing }) return null
    val dmarc_passed = dmarc.status == EmailAuthStatus.pass
    val verdict = when {
        dmarc_passed && (spf.status == EmailAuthStatus.pass || dkim.status == EmailAuthStatus.pass) ->
            EmailAuthVerdict.authenticated
        !dmarc_passed && checks.any { it.status == EmailAuthStatus.fail } -> EmailAuthVerdict.failed
        dmarc_passed || checks.any { it.status == EmailAuthStatus.other } -> EmailAuthVerdict.partial
        else -> EmailAuthVerdict.unverified
    }
    return EmailAuthSummary(verdict, checks)
}

// Longer than any host name; also keeps the pattern below away from inputs
// long enough to exhaust the regex engine's stack.
private const val max_domain_length = 253

// java.net.IDN follows IDNA2003, which maps the sharp s and the final sigma
// to other letters ("ss", a plain sigma) instead of encoding them, so the
// ASCII form shown would not be the domain the checks ran on.
private val idna_deviations = setOf('\u00DF', '\u03C2')

private val auth_bidi_controls = Regex("[\u061C\u200E\u200F\u202A-\u202E\u2066-\u2069]")

// Dot-separated labels of letters, marks, digits and hyphens. Anything else
// (a slash, a colon, an invisible format character) could not be part of a
// host name the checks looked up.
private val auth_domain_pattern = Regex("""^[\p{L}\p{M}\p{N}-]+(?:\.[\p{L}\p{M}\p{N}-]+)*\.?$""")

// Only ASCII letters: IDN folds the case of other scripts the way DNS does,
// where lowercase() would turn a closing capital sigma into a final sigma.
private fun ascii_lowercase(text: String): String =
    buildString(text.length) { text.forEach { append(if (it in 'A'..'Z') it + 32 else it) } }

// The domain of the From address, as the checks saw it: without bidi
// controls that could reorder it, and in its ASCII (punycode) form when it
// holds other scripts, so look-alike letters show up. Empty when it could
// not be a host name, which hides the badge rather than show a domain the
// checks did not see.
fun auth_display_domain(sender_email: String?): String {
    val email = sender_email.orEmpty().replace(auth_bidi_controls, "").trim()
    val at = email.lastIndexOf('@')
    val domain = if (at >= 0) ascii_lowercase(email.substring(at + 1).trim()) else ""
    if (domain.length > max_domain_length || domain.any { it in idna_deviations }) return ""
    if (!auth_domain_pattern.matches(domain)) return ""
    if (domain.all { it.code < 0x80 }) return domain
    val ascii = try {
        IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    } catch (_: IllegalArgumentException) {
        return ""
    }
    return if (ascii.isNotEmpty() && ascii.all { it.code < 0x80 }) ascii else ""
}
