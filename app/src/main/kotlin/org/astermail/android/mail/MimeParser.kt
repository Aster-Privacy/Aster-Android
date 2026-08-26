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

data class MimeResult(
    val text: String?,
    val html: String?,
)

object MimeParser {

    private val header_line_pattern = Regex("^[A-Za-z][A-Za-z0-9-]*[ \\t]*:")
    private val media_type_pattern = Regex("^[A-Za-z][A-Za-z0-9.+-]*/[A-Za-z0-9.+-]+")

    fun looks_like_mime(content: String): Boolean {
        val normalized = normalize_line_endings(strip_body_framing(content))
        if (normalized.isEmpty()) return false
        val (headers, _) = split_header_body(normalized)
        return header_block_is_mime(headers)
    }

    fun parse(raw: String): MimeResult {
        val normalized = normalize_line_endings(strip_body_framing(raw))
        val (headers, body) = split_header_body(normalized)
        val header_map = parse_headers(headers)
        val content_type = header_map["content-type"] ?: "text/plain"
        val encoding = header_map["content-transfer-encoding"]
        val charset = extract_charset(content_type)

        if (content_type.contains("multipart/", ignoreCase = true)) {
            val boundary = extract_boundary(content_type)
                ?: return MimeResult(body.trim(), null)
            val parsed = parse_multipart(body, boundary, 0)
            if (parsed.text != null || parsed.html != null) return parsed
            return MimeResult(body.trim(), null)
        }

        val decoded = decode_body(body, encoding, charset)
        return if (content_type.contains("text/html", ignoreCase = true)) {
            MimeResult(null, decoded)
        } else {
            MimeResult(decoded, null)
        }
    }

    private fun is_header_block(headers: String): Boolean {
        var saw_header = false
        for (line in headers.split("\n")) {
            if (line.isBlank()) continue
            if (line.startsWith(" ") || line.startsWith("\t")) continue
            if (!header_line_pattern.containsMatchIn(line)) return false
            saw_header = true
        }
        return saw_header
    }

    private fun header_block_is_mime(headers: String): Boolean {
        if (!is_header_block(headers)) return false
        var has_mime_header = false
        for (line in headers.split("\n")) {
            if (line.isBlank()) continue
            if (line.startsWith(" ") || line.startsWith("\t")) continue
            val name = line.substringBefore(':').trim().lowercase(java.util.Locale.ROOT)
            val value = line.substringAfter(':').trim()
            if (name == "content-type" && media_type_pattern.containsMatchIn(value)) {
                has_mime_header = true
            }
            if (name == "mime-version" && value.firstOrNull()?.isDigit() == true) {
                has_mime_header = true
            }
        }
        return has_mime_header
    }

    private fun parse_multipart(body: String, boundary: String, depth: Int = 0): MimeResult {
        if (depth > 10) return MimeResult(null, null)
        val parts = body.split("--$boundary")
        var text: String? = null
        var html: String? = null

        for (part in parts) {
            val stripped = part.replace(Regex("^[\\r\\n]+"), "")
            if (stripped.isBlank() || stripped.startsWith("--")) continue

            val (part_headers_raw, part_body) = split_header_body(stripped)
            if (!is_header_block(part_headers_raw)) continue
            val part_headers = parse_headers(part_headers_raw)
            val ct = part_headers["content-type"] ?: "text/plain"
            val enc = part_headers["content-transfer-encoding"]
            val part_charset = extract_charset(ct)
            val disposition = part_headers["content-disposition"] ?: ""

            if (disposition.contains("attachment", ignoreCase = true)) continue

            when {
                ct.contains("multipart/", ignoreCase = true) -> {
                    val nested_boundary = extract_boundary(ct)
                    if (nested_boundary != null) {
                        val nested = parse_multipart(part_body, nested_boundary, depth + 1)
                        if (html == null && nested.html != null) html = nested.html
                        if (text == null && nested.text != null) text = nested.text
                    }
                }
                ct.contains("text/html", ignoreCase = true) && html == null -> {
                    html = decode_body(part_body, enc, part_charset)
                }
                ct.contains("text/plain", ignoreCase = true) && text == null -> {
                    text = decode_body(part_body, enc, part_charset)
                }
            }
        }

        return MimeResult(text, html)
    }

    private fun normalize_line_endings(input: String): String =
        if (input.indexOf('\r') == -1) input else input.replace("\r\n", "\n").replace('\r', '\n')

