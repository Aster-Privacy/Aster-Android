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

import org.astermail.android.R

enum class PhishingLevel { safe, suspicious, dangerous }

data class PhishingSignal(
    val name: String,
    val category: String,
    val description_res: Int,
    val description_args: List<String> = emptyList(),
)

data class PhishingResult(
    val level: PhishingLevel,
    val score: Double,
    val signals: List<PhishingSignal>,
)

private val WEIGHTS = mapOf(
    "sender_authentication_failed" to 4.0,
)

fun analyze_email(
    html_content: String,
    text_content: String,
    sender_name: String,
    sender_email: String,
    is_external: Boolean,
    spf_result: String? = null,
    dkim_result: String? = null,
    dmarc_result: String? = null,
): PhishingResult {
    if (!is_external) return PhishingResult(PhishingLevel.safe, 0.0, emptyList())

    val auth_verdict = summarize_email_authentication(spf_result, dkim_result, dmarc_result)?.verdict
    val auth_failed = auth_verdict == EmailAuthVerdict.failed

    val signals = mutableListOf<PhishingSignal>()
    if (auth_failed) {
        signals += PhishingSignal(
            name = "sender_authentication_failed",
            category = "authentication",
            description_res = R.string.phishing_signal_authentication_failed,
            description_args = listOf(sender_email.substringAfter('@', sender_email)),
        )
    }

    val categories = signals.map { it.category }.toSet()
    val score = signals.sumOf { signal -> WEIGHTS[signal.name] ?: 1.0 }
    val level = when {
        score >= 9.0 && categories.size >= 2 -> PhishingLevel.dangerous
        score >= 4.0 -> PhishingLevel.suspicious
        else -> PhishingLevel.safe
    }
    return PhishingResult(level, score, signals)
}
