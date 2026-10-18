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
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_6_MONTHS,
    LAST_YEAR,
    OLDER_THAN_YEAR,
    CUSTOM,
}

internal val PRESET_TOKENS: Map<DatePreset, SearchOperator> = mapOf(
    DatePreset.LAST_7_DAYS to SearchOperator(false, "after", "7d"),
    DatePreset.LAST_30_DAYS to SearchOperator(false, "after", "30d"),
    DatePreset.LAST_6_MONTHS to SearchOperator(false, "after", "183d"),
    DatePreset.LAST_YEAR to SearchOperator(false, "after", "365d"),
    DatePreset.OLDER_THAN_YEAR to SearchOperator(false, "before", "365d"),
)

internal val SEARCH_SCOPES = listOf("all", "inbox", "starred", "sent", "drafts", "archive", "spam", "trash", "anywhere")

internal val DEFAULT_SCOPED_FOLDERS = setOf("trash", "spam")

internal val CHIP_ATTACHMENT_TYPES = listOf("image", "document", "pdf", "video")

internal val SEARCH_WITHIN_DAYS = listOf(0, 7, 30, 183, 365)

private val DATE_OPERATOR_KEYS = setOf("before", "after", "date")

private val FORM_OPERATOR_KEYS = setOf(
    "from",
    "to",
    "subject",
    "in",
    "before",
    "after",
    "date",
    "larger",
    "smaller",
    TEXT_OPERATOR_KEY,
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

internal fun single_operator_value(ops: List<SearchOperator>, key: String): String? =
    operator_values(ops, key).singleOrNull()

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

private fun is_generic_attachment(op: SearchOperator): Boolean =
    !op.negated && op.key == "has" && HAS_OPERATOR_VALUES.contains(op.value)

private fun is_typed_attachment(op: SearchOperator): Boolean =
    !op.negated && op.key == "has" && CHIP_ATTACHMENT_TYPES.contains(op.value)

internal fun has_attachment_active(ops: List<SearchOperator>): Boolean = ops.any { is_generic_attachment(it) }

internal fun attachment_filter_active(ops: List<SearchOperator>): Boolean =
    ops.any { is_generic_attachment(it) || is_typed_attachment(it) }

internal fun active_attachment_types(ops: List<SearchOperator>): List<String> =
    ops.filter { is_typed_attachment(it) }.map { it.value }

internal fun toggle_attachment(ops: List<SearchOperator>): List<SearchOperator> {
    if (attachment_filter_active(ops)) {
        return ops.filterNot { is_generic_attachment(it) || is_typed_attachment(it) }
    }

    return ops + SearchOperator(false, "has", "attachment")
}

internal fun toggle_attachment_type(ops: List<SearchOperator>, type: String): List<SearchOperator> {
    if (has_operator(ops, "has", type)) {
        val removed = ops.filterNot { !it.negated && it.key == "has" && it.value == type }
        return if (active_attachment_types(removed).isEmpty() && !has_attachment_active(removed)) {
            removed + SearchOperator(false, "has", "attachment")
        } else {
            removed
        }
    }

    return ops.filterNot { is_generic_attachment(it) } + SearchOperator(false, "has", type)
}

internal fun toggle_unread(ops: List<SearchOperator>): List<SearchOperator> {
    val stripped = ops.filterNot { !it.negated && it.key == "is" && (it.value == "unread" || it.value == "read") }
    return if (has_operator(ops, "is", "unread")) stripped else stripped + SearchOperator(false, "is", "unread")
}

internal fun is_chip_covered_operator(op: SearchOperator): Boolean = when {
    op.key == "in" -> true
    op.negated -> false
    op.key == "from" || op.key == "to" || DATE_OPERATOR_KEYS.contains(op.key) -> true
    op.key == "has" -> HAS_OPERATOR_VALUES.contains(op.value) || CHIP_ATTACHMENT_TYPES.contains(op.value)
    op.key == "is" -> op.value == "unread"
    else -> false
}

internal fun extra_chip_operators(ops: List<SearchOperator>): List<SearchOperator> =
    ops.filterNot { is_chip_covered_operator(it) }

internal fun scope_includes_spam(ops: List<SearchOperator>): Boolean =
    ops.any { !it.negated && it.key == "in" && (it.value == "spam" || it.value == "anywhere") }

internal fun scope_includes_trash(ops: List<SearchOperator>): Boolean =
    ops.any { !it.negated && it.key == "in" && (it.value == "trash" || it.value == "anywhere") }

internal fun normalize_scope_value(value: String): String = when (value) {
    "archived" -> "archive"
    "draft" -> "drafts"
    else -> value
}

internal fun default_search_scope(folder: String?): String =
    if (folder != null && DEFAULT_SCOPED_FOLDERS.contains(folder)) folder else "all"

internal data class QueryScope(
    val scope: String,
    val is_default: Boolean,
)

internal fun query_search_scope(ops: List<SearchOperator>, folder: String?): QueryScope? {
    val scopes = ops.filter { it.key == "in" }
    val fallback = default_search_scope(folder)

    if (scopes.isEmpty()) return QueryScope(fallback, true)
    if (scopes.size > 1 || scopes[0].negated) return null

    val scope = normalize_scope_value(scopes[0].value)

    return QueryScope(scope, scope == fallback)
}

internal fun set_search_scope(
    ops: List<SearchOperator>,
    folder: String?,
    scope: String,
    has_free_text: Boolean,
): List<SearchOperator> {
    val stripped = ops.filterNot { it.key == "in" }
    val needs_token = scope != "all" || (folder != null && DEFAULT_SCOPED_FOLDERS.contains(folder))

    if (!needs_token && (stripped.isNotEmpty() || has_free_text)) return stripped

    return stripped + SearchOperator(false, "in", scope)
}

internal fun clear_search_filters(folder: String?, has_free_text: Boolean): List<SearchOperator> =
    set_search_scope(emptyList(), folder, default_search_scope(folder), has_free_text)

internal fun scope_chip_active(ops: List<SearchOperator>, folder: String?): Boolean =
    query_search_scope(ops, folder)?.let { !it.is_default } ?: true

internal fun has_active_filters(ops: List<SearchOperator>, folder: String?): Boolean =
    scope_chip_active(ops, folder) || ops.any { it.key != "in" }

internal fun is_date_operator(op: SearchOperator): Boolean = !op.negated && DATE_OPERATOR_KEYS.contains(op.key)

internal fun detect_date_preset(ops: List<SearchOperator>): DatePreset {
    val dated = ops.filter { is_date_operator(it) }

    if (dated.isEmpty()) return DatePreset.ANY
    if (dated.size > 1) return DatePreset.CUSTOM

    return PRESET_TOKENS.entries.firstOrNull { it.value == dated[0] }?.key ?: DatePreset.CUSTOM
}

internal fun without_date_operators(ops: List<SearchOperator>): List<SearchOperator> =
    ops.filterNot { is_date_operator(it) }

internal fun apply_date_preset(ops: List<SearchOperator>, preset: DatePreset): List<SearchOperator> {
    if (preset == DatePreset.CUSTOM) return ops

    val base = without_date_operators(ops)
    val token = PRESET_TOKENS[preset] ?: return base

    return base + token
}

internal fun date_range_operators(first_day: LocalDate?, last_day: LocalDate?): List<SearchOperator> {
    val swap = first_day != null && last_day != null && last_day.isBefore(first_day)
    val start = if (swap) last_day else first_day
    val end = if (swap) first_day else last_day

    return listOfNotNull(
        start?.let { SearchOperator(false, "after", it.toString()) },
        end?.let { SearchOperator(false, "before", it.plusDays(1).toString()) },
    )
}

internal fun apply_custom_range(
    ops: List<SearchOperator>,
    first_day: LocalDate?,
    last_day: LocalDate?,
): List<SearchOperator> = without_date_operators(ops) + date_range_operators(first_day, last_day)

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
    val shown = ops.firstOrNull { !it.negated && it.key == key }
    val cleared = if (shown == null) ops else ops.filterNot { it === shown }

    if (trimmed.isEmpty()) return cleared

    return cleared + SearchOperator(false, key, trimmed.lowercase())
}

