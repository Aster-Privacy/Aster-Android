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
package org.astermail.android.crypto

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ProtectedMimeAttachment(
    val filename: String,
    val content_type: String,
    val data_base64: String,
    val content_id: String? = null,
)

data class ProtectedMimeInput(
    val subject: String,
    val body: String,
    val is_html: Boolean,
    val from: String,
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val attachments: List<ProtectedMimeAttachment> = emptyList(),
    val date: Date = Date(),
)

object ProtectedMimeBuilder {

    private const val ENCODED_WORD_PAYLOAD_BYTES = 45
    private const val BASE64_LINE_LENGTH = 76
    private val random = SecureRandom()

    fun build(input: ProtectedMimeInput): String {
        val boundary = "--=_astermail_protected_${random_token()}"
        val builder = StringBuilder()

        builder.append("Content-Type: multipart/mixed; boundary=\"")
            .append(boundary)
            .append("\"; protected-headers=\"v1\"\r\n\r\n")
        builder.append("--").append(boundary).append("\r\n")
        builder.append(protected_headers_part(input))
        builder.append("\r\n")
        builder.append("--").append(boundary).append("\r\n")
        builder.append(body_part(input))

        for (attachment in input.attachments) {
            builder.append("--").append(boundary).append("\r\n")
            builder.append(attachment_part(attachment))
        }

        builder.append("--").append(boundary).append("--\r\n")

        return builder.toString()
    }

    fun body_looks_like_html(body: String): Boolean {
        return body.contains("<br") ||
            body.contains("<a ") ||
            body.contains("<p>") ||
            body.contains("<div") ||
            body.contains("<html") ||
            body.contains("</")
    }

    private fun protected_headers_part(input: ProtectedMimeInput): String {
        val builder = StringBuilder()

        builder.append("Content-Type: text/rfc822-headers; charset=utf-8; protected-headers=\"v1\"\r\n")
        builder.append("Content-Disposition: inline\r\n\r\n")
        builder.append("Date: ").append(sanitize_header_value(format_rfc2822_date(input.date))).append("\r\n")
        builder.append("Subject: ").append(encode_header_value(input.subject)).append("\r\n")

        if (input.from.isNotEmpty()) {
            builder.append("From: ").append(encode_address_header(input.from)).append("\r\n")
        }
        if (input.to.isNotEmpty()) {
            builder.append("To: ").append(input.to.joinToString(", ") { encode_address_header(it) }).append("\r\n")
        }
        if (input.cc.isNotEmpty()) {
            builder.append("Cc: ").append(input.cc.joinToString(", ") { encode_address_header(it) }).append("\r\n")
        }

        return builder.toString()
    }

    private fun body_part(input: ProtectedMimeInput): String {
        if (!input.is_html) {
            return "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Transfer-Encoding: 8bit\r\n\r\n" +
                input.body + "\r\n"
        }

        val alt_boundary = "--=_astermail_alt_${random_token()}"

        return "Content-Type: multipart/alternative; boundary=\"$alt_boundary\"\r\n\r\n" +
            "--$alt_boundary\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Transfer-Encoding: base64\r\n\r\n" +
            base64_body(html_to_plain_text(input.body)) +
            "--$alt_boundary\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Transfer-Encoding: base64\r\n\r\n" +
            base64_body(input.body) +
            "--$alt_boundary--\r\n"
    }

    private fun attachment_part(attachment: ProtectedMimeAttachment): String {
        val filename = sanitize_filename(attachment.filename)
        val raw_type = sanitize_header_value(attachment.content_type)
        val content_type = if (raw_type.isEmpty()) "application/octet-stream" else raw_type
        val builder = StringBuilder()

        builder.append("Content-Type: ").append(content_type).append("; name=\"").append(filename).append("\"\r\n")
        builder.append("Content-Transfer-Encoding: base64\r\n")

        val cid = (attachment.content_id ?: "")
            .filter { it.isLetterOrDigit() || it in "@.-_+" }
            .take(255)

        if (cid.isNotEmpty()) {
            builder.append("Content-ID: <").append(cid).append(">\r\n")
            builder.append("Content-Disposition: inline; filename=\"").append(filename).append("\"\r\n")
        } else {
            builder.append("Content-Disposition: attachment; filename=\"").append(filename).append("\"\r\n")
        }

        builder.append("\r\n")
        builder.append(wrap_base64(attachment.data_base64))

        return builder.toString()
    }

