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

import java.security.MessageDigest
import java.util.Base64
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeAttachmentExtractorTest {

    private val pdf_bytes = "%PDF-1.4\n%âãÏÓ\nbinary\u0000\u0001\u0002 body\n"
        .toByteArray(Charsets.ISO_8859_1)
    private val png_bytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d,
    )
    private val notes_text = "café = crème brûlée\r\nline two with = sign"

    private fun b64(bytes: ByteArray): String =
        Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(bytes)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val forwarded_tree =
        "Content-Type: multipart/mixed; boundary=\"outer\"; protected-headers=\"v1\"\r\n" +
            "Subject: Quarterly files\r\n" +
            "\r\n" +
            "--outer\r\n" +
            "Content-Type: multipart/alternative; boundary=\"alt\"\r\n" +
            "\r\n" +
            "--alt\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "See the attached files.\r\n" +
            "--alt\r\n" +
            "Content-Type: multipart/related; boundary=\"rel\"\r\n" +
            "\r\n" +
            "--rel\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "\r\n" +
            "<p>See the attached files.</p><img src=\"cid:chart@forward\">\r\n" +
            "--rel\r\n" +
            "Content-Type: image/png\r\n" +
            "Content-Transfer-Encoding: base64\r\n" +
            "Content-ID: <chart@forward>\r\n" +
            "Content-Disposition: inline\r\n" +
            "\r\n" +
            b64(png_bytes) + "\r\n" +
            "--rel--\r\n" +
            "--alt--\r\n" +
            "--outer\r\n" +
            "Content-Type: application/pdf; name=\"report.pdf\"\r\n" +
            "Content-Transfer-Encoding: base64\r\n" +
            "Content-Disposition: attachment; filename=\"report.pdf\"\r\n" +
            "\r\n" +
            b64(pdf_bytes) + "\r\n" +
            "--outer\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Disposition: attachment;\r\n" +
            " filename*=utf-8''r%C3%A9sum%C3%A9%20notes.txt\r\n" +
            "\r\n" +
            "caf=C3=A9 =3D cr=C3=A8me br=C3=BBl=C3=A9e\r\n" +
            "line two with =3D sign\r\n" +
            "\r\n" +
            "--outer\r\n" +
            "Content-Type: application/octet-stream; name=\"=?UTF-8?B?0L7RgtGH0ZHRgi5iaW4=?=\"\r\n" +
            "Content-Transfer-Encoding: base64\r\n" +
            "Content-Disposition: attachment\r\n" +
            "\r\n" +
            b64(byteArrayOf(1, 2, 3, 4)) + "\r\n" +
            "--outer\r\n" +
            "Content-Type: application/pgp-signature; name=\"signature.asc\"\r\n" +
            "\r\n" +
            "-----BEGIN PGP SIGNATURE-----\r\nabc\r\n-----END PGP SIGNATURE-----\r\n" +
            "--outer--\r\n"

    @After
    fun clear_store() {
        DecryptedMimeAttachments.clear()
    }

    @Test
    fun body_parser_alone_drops_every_attachment() {
        val parsed = MimeParser.parse(forwarded_tree)

        assertEquals("See the attached files.", parsed.text)
        assertFalse(parsed.text.orEmpty().contains("report.pdf"))
    }

    @Test
    fun extracts_nested_attachments_with_names_types_and_bytes() {
        val parts = MimeAttachmentExtractor.extract(forwarded_tree)

        assertEquals(
            listOf("chart.png", "report.pdf", "résumé notes.txt", "отчёт.bin"),
            parts.map { it.filename },
        )
        assertEquals(
            listOf("image/png", "application/pdf", "text/plain", "application/octet-stream"),
            parts.map { it.content_type },
        )
        assertEquals("chart@forward", parts[0].content_id)
        assertTrue(parts[0].is_inline)
        assertArrayEquals(png_bytes, parts[0].data)
        assertArrayEquals(pdf_bytes, parts[1].data)
        assertEquals(notes_text, String(parts[2].data, Charsets.UTF_8))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), parts[3].data)
    }

    @Test
    fun keeps_rfc2231_continuations_and_q_encoded_names() {
        val raw =
            "Content-Type: multipart/mixed; boundary=b\n\n" +
                "--b\n" +
                "Content-Type: text/plain\n\nbody\n" +
                "--b\n" +
                "Content-Type: application/zip\n" +
                "Content-Transfer-Encoding: base64\n" +
                "Content-Disposition: attachment; filename*0*=utf-8''%E6%97%A5%E6%9C%AC;\n" +
                " filename*1*=%E8%AA%9E; filename*2=\".zip\"\n\n" +
                "UEsDBA==\n" +
                "--b\n" +
                "Content-Type: text/csv; name=\"=?iso-8859-1?Q?d=E9j=E0_vu.csv?=\"\n" +
                "Content-Disposition: attachment\n\n" +
                "a,b\n" +
                "--b--\n"

        val parts = MimeAttachmentExtractor.extract(raw)

        assertEquals(listOf("日本語.zip", "déjà vu.csv"), parts.map { it.filename })
        assertArrayEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04), parts[0].data)
        assertEquals("a,b", String(parts[1].data, Charsets.UTF_8))
    }

    @Test
    fun quoted_printable_hard_breaks_become_crlf_after_lf_normalization() {
        val raw =
            "Content-Type: multipart/mixed; boundary=t\n\n" +
                "--t\n" +
                "Content-Type: text/plain\n\nbody\n" +
                "--t\n" +
                "Content-Type: text/plain; charset=utf-8\n" +
                "Content-Transfer-Encoding: quoted-printable\n" +
                "Content-Disposition: attachment; filename=\"notes.txt\"\n\n" +
                "caf=C3=A9 =3D cr=C3=A8me  \n" +
                "line two with a soft=\n" +
                " break\n" +
                "--t--\n"

        val parts = MimeAttachmentExtractor.extract(raw)

        assertEquals(
            "café = crème\r\nline two with a soft break",
            String(parts[0].data, Charsets.UTF_8),
        )
    }

    private fun quoted_printable_attachment(body: String): String =
        "Content-Type: multipart/mixed; boundary=t\r\n\r\n" +
            "--t\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Disposition: attachment; filename=\"notes.txt\"\r\n\r\n" +
            body + "\r\n" +
            "--t--\r\n"

    @Test(timeout = 2_000)
    fun a_long_space_run_in_a_quoted_printable_part_finishes_quickly() {
        val spaces = " ".repeat(80_000)
        val parts = MimeAttachmentExtractor.extract(quoted_printable_attachment("a" + spaces + "b"))
        assertEquals("a" + spaces + "b", String(parts[0].data, Charsets.UTF_8))
        val tabs = MimeAttachmentExtractor.extract(quoted_printable_attachment("a" + "\t ".repeat(40_000) + "\r\nb"))
        assertEquals("a\r\nb", String(tabs[0].data, Charsets.UTF_8))
    }

    @Test
    fun quoted_printable_trailing_space_is_removed_only_at_line_ends() {
        val parts = MimeAttachmentExtractor.extract(
            quoted_printable_attachment("one \t \r\ntwo  three\t\nfour \rfive"),
        )
        assertEquals("one\r\ntwo  three\r\nfour \rfive", String(parts[0].data, Charsets.UTF_8))
    }

    @Test
    fun plain_bodies_without_attachments_yield_nothing() {
        assertTrue(MimeAttachmentExtractor.extract("Hello there").isEmpty())
        assertTrue(
            MimeAttachmentExtractor.extract(
                "Content-Type: multipart/alternative; boundary=x\n\n--x\nContent-Type: text/plain\n\nhi\n" +
                    "--x\nContent-Type: text/html\n\n<p>hi</p>\n--x--\n",
            ).isEmpty(),
        )
    }

    @Test
    fun multi_megabyte_attachment_round_trips() {
        val big = ByteArray(3 * 1024 * 1024 + 7) { (it * 31 + 7).toByte() }
        val raw = "Content-Type: multipart/mixed; boundary=\"big\"\r\n\r\n" +
            "--big\r\nContent-Type: text/plain\r\n\r\nlarge file\r\n" +
            "--big\r\nContent-Type: application/octet-stream\r\nContent-Transfer-Encoding: base64\r\n" +
            "Content-Disposition: attachment; filename=\"large.bin\"\r\n\r\n" +
            b64(big) + "\r\n--big--\r\n"

        val parts = MimeAttachmentExtractor.extract(raw)

        assertEquals(1, parts.size)
        assertEquals(sha256(big), sha256(parts[0].data))
    }

    @Test
    fun store_exposes_attachments_for_list_download_and_merge() {
        DecryptedMimeAttachments.keep_from_plaintext("item-1", forwarded_tree)

        val listed = DecryptedMimeAttachments.attachments_for("item-1")
        assertEquals(4, listed.size)
        assertTrue(DecryptedMimeAttachments.has("item-1"))
        assertEquals("report.pdf", listed[1].filename)
        assertEquals(pdf_bytes.size.toLong(), listed[1].size_bytes)
        assertTrue(DecryptedMimeAttachments.is_local(listed[1].id))
        assertArrayEquals(pdf_bytes, DecryptedMimeAttachments.bytes_for(listed[1].id))
        assertEquals(listed[1], DecryptedMimeAttachments.attachment_for(listed[1].id))

        val server = org.astermail.android.ui.mail.MessageAttachment(
            id = "srv-1",
            filename = "server.txt",
            content_type = "text/plain",
            size_bytes = 3,
        )
        assertEquals(
            listOf("srv-1") + listed.map { it.id },
            DecryptedMimeAttachments.merge_into("item-1", listOf(server)).map { it.id },
        )
        assertEquals(listOf(server), DecryptedMimeAttachments.merge_into("other", listOf(server)))
        assertNull(DecryptedMimeAttachments.bytes_for("srv-1"))

        DecryptedMimeAttachments.clear()
        assertFalse(DecryptedMimeAttachments.has("item-1"))
    }

    @Test
    fun pgp_mime_message_decrypts_to_attachments_with_matching_hashes() {
        val passphrase = "throwaway test key".toCharArray()
        val key = PgpKeyGenerator.generate("Test Key", "test-key@example.invalid", passphrase)
        val armored = PgpEncryptor.encrypt_to_keys(forwarded_tree, listOf(key.armored_public_key))
        assertNotNull(armored)

        val plaintext = PgpDecryptor.decrypt(armored!!, key.armored_private_key, passphrase)
        assertNotNull(plaintext)
        DecryptedMimeAttachments.keep_from_plaintext("pgp-item", plaintext!!)

        val listed = DecryptedMimeAttachments.attachments_for("pgp-item")
        assertEquals(
            listOf("chart.png", "report.pdf", "résumé notes.txt", "отчёт.bin"),
            listed.map { it.filename },
        )
        assertEquals(sha256(pdf_bytes), sha256(DecryptedMimeAttachments.bytes_for(listed[1].id)!!))
        assertEquals(sha256(png_bytes), sha256(DecryptedMimeAttachments.bytes_for(listed[0].id)!!))
    }
}