    private fun extract_charset(content_type: String): java.nio.charset.Charset {
        val match = Regex("charset=[\"']?([^\"';\\s]+)[\"']?", RegexOption.IGNORE_CASE)
            .find(content_type) ?: return Charsets.UTF_8
        return runCatching { java.nio.charset.Charset.forName(match.groupValues[1]) }
            .getOrDefault(Charsets.UTF_8)
    }

    private fun split_header_body(raw: String): Pair<String, String> {
        val crlf = raw.indexOf("\r\n\r\n")
        if (crlf != -1) return raw.substring(0, crlf) to raw.substring(crlf + 4)
        val lf = raw.indexOf("\n\n")
        if (lf != -1) return raw.substring(0, lf) to raw.substring(lf + 2)
        return split_at_first_non_header(raw)
    }

    private fun split_at_first_non_header(raw: String): Pair<String, String> {
        var offset = 0
        var saw_header = false
        while (offset < raw.length) {
            val end = raw.indexOf('\n', offset).let { if (it == -1) raw.length else it }
            val line = raw.substring(offset, end)
            val is_continuation = line.startsWith(" ") || line.startsWith("\t")
            if (!is_continuation && !header_line_pattern.containsMatchIn(line)) {
                return if (saw_header) raw.substring(0, offset) to raw.substring(offset) else raw to ""
            }
            if (!is_continuation) saw_header = true
            offset = if (end == raw.length) raw.length else end + 1
        }
        return raw to ""
    }

    private fun parse_headers(raw: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val lines = raw.split(Regex("\\r?\\n"))
        var current_key = ""
        var current_value = ""

        for (line in lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (current_key.isNotEmpty()) current_value += " " + line.trim()
            } else {
                if (current_key.isNotEmpty()) {
                    headers[current_key.lowercase(java.util.Locale.ROOT)] = current_value
                }
                val colon = line.indexOf(':')
                if (colon > 0) {
                    current_key = line.substring(0, colon).trim()
                    current_value = line.substring(colon + 1).trim()
                }
            }
        }
        if (current_key.isNotEmpty()) {
            headers[current_key.lowercase(java.util.Locale.ROOT)] = current_value
        }
        return headers
    }

    private fun extract_boundary(content_type: String): String? {
        val match = Regex("boundary=[\"']?([^\"';\\s]+)[\"']?", RegexOption.IGNORE_CASE)
            .find(content_type)
        return match?.groupValues?.get(1)
    }

    private fun decode_body(
        body: String,
        encoding: String?,
        charset: java.nio.charset.Charset = Charsets.UTF_8,
    ): String {
        if (encoding == null) return body.trim()
        return when (encoding.lowercase(java.util.Locale.ROOT).trim().trimEnd(';')) {
            "quoted-printable" -> decode_quoted_printable(body, charset).trim()
            "base64" -> decode_base64(body, charset).trim()
            else -> body.trim()
        }
    }

    private fun decode_quoted_printable(
        input: String,
        charset: java.nio.charset.Charset = Charsets.UTF_8,
    ): String {
        val joined = input.replace(Regex("=[ \\t]*\\r?\\n"), "")
        val out = java.io.ByteArrayOutputStream(joined.length)
        val literal = StringBuilder()
        var i = 0
        while (i < joined.length) {
            val ch = joined[i]
            if (ch == '=' && i + 2 < joined.length) {
                val hex = joined.substring(i + 1, i + 3)
                val n = if (hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                    hex.toIntOrNull(16)
                } else {
                    null
                }
                if (n != null) {
                    if (literal.isNotEmpty()) {
                        out.write(literal.toString().toByteArray(charset))
                        literal.setLength(0)
                    }
                    out.write(n)
                    i += 3
                    continue
                }
            }
            literal.append(ch)
            i++
        }
        if (literal.isNotEmpty()) out.write(literal.toString().toByteArray(charset))
        return String(out.toByteArray(), charset)
    }

    private fun decode_base64(
        input: String,
        charset: java.nio.charset.Charset = Charsets.UTF_8,
    ): String {
        return try {
            val cleaned = input.replace(Regex("[^A-Za-z0-9+/=]"), "")
            if (cleaned.isEmpty()) return ""
            val bytes = android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
            String(bytes, charset)
        } catch (_: Throwable) {
            input
        }
    }
}
