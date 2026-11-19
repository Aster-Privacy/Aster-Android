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
import org.astermail.android.ui.mail.write_share_file
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShareImportTargetTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val now = 10L * 24 * 60 * 60 * 1000

    private fun age(file: File, at: Long) {
        file.setLastModified(at)
        file.parentFile?.setLastModified(at)
    }

    @Test
    fun an_incoming_file_with_a_long_multibyte_name_can_be_written() {
        val cache = folder.newFolder("cache")
        val target = share_import_target(cache, "é".repeat(119) + ".pdf", now)
        assertNotNull(target)
        val written = runCatching { target!!.writeBytes(byteArrayOf(4, 5)) }
        assertTrue("incoming file could not be written: " + written.exceptionOrNull(), written.isSuccess)
        assertTrue(target!!.name.endsWith(".pdf"))
        assertTrue(target.name.toByteArray(Charsets.UTF_8).size <= 255)
        assertArrayEquals(byteArrayOf(4, 5), target.readBytes())
    }

    @Test
    fun sharing_a_downloaded_attachment_keeps_a_recent_incoming_file() {
        val cache = folder.newFolder("cache")
        val incoming = share_import_target(cache, "draft.pdf", now - 2L * 60 * 60 * 1000)!!
        incoming.writeBytes(byteArrayOf(1))
        age(incoming, now - 2L * 60 * 60 * 1000)
        write_share_file(cache, "other.pdf", byteArrayOf(2), now_ms = now)
        assertTrue("incoming file was removed by the download share prune", incoming.exists())
    }

    @Test
    fun incoming_files_older_than_a_day_are_removed_on_the_next_import() {
        val cache = folder.newFolder("cache")
        val old = share_import_target(cache, "old.pdf", now - 25L * 60 * 60 * 1000)!!
        old.writeBytes(byteArrayOf(1))
        age(old, now - 25L * 60 * 60 * 1000)
        release_share_import_holders(listOf(old.parentFile!!.name))
        val fresh = share_import_target(cache, "new.pdf", now)!!
        fresh.writeBytes(byteArrayOf(2))
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun an_incoming_file_still_attached_to_a_draft_survives_the_daily_prune() {
        val cache = folder.newFolder("cache")
        val attached = share_import_target(cache, "attached.pdf", now - 30L * 60 * 60 * 1000)!!
        attached.writeBytes(byteArrayOf(7))
        age(attached, now - 30L * 60 * 60 * 1000)
        share_import_target(cache, "next.pdf", now)!!.writeBytes(byteArrayOf(8))
        assertTrue("incoming file still attached to a draft was pruned", attached.exists())
    }

    @Test
    fun a_released_shared_file_uri_lets_the_daily_prune_remove_it() {
        val cache = folder.newFolder("cache")
        val attached = share_import_target(cache, "attached.pdf", now - 30L * 60 * 60 * 1000)!!
        attached.writeBytes(byteArrayOf(7))
        age(attached, now - 30L * 60 * 60 * 1000)
        val holder = share_import_holder_name(
            listOf("shared_attachments", "imports", attached.parentFile!!.name, attached.name),
        )
        assertEquals(attached.parentFile!!.name, holder)
        release_share_import_holders(listOf(holder!!))
        share_import_target(cache, "next.pdf", now)!!.writeBytes(byteArrayOf(8))
        assertFalse(attached.exists())
    }

    @Test
    fun only_paths_inside_the_import_directory_name_a_holder() {
        assertNull(share_import_holder_name(listOf("shared_attachments", "abc", "x.pdf")))
        assertNull(share_import_holder_name(listOf("shared_attachments", "other", "abc", "x.pdf")))
        assertNull(share_import_holder_name(listOf("files", "imports", "abc", "x.pdf")))
    }
}
