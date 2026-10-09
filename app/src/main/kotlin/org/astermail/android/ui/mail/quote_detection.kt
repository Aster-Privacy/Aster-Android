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

package org.astermail.android.ui.mail

internal const val MAX_ATTRIBUTION_LENGTH = 400

private const val HEADER_BLOCK_SPAN = 7

private val ATTRIBUTION_VERBS = listOf(
    "wrote",
    "schrieb",
    """a\s+écrit""",
    "escribió",
    """ha\s+scritto""",
    "escreveu",
    "schreef",
    "skrev",
    "kirjoitti",
    """napisał\(a\)""",
    "napisała",
    "napisał",
    "pisze",
    "пишет",
    """написал\(а\)""",
    "написала",
    "написал",
    "yazdı",
    "έγραψε",
    """napsal\(a\)""",
    "napsala",
    "napsal",
    "írta",
    """a\s+scris""",
    "写道",
    "寫道",
    """님이\s*작성""",
).joinToString("|")

private val ENGLISH_ATTRIBUTION = Regex("""^On\s.+(?<![A-Za-z0-9_])[Ww]rote\s*:\s*$""")

private val VERB_END = Regex("""(?:$ATTRIBUTION_VERBS)\s*[:：]\s*$""", RegexOption.IGNORE_CASE)

private val VERB_MID = Regex(
    """^(?:Am|Op|Den|Dne|På)\s.+\s(?:schrieb|schreef|skrev|napsal(?:\(a\)|a)?)\s.+[:：]\s*$""",
    RegexOption.IGNORE_CASE,
)

private val ATTRIBUTION_PREFIX = Regex("""^(?:On|Am|Le|El|Il|Em|Op|Den|W\s+dniu|Dne|Στις)\s""")

private val ATTRIBUTION_DETAIL = Regex("""@|\d{1,2}[:.]\d{2}|(?<![A-Za-z0-9_])(?:19|20)\d{2}(?![A-Za-z0-9_])""")

private val SENTENCE_END = Regex("""[.!?]$""")

private val FROM_LINE = Regex(
    """^\*?(?:From|Von|De|Da|Van|Från|Fra|Od|От|Lähettäjä|Feladó|Kimden|Από|发件人|寄件者|差出人|보낸\s*사람)""" +
        """\s*\*?\s*[:：]\s*\S""",
    RegexOption.IGNORE_CASE,
)

private val DATE_LINE = Regex(
    """^\*?(?:Sent|Date|Gesendet|Datum|Envoyé|Enviado|Fecha|Inviato|Data|Verzonden|Skickat|Sendt|Wysłano|""" +
        """Отправлено|Дата|Lähetetty|Päivämäärä|Elküldve|Dátum|Gönderildi|Tarih|Στάλθηκε|Ημερομηνία|""" +
        """发送时间|日期|送信日時|日付|보낸\s*날짜)\s*\*?\s*[:：]""",
    RegexOption.IGNORE_CASE,
)

private val SUBJECT_LINE = Regex(
    """^\*?(?:Subject|Betreff|Objet|Asunto|Assunto|Oggetto|Onderwerp|Ämne|Emne|Temat|Тема|Aihe|Tárgy|Konu|""" +
        """Θέμα|主题|主旨|件名|제목)\s*\*?\s*[:：]""",
    RegexOption.IGNORE_CASE,
)

private val ORIGINAL_MESSAGE = Regex(
    """^-{2,}\s*(?:Ursprüngliche Nachricht|Message d'origine|Mensaje original|Messaggio originale|""" +
        """Mensagem original|Oorspronkelijk bericht|Ursprungligt meddelande|Oprindelig meddelelse|""" +
        """Opprinnelig melding|Alkuperäinen viesti|Oryginalna wiadomość|Původní zpráva|Eredeti üzenet|""" +
        """Исходное сообщение|Orijinal ileti)\s*-{2,}$""",
    RegexOption.IGNORE_CASE,
)

private val SEPARATOR_LINE = Regex("""^(?:[_\-=—]\s*){8,}$""")

private val MARKER_LINE = Regex("""^-{2,}\s*(?:Original Message|Forwarded message)\s*-{2,}$""", RegexOption.IGNORE_CASE)

private val UNICODE_SPACE = Regex("[\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]")

internal data class QuoteStart(val start: Int, val body_from: Int)

internal data class QuoteEnd(val end: Int, val to_end: Boolean)

internal fun normalize_quote_line(text: String): String = text.replace(UNICODE_SPACE, " ").trim()

