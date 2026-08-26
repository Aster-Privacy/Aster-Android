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
    "google" to listOf("google.com", "gmail.com", "googlemail.com", "youtube.com", "google.dev", "withgoogle.com"),
    "apple" to listOf("apple.com", "icloud.com", "me.com", "mac.com", "apple.news"),
    "microsoft" to listOf(
        "microsoft.com", "outlook.com", "hotmail.com", "live.com", "msn.com",
        "microsoftonline.com", "office.com", "office365.com", "azure.com", "windows.com",
        "skype.com", "xbox.com", "sharepointonline.com",
    ),
    "amazon" to listOf("amazon.com", "amazon.jobs", "amazonaws.com", "audible.com", "primevideo.com"),
    "paypal" to listOf("paypal.com", "paypal-communication.com"),
    "netflix" to listOf("netflix.com", "netflix.net", "mailer.netflix.com"),
    "facebook" to listOf("facebook.com", "facebookmail.com", "meta.com", "fb.com"),
    "instagram" to listOf("instagram.com", "mail.instagram.com", "facebookmail.com"),
    "linkedin" to listOf("linkedin.com", "e.linkedin.com", "licdn.com"),
    "twitter" to listOf("twitter.com", "x.com"),
    "chase" to listOf("chase.com", "jpmorgan.com", "jpmchase.com"),
    "wells fargo" to listOf("wellsfargo.com", "wf.com"),
    "bank of america" to listOf("bankofamerica.com", "bofa.com", "ml.com"),
    "dropbox" to listOf("dropbox.com", "dropboxmail.com"),
    "github" to listOf("github.com", "githubusercontent.com", "githubapp.com"),
    "stripe" to listOf("stripe.com", "stripe.dev"),
    "coinbase" to listOf("coinbase.com", "coinbase.email"),
    "discord" to listOf("discord.com", "discordapp.com", "discord.gg"),
)

private val BRAND_ALIAS_LABELS = mapOf(
    "google" to setOf("google", "googlemail", "youtube"),
    "apple" to setOf("apple", "icloud"),
    "microsoft" to setOf("microsoft", "outlook", "office", "office365", "azure", "xbox", "skype"),
    "amazon" to setOf("amazon", "audible", "primevideo"),
    "paypal" to setOf("paypal"),
    "netflix" to setOf("netflix"),
    "facebook" to setOf("facebook", "facebookmail", "meta", "fb"),
    "instagram" to setOf("instagram", "facebookmail"),
    "linkedin" to setOf("linkedin", "licdn"),
    "twitter" to setOf("twitter", "x"),
    "chase" to setOf("chase", "jpmorgan", "jpmchase"),
    "wells fargo" to setOf("wellsfargo", "wf"),
    "bank of america" to setOf("bankofamerica", "bofa", "ml"),
    "dropbox" to setOf("dropbox", "dropboxmail"),
    "github" to setOf("github", "githubusercontent", "githubapp"),
    "stripe" to setOf("stripe"),
    "coinbase" to setOf("coinbase"),
    "discord" to setOf("discord", "discordapp"),
)

private val AMBIGUOUS_BRANDS = setOf("chase", "stripe", "discord", "apple", "amazon")

private val BRAND_CONTEXT_WORDS = listOf(
    "support", "security", "account", "accounts", "billing", "payments", "payment",
    "service", "services", "team", "help", "notification", "notifications", "alerts",
    "alert", "no-reply", "noreply", "verification", "verify", "recovery", "customer",
    "care", "invoice", "receipt", "store", "pay", "id", "mail", "info", "admin",
)

private val PERSONAL_NAME_SUFFIX_REGEX = Regex("""^[a-z]+ [a-z]{2,}$""")

private val EMAIL_IN_NAME_REGEX = Regex("""[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}""")

private val DIGIT_TO_LETTER = mapOf('0' to 'o', '1' to 'l', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't')

private fun normalize_for_brand_match(s: String): String {
    val sb = StringBuilder()
    for (ch in s.lowercase(java.util.Locale.ROOT)) {
        val mapped = DIGIT_TO_LETTER[ch]
        if (mapped != null) sb.append(mapped)
        else if (ch in 'a'..'z') sb.append(ch)
    }
    return sb.toString()
}

private val MULTI_LABEL_SUFFIXES = setOf(
    "co.uk", "co.jp", "co.kr", "co.nz", "co.za", "co.in", "com.au", "com.br", "com.mx",
    "com.tr", "com.sg", "com.hk", "com.cn", "com.ar", "com.pl", "net.au", "org.uk", "gov.uk",
)

