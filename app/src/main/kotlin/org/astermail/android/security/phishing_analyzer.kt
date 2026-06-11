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

private val BRAND_DISPLAY_NAMES = mapOf(
    "google" to listOf("google.com", "gmail.com"),
    "apple" to listOf("apple.com", "icloud.com"),
    "microsoft" to listOf("microsoft.com", "outlook.com", "hotmail.com"),
    "amazon" to listOf("amazon.com"),
    "paypal" to listOf("paypal.com"),
    "netflix" to listOf("netflix.com"),
    "facebook" to listOf("facebook.com", "meta.com"),
    "instagram" to listOf("instagram.com"),
    "linkedin" to listOf("linkedin.com"),
    "twitter" to listOf("twitter.com", "x.com"),
    "chase" to listOf("chase.com"),
    "wells fargo" to listOf("wellsfargo.com"),
    "bank of america" to listOf("bankofamerica.com"),
    "dropbox" to listOf("dropbox.com"),
    "github" to listOf("github.com"),
    "stripe" to listOf("stripe.com"),
    "coinbase" to listOf("coinbase.com"),
    "discord" to listOf("discord.com"),
)

private val EMAIL_IN_NAME_REGEX = Regex("""[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}""")

private val DIGIT_TO_LETTER = mapOf('0' to 'o', '1' to 'l', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't')

private fun normalize_for_brand_match(s: String): String {
    val sb = StringBuilder()
    for (ch in s.lowercase()) {
        val mapped = DIGIT_TO_LETTER[ch]
        if (mapped != null) sb.append(mapped)
        else if (ch in 'a'..'z') sb.append(ch)
    }
    return sb.toString()
}

private fun check_sender_domain_lookalike(sender_name: String, sender_email: String): List<PhishingSignal> {
    val sender_domain = sender_email.substringAfter('@', "").lowercase()
    if (sender_domain.isEmpty()) return emptyList()
    val name_lower = sender_name.lowercase()
    val registrable = sender_domain.split('.').let { parts ->
        if (parts.size >= 2) parts[parts.size - 2] else parts.firstOrNull().orEmpty()
    }
    val normalized_registrable = normalize_for_brand_match(registrable)
    if (normalized_registrable.isEmpty()) return emptyList()
    for ((brand, legit_domains) in BRAND_DISPLAY_NAMES) {
        val brand_key = brand.replace(" ", "")
        val is_legit = legit_domains.any { d -> sender_domain == d || sender_domain.endsWith(".$d") }
        if (is_legit) continue
        val name_mentions_brand = brand in name_lower
        val domain_lookalike = brand_key in normalized_registrable && registrable != brand_key
        if (name_mentions_brand && domain_lookalike) {
            return listOf(
                PhishingSignal(
                    name = "sender_domain_lookalike",
                    category = "sender_domain",
                    description_res = R.string.phishing_signal_sender_domain_mimics,
                    description_args = listOf(sender_domain, brand),
                ),
            )
        }
        if (domain_lookalike) {
            return listOf(
                PhishingSignal(
                    name = "sender_domain_lookalike",
                    category = "sender_domain",
                    description_res = R.string.phishing_signal_sender_domain_resembles,
                    description_args = listOf(sender_domain, brand),
                ),
            )
        }
    }
    return emptyList()
}

private fun check_display_name_spoof(sender_name: String, sender_email: String): List<PhishingSignal> {
    val out = mutableListOf<PhishingSignal>()
    val lower_name = sender_name.lowercase().trim()
    val sender_domain = sender_email.substringAfter('@', "").lowercase()

    for ((brand, legit_domains) in BRAND_DISPLAY_NAMES) {
        if (brand in lower_name) {
            val is_legit = legit_domains.any { d -> sender_domain == d || sender_domain.endsWith(".$d") }
            if (!is_legit) {
                out += PhishingSignal(
                    name = "display_name_brand_spoof",
                    category = "display_name",
                    description_res = R.string.phishing_signal_display_name_brand,
                    description_args = listOf(brand, sender_domain),
                )
                break
            }
        }
    }

    val email_in_name = EMAIL_IN_NAME_REGEX.find(lower_name)?.value
    if (email_in_name != null && email_in_name != sender_email.lowercase()) {
        out += PhishingSignal(
            name = "display_name_email_mismatch",
            category = "display_name",
            description_res = R.string.phishing_signal_display_name_email,
            description_args = listOf(email_in_name, sender_email),
        )
    }
    return out
}

private val WEIGHTS = mapOf(
    "sender_domain_lookalike" to 6.0,
    "display_name_brand_spoof" to 4.0,
    "display_name_email_mismatch" to 3.0,
)

fun analyze_email(
    html_content: String,
    text_content: String,
    sender_name: String,
    sender_email: String,
    is_external: Boolean,
): PhishingResult {
    if (!is_external) return PhishingResult(PhishingLevel.safe, 0.0, emptyList())

    val signals = mutableListOf<PhishingSignal>()
    signals += check_display_name_spoof(sender_name, sender_email)
    signals += check_sender_domain_lookalike(sender_name, sender_email)

    val categories = signals.map { it.category }.toSet()
    val score = signals.sumOf { WEIGHTS[it.name] ?: 1.0 }
    val level = when {
        score >= 9.0 && categories.size >= 2 -> PhishingLevel.dangerous
        score >= 4.0 -> PhishingLevel.suspicious
        else -> PhishingLevel.safe
    }
    return PhishingResult(level, score, signals)
}