internal val ADVANCED_SCOPE_VALUES = SEARCH_SCOPES

internal enum class SizeUnit(val bytes: Long) {
    BYTES(1L),
    KB(1_024L),
    MB(1_048_576L),
}

internal enum class SizeOp {
    GREATER,
    LESS,
}

internal data class AdvancedFields(
    val from: String = "",
    val to: String = "",
    val subject: String = "",
    val does_not_have: String = "",
    val attachment: Boolean = false,
    val starred: Boolean = false,
    val within_days: Int = 0,
    val date_from: LocalDate? = null,
    val date_to: LocalDate? = null,
    val size_op: SizeOp = SizeOp.GREATER,
    val size_value: String = "",
    val size_unit: SizeUnit = SizeUnit.MB,
    val scope: String = "all",
)

private fun size_fields(bytes: Long): Pair<String, SizeUnit> {
    for (unit in listOf(SizeUnit.MB, SizeUnit.KB, SizeUnit.BYTES)) {
        if (bytes % unit.bytes == 0L) return (bytes / unit.bytes).toString() to unit
    }

    return bytes.toString() to SizeUnit.BYTES
}

internal fun advanced_fields(ops: List<SearchOperator>, folder: String?, today: LocalDate): AdvancedFields {
    val scope = query_search_scope(ops, folder)?.scope ?: default_search_scope(folder)
    var fields = AdvancedFields(
        from = single_operator_value(ops, "from").orEmpty(),
        to = single_operator_value(ops, "to").orEmpty(),
        subject = single_operator_value(ops, "subject").orEmpty(),
        does_not_have = ops.filter { it.negated && it.key == TEXT_OPERATOR_KEY }.joinToString(" ") { it.value },
        attachment = has_attachment_active(ops),
        starred = has_operator(ops, "is", "starred"),
        scope = if (SEARCH_SCOPES.contains(scope)) scope else "all",
    )

    val after = single_operator_value(ops, "after")
    val relative = after?.let { parse_relative_days(it) }

    if (relative != null) {
        fields = if (SEARCH_WITHIN_DAYS.contains(relative)) {
            fields.copy(within_days = relative)
        } else {
            fields.copy(date_from = today.minusDays(relative.toLong()))
        }
    } else if (after != null) {
        fields = fields.copy(date_from = parse_iso_date(after))
    }

    val before = single_operator_value(ops, "before")?.let { parse_iso_date(it) }

    if (before != null) fields = fields.copy(date_to = before.minusDays(1))

    val larger = single_operator_value(ops, "larger")
    val size_source = larger ?: single_operator_value(ops, "smaller")
    val bytes = size_source?.takeIf { it.all { c -> c in '0'..'9' } }?.toLongOrNull()

    if (bytes != null) {
        val (value, unit) = size_fields(bytes)
        fields = fields.copy(
            size_op = if (larger != null) SizeOp.GREATER else SizeOp.LESS,
            size_value = value,
            size_unit = unit,
        )
    }

    return fields
}

