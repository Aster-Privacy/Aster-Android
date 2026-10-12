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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.search

import org.astermail.android.mail.InboxItem
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal val RECOGNIZED_OPERATOR_KEYS = setOf(
    "from",
    "to",
    "contact",
    "subject",
    "has",
    "is",
    "in",
    "before",
    "after",
    "date",
    "label",
    "filename",
)

internal val ANY_OF_OPERATOR_KEYS = setOf("from", "to", "contact", "has")

internal val SINGLE_VALUE_OPERATOR_KEYS = setOf("in", "before", "after", "date")

internal val IS_OPERATOR_VALUES = setOf("unread", "read", "starred", "unstarred", "encrypted")

internal val IN_OPERATOR_VALUES = setOf(
    "inbox",
    "sent",
    "drafts",
    "draft",
    "trash",
    "archive",
    "archived",
    "spam",
    "starred",
    "all",
    "anywhere",
)

internal val HAS_OPERATOR_VALUES = setOf("attachment", "attachments")

internal val ATTACHMENT_TYPE_EXTENSIONS: Map<String, List<String>> = mapOf(
    "pdf" to listOf(".pdf"),
    "image" to listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".bmp"),
    "document" to listOf(".doc", ".docx", ".odt", ".txt", ".rtf"),
    "spreadsheet" to listOf(".xls", ".xlsx", ".ods", ".csv"),
    "video" to listOf(".mp4", ".webm", ".avi", ".mov"),
    "audio" to listOf(".mp3", ".wav", ".ogg", ".aac", ".flac"),
    "archive" to listOf(".zip", ".rar", ".7z", ".gz", ".tar"),
)

private val MIME_PREFIX_ATTACHMENT_TYPES = setOf("image", "video", "audio")

internal const val ATTACHMENT_TYPE_SCAN_LIMIT = 400

internal data class SearchAttachment(
    val filename: String,
    val content_type: String,
)

internal fun is_attachment_type_value(value: String): Boolean = ATTACHMENT_TYPE_EXTENSIONS.containsKey(value)

internal fun is_attachment_type_operator(op: SearchOperator): Boolean =
    op.key == "has" && is_attachment_type_value(op.value)

internal fun needs_attachment_details(op: SearchOperator): Boolean =
    is_attachment_type_operator(op) || op.key == "filename"

internal fun attachment_matches_type(type: String, attachment: SearchAttachment): Boolean {
    val extensions = ATTACHMENT_TYPE_EXTENSIONS[type] ?: return false
    val filename = attachment.filename.trim().lowercase()
    val content_type = attachment.content_type.trim().lowercase()
    return extensions.any { filename.endsWith(it) } ||
        (type == "pdf" && content_type == "application/pdf") ||
        (MIME_PREFIX_ATTACHMENT_TYPES.contains(type) && content_type.startsWith("$type/"))
}

internal val DATE_OPERATOR_VALUES = setOf("today", "yesterday", "this_week", "last_week", "this_month")

private const val DAY_MILLIS = 86_400_000L

private const val MAX_RELATIVE_DAYS = 36_500

private val OPERATOR_TOKEN_REGEX = Regex("""^(-?)([A-Za-z]+):(.*)$""", RegexOption.DOT_MATCHES_ALL)

private val ISO_DATE_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

private val RELATIVE_DAYS_REGEX = Regex("""^(\d{1,5})d$""")

private val COMBINING_MARKS_REGEX = Regex("""\p{M}+""")

internal data class SearchOperator(
    val negated: Boolean,
    val key: String,
    val value: String,
)

internal data class ParsedQuery(
    val free_text: String,
    val operators: List<SearchOperator>,
) {
    val terms: List<String> = free_text_terms(free_text)
}

internal data class SearchLabelEntry(
    val name: String,
    val token: String,
) {
    val folded_name: String = fold_text(name.trim())
}

internal data class SearchLabelIndex(
    val entries: List<SearchLabelEntry>,
) {
    fun tokens_for(value: String): Set<String> {
        val wanted = fold_text(value.trim())
        if (wanted.isEmpty()) return emptySet()
        val exact = entries.filter { it.folded_name == wanted }
        val hits = exact.ifEmpty { entries.filter { it.folded_name.startsWith(wanted) } }
        return hits.mapTo(LinkedHashSet()) { it.token }
    }
}

internal val NO_SEARCH_LABELS = SearchLabelIndex(emptyList())

internal data class SearchEnv(
    val now: Long,
    val zone: ZoneId,
    val labels: SearchLabelIndex = NO_SEARCH_LABELS,
    val attachments: Map<String, List<SearchAttachment>> = emptyMap(),
)

