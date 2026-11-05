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
package org.astermail.android.compose

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.astermail.android.crypto.ProtectedMimeBuilder
import org.astermail.android.crypto.ProtectedMimeInput
import org.astermail.android.mail.MimeParser
import org.astermail.android.mail.build_plain_text_html
import org.astermail.android.ui.compose.BIDI_LINES_OPEN
import org.astermail.android.ui.compose.assemble_body_with_signature
import org.astermail.android.ui.compose.plain_signature_with_separator
import org.astermail.android.ui.mail.EmailHtmlSanitizer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class persian_send_round_trip_test {

    private val greeting = "سلام، این یک پیام آزمایشی است"
    private val with_zwnj = "می‌خواهم این را فردا بفرستم."
    private val persian_digits = "شماره سفارش ۱۲۳ و کد ۴۵۶"
    private val mixed = "جلسه ساعت 10 با تیم Aster Mail برگزار می‌شود (Room B)."
    private val lines = listOf(greeting, with_zwnj, persian_digits, mixed)

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun plain_body(body: String, signature: String = ""): String = assemble_body_with_signature(
        body = body,
        is_rich = false,
        plain_signature = signature,
        html_signature = "",
        signature_id = null,
        quote_html = "",
        place_below = false,
    )

    private fun mime_for(subject: String, html: String): String = ProtectedMimeBuilder.build(
        ProtectedMimeInput(
            subject = subject,
            body = html,
            is_html = true,
            from = "sender@astermail.org",
            to = listOf("recipient@example.com"),
        ),
    )

    private fun base64_part(mime: String, content_type: String): ByteArray {
        val start = mime.indexOf("Content-Type: $content_type; charset=utf-8\r\nContent-Transfer-Encoding: base64\r\n\r\n")
        assertTrue("missing $content_type part", start >= 0)
        val body_start = mime.indexOf("\r\n\r\n", start) + 4
        val body_end = mime.indexOf("--", body_start)
        val encoded = mime.substring(body_start, body_end)
        assertTrue(encoded.lines().all { it.length <= 76 })
        return java.util.Base64.getDecoder().decode(encoded.filter { !it.isWhitespace() })
    }

    private fun strict_utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    private fun subject_words(mime: String): List<String> {
        val header = mime.substringAfter("\r\nSubject: ").let { rest ->
            val out = StringBuilder()
            var cursor = 0
            while (true) {
                val end = rest.indexOf("\r\n", cursor)
                out.append(rest, cursor, end)
                if (!rest.startsWith(" ", end + 2) && !rest.startsWith("\t", end + 2)) break
                cursor = end + 2
            }
            out.toString()
        }
        return header.split(' ').filter { it.isNotEmpty() }
    }

    @Test
    fun each_persian_line_is_sent_unchanged_with_its_own_direction() {
        val html = plain_body(lines.joinToString("\n"))
        assertEquals(lines.joinToString("") { "<div dir=\"auto\">$it</div>" }, html)
    }

    @Test
    fun the_signature_block_takes_its_direction_from_its_text() {
        val signature = plain_signature_with_separator("آدام\nتیم پشتیبانی", true)
        val html = plain_body(greeting + "\n\n" + signature, signature)
        assertTrue(html, html.startsWith("<div dir=\"auto\">$greeting</div><div><br></div>"))
        assertTrue(
            html,
            html.endsWith("dir=\"auto\" style=\"unicode-bidi:plaintext\">--<br>آدام<br>تیم پشتیبانی</div>"),
        )
    }

    @Test
    fun a_formatted_body_is_wrapped_so_every_line_finds_its_direction() {
        val rich = "<b>$greeting</b><br>Meeting notes<br>$mixed"
        val html = assemble_body_with_signature(
            body = rich,
            is_rich = true,
            plain_signature = "",
            html_signature = "",
            signature_id = null,
            quote_html = "",
            place_below = false,
        )
        assertEquals(BIDI_LINES_OPEN + rich + "</div>", html)
    }

    @Test
    fun the_html_part_carries_the_exact_utf8_bytes() {
        val html = plain_body(lines.joinToString("\n"))
        val mime = mime_for("آزمایش", html)
        val decoded = base64_part(mime, "text/html")
        assertArrayEquals(html.toByteArray(Charsets.UTF_8), decoded)
        assertEquals(html, strict_utf8(decoded))
    }

    @Test
    fun the_plain_text_part_keeps_every_persian_line_exactly() {
        val html = plain_body(lines.joinToString("\n"))
        val mime = mime_for("آزمایش", html)
        val decoded = strict_utf8(base64_part(mime, "text/plain"))
        assertEquals(lines.joinToString("\r\n"), decoded)
        assertTrue(decoded.contains("‌"))
        assertTrue(decoded.contains("۱۲۳"))
    }

    @Test
    fun the_message_parses_back_to_the_same_body() {
        val html = plain_body(lines.joinToString("\n"))
        val parsed = MimeParser.parse(mime_for(greeting, html))
        assertEquals(html, parsed.html)
        assertEquals(lines.joinToString("\n"), parsed.text?.replace("\r\n", "\n"))
    }

    @Test
    fun a_persian_subject_decodes_byte_for_byte_and_never_splits_a_character() {
        val subject = "$greeting؛ $with_zwnj $persian_digits $mixed 🌷"
        val words = subject_words(mime_for(subject, plain_body(greeting)))
        assertTrue(words.size > 1)
        val decoded = words.joinToString("") { word ->
            assertTrue(word, word.startsWith("=?UTF-8?B?") && word.endsWith("?="))
            val bytes = java.util.Base64.getDecoder().decode(word.removePrefix("=?UTF-8?B?").removeSuffix("?="))
            assertTrue(bytes.size <= 45)
            assertTrue(word.length <= 75)
            strict_utf8(bytes)
        }
        assertEquals(subject, decoded)
        assertArrayEquals(subject.toByteArray(Charsets.UTF_8), decoded.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun a_short_persian_subject_is_one_encoded_word() {
        val words = subject_words(mime_for("سلام", plain_body(greeting)))
        assertEquals(listOf("=?UTF-8?B?" + java.util.Base64.getEncoder().encodeToString("سلام".toByteArray(Charsets.UTF_8)) + "?="), words)
    }

    @Test
    fun quoted_printable_persian_split_across_a_soft_break_decodes_exactly() {
        val bytes = greeting.toByteArray(Charsets.UTF_8)
        val encoded = bytes.joinToString("") { "=%02X".format(it.toInt() and 0xff) }
        val split_inside_a_character = encoded.substring(0, 9) + "=\r\n" + encoded.substring(9)
        val raw = "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Transfer-Encoding: quoted-printable\r\n\r\n" +
            split_inside_a_character + "\r\n"
        assertEquals(greeting, MimeParser.parse(raw).text)
    }

    @Test
    fun the_reader_lays_out_each_plain_text_line_by_its_own_direction() {
        val html = build_plain_text_html(lines.joinToString("\n"))
        assertEquals(
            "<div style=\"white-space:pre-wrap;overflow-wrap:break-word;unicode-bidi:plaintext\" dir=\"auto\">" +
                lines.joinToString("<br>") + "</div>",
            html,
        )
    }

    @Test
    fun the_reader_sanitizer_keeps_the_direction_hints() {
        val sent = EmailHtmlSanitizer.sanitize(plain_body(greeting + "\n\nThanks"))
        assertTrue(sent, sent.contains("dir=\"auto\""))
        assertTrue(sent, sent.contains(greeting))
        val rich = EmailHtmlSanitizer.sanitize(BIDI_LINES_OPEN + greeting + "<br>Thanks</div>")
        assertTrue(rich, rich.contains("unicode-bidi:plaintext"))
        assertTrue(rich, rich.contains("dir=\"auto\""))
        val plain = build_plain_text_html(greeting + "\nThanks")
        val reader = EmailHtmlSanitizer.sanitize(plain)
        assertTrue(reader, reader.contains("unicode-bidi:plaintext"))
        assertTrue(reader, reader.contains("dir=\"auto\""))
    }
}
