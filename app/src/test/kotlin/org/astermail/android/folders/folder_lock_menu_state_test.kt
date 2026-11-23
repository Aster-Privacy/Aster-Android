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

import org.astermail.android.api.labels.LabelItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class folder_lock_menu_state_test {
    private val folder_a = label("id_a", "token_a")
    private val folder_b = label("id_b", "token_b")
    private val labels = listOf(folder_a, folder_b)
    private val revoked = mutableListOf<Pair<String, String?>>()

    private fun label(id: String, token: String) = LabelItem(
        id = id,
        label_token = token,
        is_password_protected = true,
        password_set = true,
    )

    @Before
    fun before() {
        folder_lock_store.reset()
        folder_lock_store.lock_all()
        folder_lock_store.set_lock_mode(folder_lock_mode_session)
        folder_lock_store.set_folders(labels)
        folder_lock_store.set_active_folder_token("inbox")
        revoked.clear()
        folder_lock_store.register_remote_revoker { folder_id, token -> revoked.add(folder_id to token) }
    }

    @After
    fun after() {
        folder_lock_store.register_remote_revoker { _, _ -> }
        folder_lock_store.reset()
        folder_lock_store.lock_all()
        folder_lock_store.set_lock_mode(folder_lock_mode_session)
    }

    @Test
    fun locking_one_folder_keeps_the_other_unlocked() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null, "key_a", "nonce_a")
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null, "key_b", "nonce_b")
        folder_lock_store.lock("id_a")
        assertTrue(requires_unlock(folder_a))
        assertFalse(requires_unlock(folder_b))
        assertEquals(null, folder_lock_store.folder_key_material("id_a"))
        assertEquals("key_b" to "nonce_b", folder_lock_store.folder_key_material("id_b"))
        assertEquals(listOf("id_a" to "unlock_a"), revoked)
        assertEquals(setOf("token_a"), folder_lock_store.locked_folder_tokens())
        assertEquals(1, folder_lock_store.unlocked_folder_count())
    }

    @Test
    fun lock_all_locks_every_unlocked_folder_and_reports_the_count() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null)
        assertEquals(2, folder_lock_store.unlocked_folder_count())
        val start = folder_lock_store.revision.value
        assertEquals(2, folder_lock_store.lock_unlocked_folders())
        assertTrue(folder_lock_store.revision.value > start)
        assertTrue(requires_unlock(folder_a))
        assertTrue(requires_unlock(folder_b))
        assertEquals(0, folder_lock_store.unlocked_folder_count())
        assertEquals(setOf("id_a", "id_b"), revoked.map { it.first }.toSet())
        assertEquals(setOf("token_a", "token_b"), folder_lock_store.locked_folder_tokens())
    }

    @Test
    fun lock_all_with_nothing_unlocked_reports_zero() {
        assertEquals(0, folder_lock_store.lock_unlocked_folders())
        assertTrue(revoked.isEmpty())
    }

    @Test
    fun unlocking_again_after_lock_needs_no_reload() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.lock("id_a")
        folder_lock_store.mark_unlocked("id_a", "unlock_a2", null)
        assertFalse(requires_unlock(folder_a))
        assertEquals("unlock_a2", folder_lock_store.unlock_token_for_id("id_a"))
    }

    @Test
    fun leaving_a_folder_in_lock_on_leave_mode_locks_only_that_folder() {
        folder_lock_store.set_lock_mode(folder_lock_mode_on_leave)
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null)
        folder_lock_store.set_active_folder_token("token_a")
        folder_lock_store.set_active_folder_token("token_b")
        assertTrue(requires_unlock(folder_a))
        assertFalse(requires_unlock(folder_b))
        folder_lock_store.set_active_folder_token("inbox")
        assertTrue(requires_unlock(folder_b))
    }

    @Test
    fun session_mode_keeps_folders_unlocked_while_navigating() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.set_active_folder_token("token_a")
        folder_lock_store.set_active_folder_token("inbox")
        assertFalse(requires_unlock(folder_a))
    }

    @Test
    fun sign_out_clears_every_unlock_and_key() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null, "key_a", "nonce_a")
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null, "key_b", "nonce_b")
        folder_lock_store.lock_all()
        assertEquals(0, folder_lock_store.unlocked_folder_count())
        assertEquals(null, folder_lock_store.folder_key_material("id_a"))
        assertEquals(null, folder_lock_store.folder_key_material("id_b"))
        assertEquals(null, folder_lock_store.unlock_token_for_id("id_b"))
    }

    @Test
    fun an_open_message_is_reported_locked_once_its_folder_locks() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null)
        folder_lock_store.note_item_folders("item_a", "thread_a", listOf("token_a"))
        folder_lock_store.note_item_folders("item_b", "thread_b", listOf("token_b"))
        assertFalse(folder_lock_store.is_routed_to_locked_folder("item_a", "thread_a"))
        folder_lock_store.lock("id_a")
        assertTrue(folder_lock_store.is_routed_to_locked_folder("item_a", null))
        assertTrue(folder_lock_store.is_routed_to_locked_folder(null, "thread_a"))
        assertFalse(folder_lock_store.is_routed_to_locked_folder("item_b", "thread_b"))
        assertFalse(folder_lock_store.is_routed_to_locked_folder("unknown", null))
        assertFalse(is_thread_in_locked_folder(null, "item_b"))
        assertTrue(is_thread_in_locked_folder(null, "item_a"))
    }
}
