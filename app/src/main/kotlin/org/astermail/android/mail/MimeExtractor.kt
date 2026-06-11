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

import android.util.Base64

object MimeExtractor {

    private val content_type_pattern = Regex("(?im)^content-type\\s*:")
    private val boundary_pattern = Regex("(?i)boundary=\"?([^\\s\";]+)\"?")
    private val transfer_encoding_pattern = Regex("(?i)content-transfer-encoding\\s*:\\s*(\\S+)")
    private val charset_pattern = Regex("(?i)charset=\"?([^\\s\";]+)\"?")
    private val qp_eol_pattern = Regex("=\\r?\\n")
    private val qp_hex_pattern = Regex("=([0-9A-Fa-f]{2})")

    data class MimeBody(val content: String, val is_html: Boolean)

    fun try_extract(text: String): String {
        if (!content_type_pattern.containsMatchIn(text)) return text
        return try {
            extract(text, prefer_html = true)?.content ?: text
        } catch (_: Throwable) {
            text
        }
    }

    fun try_extract_typed(text: String): MimeBody? {
        if (!content_type_pattern.containsMatchIn(text)) return null
        return try {
            extract(text, prefer_html = true)
        } catch (_: Throwable) {
            null
        }
    }

    private fun find_split(text: String): Pair<String, String>? {
        val crlf = text.indexOf("\r\n\r\n")
        val lf = text.indexOf("\n\n")
        val pos: Int
        val skip: Int
        when {
            crlf >= 0 && lf >= 0 -> {
                pos = minOf(crlf, lf)
                skip = if (crlf <= lf) 4 else 2
            }
            crlf >= 0 -> { pos = crlf; skip = 4 }
            lf >= 0 -> { pos = lf; skip = 2 }
            else -> return null
        }
        return text.substring(0, pos) to text.substring(pos + skip)
    }

    private fun decode_transfer_encoding(body: String, headers: String): String {
        val encoding = transfer_encoding_pattern.find(headers)
            ?.groupValues?.getOrNull(1)
            ?.lowercase() ?: "7bit"
        val charset = charset_pattern.find(headers)
            ?.groupValues?.getOrNull(1)
            ?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() }
            ?: Charsets.UTF_8

        return when (encoding) {
            "base64" -> try {
                val raw = Base64.decode(body.replace(Regex("\\s"), ""), Base64.DEFAULT)
                String(raw, charset)
            } catch (_: Throwable) {
                body
            }
            "quoted-printable" -> {
                val joined = qp_eol_pattern.replace(body, "")
                val sb = StringBuilder(joined.length)
                val out = java.io.ByteArrayOutputStream(joined.length)
                var i = 0
                while (i < joined.length) {
                    val ch = joined[i]
                    if (ch == '=' && i + 2 < joined.length) {
                        val a = joined[i + 1]
                        val b = joined[i + 2]
                        val hex = "$a$b"
                        val n = hex.toIntOrNull(16)
                        if (n != null) {
                            if (sb.isNotEmpty()) {
                                out.write(sb.toString().toByteArray(charset))
                                sb.setLength(0)
                            }
                            out.write(n)
                            i += 3
                            continue
                        }
                    }
                    sb.append(ch)
                    i++
                }
                if (sb.isNotEmpty()) out.write(sb.toString().toByteArray(charset))
                String(out.toByteArray(), charset)
            }
            else -> body
        }
    }

    private fun get_boundary(headers: String): String? =
        boundary_pattern.find(headers)?.groupValues?.getOrNull(1)

    private fun extract_from_multipart(
        body: String,
        boundary: String,
        prefer_html: Boolean,
    ): MimeBody? {
        val parts = body.split("--$boundary")
        var plain: MimeBody? = null
        var html: MimeBody? = null

        for (part in parts) {
            val trimmed = part.replace(Regex("^[\\r\\n]+"), "")
            if (trimmed.startsWith("--") || trimmed.isEmpty()) continue
            val split = find_split(trimmed) ?: continue
            val headers = split.first
            val payload = split.second
            val lower = headers.lowercase()
            val nested = get_boundary(headers)

            if (nested != null && (
                    lower.contains("multipart/alternative") ||
                        lower.contains("multipart/related") ||
                        lower.contains("multipart/mixed")
                    )
            ) {
                val r = extract_from_multipart(payload, nested, prefer_html)
                if (r != null) return r
                continue
            }

            if (lower.contains("text/html") && html == null) {
                html = MimeBody(decode_transfer_encoding(payload.trim(), lower), true)
            } else if (lower.contains("text/plain") && plain == null) {
                plain = MimeBody(decode_transfer_encoding(payload.trim(), lower), false)
            }
        }

        return if (prefer_html) html ?: plain else plain ?: html
    }

    private fun extract(raw: String, prefer_html: Boolean): MimeBody? {
        val split = find_split(raw) ?: return null
        val headers = split.first
        val body = split.second
        if (!content_type_pattern.containsMatchIn(headers)) return null

        val boundary = get_boundary(headers)
        if (boundary != null) {
            val r = extract_from_multipart(body, boundary, prefer_html)
            if (r != null) return r
        }

        val lower = headers.lowercase()
        if (lower.contains("text/html")) {
            return MimeBody(decode_transfer_encoding(body.trim(), lower), true)
        }
        if (lower.contains("text/plain")) {
            return MimeBody(decode_transfer_encoding(body.trim(), lower), false)
        }
        return MimeBody(body.trim(), false)
    }
}
