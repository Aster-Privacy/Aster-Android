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

import org.astermail.android.api.mail.MailItemMetadata

val CATEGORY_TABS: List<String> = listOf("primary", "promotions", "social", "updates")

private val RULE_CATEGORY_IDS: Set<String> =
    setOf("primary", "important", "promotions", "social", "updates", "forums")

private val UPDATES_LOCALPARTS: Set<String> = setOf(
    "receipts",
    "receipt",
    "billing",
    "invoice",
    "invoices",
    "notifications",
    "notification",
    "notify",
    "alerts",
    "alert",
    "security",
    "orders",
    "order",
    "statements",
    "statement",
)

private val PROMO_LOCALPARTS: Set<String> = setOf(
    "marketing",
    "offers",
    "deals",
    "promo",
    "promotions",
    "news",
    "newsletter",
    "newsletters",
)

private const val MAX_HEADER_VALUE = 2048
private const val MAX_SUBJECT = 512

private fun domain_in_set(domain: String, set: Set<String>): Boolean {
    var current = domain
    while (current.isNotEmpty()) {
        if (set.contains(current)) return true
        val dot = current.indexOf('.')
        if (dot == -1) return false
        current = current.substring(dot + 1)
    }
    return false
}

private fun sender_domain(email: String): String {
    val at = email.indexOf('@')
    if (at == -1) return ""
    return email.substring(at + 1).lowercase().trimEnd('.', '>')
}

private fun get_localpart(email: String): String {
    val at = email.indexOf('@')
    return (if (at == -1) email else email.substring(0, at)).lowercase()
}

private val AT_DOMAIN_REGEX = Regex("""@([^@>\s]+)""")

private fun domain_of_value(value: String): String {
    val match = AT_DOMAIN_REGEX.find(value) ?: return ""
    return match.groupValues[1].lowercase().trimEnd('.', '>')
}

private val DKIM_D_REGEX = Regex("""(?:^|;)\s*d=\s*([^;\s]+)""", RegexOption.IGNORE_CASE)

private fun dkim_domain(headers: Map<String, String>): String {
    val sig = headers["dkim-signature"] ?: ""
    val match = DKIM_D_REGEX.find(sig) ?: return ""
    return match.groupValues[1].lowercase().trimEnd('.', '>')
}

private fun build_header_lookup(raw_headers: List<Pair<String, String>>): Map<String, String> {
    val lookup = HashMap<String, String>()
    for ((name, value) in raw_headers) {
        if (name.isNotEmpty()) {
            lookup[name.lowercase()] = value.take(MAX_HEADER_VALUE)
        }
    }
    return lookup
}

private fun matches_any(text: String, patterns: List<Regex>): Boolean {
    for (pattern in patterns) {
        if (pattern.containsMatchIn(text)) return true
    }
    return false
}

fun classify(
    envelope: DecryptedEnvelope,
    metadata: MailItemMetadata?,
    rule_category: String? = null,
): String {
    val pinned_category = metadata?.category
    if (metadata?.category_pinned == true && pinned_category != null) {
        return pinned_category
    }

    if (rule_category != null && RULE_CATEGORY_IDS.contains(rule_category)) {
        return rule_category
    }

    val email = envelope.from_email
    val from_domain = sender_domain(email)
    val localpart = get_localpart(email)
    val subject = envelope.subject.take(MAX_SUBJECT)
    val headers = build_header_lookup(envelope.raw_headers)
    val precedence = (headers["precedence"] ?: "").lowercase()

    val auth_domains = mutableListOf(from_domain)
    val dkim = dkim_domain(headers)
    if (dkim.isNotEmpty()) auth_domains.add(dkim)
    val return_path = domain_of_value(headers["return-path"] ?: "")
    if (return_path.isNotEmpty()) auth_domains.add(return_path)
    val sender = domain_of_value(headers["sender"] ?: "")
    if (sender.isNotEmpty()) auth_domains.add(sender)

    fun in_any(set: Set<String>): Boolean = auth_domains.any { domain_in_set(it, set) }

    if (domain_in_set(from_domain, ASTER_DOMAIN_SUFFIXES) &&
        envelope.sender_verification != "invalid"
    ) {
        return "primary"
    }

    if (domain_in_set(from_domain, SOCIAL_DOMAIN_SUFFIXES)) {
        return "social"
    }

    val has_list_headers =
        headers.containsKey("list-id") ||
            headers.containsKey("list-post") ||
            headers.containsKey("mailing-list")

    if (has_list_headers || domain_in_set(from_domain, FORUM_DOMAIN_SUFFIXES)) {
        return "forums"
    }

    val has_unsubscribe =
        !envelope.list_unsubscribe.isNullOrEmpty() ||
            headers.containsKey("list-unsubscribe")
    val auto_submitted = (headers["auto-submitted"] ?: "").lowercase()
    val bulk_precedence =
        precedence == "bulk" || precedence == "list" || precedence == "auto_replied"
    val is_automated =
        has_unsubscribe ||
            bulk_precedence ||
            headers.containsKey("feedback-id") ||
            headers.containsKey("x-csa-complaints") ||
            (auto_submitted != "" && auto_submitted != "no") ||
            BULK_SENDER_LOCALPARTS.contains(localpart) ||
            in_any(MARKETING_DOMAIN_SUFFIXES) ||
            in_any(BULK_INFRA_DOMAIN_SUFFIXES)

    if (!is_automated) {
        if (in_any(UPDATES_DOMAIN_SUFFIXES) && matches_any(subject, UPDATES_SUBJECT_PATTERNS)) {
            return "updates"
        }
        return "primary"
    }

    val promo_signal =
        in_any(MARKETING_DOMAIN_SUFFIXES) ||
            PROMO_LOCALPARTS.contains(localpart) ||
            matches_any(subject, PROMOTIONS_SUBJECT_PATTERNS)
    val trusted_transactional =
        in_any(UPDATES_DOMAIN_SUFFIXES) || UPDATES_LOCALPARTS.contains(localpart)
    val transactional_signal =
        trusted_transactional || matches_any(subject, UPDATES_SUBJECT_PATTERNS)

    if (transactional_signal && (!promo_signal || trusted_transactional)) {
        return "updates"
    }

    if (promo_signal) {
        return "promotions"
    }

    if (has_unsubscribe || in_any(BULK_INFRA_DOMAIN_SUFFIXES)) {
        return "promotions"
    }

    return "primary"
}

fun category_for_tab(category: String?): String {
    if (category == "forums") return "updates"
    if (category != null && CATEGORY_TABS.contains(category)) return category
    return "primary"
}

fun category_unread_counts(items: List<InboxItem>): Map<String, Int> {
    val by_thread = LinkedHashMap<String, MutableList<InboxItem>>()
    for (item in items) {
        if (item.is_trashed || item.is_archived || item.is_spam) continue
        by_thread.getOrPut(item.thread_token ?: item.id) { mutableListOf() }.add(item)
    }
    val counts = HashMap<String, Int>()
    for (tab in CATEGORY_TABS) counts[tab] = 0
    for ((_, msgs) in by_thread) {
        if (msgs.none { !it.is_read }) continue
        val newest = msgs.maxByOrNull { it.timestamp } ?: continue
        val tab = category_for_tab(newest.category)
        counts[tab] = (counts[tab] ?: 0) + 1
    }
    return counts
}
