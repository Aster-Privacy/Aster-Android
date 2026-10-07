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

package org.astermail.android.share

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenedAttachmentCacheTest {

    private lateinit var cache_dir: File

    @Before
    fun set_up() {
        cache_dir = Files.createTempDirectory("opened_attachment_cache").toFile()
    }

    @After
    fun tear_down() {
        cache_dir.deleteRecursively()
    }

    private fun is_inside_private_dir(file: File): Boolean =
        file.canonicalPath.startsWith(opened_attachments_dir(cache_dir).canonicalPath + File.separator)

    @Test
    fun an_opened_attachment_is_written_inside_the_private_cache() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val file = write_opened_attachment(cache_dir, "report.pdf", bytes)
        assertTrue(is_inside_private_dir(file))
        assertEquals("report.pdf", file.name)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun a_hostile_name_cannot_leave_the_private_cache() {
        for (name in listOf("../../escape.txt", "..\\..\\escape.txt", "/sdcard/Download/escape.txt", "..", "")) {
            val file = write_opened_attachment(cache_dir, name, byteArrayOf(9))
            assertTrue("escaped with $name: ${file.path}", is_inside_private_dir(file))
        }
        assertFalse(File(cache_dir.parentFile, "escape.txt").exists())
    }

    @Test
    fun two_attachments_with_one_name_do_not_overwrite_each_other() {
        val first = write_opened_attachment(cache_dir, "a.txt", byteArrayOf(1))
        val second = write_opened_attachment(cache_dir, "a.txt", byteArrayOf(2))
        assertArrayEquals(byteArrayOf(1), first.readBytes())
        assertArrayEquals(byteArrayOf(2), second.readBytes())
    }

    @Test
    fun clearing_removes_every_opened_copy() {
        val first = write_opened_attachment(cache_dir, "a.txt", byteArrayOf(1))
        val second = write_opened_attachment(cache_dir, "b.txt", byteArrayOf(2))
        assertEquals(2, clear_opened_attachments(cache_dir))
        assertFalse(first.exists())
        assertFalse(second.exists())
        assertEquals(0, opened_attachments_dir(cache_dir).listFiles().orEmpty().size)
    }

    @Test
    fun clearing_an_absent_cache_is_harmless() {
        assertEquals(0, clear_opened_attachments(cache_dir))
    }

    @Test
    fun old_copies_are_pruned_when_a_new_one_is_written() {
        val start = 1_000_000_000_000L
        val old = write_opened_attachment(cache_dir, "old.txt", byteArrayOf(1), now_ms = start)
        val recent = write_opened_attachment(
            cache_dir,
            "recent.txt",
            byteArrayOf(2),
            now_ms = start + OPENED_ATTACHMENT_MAX_AGE_MS - 60_000L,
        )
        val fresh = write_opened_attachment(
            cache_dir,
            "fresh.txt",
            byteArrayOf(3),
            now_ms = start + OPENED_ATTACHMENT_MAX_AGE_MS + 1L,
        )
        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertTrue(fresh.exists())
    }
}
