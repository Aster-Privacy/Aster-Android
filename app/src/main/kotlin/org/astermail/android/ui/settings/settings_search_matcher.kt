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

package org.astermail.android.ui.settings

import java.text.Normalizer
import java.util.Locale

internal data class settings_search_candidate(
    val screen_id: String,
    val label: String,
    val parent: String,
    val keywords: List<String>,
    val is_screen_title: Boolean,
    val is_description: Boolean,
)

internal data class settings_search_scored(
    val candidate: settings_search_candidate,
    val score: Int,
)

internal class settings_search_prepared(val candidate: settings_search_candidate) {
    val label_norm: String = normalize_settings_search_text(candidate.label)
    val label_words: List<String> = split_settings_search_words(label_norm)
    val label_stem: String = label_words.joinToString(" ") { settings_search_stem(it) }
    val parent_norm: String = normalize_settings_search_text(candidate.parent)
    val parent_words: List<String> = split_settings_search_words(parent_norm)
    val keyword_phrases: List<String> = candidate.keywords
        .map { normalize_settings_search_text(it) }
        .filter { it.isNotEmpty() }
        .distinct()
    val keyword_words: List<String> = keyword_phrases.flatMap { split_settings_search_words(it) }.distinct()
    val keyword_compact: List<String> = keyword_phrases.map { phrase ->
        split_settings_search_words(phrase).filter { it !in settings_search_stop_words }.joinToString(" ")
    }.filter { it.isNotEmpty() }
}

private const val match_none = 0
private const val match_typo = 1
private const val match_prefix = 2
private const val match_exact = 3

internal val settings_search_stop_words: Set<String> = setOf(
    "a", "an", "the", "to", "my", "me", "i", "how", "do", "does", "can", "where", "is", "what", "of", "for",
    "in", "change", "turn", "off", "on", "enable", "disable", "set", "setting", "settings", "toggle", "find",
    "want", "please", "and",
)

private val description_suffixes = listOf("_subtitle", "_description", "_desc", "_sub", "_body", "_hint")

internal fun is_settings_description_name(resource_name: String): Boolean =
    description_suffixes.any { resource_name.endsWith(it) }

internal fun normalize_settings_search_text(text: String): String {
    val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    var last_space = true
    for (ch in decomposed) {
        when {
            ch in '̀'..'ͯ' -> Unit
            ch == '\'' || ch == '’' -> Unit
            Character.isLetterOrDigit(ch) || Character.getType(ch) == Character.NON_SPACING_MARK.toInt() ||
                Character.getType(ch) == Character.COMBINING_SPACING_MARK.toInt() -> {
                out.append(ch)
                last_space = false
            }
            !last_space -> {
                out.append(' ')
                last_space = true
            }
        }
    }
    return Normalizer.normalize(out.toString().trim(), Normalizer.Form.NFC)
}

internal fun split_settings_search_keywords(raw: String): List<String> =
    raw.split(',', '،', '、').map { it.trim() }.filter { it.isNotEmpty() }

internal fun split_settings_search_words(normalized: String): List<String> =
    if (normalized.isEmpty()) emptyList() else normalized.split(' ')

internal fun settings_search_edit_distance(a: String, b: String): Int {
    val rows = a.length + 1
    val cols = b.length + 1
    val d = Array(rows) { IntArray(cols) }
    for (i in 0 until rows) d[i][0] = i
    for (j in 0 until cols) d[0][j] = j
    for (i in 1 until rows) {
        for (j in 1 until cols) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var best = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                best = minOf(best, d[i - 2][j - 2] + 1)
            }
            d[i][j] = best
        }
    }
    return d[a.length][b.length]
}