    private fun sanitize_header_value(value: String): String {
        return value.filter { it != '\r' && it != '\n' && it != '\u0000' }
    }

    private fun needs_encoded_word(value: String): Boolean {
        return value.any { it.code > 0x7f || it.code < 0x20 }
    }

    private fun base64_of_string(value: String): String {
        return android.util.Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
    }

    private fun encoded_word_chunks(value: String): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        var current_bytes = 0
        var index = 0

        while (index < value.length) {
            val point = value.codePointAt(index)
            val char_count = Character.charCount(point)
            val piece = value.substring(index, index + char_count)
            val size = piece.toByteArray(Charsets.UTF_8).size

            if (current_bytes + size > ENCODED_WORD_PAYLOAD_BYTES) {
                chunks.add("=?UTF-8?B?${base64_of_string(current.toString())}?=")
                current.setLength(0)
                current_bytes = 0
            }

            current.append(piece)
            current_bytes += size
            index += char_count
        }

        if (current.isNotEmpty()) {
            chunks.add("=?UTF-8?B?${base64_of_string(current.toString())}?=")
        }

        return chunks
    }

    private fun encode_header_value(value: String): String {
        val sanitized = sanitize_header_value(value)

        if (!needs_encoded_word(sanitized)) return sanitized

        return encoded_word_chunks(sanitized).joinToString("\r\n ")
    }

    private fun encode_address_header(value: String): String {
        val sanitized = sanitize_header_value(value)

        if (!needs_encoded_word(sanitized)) return sanitized

        val trimmed = sanitized.trimEnd()
        val open = trimmed.lastIndexOf('<')

        if (open != -1 && trimmed.endsWith(">")) {
            val display = trimmed.substring(0, open).trim().trim('"')
            val address = trimmed.substring(open)

            if (!needs_encoded_word(address)) {
                if (display.isEmpty()) return address

                return "${encoded_word_chunks(display).joinToString("\r\n ")} $address"
            }
        }

        return encoded_word_chunks(sanitized).joinToString("\r\n ")
    }

    private fun sanitize_filename(value: String): String {
        val cleaned = value
            .filter { it.code >= 0x20 && it.code != 0x7f && it != '"' && it != '\\' && it != '\r' && it != '\n' }
            .take(255)
            .trim()

        return cleaned.ifEmpty { "attachment" }
    }

    private fun wrap_base64(data: String): String {
        val compact = data.filter { !it.isWhitespace() }
        val builder = StringBuilder()
        var index = 0

        while (index < compact.length) {
            val end = minOf(index + BASE64_LINE_LENGTH, compact.length)
            builder.append(compact, index, end).append("\r\n")
            index = end
        }

        return builder.toString()
    }

    private fun base64_body(value: String): String = wrap_base64(base64_of_string(value))

    fun html_to_plain_text(html: String): String {
        val out = StringBuilder()
        var index = 0

        while (index < html.length) {
            val c = html[index]

            if (c == '<') {
                val tag = StringBuilder()
                index += 1

                while (index < html.length && html[index] != '>') {
                    tag.append(html[index])
                    index += 1
                }

                index += 1

                val lowered = tag.toString().trim().lowercase(Locale.US)

                if (
                    lowered.startsWith("br") ||
                    lowered.startsWith("/p") ||
                    lowered.startsWith("/div") ||
                    lowered.startsWith("/tr") ||
                    lowered.startsWith("/li") ||
                    lowered.startsWith("/h1") ||
                    lowered.startsWith("/h2") ||
                    lowered.startsWith("/h3")
                ) {
                    out.append('\n')
                }

                continue
            }

            out.append(c)
            index += 1
        }

        val decoded = out.toString()
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        val collapsed = StringBuilder()
        var blank_run = 0

        for (line in decoded.split("\r\n", "\r", "\n")) {
            val trimmed = line.trimEnd()

            if (trimmed.isBlank()) {
                blank_run += 1
                if (blank_run > 1) continue
            } else {
                blank_run = 0
            }

            collapsed.append(trimmed).append("\r\n")
        }

        val result = collapsed.toString().trim()

        return result.ifEmpty { " " }
    }

    private fun random_token(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)

        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun format_rfc2822_date(date: Date): String {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss +0000", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")

        return format.format(date)
    }
}
