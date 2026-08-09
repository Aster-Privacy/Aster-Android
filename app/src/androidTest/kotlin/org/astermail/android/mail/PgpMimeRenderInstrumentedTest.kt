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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PgpMimeRenderInstrumentedTest {

    private val sourcehut_body =
        "Content-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Type: text/plain; charset=UTF-8; format=flowed\r\n" +
            "\r\n" +
            "This is a test email sent from sourcehut to confirm that PGP is working as =\r\n" +
            "you \r\n" +
            "expect. This email is signed with this key:\r\n" +
            "\r\n" +
            "447B 69E4 B34B E90B C829 A0E9 6597 04D1 A38A 93AE\r\n" +
            "\r\n" +
            "You may update your PGP settings here:\r\n" +
            "\r\n" +
            "https://meta.sr.ht/privacy\r\n"

    @Test
    fun renders_pgp_mime_body_without_raw_headers_on_device() {
        assertTrue(MimeParser.looks_like_mime(sourcehut_body))

        val parsed = MimeParser.parse(sourcehut_body)
        val text = parsed.text ?: ""

        assertFalse(text.contains("Content-Transfer-Encoding"))
        assertFalse(text.contains("Content-Type"))
        assertFalse(text.contains("\r"))
        assertTrue(text.startsWith("This is a test email sent from sourcehut to confirm that PGP is working as you"))
        assertTrue(text.contains("447B 69E4 B34B E90B C829 A0E9 6597 04D1 A38A 93AE"))

        val html = build_plain_text_html(text)

        assertFalse(html.contains("Content-Transfer-Encoding"))
        assertFalse(html.contains("<br><br><br>"))
        assertTrue(html.contains("<a href=\"https://meta.sr.ht/privacy\">https://meta.sr.ht/privacy</a>"))
    }

    @Test
    fun decodes_base64_pgp_mime_body_on_device() {
        val payload = android.util.Base64.encodeToString(
            "Café ☕ body from an external sender".toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        val raw = "Content-Transfer-Encoding: base64\r\n" +
            "Content-Type: text/plain; charset=UTF-8\r\n" +
            "\r\n" +
            payload + "\r\n"

        assertEquals("Café ☕ body from an external sender", MimeParser.parse(raw).text)
    }

    @Test
    fun decodes_multipart_signed_body_on_device() {
        val raw = "MIME-Version: 1.0\r\n" +
            "Content-Type: multipart/signed; micalg=pgp-sha256;\r\n" +
            " protocol=\"application/pgp-signature\"; boundary=\"sig-1\"\r\n" +
            "\r\n" +
            "--sig-1\r\n" +
            "Content-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Type: text/plain; charset=UTF-8; format=flowed\r\n" +
            "\r\n" +
            "Signed message body =E2=98=95\r\n" +
            "--sig-1\r\n" +
            "Content-Type: application/pgp-signature; name=\"signature.asc\"\r\n" +
            "\r\n" +
            "-----BEGIN PGP SIGNATURE-----\r\n" +
            "-----END PGP SIGNATURE-----\r\n" +
            "--sig-1--\r\n"

        val parsed = MimeParser.parse(raw)

        assertEquals("Signed message body ☕", parsed.text)
        assertFalse(build_plain_text_html(parsed.text ?: "").contains("BEGIN PGP SIGNATURE"))
    }

    @Test
    fun decodes_windows_1252_body_on_device() {
        val raw = "Content-Type: text/plain; charset=windows-1252\r\n" +
            "Content-Transfer-Encoding: quoted-printable\r\n" +
            "\r\n" +
            "caf=E9 na=EFve\r\n"

        assertEquals("café naïve", MimeParser.parse(raw).text)
    }
}
