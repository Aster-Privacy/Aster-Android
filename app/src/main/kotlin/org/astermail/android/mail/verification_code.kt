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

private const val MIN_CODE_LENGTH = 4
private const val MAX_CODE_LENGTH = 10
private const val MIN_GROUP_LENGTH = 3
private const val MAX_GROUP_LENGTH = 5
private const val KEYWORD_WINDOW = 48
private const val MAX_SCAN_LENGTH = 4000

private val CODE_WORD_PATTERN = Regex(
    "(?<![\\p{L}\\p{N}])(\\p{L}*)codes?(?!\\p{L})",
    RegexOption.IGNORE_CASE,
)

private val KEYWORD_PATTERN = Regex(
    "(?<![\\p{L}\\p{N}])(?:v[eé]rif\\p{L}*|otp|one[- ]?time|pin|2fa|log[- ]?in|sign[- ]?in|c[oó]digos?|codice|код\\p{L}{0,3})(?![\\p{L}\\p{N}])",
    RegexOption.IGNORE_CASE,
)

private val CJK_KEYWORD_PATTERN = Regex("验证码|校验码|动态码|認証コード|確認コード|ワンタイム")

private val CODE_QUALIFIERS = setOf(
    "promo", "promotional", "promocional", "promozionale", "promotionnel",
    "discount", "coupon", "voucher", "referral", "gift", "invite", "invitation",
    "postal", "zip", "area", "post", "bar", "qr", "source", "country", "error", "status",
    "rabatt", "gutschein", "aktions", "réduction", "descuento", "sconto", "regalo",
)

private val REFERENCE_WORDS = setOf(
    "order", "tracking", "invoice", "ref", "reference", "booking", "ticket", "case",
    "shipment", "parcel", "package", "account", "customer", "member", "membership",
    "phone", "tel", "telephone", "fax", "call", "mobile", "transaction", "receipt",
    "confirmation", "bestellung", "bestellnummer", "rechnung", "pedido", "factura",
    "commande", "facture", "ordine", "fattura", "заказ", "заказа",
)

private val NUMBER_WORDS = setOf("number", "no", "nr", "num", "id", "nummer", "número", "numéro", "numero", "номер")

private val CURRENCY_WORDS = setOf(
    "usd", "eur", "gbp", "jpy", "cad", "aud", "chf", "inr", "cny", "rub", "brl", "mxn",
    "sek", "nok", "dkk", "pln", "dollar", "dollars", "euro", "euros", "pound", "pounds", "yen",
)

private const val CURRENCY_SIGNS = "$€£¥₹₽₩"
private const val REJECT_BEFORE = "$€£¥₹₽₩#+/@"
private const val REJECT_AFTER = "%/$€£¥₹₽₩"
private const val NUMBER_PUNCTUATION = ".,:/"
private const val WORD_SKIP = " \t:.#-"

private class code_token(val start: Int, val end: Int, val value: String) {
    val has_digit: Boolean = value.any { it in '0'..'9' }
}

private class code_span(val start: Int, val end: Int, val value: String)

fun detect_verification_code(vararg parts: String?): String? {
    val text = parts.filterNot { it.isNullOrBlank() }.joinToString("\n").take(MAX_SCAN_LENGTH)
    if (text.isEmpty()) return null
    val keywords = keyword_spans(text)
    if (keywords.isEmpty()) return null
    var best: String? = null
    var best_gap = Int.MAX_VALUE
    for (candidate in code_candidates(text)) {
        if (keywords.any { candidate.start < it.last + 1 && it.first < candidate.end }) continue
        val gap = keywords.minOf { keyword ->
            if (keyword.first >= candidate.end) keyword.first - candidate.end else candidate.start - (keyword.last + 1)
        }
        if (gap <= KEYWORD_WINDOW && gap < best_gap) {
            best = candidate.value
            best_gap = gap
        }
    }
    return best
}

private fun keyword_spans(text: String): List<IntRange> {
    val spans = mutableListOf<IntRange>()
    CODE_WORD_PATTERN.findAll(text).forEach { match ->
        val prefix = match.groupValues[1].lowercase()
        if (prefix !in CODE_QUALIFIERS && !qualified(text, match.range)) spans.add(match.range)
    }
    KEYWORD_PATTERN.findAll(text).forEach { match ->
        if (!qualified(text, match.range)) spans.add(match.range)
    }
    CJK_KEYWORD_PATTERN.findAll(text).forEach { spans.add(it.range) }
    return spans
}

