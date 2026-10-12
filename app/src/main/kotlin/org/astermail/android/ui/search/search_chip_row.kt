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

package org.astermail.android.ui.search

import org.astermail.android.mail.InboxItem
import java.time.LocalDate

internal data class ChipPerson(
    val name: String,
    val email: String,
    val count: Int,
)

internal typealias ChipPeopleLists = Pair<List<ChipPerson>, List<ChipPerson>>

internal val NO_CHIP_PEOPLE: ChipPeopleLists = emptyList<ChipPerson>() to emptyList()

internal fun chip_people_for(
    corpus: List<InboxItem>,
    produced: Pair<List<InboxItem>, ChipPeopleLists>?,
): ChipPeopleLists = produced?.takeIf { it.first === corpus }?.second ?: NO_CHIP_PEOPLE

internal enum class DatePreset {
    ANY,
    WEEK,
    MONTH,
    SIX_MONTHS,
    YEAR,
    CUSTOM,
}

internal val PRESET_DAYS: Map<DatePreset, Int> = mapOf(
    DatePreset.WEEK to 7,
    DatePreset.MONTH to 30,
    DatePreset.SIX_MONTHS to 183,
    DatePreset.YEAR to 365,
)

internal fun collect_chip_people(items: List<InboxItem>, limit: Int = 200): List<ChipPerson> {
    val by_email = LinkedHashMap<String, ChipPerson>()

    for (item in items) {
        val email = (item.display_sender_email ?: item.sender_email).trim().lowercase()

        if (email.isEmpty() || !email.contains("@")) continue

        val existing = by_email[email]

        if (existing != null && existing.name.isNotEmpty()) {
            by_email[email] = existing.copy(count = existing.count + 1)
            continue
        }

        val raw_name = (item.display_sender_name ?: item.sender_name).trim()
        val trusted_name = org.astermail.android.security.trusted_sender_name(
            org.astermail.android.ui.mail.inbox_item_trust_message(item),
            raw_name,
            "",
        )
        val name = if (trusted_name != raw_name && trusted_name.equals(email, ignoreCase = true)) "" else trusted_name

        by_email[email] = ChipPerson(name, email, (existing?.count ?: 0) + 1)
    }

    return by_email.values
        .sortedWith(compareByDescending<ChipPerson> { it.count }.thenBy { it.email })
        .take(limit)
}

internal fun collect_recipient_people(items: List<InboxItem>, limit: Int = 200): List<ChipPerson> {
    val by_email = LinkedHashMap<String, ChipPerson>()

    for (item in items) {
        val addresses = item.to_addresses + listOfNotNull(item.received_on)

        for (raw in addresses) {
            val email = raw.trim().lowercase()

            if (email.isEmpty() || !email.contains("@")) continue

            val existing = by_email[email]

            by_email[email] = existing?.copy(count = existing.count + 1)
                ?: ChipPerson("", email, 1)
        }
    }

    return by_email.values
        .sortedWith(compareByDescending<ChipPerson> { it.count }.thenBy { it.email })
        .take(limit)
}

internal fun operator_value(ops: List<SearchOperator>, key: String): String? =
    ops.firstOrNull { !it.negated && it.key == key }?.value

internal fun operator_values(ops: List<SearchOperator>, key: String): List<String> =
    ops.filter { !it.negated && it.key == key }.map { it.value }

internal fun without_key(ops: List<SearchOperator>, key: String): List<SearchOperator> =
    ops.filterNot { !it.negated && it.key == key }

internal fun has_operator(ops: List<SearchOperator>, key: String, value: String): Boolean =
    ops.any { !it.negated && it.key == key && it.value == value }

internal fun toggle_operator(ops: List<SearchOperator>, key: String, value: String): List<SearchOperator> {
    if (has_operator(ops, key, value)) {
        return ops.filterNot { !it.negated && it.key == key && it.value == value }
    }

    return ops + SearchOperator(false, key, value)
}

internal fun has_attachment_active(ops: List<SearchOperator>): Boolean =
    operator_values(ops, "has").any { HAS_OPERATOR_VALUES.contains(it) }