internal fun search_local_day(
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = org.astermail.android.ui.mail.AsterTimePreferences.account_zone_id(),
): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

internal fun current_search_env(labels: SearchLabelIndex = NO_SEARCH_LABELS): SearchEnv = SearchEnv(
    now = System.currentTimeMillis(),
    zone = org.astermail.android.ui.mail.AsterTimePreferences.account_zone_id(),
    labels = labels,
)

internal fun fold_text(text: String): String {
    if (text.all { it.code < 128 }) return text.lowercase()
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    return COMBINING_MARKS_REGEX.replace(decomposed, "").lowercase()
}

private data class QueryToken(
    val text: String,
    val start: Int,
    val end: Int,
)

private sealed interface TokenKind {
    data class Operator(val operator: SearchOperator, val closed: Boolean) : TokenKind

    data object Pending : TokenKind

    data class Term(val term: String) : TokenKind
}

private fun tokenize_query(raw: String): List<QueryToken> {
    val tokens = mutableListOf<QueryToken>()
    var start = -1
    var in_quotes = false

    for (i in raw.indices) {
        val c = raw[i]
        if (c == '"') in_quotes = !in_quotes
        if (c.isWhitespace() && !in_quotes) {
            if (start >= 0) {
                tokens.add(QueryToken(raw.substring(start, i), start, i))
                start = -1
            }
            continue
        }
        if (start < 0) start = i
    }

    if (start >= 0) tokens.add(QueryToken(raw.substring(start), start, raw.length))

    return tokens
}

private fun plain_term(token: String): TokenKind {
    val term = fold_text(token.replace("\"", "").trim())
    return if (term.isEmpty()) TokenKind.Pending else TokenKind.Term(term)
}

private fun classify_token(token: String): TokenKind {
    val match = OPERATOR_TOKEN_REGEX.find(token) ?: return plain_term(token)
    val key = match.groupValues[2].lowercase()

    if (!RECOGNIZED_OPERATOR_KEYS.contains(key)) return plain_term(token)

    val raw_value = match.groupValues[3]
    val value = raw_value.replace("\"", "").trim().lowercase()

    if (value.isEmpty()) return TokenKind.Pending
    if (!is_evaluable_operator(key, value)) return plain_term(token)

    val closed = raw_value.count { it == '"' } % 2 == 0

    return TokenKind.Operator(SearchOperator(match.groupValues[1] == "-", key, value), closed)
}

internal fun is_evaluable_operator(key: String, value: String): Boolean = when (key) {
    "is" -> IS_OPERATOR_VALUES.contains(value)
    "in" -> IN_OPERATOR_VALUES.contains(value)
    "has" -> HAS_OPERATOR_VALUES.contains(value) || is_attachment_type_value(value)
    "before", "after" -> parse_iso_date(value) != null || parse_relative_days(value) != null
    "date" -> DATE_OPERATOR_VALUES.contains(value) || parse_iso_date(value) != null
    "from", "to", "contact", "subject", "label", "filename" -> value.isNotBlank()
    else -> false
}

internal fun parse_query(raw: String): ParsedQuery {
    val operators = mutableListOf<SearchOperator>()
    val kept = mutableListOf<String>()

    for (token in tokenize_query(raw)) {
        when (val kind = classify_token(token.text)) {
            is TokenKind.Operator -> operators.add(kind.operator)
            else -> kept.add(token.text)
        }
    }

    return ParsedQuery(
        free_text = kept.joinToString(" "),
        operators = operators,
    )
}

internal fun free_text_terms(free_text: String): List<String> =
    tokenize_query(free_text).mapNotNull { (classify_token(it.text) as? TokenKind.Term)?.term }

internal fun has_search_query(parsed: ParsedQuery): Boolean =
    parsed.operators.isNotEmpty() || parsed.terms.isNotEmpty()

internal fun combined_query(chips: List<SearchOperator>, text: String): ParsedQuery {
    val typed = parse_query(text)
    return ParsedQuery(typed.free_text, chips + typed.operators)
}

private val CHIP_LIVE_KEYS = setOf("is", "has", "in")

internal fun pending_typed_operators(text: String): List<SearchOperator> =
    parse_query(text).operators.filterNot { it.key in CHIP_LIVE_KEYS }

internal fun chip_visible_operators(chips: List<SearchOperator>, text: String): List<SearchOperator> =
    chips + parse_query(text).operators.filter { it.key in CHIP_LIVE_KEYS }

internal fun pending_operator_text(pending: List<SearchOperator>, free_text: String): String =
    (pending.map { operator_token(it) } + free_text).filter { it.isNotBlank() }.joinToString(" ")

