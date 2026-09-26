// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PdfDocumentLoaderTest {

    @Test
    fun `pdf content types are recognized`() {
        assertTrue(is_pdf_attachment("application/pdf", "report.pdf"))
        assertTrue(is_pdf_attachment("Application/PDF; name=report.pdf", "report"))
        assertTrue(is_pdf_attachment("application/x-pdf", "report"))
    }

    @Test
    fun `a generic content type falls back to the file name`() {
        assertTrue(is_pdf_attachment("application/octet-stream", "Report.PDF"))
        assertTrue(is_pdf_attachment("", " invoice.pdf "))
        assertFalse(is_pdf_attachment("application/octet-stream", "report.pdf.exe"))
    }

    @Test
    fun `a specific non pdf content type is never treated as pdf`() {
        assertFalse(is_pdf_attachment("text/html", "phish.pdf"))
        assertFalse(is_pdf_attachment("image/png", "scan.pdf"))
    }

    @Test
    fun `pdf magic is found near the start`() {
        assertTrue(looks_like_pdf("%PDF-1.7\n".toByteArray()))
        assertTrue(looks_like_pdf(ByteArray(100) + "%PDF-2.0".toByteArray()))
    }

    @Test
    fun `bytes without pdf magic are rejected`() {
        assertFalse(looks_like_pdf(ByteArray(0)))
        assertFalse(looks_like_pdf("%PDF".toByteArray()))
        assertFalse(looks_like_pdf("<html>%PD F-1.7".toByteArray()))
        assertFalse(looks_like_pdf(ByteArray(1024) + "%PDF-1.7".toByteArray()))
    }

    @Test
    fun `password length is bounded`() {
        assertFalse(is_pdf_password_acceptable(""))
        assertTrue(is_pdf_password_acceptable("pässwörd-密码"))
        assertTrue(is_pdf_password_acceptable("a".repeat(MAX_PDF_PASSWORD_LENGTH)))
        assertFalse(is_pdf_password_acceptable("a".repeat(MAX_PDF_PASSWORD_LENGTH + 1)))
    }

    @Test
    fun `a security exception without a password asks for one`() {
        assertEquals(
            pdf_open_failure.password_required,
            classify_pdf_open_error(SecurityException(), null, supports_password = true),
        )
        assertEquals(
            pdf_open_failure.password_required,
            classify_pdf_open_error(SecurityException(), "", supports_password = true),
        )
    }

    @Test
    fun `a security exception with a password is an incorrect password`() {
        assertEquals(
            pdf_open_failure.password_incorrect,
            classify_pdf_open_error(SecurityException(), "wrong", supports_password = true),
        )
    }

    @Test
    fun `a locked pdf on an unsupported device falls back`() {
        assertEquals(
            pdf_open_failure.password_unsupported,
            classify_pdf_open_error(SecurityException(), null, supports_password = false),
        )
    }

    @Test
    fun `other errors are unreadable`() {
        assertEquals(
            pdf_open_failure.unreadable,
            classify_pdf_open_error(IOException("corrupt"), "pw", supports_password = true),
        )
        assertEquals(
            pdf_open_failure.unreadable,
            classify_pdf_open_error(IllegalStateException(), null, supports_password = false),
        )
    }

    @Test
    fun `page bitmap keeps the page aspect ratio`() {
        assertEquals(pdf_page_size(1000, 1294), pdf_page_bitmap_size(612, 792, 1000))
        assertEquals(pdf_page_size(800, 400), pdf_page_bitmap_size(200, 100, 800))
    }

    @Test
    fun `page bitmap width is capped`() {
        assertEquals(pdf_page_size(MAX_PDF_PAGE_WIDTH_PX, MAX_PDF_PAGE_WIDTH_PX), pdf_page_bitmap_size(100, 100, 50_000))
    }

    @Test
    fun `a very tall page is capped by height`() {
        val size = pdf_page_bitmap_size(100, 100_000, 1000)
        assertEquals(MAX_PDF_PAGE_HEIGHT_PX, size.height)
        assertEquals(4, size.width)
    }

    @Test
    fun `degenerate page sizes never produce an empty bitmap`() {
        val size = pdf_page_bitmap_size(0, 0, 0)
        assertTrue(size.width >= 1 && size.height >= 1)
        val sliver = pdf_page_bitmap_size(100_000, 1, 1)
        assertTrue(sliver.width >= 1 && sliver.height >= 1)
    }
}
