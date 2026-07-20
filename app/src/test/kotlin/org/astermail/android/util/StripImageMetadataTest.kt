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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StripImageMetadataTest {
    private fun bytes_of(vararg values: Int): ByteArray =
        ByteArray(values.size) { (values[it] and 0xff).toByte() }

    private fun ascii(text: String): ByteArray =
        ByteArray(text.length) { (text[it].code and 0xff).toByte() }

    private fun u16_be(value: Int): ByteArray =
        bytes_of((value ushr 8) and 0xff, value and 0xff)

    private fun u32_be(value: Int): ByteArray = bytes_of(
        (value ushr 24) and 0xff,
        (value ushr 16) and 0xff,
        (value ushr 8) and 0xff,
        value and 0xff,
    )

    private fun u32_le(value: Int): ByteArray = bytes_of(
        value and 0xff,
        (value ushr 8) and 0xff,
        (value ushr 16) and 0xff,
        (value ushr 24) and 0xff,
    )

    private fun jpeg_segment(marker: Int, payload: ByteArray): ByteArray =
        bytes_of(0xff, marker) + u16_be(payload.size + 2) + payload

    private val jpeg_soi = bytes_of(0xff, 0xd8)

    private val jpeg_dqt = jpeg_segment(0xdb, ByteArray(8) { (it + 1).toByte() })

    private val jpeg_scan =
        bytes_of(0xff, 0xda, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3f, 0x00) +
            bytes_of(0x9a, 0x28, 0xa2, 0x8a, 0xff, 0x00, 0x41) +
            bytes_of(0xff, 0xd9)

    private val jpeg_exif_app1 = jpeg_segment(
        0xe1,
        ascii("Exif") + bytes_of(0x00, 0x00) + ascii("MM") + ByteArray(24) { 0x7f },
    )

    private val jpeg_icc_app2 = jpeg_segment(
        0xe2,
        ascii("ICC_PROFILE") + bytes_of(0x00, 0x01, 0x01) + ByteArray(16) { 0x33 },
    )

    private fun png_chunk(type: String, data: ByteArray): ByteArray =
        u32_be(data.size) + ascii(type) + data + bytes_of(0xde, 0xad, 0xbe, 0xef)

    private val png_signature = bytes_of(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    private val png_ihdr = png_chunk(
        "IHDR",
        u32_be(2) + u32_be(2) + bytes_of(0x08, 0x02, 0x00, 0x00, 0x00),
    )

    private val png_idat = png_chunk("IDAT", ByteArray(20) { (it * 7 + 3).toByte() })

    private val png_iend = png_chunk("IEND", ByteArray(0))

    private fun riff_chunk(fourcc: String, data: ByteArray): ByteArray {
        val padding = if (data.size % 2 == 1) bytes_of(0x00) else ByteArray(0)
        return ascii(fourcc) + u32_le(data.size) + data + padding
    }

    private fun webp_of(body: ByteArray): ByteArray =
        ascii("RIFF") + u32_le(4 + body.size) + ascii("WEBP") + body

    private fun vp8x_payload(flags: Int): ByteArray =
        bytes_of(flags, 0x00, 0x00, 0x00, 0x0f, 0x00, 0x00, 0x0f, 0x00, 0x00)

    @Test
    fun jpeg_drops_exif_app1_and_keeps_scan_data_byte_identical() {
        val original = jpeg_soi + jpeg_exif_app1 + jpeg_dqt + jpeg_scan

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)
        assertArrayEquals(jpeg_soi + jpeg_dqt + jpeg_scan, result.data)
        assertTrue(result.data.size < original.size)
    }

    @Test
    fun jpeg_preserves_icc_profile_app2() {
        val original = jpeg_soi + jpeg_icc_app2 + jpeg_exif_app1 + jpeg_dqt + jpeg_scan

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)
        assertArrayEquals(jpeg_soi + jpeg_icc_app2 + jpeg_dqt + jpeg_scan, result.data)
    }

    @Test
    fun jpeg_drops_non_icc_app2_and_comment_segments() {
        val foreign_app2 = jpeg_segment(0xe2, ascii("NOT_ICC") + ByteArray(4) { 0x11 })
        val comment = jpeg_segment(0xfe, ascii("private note"))
        val original = jpeg_soi + foreign_app2 + comment + jpeg_dqt + jpeg_scan

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)
        assertArrayEquals(jpeg_soi + jpeg_dqt + jpeg_scan, result.data)
    }

    @Test
    fun jpeg_without_metadata_round_trips_unchanged() {
        val original = jpeg_soi + jpeg_dqt + jpeg_scan

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)
        assertArrayEquals(original, result.data)
    }

    @Test
    fun png_drops_text_and_exif_chunks_and_keeps_idat() {
        val text_chunk = png_chunk("tEXt", ascii("Comment") + bytes_of(0x00) + ascii("camera"))
        val exif_chunk = png_chunk("eXIf", ascii("MM") + ByteArray(12) { 0x5a })
        val original = png_signature + png_ihdr + text_chunk + exif_chunk + png_idat + png_iend

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)
        assertArrayEquals(png_signature + png_ihdr + png_idat + png_iend, result.data)
    }

    @Test
    fun png_keeps_allowlisted_ancillary_chunks() {
        val icc_chunk = png_chunk("iCCP", ascii("profile") + bytes_of(0x00, 0x00) + ByteArray(8) { 0x21 })
        val phys_chunk = png_chunk("pHYs", u32_be(2835) + u32_be(2835) + bytes_of(0x01))
        val text_chunk = png_chunk("iTXt", ascii("xmp") + ByteArray(6) { 0x30 })
        val original = png_signature + png_ihdr + icc_chunk + phys_chunk + text_chunk + png_idat + png_iend

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)
        assertArrayEquals(png_signature + png_ihdr + icc_chunk + phys_chunk + png_idat + png_iend, result.data)
    }

    @Test
    fun png_without_iend_returns_failed() {
        val original = png_signature + png_ihdr + png_idat

        val result = strip_metadata(original)

        assertEquals(strip_status.failed, result.status)
        assertArrayEquals(original, result.data)
    }

    @Test
    fun webp_drops_exif_and_xmp_clears_vp8x_flags_and_rewrites_riff_size() {
        val vp8x = riff_chunk("VP8X", vp8x_payload(0x1c))
        val vp8 = riff_chunk("VP8 ", ByteArray(14) { (it + 1).toByte() })
        val exif = riff_chunk("EXIF", ascii("MM") + ByteArray(9) { 0x44 })
        val xmp = riff_chunk("XMP ", ascii("<x:xmpmeta/>"))
        val original = webp_of(vp8x + vp8 + exif + xmp)

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)

        val expected_vp8x = riff_chunk("VP8X", vp8x_payload(0x10))
        assertArrayEquals(webp_of(expected_vp8x + vp8), result.data)

        val declared_size = (result.data[4].toInt() and 0xff) or
            ((result.data[5].toInt() and 0xff) shl 8) or
            ((result.data[6].toInt() and 0xff) shl 16) or
            ((result.data[7].toInt() and 0xff) shl 24)
        assertEquals(result.data.size - 8, declared_size)
        assertEquals(0x10, result.data[12 + 8].toInt() and 0xff)
    }

    @Test
    fun webp_handles_odd_sized_chunk_padding() {
        val vp8 = riff_chunk("VP8L", ByteArray(7) { (it + 9).toByte() })
        val exif = riff_chunk("EXIF", ByteArray(3) { 0x66 })
        val original = webp_of(vp8 + exif)

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)
        assertArrayEquals(webp_of(vp8), result.data)
        assertEquals(0, result.data.size % 2)
    }

    @Test
    fun webp_without_metadata_round_trips_unchanged() {
        val original = webp_of(riff_chunk("VP8 ", ByteArray(12) { (it + 2).toByte() }))

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)
        assertArrayEquals(original, result.data)
    }

    @Test
    fun unrecognised_format_returns_unsupported_with_original_bytes() {
        val original = ByteArray(64) { (it * 3 + 1).toByte() }

        val result = strip_metadata(original)

        assertEquals(strip_status.unsupported, result.status)
        assertArrayEquals(original, result.data)
    }

    @Test
    fun empty_input_returns_unsupported() {
        val result = strip_metadata(ByteArray(0))

        assertEquals(strip_status.unsupported, result.status)
        assertEquals(0, result.data.size)
    }

    @Test
    fun truncated_jpeg_returns_failed_without_throwing() {
        val original = bytes_of(0xff, 0xd8, 0xff, 0xe1, 0x00, 0xff, 0x01, 0x02, 0x03)

        val result = strip_metadata(original)

        assertEquals(strip_status.failed, result.status)
        assertArrayEquals(original, result.data)
    }

    @Test
    fun truncated_webp_returns_failed_without_throwing() {
        val original = ascii("RIFF") + u32_le(0x7ffffff0) + ascii("WEBP") +
            ascii("VP8X") + u32_le(0x00ffffff) + bytes_of(0x01, 0x02)

        val result = strip_metadata(original)

        assertEquals(strip_status.failed, result.status)
        assertArrayEquals(original, result.data)
    }

    @Test
    fun high_bit_bytes_are_read_unsigned() {
        val payload = ByteArray(200) { 0xff.toByte() }
        val big_exif = jpeg_segment(0xe1, ascii("Exif") + bytes_of(0x00, 0x00) + payload)
        val original = jpeg_soi + big_exif + jpeg_dqt + jpeg_scan

        val result = strip_metadata(original)

        assertEquals(strip_status.stripped, result.status)
        assertArrayEquals(jpeg_soi + jpeg_dqt + jpeg_scan, result.data)
    }
}
