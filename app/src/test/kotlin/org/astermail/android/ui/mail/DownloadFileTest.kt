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

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a second save with the same name keeps the first file`() {
        val dir = folder.newFolder("Download")
        val first = write_download_file(dir, "report.pdf", byteArrayOf(1))
        val second = write_download_file(dir, "report.pdf", byteArrayOf(2))
        assertEquals("report.pdf", first?.name)
        assertEquals("report (1).pdf", second?.name)
        assertArrayEquals(byteArrayOf(1), File(dir, "report.pdf").readBytes())
        assertArrayEquals(byteArrayOf(2), File(dir, "report (1).pdf").readBytes())
    }

    @Test
    fun `a file already in the folder is never overwritten`() {
        val dir = folder.newFolder("Download")
        File(dir, "notes.txt").writeText("mine")
        File(dir, "notes (1).txt").writeText("also mine")
        val saved = write_download_file(dir, "notes.txt", "attachment".toByteArray())
        assertEquals("notes (2).txt", saved?.name)
        assertEquals("mine", File(dir, "notes.txt").readText())
        assertEquals("also mine", File(dir, "notes (1).txt").readText())
        assertEquals("attachment", File(dir, "notes (2).txt").readText())
    }

    @Test
    fun `the counter goes at the end of a name without an extension`() {
        val dir = folder.newFolder("Download")
        write_download_file(dir, "README", byteArrayOf(1))
        write_download_file(dir, ".profile", byteArrayOf(1))
        assertEquals("README (1)", write_download_file(dir, "README", byteArrayOf(2))?.name)
        assertEquals(".profile (1)", write_download_file(dir, ".profile", byteArrayOf(2))?.name)
    }

    @Test
    fun `a name that leaves the folder is refused`() {
        val dir = folder.newFolder("Download")
        assertNull(write_download_file(dir, "../outside.txt", byteArrayOf(1)))
        assertFalse(File(dir.parentFile, "outside.txt").exists())
    }

    private fun utf8_size(value: String) = value.toByteArray(Charsets.UTF_8).size

    @Test
    fun `a long multibyte name stays within 255 bytes and keeps its extension`() {
        val name = sanitize_filename("\u6f22".repeat(150) + ".pdf")
        assertTrue(utf8_size(name) <= 255)
        assertTrue(name.endsWith(".pdf"))
        val dir = folder.newFolder("Download")
        assertEquals(name, write_download_file(dir, name, byteArrayOf(1))?.name)
    }

    @Test
    fun `a long name keeps room for the counter suffix and the extension`() {
        val dir = folder.newFolder("Download")
        val name = sanitize_filename("a".repeat(400) + ".pdf")
        val names = (0 until 3).map { write_download_file(dir, name, byteArrayOf(1))?.name }
        assertEquals(3, names.toSet().size)
        names.forEach {
            assertTrue(utf8_size(it!!) <= 255)
            assertTrue(it.endsWith(".pdf"))
        }
        assertTrue(names[2]!!.endsWith(" (2).pdf"))
    }

    @Test
    fun `truncation never splits a surrogate pair`() {
        val name = sanitize_filename("\uD83D\uDE00".repeat(100) + ".txt")
        assertTrue(utf8_size(name) <= 255)
        assertFalse(name.contains('\uFFFD'))
        assertEquals(name, String(name.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        assertTrue(name.endsWith(".txt"))
    }

    @Test
    fun `del c1 controls and bidi overrides are replaced`() {
        val name = sanitize_filename("a\u007Fb\u0085c\u009Fd\u202Ee\u2066f.txt")
        assertFalse(name.any { it == '\u007F' || it in '\u0080'..'\u009F' })
        assertFalse(name.any { it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' })
        assertTrue(name.endsWith(".txt"))
    }

    @Test
    fun `a failed write leaves no partial file`() {
        val dir = folder.newFolder("Download")
        val result = write_download_file(dir, "big.bin", byteArrayOf(1)) { target, _ ->
            target.writeBytes(byteArrayOf(9))
            throw java.io.IOException("disk full")
        }
        assertNull(result)
        assertEquals(0, dir.listFiles()?.size)
    }

    @Test
    fun `a share file can be created for a name of the maximum length`() {
        val cache = folder.newFolder("cache")
        val name = sanitize_filename("n".repeat(300) + ".pdf")
        assertTrue(utf8_size(name) in 240..255)
        val created = runCatching { write_share_file(cache, name, byteArrayOf(1, 2, 3)) }
        assertTrue("share file creation failed: " + created.exceptionOrNull(), created.isSuccess)
        val target = created.getOrThrow()
        assertTrue(target.isFile)
        assertEquals(name, target.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertTrue(target.canonicalPath.startsWith(File(cache, "shared_attachments").canonicalPath))
    }
}