internal fun quote_operator_value(value: String): String {
    val cleaned = value.replace("\"", "").trim()
    return if (cleaned.any { it.isWhitespace() }) "\"$cleaned\"" else cleaned
}

internal fun operator_token(op: SearchOperator): String =
    "${if (op.negated) "-" else ""}${op.key}:${quote_operator_value(op.value)}"

fun recipient_search_query(name: String): String = "to:${quote_operator_value(name)}"

internal data class LiftResult(
    val operators: List<SearchOperator>,
    val text: String,
)

internal fun lift_operators(text: String, submit: Boolean): LiftResult {
    val lifted = mutableListOf<SearchOperator>()
    val remaining = StringBuilder()
    var cursor = 0

    for (token in tokenize_query(text)) {
        val kind = classify_token(token.text) as? TokenKind.Operator ?: continue
        val complete = kind.closed && (submit || token.end < text.length)
        if (!complete) continue

        lifted.add(kind.operator)
        remaining.append(text, cursor, token.start)
        cursor = token.end
        while (cursor < text.length && text[cursor].isWhitespace()) cursor++
    }

    if (lifted.isEmpty()) return LiftResult(emptyList(), text)

    remaining.append(text, cursor, text.length)

    return LiftResult(lifted, remaining.toString())
}

internal fun merge_lifted(
    existing: List<SearchOperator>,
    lifted: List<SearchOperator>,
): List<SearchOperator> {
    var merged = existing

    for (op in lifted) {
        if (!op.negated && SINGLE_VALUE_OPERATOR_KEYS.contains(op.key)) {
            merged = without_key(merged, op.key) + op
            continue
        }
        if (!merged.contains(op)) merged = merged + op
    }

    return merged
}

internal fun effective_operators(ops: List<SearchOperator>): List<SearchOperator> {
    val last_scope = ops.lastOrNull { !it.negated && it.key == "in" } ?: return ops
    return ops.filter { it.negated || it.key != "in" || it === last_scope }
}

internal fun parse_iso_date(value: String): LocalDate? {
    val match = ISO_DATE_REGEX.find(value) ?: return null
    return try {
        LocalDate.of(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt(),
        )
    } catch (e: java.time.DateTimeException) {
        null
    }
}

internal fun parse_relative_days(value: String): Int? {
    val match = RELATIVE_DAYS_REGEX.find(value) ?: return null
    return match.groupValues[1].toIntOrNull()?.takeIf { it <= MAX_RELATIVE_DAYS }
}

internal fun start_of_day_millis(date: LocalDate, zone: ZoneId): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()

internal fun date_bound_millis(value: String, env: SearchEnv): Long? {
    parse_iso_date(value)?.let { return start_of_day_millis(it, env.zone) }
    return parse_relative_days(value)?.let { env.now - it * DAY_MILLIS }
}

internal fun date_window_millis(value: String, env: SearchEnv): LongRange? {
    val today = Instant.ofEpochMilli(env.now).atZone(env.zone).toLocalDate()

    fun day_start(days_back: Long): Long = start_of_day_millis(today.minusDays(days_back), env.zone)

    return when (value) {
        "today" -> day_start(0)..Long.MAX_VALUE
        "yesterday" -> day_start(1) until day_start(0)
        "this_week" -> day_start(6)..Long.MAX_VALUE
        "last_week" -> day_start(13) until day_start(6)
        "this_month" -> day_start(29)..Long.MAX_VALUE
        else -> parse_iso_date(value)?.let {
            start_of_day_millis(it, env.zone) until start_of_day_millis(it.plusDays(1), env.zone)
        }
    }
}

private fun parse_digits(text: String, start: Int, end: Int): Int {
    var acc = 0
    for (i in start until end) {
        val c = text[i]
        if (c < '0' || c > '9') return -1
        acc = acc * 10 + (c - '0')
    }
    return acc
}

private fun days_from_civil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = (y - era * 400).toLong()
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
}

internal fun parse_item_timestamp(ts: String): Long {
    if (ts.length < 19) return 0L
    val year = parse_digits(ts, 0, 4)
    val month = parse_digits(ts, 5, 7)
    val day = parse_digits(ts, 8, 10)
    val hour = parse_digits(ts, 11, 13)
    val minute = parse_digits(ts, 14, 16)
    val second = parse_digits(ts, 17, 19)
    if (year < 0 || month !in 1..12 || day !in 1..31) return 0L
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return 0L
    return (days_from_civil(year, month, day) * 86_400L + hour * 3600L + minute * 60L + second) * 1000L
}

private fun is_received_item(item: InboxItem): Boolean {
    val type = item.raw_item.item_type
    return type == null || type == "received"
}