private fun qualified(text: String, range: IntRange): Boolean {
    val before = previous_word(text, range.first).lowercase()
    val after = next_word(text, range.last + 1).lowercase()
    return before in CODE_QUALIFIERS || after in CODE_QUALIFIERS
}

private fun code_candidates(text: String): List<code_span> {
    val tokens = tokenize(text)
    val candidates = mutableListOf<code_span>()
    var index = 0
    while (index < tokens.size) {
        var run_end = index
        while (run_end + 1 < tokens.size && linked(text, tokens[run_end], tokens[run_end + 1])) run_end++
        val run = tokens.subList(index, run_end + 1)
        val span = when (run.size) {
            1 -> code_span(run[0].start, run[0].end, run[0].value)
            2 -> grouped_span(run[0], run[1])
            else -> null
        }
        if (span != null && valid_code(span.value) && plausible_context(text, span)) candidates.add(span)
        index = run_end + 1
    }
    return candidates
}

private fun grouped_span(first: code_token, second: code_token): code_span? {
    if (first.value.length != second.value.length) return null
    if (first.value.length !in MIN_GROUP_LENGTH..MAX_GROUP_LENGTH) return null
    return code_span(first.start, second.end, first.value + second.value)
}

private fun tokenize(text: String): List<code_token> {
    val tokens = mutableListOf<code_token>()
    var index = 0
    while (index < text.length) {
        if (!is_word_char(text[index])) {
            index++
            continue
        }
        val start = index
        while (index < text.length && is_word_char(text[index])) index++
        val value = text.substring(start, index)
        if (value.all { it in 'A'..'Z' || it in '0'..'9' }) tokens.add(code_token(start, index, value))
        else tokens.add(code_token(start, index, ""))
    }
    return tokens
}

private fun is_word_char(char: Char): Boolean {
    if (!char.isLetterOrDigit()) return false
    return when (Character.UnicodeScript.of(char.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL -> false
        else -> true
    }
}

private fun linked(text: String, left: code_token, right: code_token): Boolean {
    if (right.start - left.end != 1) return false
    if (!left.has_digit || !right.has_digit) return false
    val separator = text[left.end]
    return separator == ' ' || separator == '-'
}

private fun valid_code(value: String): Boolean {
    if (value.length !in MIN_CODE_LENGTH..MAX_CODE_LENGTH) return false
    if (value.none { it in '0'..'9' }) return false
    if (value.length == 4 && value.all { it in '0'..'9' } && value.toInt() in 1900..2099) return false
    return true
}

private fun plausible_context(text: String, span: code_span): Boolean {
    val previous = text.getOrNull(span.start - 1)
    val before_previous = text.getOrNull(span.start - 2)
    val next = text.getOrNull(span.end)
    val after_next = text.getOrNull(span.end + 1)
    if (previous != null && previous in REJECT_BEFORE) return false
    if (previous != null && previous in NUMBER_PUNCTUATION && before_previous?.isDigit() == true) return false
    if (next != null && next in REJECT_AFTER) return false
    if (next != null && next in NUMBER_PUNCTUATION && after_next?.isDigit() == true) return false
    if (previous == ' ' && before_previous != null && before_previous in CURRENCY_SIGNS) return false
    if (next == ' ' && after_next != null && after_next in CURRENCY_SIGNS) return false
    val before_word = previous_word(text, span.start).lowercase()
    if (before_word in CURRENCY_WORDS || next_word(text, span.end).lowercase() in CURRENCY_WORDS) return false
    if (before_word in REFERENCE_WORDS) return false
    if (before_word in NUMBER_WORDS) {
        val word_start = previous_word_start(text, span.start)
        if (previous_word(text, word_start).lowercase() in REFERENCE_WORDS) return false
    }
    return true
}

private fun previous_word_start(text: String, index: Int): Int {
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] in WORD_SKIP) cursor--
    while (cursor >= 0 && text[cursor].isLetter()) cursor--
    return cursor + 1
}

private fun previous_word(text: String, index: Int): String {
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] in WORD_SKIP) cursor--
    val end = cursor + 1
    while (cursor >= 0 && text[cursor].isLetter()) cursor--
    return text.substring(cursor + 1, end)
}

private fun next_word(text: String, index: Int): String {
    var cursor = index
    while (cursor < text.length && text[cursor] in WORD_SKIP) cursor++
    val start = cursor
    while (cursor < text.length && text[cursor].isLetter()) cursor++
    return text.substring(start, cursor)
}