internal fun quick_row_operators(ops: List<SearchOperator>): List<SearchOperator> {
    val seen = mutableSetOf<String>()

    return ops.filter { op ->
        if (op.negated) return@filter false

        when (op.key) {
            "from", "to", "before", "after" -> seen.add(op.key)
            "has" -> HAS_OPERATOR_VALUES.contains(op.value)
            "is" -> op.value == "unread"
            else -> false
        }
    }
}

internal fun custom_chip_operators(ops: List<SearchOperator>): List<SearchOperator> {
    val quick = quick_row_operators(ops)
    return ops.filterNot { op -> quick.any { it === op } }
}

internal fun scope_includes_spam(ops: List<SearchOperator>): Boolean =
    ops.any { !it.negated && it.key == "in" && (it.value == "spam" || it.value == "anywhere") }

internal fun scope_includes_trash(ops: List<SearchOperator>): Boolean =
    ops.any { !it.negated && it.key == "in" && (it.value == "trash" || it.value == "anywhere") }

internal fun detect_date_preset(ops: List<SearchOperator>): DatePreset {
    val before = operator_values(ops, "before")
    val after = operator_values(ops, "after")

    if (before.isEmpty() && after.isEmpty()) return DatePreset.ANY

    if (after.isEmpty() && before.size == 1) {
        for ((preset, days) in PRESET_DAYS) {
            if (before[0] == "${days}d") return preset
        }
    }

    return DatePreset.CUSTOM
}

internal fun custom_date_range(ops: List<SearchOperator>): Pair<LocalDate, LocalDate>? {
    val before = operator_values(ops, "before")
    val after = operator_values(ops, "after")

    if (before.size != 1 || after.size != 1) return null

    val first_day = parse_iso_date(after[0]) ?: return null
    val end = parse_iso_date(before[0]) ?: return null

    if (!end.isAfter(first_day)) return null

    return first_day to end.minusDays(1)
}

internal fun set_person(
    ops: List<SearchOperator>,
    key: String,
    value: String,
): List<SearchOperator> {
    val trimmed = value.replace("\"", "").trim()
    val cleared = without_key(ops, key)

    if (trimmed.isEmpty()) return cleared

    return cleared + SearchOperator(false, key, trimmed.lowercase())
}

internal fun toggle_unread(ops: List<SearchOperator>): List<SearchOperator> =
    toggle_operator(ops, "is", "unread")

internal fun toggle_attachment(ops: List<SearchOperator>): List<SearchOperator> {
    if (has_attachment_active(ops)) {
        return ops.filterNot { !it.negated && it.key == "has" && HAS_OPERATOR_VALUES.contains(it.value) }
    }

    return ops + SearchOperator(false, "has", "attachment")
}

internal fun without_date_bounds(ops: List<SearchOperator>): List<SearchOperator> =
    without_key(without_key(ops, "before"), "after")

internal fun apply_date_preset(ops: List<SearchOperator>, preset: DatePreset): List<SearchOperator> {
    val base = without_date_bounds(ops)
    val days = PRESET_DAYS[preset] ?: return base

    return base + SearchOperator(false, "before", "${days}d")
}

internal fun apply_custom_range(
    ops: List<SearchOperator>,
    first_day: LocalDate,
    last_day: LocalDate,
): List<SearchOperator> {
    val start = if (last_day.isBefore(first_day)) last_day else first_day
    val end = if (last_day.isBefore(first_day)) first_day else last_day

    return without_date_bounds(ops) +
        SearchOperator(false, "after", start.toString()) +
        SearchOperator(false, "before", end.plusDays(1).toString())
}

internal val ADVANCED_SCOPE_VALUES = listOf("", "inbox", "sent", "drafts", "archive", "spam", "trash", "anywhere")

internal data class AdvancedFields(
    val from: String,
    val to: String,
    val subject: String,
    val attachment: Boolean,
    val unread: Boolean,
    val starred: Boolean,
    val preset: DatePreset,
    val scope: String,
    val cleared: Boolean = false,
)

private fun advanced_scope(ops: List<SearchOperator>): String =
    when (val scope = ops.lastOrNull { !it.negated && it.key == "in" }?.value) {
        "draft" -> "drafts"
        "archived" -> "archive"
        null -> ""
        else -> if (ADVANCED_SCOPE_VALUES.contains(scope)) scope else ""
    }