private fun sender_fields(item: InboxItem): List<String?> = listOf(
    item.sender_name,
    item.sender_email,
    item.display_sender_name,
    item.display_sender_email,
)

private fun sender_addresses(item: InboxItem): List<String?> =
    listOf(item.sender_email, item.display_sender_email)

private fun recipient_fields(item: InboxItem): List<String?> = item.to_addresses + item.received_on

private fun matches_person(
    value: String,
    names: List<String?>,
    addresses: List<String?>,
): Boolean {
    if (is_full_address(value)) return addresses.any { address_field_matches(it, value) }
    val wanted = fold_text(value)
    return names.any { it != null && fold_text(it).contains(wanted) }
}

private fun matches_label(item: InboxItem, value: String, labels: SearchLabelIndex): Boolean {
    val tokens = labels.tokens_for(value)
    if (tokens.isEmpty()) return false
    return item.tag_tokens.any { tokens.contains(it) } ||
        org.astermail.android.folders.inbox_item_folder_tokens(item).any { tokens.contains(it) }
}

private fun operator_result(item: InboxItem, op: SearchOperator, env: SearchEnv): Boolean? = when (op.key) {
    "from" -> matches_person(op.value, sender_fields(item), sender_addresses(item))
    "to" -> matches_person(op.value, recipient_fields(item), recipient_fields(item))
    "contact" -> matches_person(
        op.value,
        sender_fields(item) + recipient_fields(item),
        sender_addresses(item) + recipient_fields(item),
    )
    "subject" -> fold_text(item.subject).contains(fold_text(op.value))
    "has" -> when {
        HAS_OPERATOR_VALUES.contains(op.value) -> item.has_attachments
        is_attachment_type_value(op.value) ->
            item.has_attachments &&
                env.attachments[item.id].orEmpty().any { attachment_matches_type(op.value, it) }
        else -> null
    }
    "is" -> when (op.value) {
        "unread" -> !item.is_read
        "read" -> item.is_read
        "starred" -> item.is_starred
        "unstarred" -> !item.is_starred
        "encrypted" -> item.is_encrypted
        else -> null
    }
    "in" -> when (op.value) {
        "inbox" -> is_received_item(item) && !item.is_trashed && !item.is_archived && !item.is_spam
        "sent" -> item.raw_item.item_type == "sent"
        "drafts", "draft" -> item.raw_item.item_type == "draft"
        "trash" -> item.is_trashed
        "archive", "archived" -> item.is_archived
        "spam" -> item.is_spam
        "starred" -> item.is_starred
        "all" -> !item.is_trashed && !item.is_spam
        "anywhere" -> true
        else -> null
    }
    "before" -> date_bound_millis(op.value, env)?.let { parse_item_timestamp(item.timestamp) < it }
    "after" -> date_bound_millis(op.value, env)?.let { parse_item_timestamp(item.timestamp) >= it }
    "date" -> date_window_millis(op.value, env)?.contains(parse_item_timestamp(item.timestamp))
    "label" -> matches_label(item, op.value, env.labels)
    "filename" -> item.has_attachments &&
        env.attachments[item.id].orEmpty().any { fold_text(it.filename).contains(fold_text(op.value)) }
    else -> null
}

private fun evaluate_operator(item: InboxItem, op: SearchOperator, env: SearchEnv): Boolean {
    val result = operator_result(item, op, env) ?: return true
    return if (op.negated) !result else result
}

private fun is_evaluable(op: SearchOperator): Boolean =
    RECOGNIZED_OPERATOR_KEYS.contains(op.key) && is_evaluable_operator(op.key, op.value)

internal fun matches_item(
    item: InboxItem,
    parsed: ParsedQuery,
    apply_scope: Boolean = true,
    env: SearchEnv = current_search_env(),
): Boolean {
    val operators = effective_operators(parsed.operators)
    if (apply_scope) {
        if (item.is_spam && !scope_includes_spam(operators)) return false
        if (item.is_trashed && !scope_includes_trash(operators)) return false
    }

    val any_of = operators.filter {
        !it.negated && ANY_OF_OPERATOR_KEYS.contains(it.key) && is_evaluable(it)
    }

    for ((_, group) in any_of.groupBy { it.key }) {
        if (group.none { evaluate_operator(item, it, env) }) return false
    }

    for (op in operators) {
        if (!op.negated && ANY_OF_OPERATOR_KEYS.contains(op.key)) continue
        if (!evaluate_operator(item, op, env)) return false
    }

    if (parsed.terms.isEmpty()) return true

    val haystack = fold_text(
        (sender_fields(item) + item.subject + item.preview + recipient_fields(item))
            .filterNotNull()
            .joinToString("\n"),
    )

    return parsed.terms.all { haystack.contains(it) }
}
