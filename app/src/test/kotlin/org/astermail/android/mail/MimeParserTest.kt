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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeParserTest {

    private val transfer_encoding_first =
        "Content-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Type: text/plain; charset=UTF-8; format=flowed\r\n" +
            "\r\n" +
            "This is a test email sent from sourcehut to confirm that PGP is working as =\r\n" +
            "you\r\n" +
            "expect. This email is signed with this key:\r\n"

    @Test
    fun detects_mime_when_content_type_is_not_the_first_header() {
        assertTrue(MimeParser.looks_like_mime(transfer_encoding_first))
    }

    @Test
    fun decodes_body_when_content_type_is_not_the_first_header() {
        val result = MimeParser.parse(transfer_encoding_first)

        assertNull(result.html)
        assertEquals(
            "This is a test email sent from sourcehut to confirm that PGP is working as you\n" +
                "expect. This email is signed with this key:",
            result.text,
        )
    }

    @Test
    fun output_has_no_carriage_returns() {
        val result = MimeParser.parse(transfer_encoding_first)

        assertFalse(result.text!!.contains("\r"))
    }

    @Test
    fun parses_multipart_signed_with_leading_transfer_encoding() {
        val raw = "MIME-Version: 1.0\r\n" +
            "Content-Type: multipart/signed; micalg=pgp-sha256;\r\n" +
            " protocol=\"application/pgp-signature\"; boundary=\"aster-1\"\r\n" +
            "\r\n" +
            "--aster-1\r\n" +
            "Content-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "\r\n" +
            "Hello =E2=98=95 world\r\n" +
            "--aster-1\r\n" +
            "Content-Type: application/pgp-signature; name=\"signature.asc\"\r\n" +
            "\r\n" +
            "-----BEGIN PGP SIGNATURE-----\r\n" +
            "-----END PGP SIGNATURE-----\r\n" +
            "--aster-1--\r\n"

        val result = MimeParser.parse(raw)

        assertEquals("Hello ☕ world", result.text)
        assertNull(result.html)
    }

    @Test
    fun prefers_html_part_when_present() {
        val raw = "Content-Type: multipart/alternative; boundary=\"b\"\n" +
            "\n" +
            "--b\n" +
            "Content-Type: text/plain\n" +
            "\n" +
            "plain body\n" +
            "--b\n" +
            "Content-Type: text/html\n" +
            "\n" +
            "<p>html body</p>\n" +
            "--b--\n"

        val result = MimeParser.parse(raw)

        assertEquals("plain body", result.text)
        assertEquals("<p>html body</p>", result.html)
    }

    @Test
    fun keeps_part_without_content_type_as_plain_text() {
        val raw = "Content-Type: multipart/mixed; boundary=\"b\"\n" +
            "\n" +
            "--b\n" +
            "Content-Transfer-Encoding: quoted-printable\n" +
            "\n" +
            "defaulted =74=6F text/plain\n" +
            "--b--\n"

        val result = MimeParser.parse(raw)

        assertEquals("defaulted to text/plain", result.text)
    }

    @Test
    fun skips_attachment_parts() {
        val raw = "Content-Type: multipart/mixed; boundary=\"b\"\n" +
            "\n" +
            "--b\n" +
            "Content-Type: text/plain\n" +
            "Content-Disposition: attachment; filename=\"notes.txt\"\n" +
            "\n" +
            "attachment payload\n" +
            "--b\n" +
            "Content-Type: text/plain\n" +
            "\n" +
            "real body\n" +
            "--b--\n"

        assertEquals("real body", MimeParser.parse(raw).text)
    }

    @Test
    fun ignores_plain_text_that_only_mentions_headers() {
        assertFalse(MimeParser.looks_like_mime("Hello, see the Content-Type: header docs"))
        assertFalse(MimeParser.looks_like_mime("Content-Type: whatever I want\nnot a mime body"))
        assertFalse(MimeParser.looks_like_mime("Subject: hi\nFrom: someone\n\nbody"))
    }

    @Test
    fun handles_mime_without_blank_line_separator() {
        val raw = "Content-Type: text/plain; charset=UTF-8\nbody line one\nbody line two"

        val result = MimeParser.parse(raw)

        assertEquals("body line one\nbody line two", result.text)
    }

    @Test
    fun leaves_undecodable_equals_signs_alone() {
        val raw = "Content-Type: text/plain\n" +
            "Content-Transfer-Encoding: quoted-printable\n" +
            "\n" +
            "price = 5 dollars"

        assertEquals("price = 5 dollars", MimeParser.parse(raw).text)
    }
}
