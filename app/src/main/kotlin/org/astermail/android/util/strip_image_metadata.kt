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

package org.astermail.android.util

import java.io.ByteArrayOutputStream

enum class strip_status { stripped, unsupported, failed }

data class strip_result(val data: ByteArray, val status: strip_status) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is strip_result) return false
        return status == other.status && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * data.contentHashCode() + status.hashCode()
}

private enum class image_format { jpeg, png, webp }

private val png_signature = intArrayOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

private val png_keep_chunks = setOf(
    "IHDR",
    "PLTE",
    "IDAT",
    "IEND",
    "tRNS",
    "gAMA",
    "cHRM",
    "sRGB",
    "iCCP",
    "sBIT",
    "bKGD",
    "hIST",
    "pHYs",
    "sPLT",
    "acTL",
    "fcTL",
    "fdAT",
    "cICP",
    "mDCv",
    "cLLi",
)

private fun byte_at(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xff

private fun read_u32_be(bytes: ByteArray, offset: Int): Long =
    ((byte_at(bytes, offset).toLong() shl 24) or
        (byte_at(bytes, offset + 1).toLong() shl 16) or
        (byte_at(bytes, offset + 2).toLong() shl 8) or
        byte_at(bytes, offset + 3).toLong()) and 0xffffffffL

private fun read_u32_le(bytes: ByteArray, offset: Int): Long =
    ((byte_at(bytes, offset + 3).toLong() shl 24) or
        (byte_at(bytes, offset + 2).toLong() shl 16) or
        (byte_at(bytes, offset + 1).toLong() shl 8) or
        byte_at(bytes, offset).toLong()) and 0xffffffffL

private fun write_u32_le(bytes: ByteArray, offset: Int, value: Long) {
    bytes[offset] = (value and 0xffL).toByte()
    bytes[offset + 1] = ((value ushr 8) and 0xffL).toByte()
    bytes[offset + 2] = ((value ushr 16) and 0xffL).toByte()
    bytes[offset + 3] = ((value ushr 24) and 0xffL).toByte()
}

private fun read_ascii(bytes: ByteArray, offset: Int, length: Int): String {
    val builder = StringBuilder(length)
    for (i in 0 until length) builder.append(byte_at(bytes, offset + i).toChar())
    return builder.toString()
}

private fun has_ascii_prefix(bytes: ByteArray, start: Int, end: Int, prefix: String): Boolean {
    if (end - start < prefix.length) return false
    for (i in prefix.indices) {
        if (byte_at(bytes, start + i) != prefix[i].code) return false
    }
    return true
}

private fun concat_chunks(chunks: List<ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    for (chunk in chunks) out.write(chunk)
    return out.toByteArray()
}

private fun detect_format(bytes: ByteArray): image_format? {
    if (bytes.size >= 3 &&
        byte_at(bytes, 0) == 0xff &&
        byte_at(bytes, 1) == 0xd8 &&
        byte_at(bytes, 2) == 0xff
    ) {
        return image_format.jpeg
    }

    if (bytes.size >= 8 && png_signature.indices.all { byte_at(bytes, it) == png_signature[it] }) {
        return image_format.png
    }

    if (bytes.size >= 12 &&
        read_ascii(bytes, 0, 4) == "RIFF" &&
        read_ascii(bytes, 8, 4) == "WEBP"
    ) {
        return image_format.webp
    }

    return null
}

private fun should_drop_jpeg_segment(
    bytes: ByteArray,
    marker: Int,
    payload_start: Int,
    payload_end: Int,
): Boolean {
    if (marker == 0xfe) return true
    if (marker == 0xe1) return true

    if (marker == 0xe2) {
        return !has_ascii_prefix(bytes, payload_start, payload_end, "ICC_PROFILE")
    }

    if (marker == 0xee) {
        return !has_ascii_prefix(bytes, payload_start, payload_end, "Adobe")
    }

    if (marker in 0xe3..0xef) return true

    return false
}

private fun strip_jpeg(bytes: ByteArray): ByteArray? {
    if (bytes.size < 4 || byte_at(bytes, 0) != 0xff || byte_at(bytes, 1) != 0xd8) return null

    val kept = mutableListOf(bytes.copyOfRange(0, 2))
    var offset = 2

    while (offset < bytes.size) {
        if (byte_at(bytes, offset) != 0xff) return null

        var marker_offset = offset

        while (marker_offset < bytes.size && byte_at(bytes, marker_offset) == 0xff) {
            marker_offset++
        }

        if (marker_offset >= bytes.size) return null

        val marker = byte_at(bytes, marker_offset)

        if (marker == 0xda || marker == 0xd9) {
            kept.add(bytes.copyOfRange(offset, bytes.size))

            return concat_chunks(kept)
        }

        if (marker == 0x01 || marker in 0xd0..0xd7) {
            kept.add(bytes.copyOfRange(offset, marker_offset + 1))
            offset = marker_offset + 1
            continue
        }

        val length_offset = marker_offset + 1

        if (length_offset + 1 >= bytes.size) return null

        val segment_length = (byte_at(bytes, length_offset) shl 8) or byte_at(bytes, length_offset + 1)

        if (segment_length < 2) return null

        val segment_end = length_offset + segment_length

        if (segment_end > bytes.size) return null

        if (should_drop_jpeg_segment(bytes, marker, length_offset + 2, segment_end)) {
            offset = segment_end
            continue
        }

        kept.add(bytes.copyOfRange(offset, segment_end))
        offset = segment_end
    }

    return concat_chunks(kept)
}

private fun strip_png(bytes: ByteArray): ByteArray? {
    if (bytes.size < 8) return null

    for (i in 0 until 8) {
        if (byte_at(bytes, i) != png_signature[i]) return null
    }

    val kept = mutableListOf(bytes.copyOfRange(0, 8))
    var offset = 8
    var saw_end = false

    while (offset + 8 <= bytes.size) {
        val length = read_u32_be(bytes, offset)

        if (length > 0x7fffffffL) return null

        val type = read_ascii(bytes, offset + 4, 4)
        val chunk_end = offset.toLong() + 12L + length

        if (chunk_end > bytes.size.toLong()) return null

        val is_critical = (byte_at(bytes, offset + 4) and 0x20) == 0

        if (is_critical || png_keep_chunks.contains(type)) {
            kept.add(bytes.copyOfRange(offset, chunk_end.toInt()))
        }

        offset = chunk_end.toInt()

        if (type == "IEND") {
            saw_end = true
            break
        }
    }

    if (!saw_end) return null

    return concat_chunks(kept)
}

private fun strip_webp(bytes: ByteArray): ByteArray? {
    if (bytes.size < 12) return null
    if (read_ascii(bytes, 0, 4) != "RIFF") return null
    if (read_ascii(bytes, 8, 4) != "WEBP") return null

    val riff_size = read_u32_le(bytes, 4)
    val declared_end = minOf(bytes.size.toLong(), 8L + riff_size).toInt()

    if (declared_end < 12) return null

    val kept = mutableListOf<ByteArray>()
    var offset = 12

    while (offset + 8 <= declared_end) {
        val fourcc = read_ascii(bytes, offset, 4)
        val size = read_u32_le(bytes, offset + 4)

        if (size > 0x7fffffffL) return null

        val padded_size = size + (size % 2L)
        val chunk_end = offset.toLong() + 8L + padded_size

        if (chunk_end > declared_end.toLong()) return null

        if (fourcc != "EXIF" && fourcc != "XMP ") {
            val chunk = bytes.copyOfRange(offset, chunk_end.toInt())

            if (fourcc == "VP8X" && size >= 1L) {
                chunk[8] = ((chunk[8].toInt() and 0xff) and 0x0c.inv()).toByte()
            }

            kept.add(chunk)
        }

        offset = chunk_end.toInt()
    }

    if (kept.isEmpty()) return null

    var body_length = 0

    for (chunk in kept) body_length += chunk.size

    val out = ByteArray(12 + body_length)

    bytes.copyInto(out, 0, 0, 12)
    write_u32_le(out, 4, 4L + body_length.toLong())

    var write_offset = 12

    for (chunk in kept) {
        chunk.copyInto(out, write_offset)
        write_offset += chunk.size
    }

    return out
}

private fun strip_lossless(bytes: ByteArray, format: image_format): ByteArray? = when (format) {
    image_format.jpeg -> strip_jpeg(bytes)
    image_format.png -> strip_png(bytes)
    image_format.webp -> strip_webp(bytes)
}

fun strip_metadata(data: ByteArray): strip_result {
    val format = detect_format(data) ?: return strip_result(data, strip_status.unsupported)

    val stripped = try {
        strip_lossless(data, format)
    } catch (_: Throwable) {
        null
    }

    if (stripped != null && stripped.isNotEmpty()) {
        return strip_result(stripped, strip_status.stripped)
    }

    return strip_result(data, strip_status.failed)
}
