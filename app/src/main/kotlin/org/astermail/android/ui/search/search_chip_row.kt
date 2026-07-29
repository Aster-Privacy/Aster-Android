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

internal data class ChipPerson(
    val name: String,
    val email: String,
    val count: Int,
)

internal val ATTACHMENT_CHIP_TYPES = listOf("image", "document", "pdf", "video")

internal val ATTACHMENT_EXTENSIONS: Map<String, List<String>> = mapOf(
    "pdf" to listOf(".pdf"),
    "image" to listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".bmp"),
    "document" to listOf(".doc", ".docx", ".odt", ".txt", ".rtf"),
    "spreadsheet" to listOf(".xls", ".xlsx", ".ods", ".csv"),
    "video" to listOf(".mp4", ".webm", ".avi", ".mov"),
    "audio" to listOf(".mp3", ".wav", ".ogg", ".aac", ".flac"),
    "archive" to listOf(".zip", ".rar", ".7z", ".gz", ".tar"),
)

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

        val name = (item.display_sender_name ?: item.sender_name).trim()
        val existing = by_email[email]

        if (existing == null) {
            by_email[email] = ChipPerson(name, email, 1)
            continue
        }

        by_email[email] = existing.copy(
            name = existing.name.ifEmpty { name },
            count = existing.count + 1,
        )
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
    ops.filter { it.key != key }

internal fun is_quick_operator(op: SearchOperator): Boolean {
    if (op.negated) return false

    return when (op.key) {
        "from", "to", "before", "after" -> true
        "has" -> op.value == "attachment" || op.value == "attachments" ||
            ATTACHMENT_CHIP_TYPES.contains(op.value)
        "is" -> op.value == "unread"
        else -> false
    }
}

internal fun active_attachment_types(ops: List<SearchOperator>): List<String> =
    operator_values(ops, "has").filter { ATTACHMENT_CHIP_TYPES.contains(it) }

internal fun has_attachment_active(ops: List<SearchOperator>): Boolean =
    operator_values(ops, "has").any {
        it == "attachment" || it == "attachments" || ATTACHMENT_CHIP_TYPES.contains(it)
    }

internal fun scope_includes_spam(ops: List<SearchOperator>): Boolean =
    ops.any { !it.negated && it.key == "in" && (it.value == "spam" || it.value == "anywhere") }

internal fun scope_includes_trash(ops: List<SearchOperator>): Boolean =
    ops.any { !it.negated && it.key == "in" && (it.value == "trash" || it.value == "anywhere") }

internal fun detect_date_preset(ops: List<SearchOperator>): DatePreset {
    val before = operator_value(ops, "before")
    val after = operator_value(ops, "after")

    if (before == null && after == null) return DatePreset.ANY

    if (after == null && before != null) {
        for ((preset, days) in PRESET_DAYS) {
            if (before == "${days}d") return preset
        }
    }

    return DatePreset.CUSTOM
}

internal fun set_person(
    ops: List<SearchOperator>,
    key: String,
    value: String,
): List<SearchOperator> {
    val trimmed = value.trim()
    val cleared = without_key(ops, key)

    if (trimmed.isEmpty()) return cleared

    return cleared + SearchOperator(false, key, trimmed.lowercase())
}

internal fun toggle_unread(ops: List<SearchOperator>): List<SearchOperator> {
    val active = ops.any { !it.negated && it.key == "is" && it.value == "unread" }

    if (active) return ops.filterNot { !it.negated && it.key == "is" && it.value == "unread" }

    return ops + SearchOperator(false, "is", "unread")
}

internal fun toggle_attachment(ops: List<SearchOperator>): List<SearchOperator> {
    if (has_attachment_active(ops)) return without_key(ops, "has")

    return ops + SearchOperator(false, "has", "attachment")
}

internal fun toggle_attachment_type(ops: List<SearchOperator>, type: String): List<SearchOperator> {
    val active = active_attachment_types(ops)
    val base = without_key(ops, "has")

    if (active.contains(type)) {
        val remaining = active.filterNot { it == type }

        if (remaining.isEmpty()) return base + SearchOperator(false, "has", "attachment")

        return base + remaining.map { SearchOperator(false, "has", it) }
    }

    return base + (active + type).map { SearchOperator(false, "has", it) }
}

internal fun apply_date_preset(ops: List<SearchOperator>, preset: DatePreset): List<SearchOperator> {
    val base = without_key(without_key(ops, "before"), "after")
    val days = PRESET_DAYS[preset] ?: return base

    return base + SearchOperator(false, "before", "${days}d")
}

internal fun apply_custom_range(
    ops: List<SearchOperator>,
    after_date: String,
    before_date: String,
): List<SearchOperator> {
    val base = without_key(without_key(ops, "before"), "after")

    return base +
        SearchOperator(false, "after", after_date) +
        SearchOperator(false, "before", before_date)
}

internal fun matches_attachment_type(item: InboxItem, type: String): Boolean {
    if (!item.has_attachments) return false

    val extensions = ATTACHMENT_EXTENSIONS[type] ?: return true
    val haystack = (item.subject + " " + item.preview).lowercase()

    return extensions.any { haystack.contains(it) }
}