internal fun advanced_fields(ops: List<SearchOperator>): AdvancedFields = AdvancedFields(
    from = operator_value(ops, "from").orEmpty(),
    to = operator_value(ops, "to").orEmpty(),
    subject = operator_value(ops, "subject").orEmpty(),
    attachment = has_attachment_active(ops),
    unread = has_operator(ops, "is", "unread"),
    starred = has_operator(ops, "is", "starred") || has_operator(ops, "in", "starred"),
    preset = detect_date_preset(ops),
    scope = advanced_scope(ops),
)

private fun advanced_text_operator(key: String, text: String): SearchOperator? {
    val value = text.replace("\"", "").trim().lowercase()
    return if (value.isEmpty()) null else SearchOperator(false, key, value)
}

internal fun build_advanced_operators(
    ops: List<SearchOperator>,
    fields: AdvancedFields,
): List<SearchOperator> {
    val first_owned = listOf("from", "to", "subject").mapNotNull { key ->
        ops.firstOrNull { !it.negated && it.key == key }
    }
    val kept = if (fields.cleared) {
        emptyList()
    } else {
        ops.filterNot { op ->
            !op.negated && (
                first_owned.any { it === op } ||
                    (op.key == "has" && HAS_OPERATOR_VALUES.contains(op.value)) ||
                    (op.key == "is" && (op.value == "unread" || op.value == "starred")) ||
                    op.key == "before" || op.key == "after" || op.key == "in"
                )
        }
    }
    val preset_days = PRESET_DAYS[fields.preset]
    val dates = when {
        preset_days != null -> listOf(SearchOperator(false, "before", "${preset_days}d"))
        fields.preset == DatePreset.CUSTOM && !fields.cleared ->
            ops.filter { !it.negated && (it.key == "before" || it.key == "after") }
        else -> emptyList()
    }

    return kept +
        listOfNotNull(
            advanced_text_operator("from", fields.from),
            advanced_text_operator("to", fields.to),
            advanced_text_operator("subject", fields.subject),
            if (fields.attachment) SearchOperator(false, "has", "attachment") else null,
            if (fields.unread) SearchOperator(false, "is", "unread") else null,
            if (fields.starred) SearchOperator(false, "is", "starred") else null,
        ) +
        dates +
        listOfNotNull(if (fields.scope.isEmpty()) null else SearchOperator(false, "in", fields.scope))
}

internal enum class ScopeSuggestionKind {
    NARROW,
    WIDEN,
}

internal data class ScopeSuggestion(
    val kind: ScopeSuggestionKind,
    val folder: String,
)

private fun narrow_operator(folder: String): SearchOperator? =
    org.astermail.android.mail.search_narrow_query(folder)?.let { parse_query(it).operators.firstOrNull() }

internal fun scope_suggestion(folder: String?, ops: List<SearchOperator>): ScopeSuggestion? {
    if (folder == null) return null
    val narrow = narrow_operator(folder) ?: return null
    val effective = effective_operators(ops)
    val narrowed = effective.any { it == narrow }
    val scoped = effective.any { !it.negated && it.key == "in" }
    val default_scoped = org.astermail.android.mail.search_scope_query(folder) != null

    return when {
        narrowed && default_scoped -> ScopeSuggestion(ScopeSuggestionKind.WIDEN, folder)
        narrowed || scoped -> null
        else -> ScopeSuggestion(ScopeSuggestionKind.NARROW, folder)
    }
}

internal fun apply_scope_suggestion(
    ops: List<SearchOperator>,
    suggestion: ScopeSuggestion,
): List<SearchOperator> {
    val base = ops.filterNot { !it.negated && it.key == "in" }

    return when (suggestion.kind) {
        ScopeSuggestionKind.WIDEN -> base + SearchOperator(false, "in", "anywhere")
        ScopeSuggestionKind.NARROW -> {
            val narrow = narrow_operator(suggestion.folder) ?: return ops
            base.filterNot { it == narrow } + narrow
        }
    }
}
