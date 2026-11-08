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

package org.astermail.android.folders

import kotlinx.coroutines.runBlocking
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.VerifyFolderPasswordResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class folder_unlock_grant_test {
    private val folder = LabelItem(
        id = "id_a",
        label_token = "token_a",
        is_password_protected = true,
        password_set = true,
    )

    @Before
    fun before() {
        folder_lock_store.lock_all()
        folder_lock_store.set_folders(listOf(folder))
    }

    @After
    fun after() = folder_lock_store.lock_all()

    @Test
    fun new_lock_keeps_the_server_unlock_token() = runBlocking {
        val opened = open_folder_unlock_session("id_a") {
            VerifyFolderPasswordResponse(
                verified = true,
                encrypted_folder_key = "enc",
                folder_key_nonce = "nonce",
                unlock_token = "server_token",
            )
        }
        assertTrue(opened)
        assertEquals("server_token", folder_lock_store.unlock_token_for_id("id_a"))
        assertFalse(requires_unlock(folder))
    }

    @Test
    fun missing_token_leaves_the_folder_locked() = runBlocking {
        val opened = open_folder_unlock_session("id_a") {
            VerifyFolderPasswordResponse(verified = true)
        }
        assertFalse(opened)
        assertNull(folder_lock_store.unlock_token_for_id("id_a"))
        assertTrue(requires_unlock(folder))
    }

    @Test
    fun unverified_password_leaves_the_folder_locked() = runBlocking {
        val opened = open_folder_unlock_session("id_a") {
            VerifyFolderPasswordResponse(verified = false, unlock_token = "server_token")
        }
        assertFalse(opened)
        assertTrue(requires_unlock(folder))
    }

    @Test
    fun failed_verify_replaces_a_tokenless_session_with_a_lock() = runBlocking {
        folder_lock_store.mark_unlocked("id_a")
        val opened = open_folder_unlock_session("id_a") { error("offline") }
        assertFalse(opened)
        assertTrue(requires_unlock(folder))
    }
}
