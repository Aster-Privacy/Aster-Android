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
import org.astermail.android.api.preferences.CustomCategoryRule

val CATEGORY_TABS: List<String> = listOf("primary", "promotions", "social", "updates")

private val RULE_CATEGORY_IDS: Set<String> = BUILTIN_CATEGORY_IDS + "important"

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

private fun match_custom_category(
    auth_domains: List<String>,
    subject: String,
    custom_categories: List<CustomCategoryRule>,
): String? {
    if (custom_categories.isEmpty()) return null
    val lower_subject = subject.lowercase()
    for (rule in custom_categories) {
        if (!rule.enabled) continue
        val domain_match = rule.match_domains.any { suffix ->
            auth_domains.any { domain_in_set(it, setOf(suffix)) }
        }
        if (domain_match) return rule.id
        val keyword_match = rule.match_keywords.any { lower_subject.contains(it) }
        if (keyword_match) return rule.id
    }
    return null
}

private fun resolve_rule_category(
    rule_category: String?,
    custom_categories: List<CustomCategoryRule>,
): String? {
    if (rule_category.isNullOrEmpty()) return null
    if (RULE_CATEGORY_IDS.contains(rule_category)) return rule_category
    val custom = custom_categories.any { it.id == rule_category && it.enabled }
    return if (custom) rule_category else null
}

fun classify(
    envelope: DecryptedEnvelope,
    metadata: MailItemMetadata?,
    rule_category: String? = null,
    custom_categories: List<CustomCategoryRule> = emptyList(),
): String {
    val pinned_category = metadata?.category
    if (metadata?.category_pinned == true && pinned_category != null) {
        return pinned_category
    }

    val from_rule = resolve_rule_category(rule_category, custom_categories)
    if (from_rule != null) {
        return from_rule
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

    val custom_match = match_custom_category(auth_domains, subject, custom_categories)
    if (custom_match != null) {
        return custom_match
    }

    if (domain_in_set(from_domain, SOCIAL_DOMAIN_SUFFIXES)) {
        return "social"
    }

    if (in_any(FINANCE_DOMAIN_SUFFIXES) && matches_any(subject, FINANCE_SUBJECT_PATTERNS)) {
        return "finance"
    }

    if (domain_in_set(from_domain, FINANCE_DOMAIN_SUFFIXES)) {
        return "finance"
    }

    if (in_any(TRAVEL_DOMAIN_SUFFIXES) && matches_any(subject, TRAVEL_SUBJECT_PATTERNS)) {
        return "travel"
    }

    if (domain_in_set(from_domain, TRAVEL_DOMAIN_SUFFIXES)) {
        return "travel"
    }

    if (in_any(SHOPPING_DOMAIN_SUFFIXES) && matches_any(subject, SHOPPING_SUBJECT_PATTERNS)) {
        return "shopping"
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
        val known_service =
            in_any(UPDATES_DOMAIN_SUFFIXES) ||
                in_any(SHOPPING_DOMAIN_SUFFIXES) ||
                in_any(FINANCE_DOMAIN_SUFFIXES) ||
                in_any(TRAVEL_DOMAIN_SUFFIXES)
        if (known_service && matches_any(subject, UPDATES_SUBJECT_PATTERNS)) {
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

fun category_for_tab(category: String?, active_tabs: List<String> = CATEGORY_TABS): String {
    if (category.isNullOrEmpty()) return "primary"
    if (active_tabs.contains(category)) return category
    if (is_custom_category_id(category)) return "primary"
    if (!BUILTIN_CATEGORY_IDS.contains(category)) return "primary"

    var target = fold_builtin(category)
    var guard = 0
    while (!active_tabs.contains(target) && target != "primary" && guard < 8) {
        target = fold_builtin(target)
        guard += 1
    }
    return if (active_tabs.contains(target)) target else "primary"
}

fun category_unread_counts(
    items: List<InboxItem>,
    active_tabs: List<String> = CATEGORY_TABS,
): Map<String, Int> {
    val by_thread = LinkedHashMap<String, MutableList<InboxItem>>()
    for (item in items) {
        if (item.is_trashed || item.is_archived || item.is_spam) continue
        by_thread.getOrPut(item.thread_token ?: item.id) { mutableListOf() }.add(item)
    }
    val counts = HashMap<String, Int>()
    for (tab in active_tabs) counts[tab] = 0
    for ((_, msgs) in by_thread) {
        if (msgs.none { !it.is_read }) continue
        val newest = msgs.maxByOrNull { it.timestamp } ?: continue
        val tab = category_for_tab(newest.category, active_tabs)
        counts[tab] = (counts[tab] ?: 0) + 1
    }
    return counts
}