private fun registrable_label(domain: String): String {
    val parts = domain.split('.').filter { it.isNotEmpty() }
    if (parts.size < 2) return parts.firstOrNull().orEmpty()
    val last_two = parts.takeLast(2).joinToString(".")
    return if (last_two in MULTI_LABEL_SUFFIXES && parts.size >= 3) {
        parts[parts.size - 3]
    } else {
        parts[parts.size - 2]
    }
}

private fun is_brand_domain(sender_domain: String, brand: String, legit_domains: List<String>): Boolean {
    if (sender_domain.isEmpty()) return false
    if (legit_domains.any { d -> sender_domain == d || sender_domain.endsWith(".$d") }) return true
    val label = registrable_label(sender_domain)
    if (label.isEmpty()) return false
    val aliases = BRAND_ALIAS_LABELS[brand] ?: setOf(brand.replace(" ", ""))
    return label in aliases
}

private fun display_name_targets_brand(display_name: String, brand: String): Boolean {
    val name = display_name.lowercase(java.util.Locale.ROOT).trim()
    if (name.isEmpty()) return false
    val brand_pattern = Regex("\\b" + Regex.escape(brand) + "\\b")
    if (!brand_pattern.containsMatchIn(name)) return false
    val stripped = name.replace(brand_pattern, " ").replace(Regex("[^a-z0-9 ]"), " ").trim()
    val remainder = stripped.replace(Regex("\\s+"), " ")
    if (remainder.isEmpty()) return true
    val remainder_words = remainder.split(" ").filter { it.isNotEmpty() }
    if (remainder_words.all { it in BRAND_CONTEXT_WORDS }) return true
    if (brand in AMBIGUOUS_BRANDS && PERSONAL_NAME_SUFFIX_REGEX.matches(name)) return false
    return brand !in AMBIGUOUS_BRANDS
}

private fun check_sender_domain_lookalike(sender_name: String, sender_email: String): List<PhishingSignal> {
    val sender_domain = sender_email.substringAfter('@', "").lowercase(java.util.Locale.ROOT)
    if (sender_domain.isEmpty()) return emptyList()
    val name_lower = sender_name.lowercase(java.util.Locale.ROOT)
    val registrable = registrable_label(sender_domain)
    val normalized_registrable = normalize_for_brand_match(registrable)
    if (normalized_registrable.isEmpty()) return emptyList()
    for ((brand, legit_domains) in BRAND_DISPLAY_NAMES) {
        val brand_key = brand.replace(" ", "")
        if (is_brand_domain(sender_domain, brand, legit_domains)) continue
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
    val lower_name = sender_name.lowercase(java.util.Locale.ROOT).trim()
    val sender_domain = sender_email.substringAfter('@', "").lowercase(java.util.Locale.ROOT)

    for ((brand, legit_domains) in BRAND_DISPLAY_NAMES) {
        if (!display_name_targets_brand(lower_name, brand)) continue
        if (is_brand_domain(sender_domain, brand, legit_domains)) continue
        out += PhishingSignal(
            name = "display_name_brand_spoof",
            category = "display_name",
            description_res = R.string.phishing_signal_display_name_brand,
            description_args = listOf(brand, sender_domain),
        )
        break
    }

    val email_in_name = EMAIL_IN_NAME_REGEX.find(lower_name)?.value
    if (email_in_name != null && email_in_name != sender_email.lowercase(java.util.Locale.ROOT)) {
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

    val spf = spf_result?.lowercase(java.util.Locale.ROOT)
    val dkim = dkim_result?.lowercase(java.util.Locale.ROOT)
    val dmarc = dmarc_result?.lowercase(java.util.Locale.ROOT)
    val authenticated = dmarc == "pass" && (dkim == "pass" || spf == "pass")
    val auth_failed = dmarc == "fail" || spf == "fail" || dkim == "fail"

    val signals = mutableListOf<PhishingSignal>()
    signals += check_display_name_spoof(sender_name, sender_email)
    signals += check_sender_domain_lookalike(sender_name, sender_email)
    if (auth_failed) {
        signals += PhishingSignal(
            name = "sender_authentication_failed",
            category = "authentication",
            description_res = R.string.phishing_signal_authentication_failed,
            description_args = listOf(sender_email.substringAfter('@', sender_email)),
        )
    }

    val categories = signals.map { it.category }.toSet()
    val score = signals.sumOf { signal ->
        val weight = WEIGHTS[signal.name] ?: 1.0
        if (authenticated && signal.category == "display_name") weight / 2.0 else weight
    }
    val level = when {
        score >= 9.0 && categories.size >= 2 -> PhishingLevel.dangerous
        score >= 4.0 -> PhishingLevel.suspicious
        else -> PhishingLevel.safe
    }
    return PhishingResult(level, score, signals)
}