private fun is_unspaced_script(token: String): Boolean = token.any { ch ->
    when (Character.UnicodeScript.of(ch.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.THAI,
        -> true
        else -> false
    }
}

internal fun settings_search_stem(word: String): String {
    if (word.length <= 3 || word.any { it !in 'a'..'z' }) return word
    return when {
        word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
        word.endsWith("sses") -> word.dropLast(2)
        word.endsWith("xes") || word.endsWith("ches") || word.endsWith("shes") -> word.dropLast(2)
        word.endsWith("ases") -> word.dropLast(2)
        word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") || word.endsWith("as") -> word
        word.endsWith("s") -> word.dropLast(1)
        else -> word
    }
}

internal fun settings_search_query_tokens(normalized: String): List<String> {
    val tokens = split_settings_search_words(normalized).distinct()
    val meaningful = tokens.filter { it !in settings_search_stop_words }
    return meaningful.ifEmpty { tokens }
}

private fun token_typo_match(token: String, word: String): Boolean {
    if (token.length < 4 || token.any { it.isDigit() }) return false
    if (kotlin.math.abs(token.length - word.length) <= 1 && settings_search_edit_distance(token, word) <= 1) {
        return true
    }
    if (token.length >= 5 && word.length > token.length) {
        return settings_search_edit_distance(token, word.substring(0, token.length)) <= 1
    }
    return false
}

private fun token_match(token: String, words: List<String>, full_text: String): Int {
    var best = match_none
    val token_stem = settings_search_stem(token)
    for ((index, word) in words.withIndex()) {
        if (word == token) return match_exact
        if (settings_search_stem(word) == token_stem) return match_exact
        if (index + 1 < words.size && word.length + words[index + 1].length == token.length &&
            token == word + words[index + 1]
        ) {
            return match_exact
        }
        if (word.startsWith(token)) {
            best = maxOf(best, match_prefix)
        } else if (best < match_typo && token_typo_match(token, word)) {
            best = match_typo
        }
    }
    if (best < match_prefix && is_unspaced_script(token) && full_text.contains(token)) {
        best = match_prefix
    }
    return best
}

private fun settings_search_tier(row: settings_search_prepared, query: String, tokens: List<String>): Int {
    val keyword_text = row.keyword_phrases.joinToString(" ")
    val in_label = tokens.map { token_match(it, row.label_words, row.label_norm) }
    val in_keywords = tokens.map { token_match(it, row.keyword_words, keyword_text) }
    val in_parent = tokens.map { token_match(it, row.parent_words, row.parent_norm) }
    val best = tokens.indices.map { maxOf(in_label[it], in_keywords[it], in_parent[it]) }
    if (best.any { it == match_none }) return 0

    val label_clean = in_label.all { it >= match_prefix }
    val label_any = in_label.all { it >= match_typo }
    val label_or_keyword_clean = tokens.indices.all { maxOf(in_label[it], in_keywords[it]) >= match_prefix }
    val label_or_keyword_any = tokens.indices.all { maxOf(in_label[it], in_keywords[it]) >= match_typo }

    if (row.candidate.is_description) {
        return when {
            label_clean -> 300
            label_any -> 250
            label_or_keyword_any -> 200
            else -> 100
        }
    }
    return when {
        row.label_norm == query -> 1000
        row.label_stem == query.split(' ').joinToString(" ") { settings_search_stem(it) } -> 900
        row.label_norm.startsWith(query) -> 800
        row.keyword_phrases.any { it == query } || row.keyword_compact.any { it == query } -> 700
        label_clean -> 600
        row.keyword_phrases.any { it.startsWith(query) } -> 500
        label_or_keyword_clean -> 450
        label_any -> 350
        label_or_keyword_any -> 300
        else -> 100
    }
}

internal fun prepare_settings_search(candidates: List<settings_search_candidate>): List<settings_search_prepared> =
    candidates.map { settings_search_prepared(it) }

internal fun rank_settings_search(
    query: String,
    rows: List<settings_search_prepared>,
    limit: Int = 40,
    per_screen_limit: Int = 3,
): List<settings_search_scored> {
    val normalized = normalize_settings_search_text(query)
    val tokens = settings_search_query_tokens(normalized)
    if (tokens.isEmpty()) return emptyList()
    val effective = tokens.joinToString(" ")
    val scored = rows.mapNotNull { row ->
        val tier = settings_search_tier(row, effective, tokens)
        if (tier == 0) {
            null
        } else {
            val bonus = if (row.candidate.is_screen_title) 20 else 0
            val length_penalty = (row.candidate.label.length / 12).coerceAtMost(6)
            settings_search_scored(row.candidate, tier + bonus - length_penalty)
        }
    }
    val per_screen = mutableMapOf<String, Int>()
    return scored
        .sortedWith(
            compareByDescending<settings_search_scored> { it.score }
                .thenBy { it.candidate.label.length },
        )
        .distinctBy { it.candidate.screen_id + "|" + it.candidate.label.lowercase(Locale.ROOT) }
        .filter { hit ->
            val used = per_screen[hit.candidate.screen_id] ?: 0
            if (used >= per_screen_limit) {
                false
            } else {
                per_screen[hit.candidate.screen_id] = used + 1
                true
            }
        }
        .take(limit)
}
