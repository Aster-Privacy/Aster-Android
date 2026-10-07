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

package org.astermail.android.auth

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DecryptedFileWipeTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun put(root: File, path: String): File {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        file.writeText("plaintext")
        return file
    }

    @Test
    fun sign_out_wipe_removes_every_decrypted_cache() {
        val cache = temp.newFolder("cache")
        val leftovers = listOf(
            put(cache, "shared_attachments/1_invoice.pdf"),
            put(cache, "opened_attachments/abc/contract.pdf"),
            put(cache, "draft_attachments/0_draft/0_photo.jpg"),
            put(cache, "email_img_cache/journal"),
            put(cache, "email_img_cache/0a1b.1"),
            put(cache, "exports/aster_export_20261006.zip"),
            put(cache, "exports/aster-recovery-codes.txt"),
        )
        wipe_decrypted_caches(cache)
        leftovers.forEach { assertFalse(it.path, it.exists()) }
    }

    @Test
    fun sign_out_wipe_leaves_unrelated_cache_files() {
        val cache = temp.newFolder("cache")
        val other = put(cache, "image_cache/avatar.png")
        put(cache, "exports/aster_export_1.zip")
        wipe_decrypted_caches(cache)
        assertTrue(other.exists())
    }

    @Test
    fun sign_out_wipe_tolerates_missing_directories() {
        assertEquals(0, wipe_decrypted_caches(temp.newFolder("empty")))
    }

    @Test
    fun outbox_wipe_removes_only_the_listed_pending_sends() {
        val files = temp.newFolder("files")
        val mine = put(files, "outbox_attachments/send-1.json")
        val also_mine = put(files, "outbox_attachments/send-2.json")
        val theirs = put(files, "outbox_attachments/send-9.json")
        assertEquals(2, wipe_outbox_attachments(files, listOf("send-1", "send-2", "send-missing")))
        assertFalse(mine.exists())
        assertFalse(also_mine.exists())
        assertTrue(theirs.exists())
    }

    @Test
    fun outbox_wipe_without_an_account_removes_everything() {
        val files = temp.newFolder("files")
        val first = put(files, "outbox_attachments/send-1.json")
        val second = put(files, "outbox_attachments/send-9.json")
        wipe_outbox_attachments(files, null)
        assertFalse(first.exists())
        assertFalse(second.exists())
    }

    @Test
    fun outbox_wipe_ignores_ids_that_leave_the_directory() {
        val files = temp.newFolder("files")
        val outside = put(files, "secret.json")
        put(files, "outbox_attachments/keep.json")
        assertEquals(0, wipe_outbox_attachments(files, listOf("../secret", "", "a/b", "a\\b")))
        assertTrue(outside.exists())
    }
}
