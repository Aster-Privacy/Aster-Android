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

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class MimeAttachmentPart(
    val filename: String,
    val content_type: String,
    val content_id: String?,
    val is_inline: Boolean,
    val data: ByteArray,
)

object MimeAttachmentExtractor {

    private const val MAX_MIME_DEPTH = 12
    private const val MAX_MIME_ATTACHMENTS = 100

    private val SKIPPED_LEAF_TYPES = setOf(
        "application/pgp-signature",
        "application/pgp-keys",
        "application/pgp-encrypted",
        "application/pkcs7-signature",
        "application/x-pkcs7-signature",
        "text/rfc822-headers",
    )

    private val BODY_LEAF_TYPES = setOf("text/plain", "text/html")

    private val HEADER_SPLIT = Regex("\\r?\\n\\r?\\n")
    private val HEADER_FOLD = Regex("\\r?\\n[ \\t]+")
    private val LINE_SPLIT = Regex("\\r?\\n")
    private val LEADING_NEWLINE = Regex("^\\r?\\n")
    private val HEADER_NAME = Regex("^[!-9;-~]+$")
    private val CONTENT_TYPE_LINE = Regex(
        "^content-type\\s*:",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val EXTENDED_PARAM = Regex("^([^*]+)\\*(?:(\\d+)\\*?|)$")
    private val ENCODED_WORD = Regex("=\\?([^?\\s]+)\\?([BbQq])\\?([^?\\s]*)\\?=")
    private val ENCODED_WORD_GAP = Regex("(=\\?[^?\\s]+\\?[BbQq]\\?[^?\\s]*\\?=)\\s+(?==\\?)")
    private val QUOTED_ESCAPE = Regex("\\\\(.)")
    private val QP_SOFT_BREAK = Regex("=[ \\t]*\\r?\\n")
    private val QP_TRAILING_SPACE = Regex("[ \\t]+(?=\\r?\\n)")
    private val CONTROL_CHARS = Regex("[\\u0000-\\u001f\\u007f]")
    private val SIMPLE_SUBTYPE = Regex("^[a-z0-9]{1,8}$")

    private class Node(val headers: Map<String, String>, val body: String)

    private class HeaderValue(val value: String, val params: Map<String, String>)

    private class ExtendedSegment(val index: Int, val text: String, val encoded: Boolean)

    fun extract(raw: String): List<MimeAttachmentPart> {
        if (!CONTENT_TYPE_LINE.containsMatchIn(raw)) return emptyList()
        val root = split_header_block(raw.removePrefix("﻿")) ?: return emptyList()
        if (!root.headers.containsKey("content-type")) return emptyList()
        val out = mutableListOf<MimeAttachmentPart>()
        runCatching { collect_parts(root, out, 0) }
        return out
    }

    fun decode_encoded_words(input: String): String {
        val collapsed = ENCODED_WORD_GAP.replace(input, "$1")
        return ENCODED_WORD.replace(collapsed) { match ->
            val charset = match.groupValues[1].substringBefore('*')
            val text = match.groupValues[3]
            if (match.groupValues[2].equals("b", ignoreCase = true)) {
                base64_decode(text)?.let { decode_with_charset(it, charset) } ?: match.value
            } else {
                decode_with_charset(escaped_to_bytes(text, '=', underscore_is_space = true), charset)
            }
        }
    }

    private fun split_header_block(raw: String): Node? {
        val match = HEADER_SPLIT.find(raw)
        val header_text = if (match != null) raw.substring(0, match.range.first) else raw
        val body = if (match != null) raw.substring(match.range.last + 1) else ""
        val headers = LinkedHashMap<String, String>()
        for (line in HEADER_FOLD.replace(header_text, " ").split(LINE_SPLIT)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            if (!HEADER_NAME.matches(name)) continue
            headers.putIfAbsent(name, line.substring(colon + 1).trim())
        }
        if (headers.isEmpty()) return null
        return Node(headers, body)
    }

    private fun split_params(input: String): MutableList<String> {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var in_quotes = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && in_quotes && i + 1 < input.length) {
                current.append(c).append(input[i + 1])
                i += 2
                continue
            }
            if (c == '"') in_quotes = !in_quotes
            if (c == ';' && !in_quotes) {
                segments.add(current.toString())
                current.setLength(0)
            } else {
                current.append(c)
            }
            i += 1
        }
        segments.add(current.toString())
        return segments
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            return QUOTED_ESCAPE.replace(trimmed.substring(1, trimmed.length - 1), "$1")
        }
        return trimmed
    }

    private fun is_hex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun escaped_to_bytes(input: String, marker: Char, underscore_is_space: Boolean): ByteArray {
        val out = ByteArrayOutputStream(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == marker && i + 2 < input.length && is_hex(input[i + 1]) && is_hex(input[i + 2])) {
                out.write(input.substring(i + 1, i + 3).toInt(16))
                i += 3
                continue
            }
            if (underscore_is_space && c == '_') {
                out.write(' '.code)
                i += 1
                continue
            }
            val code_point = input.codePointAt(i)
            out.write(String(Character.toChars(code_point)).toByteArray(Charsets.UTF_8))
            i += Character.charCount(code_point)
        }
        return out.toByteArray()
    }

    private fun strict_decode(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    private fun decode_with_charset(bytes: ByteArray, charset: String): String {
        val label = charset.trim().ifEmpty { "utf-8" }
        val named = runCatching { Charset.forName(label) }.getOrNull()
        return named?.let { strict_decode(bytes, it) }
            ?: strict_decode(bytes, Charsets.UTF_8)
            ?: String(bytes, Charsets.ISO_8859_1)
    }

    private fun parse_header_value(raw: String?): HeaderValue {
        val segments = split_params(raw ?: "")
        val value = segments.removeFirstOrNull()?.trim()?.lowercase() ?: ""
        val simple = LinkedHashMap<String, String>()
        val extended = LinkedHashMap<String, MutableList<ExtendedSegment>>()
        for (segment in segments) {
            val eq = segment.indexOf('=')
            if (eq <= 0) continue
            val key = segment.substring(0, eq).trim().lowercase()
            val raw_value = unquote(segment.substring(eq + 1))
            val ext = EXTENDED_PARAM.find(key)
            if (ext == null) {
                simple.putIfAbsent(key, raw_value)
                continue
            }
            val index = ext.groupValues[2].toIntOrNull() ?: 0
            extended.getOrPut(ext.groupValues[1]) { mutableListOf() }
                .add(ExtendedSegment(index, raw_value, key.endsWith("*")))
        }
        for ((base, entries) in extended) {
            entries.sortBy { it.index }
            val first = entries.firstOrNull() ?: continue
            if (!first.encoded) {
                simple[base] = entries.joinToString("") { it.text }
                continue
            }
            val first_quote = first.text.indexOf('\'')
            val second_quote = if (first_quote >= 0) first.text.indexOf('\'', first_quote + 1) else -1
            val charset = if (second_quote >= 0) first.text.substring(0, first_quote) else "utf-8"
            val bytes = ByteArrayOutputStream()
            for (entry in entries) {
                val text = if (entry === first && second_quote >= 0) {
                    entry.text.substring(second_quote + 1)
                } else {
                    entry.text
                }
                bytes.write(
                    if (entry.encoded) {
                        escaped_to_bytes(text, '%', underscore_is_space = false)
                    } else {
                        text.toByteArray(Charsets.UTF_8)
                    },
                )
            }
            simple[base] = decode_with_charset(bytes.toByteArray(), charset)
        }
        return HeaderValue(value, simple)
    }

    private fun base64_decode(input: String): ByteArray? {
        val cleaned = input.filter {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/'
        }
        val usable = if (cleaned.length % 4 == 1) cleaned.dropLast(1) else cleaned
        val padded = usable + "===".substring(0, (4 - usable.length % 4) % 4)
        return runCatching { java.util.Base64.getDecoder().decode(padded) }.getOrNull()
    }

    private fun quoted_printable_decode(input: String): ByteArray {
        val unfolded = QP_SOFT_BREAK.replace(input, "")
        return escaped_to_bytes(QP_TRAILING_SPACE.replace(unfolded, ""), '=', underscore_is_space = false)
    }

    private fun decode_part_bytes(body: String, encoding: String): ByteArray = when (encoding) {
        "base64" -> base64_decode(body) ?: ByteArray(0)
        "quoted-printable" -> quoted_printable_decode(body)
        else -> if (body.all { it.code <= 0xff }) {
            ByteArray(body.length) { body[it].code.toByte() }
        } else {
            body.toByteArray(Charsets.UTF_8)
        }
    }

    private fun strip_part_body_terminator(body: String): String = when {
        body.endsWith("\r\n") -> body.dropLast(2)
        body.endsWith("\n") -> body.dropLast(1)
        else -> body
    }

    private fun split_multipart(body: String, boundary: String): List<String> {
        val delimiter = Regex("(?:^|\\r?\\n)--${Regex.escape(boundary)}(--)?[ \\t]*(?=\\r?\\n|$)")
        val parts = mutableListOf<String>()
        var start = -1
        for (match in delimiter.findAll(body)) {
            if (start >= 0) parts.add(body.substring(start, match.range.first))
            if (match.groupValues[1] == "--") {
                start = -1
                break
            }
            var after = match.range.last + 1
            if (body.startsWith("\r\n", after)) {
                after += 2
            } else if (body.startsWith("\n", after)) {
                after += 1
            }
            start = after
        }
        if (start >= 0) parts.add(body.substring(start))
        return parts
    }

    private fun extension_for_type(content_type: String): String {
        if (content_type == "message/rfc822") return ".eml"
        val sub = content_type.substringAfter('/', "")
        return if (SIMPLE_SUBTYPE.matches(sub)) ".$sub" else ""
    }

    private fun resolve_filename(disposition: HeaderValue, content_type: HeaderValue): String? {
        val raw = disposition.params["filename"] ?: content_type.params["name"] ?: return null
        return CONTROL_CHARS.replace(decode_encoded_words(raw), "").trim().ifEmpty { null }
    }

    private fun collect_parts(node: Node, out: MutableList<MimeAttachmentPart>, depth: Int) {
        if (depth > MAX_MIME_DEPTH || out.size >= MAX_MIME_ATTACHMENTS) return
        val content_type = parse_header_value(node.headers["content-type"])
        val type = content_type.value.ifEmpty { "text/plain" }
        val boundary = content_type.params["boundary"]
        if (type.startsWith("multipart/") && !boundary.isNullOrEmpty()) {
            for (raw_part in split_multipart(node.body, boundary)) {
                val child = split_header_block(LEADING_NEWLINE.replaceFirst(raw_part, "\n"))
                if (child != null) {
                    collect_parts(child, out, depth + 1)
                } else if (raw_part.isNotBlank()) {
                    collect_parts(Node(emptyMap(), LEADING_NEWLINE.replaceFirst(raw_part, "")), out, depth + 1)
                }
            }
            return
        }
        if (type in SKIPPED_LEAF_TYPES) return
        val disposition = parse_header_value(node.headers["content-disposition"])
        val filename = resolve_filename(disposition, content_type)
        val content_id = node.headers["content-id"]
            ?.trim()
            ?.removePrefix("<")
            ?.removeSuffix(">")
            ?.trim()
            ?.ifEmpty { null }
        val is_attachment_disposition = disposition.value == "attachment"
        if (type in BODY_LEAF_TYPES && filename == null && !is_attachment_disposition) return
        if (filename == null && !is_attachment_disposition && content_id == null) return
        val encoding = (node.headers["content-transfer-encoding"] ?: "7bit").trim().lowercase()
        val data = decode_part_bytes(strip_part_body_terminator(node.body), encoding)
        if (data.isEmpty()) return
        out.add(
            MimeAttachmentPart(
                filename = filename
                    ?: "${content_id?.substringBefore('@') ?: "attachment"}${extension_for_type(type)}",
                content_type = type,
                content_id = content_id,
                is_inline = disposition.value == "inline",
                data = data,
            ),
        )
    }
}