private fun advanced_text_operator(key: String, text: String): SearchOperator? {
    val value = text.replace("\"", "").trim().lowercase()
    return if (value.isEmpty()) null else SearchOperator(false, key, value)
}

private fun excluded_word_operators(text: String): List<SearchOperator> =
    text.trim()
        .split(Regex("""\s+"""))
        .map { it.trimStart('-').replace("\"", "").lowercase() }
        .filter { it.isNotEmpty() }
        .map { SearchOperator(true, TEXT_OPERATOR_KEY, it) }

private fun is_form_owned(op: SearchOperator): Boolean =
    (op.negated == (op.key == TEXT_OPERATOR_KEY) && FORM_OPERATOR_KEYS.contains(op.key)) ||
        (!op.negated && op.key == "has" && HAS_OPERATOR_VALUES.contains(op.value)) ||
        (!op.negated && op.key == "is" && op.value == "starred")

internal fun size_operator(fields: AdvancedFields): SearchOperator? {
    val amount = fields.size_value.trim().toLongOrNull() ?: return null
    if (amount <= 0L) return null
    val key = if (fields.size_op == SizeOp.GREATER) "larger" else "smaller"
    return SearchOperator(false, key, (amount * fields.size_unit.bytes).toString())
}

internal fun build_advanced_operators(
    ops: List<SearchOperator>,
    fields: AdvancedFields,
    folder: String?,
    has_free_text: Boolean,
): List<SearchOperator> {
    val kept = ops.filterNot { is_form_owned(it) }
    val built = listOfNotNull(
        advanced_text_operator("from", fields.from),
        advanced_text_operator("to", fields.to),
        advanced_text_operator("subject", fields.subject),
    ) +
        excluded_word_operators(fields.does_not_have) +
        listOfNotNull(
            if (fields.attachment) SearchOperator(false, "has", "attachment") else null,
            if (fields.starred) SearchOperator(false, "is", "starred") else null,
            size_operator(fields),
            if (fields.within_days > 0) SearchOperator(false, "after", "${fields.within_days}d") else null,
        ) +
        date_range_operators(fields.date_from, fields.date_to)

    return set_search_scope(kept + built, folder, fields.scope, has_free_text)
}
