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

    fun looks_like_mime(content: String): Boolean {
        val trimmed = content.trimStart()
        return trimmed.startsWith("Content-Type:", ignoreCase = true) ||
            trimmed.startsWith("MIME-Version:", ignoreCase = true)
    }

    fun parse(raw: String): MimeResult {
        val (headers, body) = split_header_body(raw)
        val header_map = parse_headers(headers)
        val content_type = header_map["content-type"] ?: "text/plain"
        val encoding = header_map["content-transfer-encoding"]

        if (content_type.contains("multipart/", ignoreCase = true)) {
            val boundary = extract_boundary(content_type) ?: return MimeResult(body, null)
            return parse_multipart(body, boundary)
        }

        val decoded = decode_body(body, encoding)
        return if (content_type.contains("text/html", ignoreCase = true)) {
            MimeResult(null, decoded)
        } else {
            MimeResult(decoded, null)
        }
    }

    private fun parse_multipart(body: String, boundary: String): MimeResult {
        val parts = body.split("--$boundary")
        var text: String? = null
        var html: String? = null

        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty() || trimmed == "--") continue

            val (part_headers_raw, part_body) = split_header_body(part)
            val part_headers = parse_headers(part_headers_raw)
            val ct = part_headers["content-type"] ?: "text/plain"
            val enc = part_headers["content-transfer-encoding"]
            val disposition = part_headers["content-disposition"] ?: ""

            if (disposition.contains("attachment", ignoreCase = true)) continue

            when {
                ct.contains("multipart/", ignoreCase = true) -> {
                    val nested_boundary = extract_boundary(ct)
                    if (nested_boundary != null) {
                        val nested = parse_multipart(part_body, nested_boundary)
                        if (html == null && nested.html != null) html = nested.html
                        if (text == null && nested.text != null) text = nested.text
                    }
                }
                ct.contains("text/html", ignoreCase = true) && html == null -> {
                    html = decode_body(part_body, enc)
                }
                ct.contains("text/plain", ignoreCase = true) && text == null -> {
                    text = decode_body(part_body, enc)
                }
            }
        }

        return MimeResult(text, html)
    }

    private fun split_header_body(raw: String): Pair<String, String> {
        val crlf = raw.indexOf("\r\n\r\n")
        if (crlf != -1) return raw.substring(0, crlf) to raw.substring(crlf + 4)
        val lf = raw.indexOf("\n\n")
        if (lf != -1) return raw.substring(0, lf) to raw.substring(lf + 2)
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
                    headers[current_key.lowercase()] = current_value
                }
                val colon = line.indexOf(':')
                if (colon > 0) {
                    current_key = line.substring(0, colon).trim()
                    current_value = line.substring(colon + 1).trim()
                }
            }
        }
        if (current_key.isNotEmpty()) {
            headers[current_key.lowercase()] = current_value
        }
        return headers
    }

    private fun extract_boundary(content_type: String): String? {
        val match = Regex("boundary=[\"']?([^\"';\\s]+)[\"']?", RegexOption.IGNORE_CASE)
            .find(content_type)
        return match?.groupValues?.get(1)
    }

    private fun decode_body(body: String, encoding: String?): String {
        if (encoding == null) return body.trim()
        return when (encoding.lowercase().trim()) {
            "quoted-printable" -> decode_quoted_printable(body).trim()
            "base64" -> decode_base64(body).trim()
            else -> body.trim()
        }
    }

    private fun decode_quoted_printable(input: String): String {
        return input
            .replace(Regex("=\\r?\\n"), "")
            .replace(Regex("=([0-9A-Fa-f]{2})")) { match ->
                match.groupValues[1].toInt(16).toChar().toString()
            }
    }

    private fun decode_base64(input: String): String {
        return try {
            val cleaned = input.replace(Regex("[\\r\\n\\s]"), "")
            if (cleaned.isEmpty()) return ""
            val bytes = android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (_: Throwable) {
            input
        }
    }
}
