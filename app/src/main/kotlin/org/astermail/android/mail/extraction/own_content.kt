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
package org.astermail.android.mail.extraction

import org.astermail.android.mail.html_to_plain_text
import org.astermail.android.mail.looks_like_html_body
import org.astermail.android.mail.strip_quoted_html
import org.astermail.android.ui.mail.find_quote_start
import org.astermail.android.ui.mail.is_boilerplate_text
import org.astermail.android.ui.mail.is_quoted_line
import org.astermail.android.ui.mail.is_separator_text
import org.astermail.android.ui.mail.normalize_quote_line
import java.util.Locale

private val leading_subject_prefix = Regex("^\\s*([\\p{L}]{2,5})\\s*:")
private val reply_subject_prefixes = setOf("re", "aw", "sv", "antw")
private val forward_subject_prefixes = setOf("fw", "fwd", "wg", "tr", "rv", "enc")

private val forwarded_marker = Regex(
    "(?:^|\\n)[ \\t>]*(?:-{2,}\\s*(?:forwarded message|weitergeleitete nachricht|message transféré|" +
        "mensaje reenviado|messaggio inoltrato|mensagem encaminhada|doorgestuurd bericht)\\s*-{2,}|" +
        "begin forwarded message\\s*:)",
    RegexOption.IGNORE_CASE,
)

private val inline_quote_markers = listOf(
    Regex("(?:^|\\s)On\\s[^\\n]{4,300}?\\s[Ww]rote\\s*:"),
    Regex("(?:^|\\s)Am\\s[^\\n]{4,300}?\\sschrieb\\b[^\\n]{0,200}?:"),
    Regex(
        "-{2,}\\s*(?:Original Message|Ursprüngliche Nachricht|Message d'origine|Mensaje original)\\s*-{2,}",
        RegexOption.IGNORE_CASE,
    ),
    Regex(
        "(?:^|\\s)(?:From|Von|De)\\s*:\\s*\\S[^\\n]{0,200}?\\s(?:Sent|Date|Gesendet|Datum|Envoyé|Enviado|Fecha)\\s*:",
    ),
)

private fun leading_prefix(subject: String): String? =
    leading_subject_prefix.find(subject)?.groupValues?.get(1)?.lowercase(Locale.ROOT)

internal fun is_reply_subject(subject: String): Boolean = leading_prefix(subject) in reply_subject_prefixes

private fun is_forwarded_message(subject: String, text: String): Boolean {
    val prefix = leading_prefix(subject)
    if (prefix in reply_subject_prefixes) return false
    return prefix in forward_subject_prefixes || forwarded_marker.containsMatchIn(text)
}

private fun without_quoted_markup(html: String, is_reply: Boolean): String {
    val document = runCatching { org.jsoup.Jsoup.parse(strip_quoted_html(html)) }.getOrNull()
        ?: return strip_quoted_html(html)
    document.select(if (is_reply) "blockquote" else "blockquote[type=cite]").remove()
    return document.outerHtml()
}

private fun without_quoted_lines(text: String): String {
    val raw_lines = text.split('\n').filterNot { is_quoted_line(normalize_quote_line(it)) }
    val lines = raw_lines.map(::normalize_quote_line)
    val marker = lines.indexOfFirst { it.isNotEmpty() && !is_separator_text(it) && is_boilerplate_text(it) }
    val start = find_quote_start(lines)?.start
    val cut = listOfNotNull(start, marker.takeIf { it >= 0 }).minOrNull() ?: raw_lines.size
    return raw_lines.subList(0, cut).joinToString("\n").trim()
}

private fun before_inline_quote(text: String): String {
    val cut = inline_quote_markers.mapNotNull { it.find(text)?.range?.first }.minOrNull() ?: return text
    return text.substring(0, cut)
}

internal fun own_message_text(subject: String, text: String, html: String?): String {
    val full = normalize_email_text(text, html)
    if (is_forwarded_message(subject, full)) return full
    val reply = is_reply_subject(subject)
    val source = if (text.isNotBlank()) text else html.orEmpty()
    val readable = if (looks_like_html_body(source)) {
        html_to_plain_text(without_quoted_markup(source, reply))
    } else {
        decode_basic_entities(source)
    }
    val own = without_quoted_lines(readable)
    if (own.isNotBlank()) return own
    return if (reply) "" else full
}

internal fun own_preview_text(subject: String, preview: String): String {
    if (is_forwarded_message(subject, preview)) return preview
    val own = before_inline_quote(without_quoted_lines(preview)).trim()
    if (own.isNotBlank()) return own
    return if (is_reply_subject(subject)) "" else preview
}