internal fun is_quoted_line(text: String): Boolean = text.startsWith(">")

internal fun is_attribution_text(text: String): Boolean {
    if (text.isEmpty() || text.length > MAX_ATTRIBUTION_LENGTH) return false
    if (is_quoted_line(text)) return false
    if (ENGLISH_ATTRIBUTION.containsMatchIn(text)) return true
    if (VERB_MID.containsMatchIn(text)) return ATTRIBUTION_DETAIL.containsMatchIn(text)
    if (!VERB_END.containsMatchIn(text)) return false
    return ATTRIBUTION_DETAIL.containsMatchIn(text)
}

internal fun is_separator_text(text: String): Boolean = SEPARATOR_LINE.containsMatchIn(text)

internal fun is_boilerplate_text(text: String): Boolean {
    if (text.isEmpty() || is_separator_text(text)) return true
    return MARKER_LINE.containsMatchIn(text) || ORIGINAL_MESSAGE.containsMatchIn(text)
}

internal fun has_reply_text_before(lines: List<String>, index: Int): Boolean =
    (0 until index).any { !is_boilerplate_text(lines[it]) }

private fun is_header_block(lines: List<String>, index: Int): Boolean {
    if (!FROM_LINE.containsMatchIn(lines[index])) return false
    var has_date = false
    var has_subject = false
    val limit = minOf(lines.size, index + 1 + HEADER_BLOCK_SPAN)
    for (j in index + 1 until limit) {
        if (DATE_LINE.containsMatchIn(lines[j])) has_date = true
        if (SUBJECT_LINE.containsMatchIn(lines[j])) has_subject = true
    }
    return has_date && has_subject
}

private fun with_separator(lines: List<String>, start: Int, body_from: Int): QuoteStart {
    val previous = lines.getOrNull(start - 1)
    if (previous != null && is_separator_text(previous)) return QuoteStart(start - 1, body_from)
    return QuoteStart(start, body_from)
}

private fun wraps_into_attribution(text: String, next: String?): Boolean {
    if (next.isNullOrEmpty() || is_quoted_line(next)) return false
    if (!ATTRIBUTION_PREFIX.containsMatchIn(text)) return false
    if (SENTENCE_END.containsMatchIn(text)) return false
    return is_attribution_text("$text $next")
}

internal fun find_quote_start(lines: List<String>): QuoteStart? {
    for (i in lines.indices) {
        val text = lines[i]
        if (text.isEmpty() || is_quoted_line(text)) continue
        if (is_attribution_text(text)) return with_separator(lines, i, i + 1)
        if (wraps_into_attribution(text, lines.getOrNull(i + 1))) return with_separator(lines, i, i + 2)
        if (ORIGINAL_MESSAGE.containsMatchIn(text)) return QuoteStart(i, i + 1)
        if (is_header_block(lines, i)) return with_separator(lines, i, lines.size)
    }
    return null
}

internal fun find_trailing_quote(lines: List<String>): QuoteStart? {
    var last = lines.size - 1
    while (last >= 0 && lines[last].isEmpty()) last--
    if (last < 0 || !is_quoted_line(lines[last])) return null
    var first = last
    while (first > 0 && (lines[first - 1].isEmpty() || is_quoted_line(lines[first - 1]))) first--
    while (lines[first].isEmpty()) first++
    var intro = first - 1
    while (intro >= 0 && lines[intro].isEmpty()) intro--
    val intro_text = if (intro >= 0) lines[intro] else ""
    if (intro_text.isNotEmpty() && intro_text.length <= MAX_ATTRIBUTION_LENGTH &&
        VERB_END.containsMatchIn(intro_text)
    ) {
        return QuoteStart(intro, first)
    }
    return QuoteStart(first, first)
}

internal fun resolve_quote_end(lines: List<String>, body_from: Int): QuoteEnd? {
    val to_end = QuoteEnd(lines.size - 1, true)
    var first = body_from
    while (first < lines.size && lines[first].isEmpty()) first++
    if (first >= lines.size || !is_quoted_line(lines[first])) return to_end
    var last_quoted = first
    for (k in first + 1 until lines.size) {
        val text = lines[k]
        if (text.isEmpty()) continue
        if (is_quoted_line(text)) {
            last_quoted = k
            continue
        }
        for (rest in k + 1 until lines.size) {
            if (is_quoted_line(lines[rest])) return null
        }
        return QuoteEnd(last_quoted, false)
    }
    return to_end
}
