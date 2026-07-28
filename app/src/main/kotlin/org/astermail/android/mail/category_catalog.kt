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

import org.astermail.android.R
import org.astermail.android.api.preferences.CustomCategoryRule

data class BuiltinCategory(
    val id: String,
    val icon: String,
    val label_res: Int,
    val default_enabled: Boolean,
    val removable: Boolean,
    val fold_target: String,
)

val BUILTIN_CATEGORIES: List<BuiltinCategory> = listOf(
    BuiltinCategory("primary", "inbox", R.string.rules_category_primary, true, false, "primary"),
    BuiltinCategory("promotions", "tag", R.string.rules_category_promotions, true, true, "primary"),
    BuiltinCategory("social", "users", R.string.rules_category_social, true, true, "primary"),
    BuiltinCategory("updates", "bell", R.string.rules_category_updates, true, true, "primary"),
    BuiltinCategory("forums", "chat", R.string.rules_category_forums, false, true, "updates"),
    BuiltinCategory("finance", "credit_card", R.string.rules_category_finance, false, true, "updates"),
    BuiltinCategory("travel", "plane", R.string.rules_category_travel, false, true, "updates"),
    BuiltinCategory(
        "shopping",
        "shopping_bag",
        R.string.rules_category_shopping,
        false,
        true,
        "promotions",
    ),
)

val BUILTIN_CATEGORY_IDS: Set<String> = BUILTIN_CATEGORIES.map { it.id }.toSet()

val DEFAULT_ENABLED_CATEGORIES: List<String> =
    BUILTIN_CATEGORIES.filter { it.default_enabled && it.id != "primary" }.map { it.id }

fun builtin_category(id: String): BuiltinCategory? = BUILTIN_CATEGORIES.firstOrNull { it.id == id }

fun fold_builtin(id: String): String = builtin_category(id)?.fold_target ?: "primary"

const val CUSTOM_CATEGORY_PREFIX = "custom:"

fun is_custom_category_id(id: String): Boolean = id.startsWith(CUSTOM_CATEGORY_PREFIX)

const val MAX_CUSTOM_CATEGORY_NAME = 40
const val MAX_CUSTOM_CATEGORY_RULES = 10
const val MAX_MATCH_TERMS = 25
const val MAX_MATCH_TERM_LENGTH = 100

private val DOMAIN_PATTERN =
    Regex("""^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$""", RegexOption.IGNORE_CASE)

private val KEYWORD_PATTERN = Regex("""^[\p{L}\p{N} '&._-]+$""")

fun is_valid_match_domain(value: String): Boolean = DOMAIN_PATTERN.matches(value.trim())

fun is_valid_match_keyword(value: String): Boolean = KEYWORD_PATTERN.matches(value.trim())

private fun clean_terms(values: List<String>, is_valid: (String) -> Boolean): List<String> {
    val seen = LinkedHashSet<String>()
    for (raw in values) {
        val value = raw.trim().lowercase().take(MAX_MATCH_TERM_LENGTH)
        if (value.isEmpty() || seen.contains(value) || !is_valid(value)) continue
        seen.add(value)
        if (seen.size >= MAX_MATCH_TERMS) break
    }
    return seen.toList()
}

fun sanitize_custom_category(raw: CustomCategoryRule): CustomCategoryRule? {
    val name = raw.name.trim().take(MAX_CUSTOM_CATEGORY_NAME)
    if (name.isEmpty()) return null
    val icon = if (CUSTOM_CATEGORY_ICON_CHOICES.contains(raw.icon)) raw.icon else "tag"
    return CustomCategoryRule(
        id = if (is_custom_category_id(raw.id)) raw.id else "$CUSTOM_CATEGORY_PREFIX${java.util.UUID.randomUUID()}",
        name = name,
        icon = icon,
        match_domains = clean_terms(raw.match_domains, ::is_valid_match_domain),
        match_keywords = clean_terms(raw.match_keywords, ::is_valid_match_keyword),
        enabled = raw.enabled,
    )
}

fun sanitize_custom_categories(raw: List<CustomCategoryRule>): List<CustomCategoryRule> {
    val result = mutableListOf<CustomCategoryRule>()
    for (entry in raw) {
        val sanitized = sanitize_custom_category(entry) ?: continue
        result.add(sanitized)
        if (result.size >= MAX_CUSTOM_CATEGORY_RULES) break
    }
    return result
}

fun allowed_custom_categories(
    custom_categories: List<CustomCategoryRule>,
    limit: Int,
): List<CustomCategoryRule> =
    if (limit < 0) custom_categories else custom_categories.take(limit)

val CUSTOM_CATEGORY_ICON_CHOICES: List<String> = listOf(
    "tag",
    "star",
    "heart",
    "briefcase",
    "home",
    "globe",
    "shopping_bag",
    "credit_card",
    "plane",
    "academic_cap",
    "megaphone",
    "gift",
    "folder",
    "sparkles",
    "inbox",
    "users",
    "bell",
    "chat",
)

fun active_category_tabs(
    enabled_categories: List<String>,
    custom_categories: List<CustomCategoryRule>,
    limit: Int,
): List<String> {
    val enabled = enabled_categories.toSet()
    val tabs = mutableListOf("primary")
    for (category in BUILTIN_CATEGORIES) {
        if (category.id == "primary") continue
        if (enabled.contains(category.id)) tabs.add(category.id)
    }
    for (rule in allowed_custom_categories(custom_categories, limit)) {
        if (rule.enabled) tabs.add(rule.id)
    }
    return tabs
}
