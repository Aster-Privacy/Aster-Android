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

import org.astermail.android.api.folder_unlock_request
import org.astermail.android.api.labels.LabelItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class folder_unlock_token_test {
    private fun label(id: String, token: String, protected: Boolean = true) = LabelItem(
        id = id,
        label_token = token,
        is_password_protected = protected,
        password_set = protected,
    )

    private fun request(path: String, parameters: Map<String, List<String>> = emptyMap()) =
        folder_unlock_request(method = "GET", path = path, parameters = parameters)

    @Before
    fun before() {
        folder_lock_store.lock_all()
        folder_lock_store.set_folders(
            listOf(
                label("id_a", "token_a"),
                label("id_b", "token_b"),
                label("id_c", "token_c", protected = false),
            ),
        )
    }

    @After
    fun after() = folder_lock_store.lock_all()

    @Test
    fun verify_response_token_is_kept_for_the_folder() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null, "enc_key", "nonce")
        assertEquals("unlock_a", folder_lock_store.unlock_token_for_id("id_a"))
        assertEquals("enc_key" to "nonce", folder_lock_store.folder_key_material("id_a"))
        assertNull(folder_lock_store.unlock_token_for_id("id_b"))
    }

    @Test
    fun expired_absolute_deadline_relocks_the_folder() {
        val past = java.time.Instant.now().minusSeconds(60).toString()
        folder_lock_store.mark_unlocked("id_a", "unlock_a", past)
        assertTrue(requires_unlock(label("id_a", "token_a")))
        assertNull(folder_lock_store.unlock_token_for_id("id_a"))
    }

    @Test
    fun folder_scoped_request_gets_the_matching_token() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null)
        assertEquals(
            "unlock_a",
            folder_lock_store.resolve_unlock_header(
                request("/api/mail/v1/messages", mapOf("label_token" to listOf("token_a"))),
            ),
        )
        assertEquals(
            "unlock_b",
            folder_lock_store.resolve_unlock_header(request("/api/mail/v1/labels/id_b")),
        )
    }

    @Test
    fun item_request_uses_the_recorded_folder_of_that_item() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null)
        folder_lock_store.note_item_folders("item_1", "thread_1", listOf("token_b"))
        assertEquals(
            "unlock_b",
            folder_lock_store.resolve_unlock_header(request("/api/mail/v1/messages/item_1")),
        )
        assertEquals(
            "unlock_b",
            folder_lock_store.resolve_unlock_header(
                request("/api/mail/v1/messages/threads/thread_1/messages"),
            ),
        )
    }

    @Test
    fun unindexed_item_with_two_folders_unlocked_still_carries_a_token() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null)
        val resolved = folder_lock_store.resolve_unlock_header(
            request("/api/mail/v1/messages/never_listed_item"),
        )
        assertTrue(resolved == "unlock_a" || resolved == "unlock_b")
    }

    @Test
    fun unindexed_thread_with_two_folders_unlocked_still_carries_a_token() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null)
        val resolved = folder_lock_store.resolve_unlock_header(
            request("/api/mail/v1/messages/threads/never_listed_thread/messages"),
        )
        assertTrue(resolved == "unlock_a" || resolved == "unlock_b")
    }

    @Test
    fun attachment_with_two_folders_unlocked_still_carries_a_token() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null)
        val resolved = folder_lock_store.resolve_unlock_header(
            request("/api/mail/v1/attachments/some_attachment_id"),
        )
        assertTrue(resolved == "unlock_a" || resolved == "unlock_b")
    }

    @Test
    fun unscoped_request_never_carries_an_unlock_token() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        assertNull(folder_lock_store.resolve_unlock_header(request("/api/mail/v1/messages")))
        assertNull(folder_lock_store.resolve_unlock_header(request("/api/mail/v1/messages/stats")))
    }

    @Test
    fun locked_folder_tokens_track_unlock_state() {
        assertEquals(setOf("token_a", "token_b"), folder_lock_store.locked_folder_tokens())
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        assertEquals(setOf("token_b"), folder_lock_store.locked_folder_tokens())
    }

    @Test
    fun on_leave_mode_locks_the_folder_you_navigate_away_from() {
        folder_lock_store.set_lock_mode(folder_lock_mode_on_leave)
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.set_active_folder_token("token_a")
        folder_lock_store.set_active_folder_token("inbox")
        assertTrue(requires_unlock(label("id_a", "token_a")))
        folder_lock_store.set_lock_mode(folder_lock_mode_session)
    }

    @Test
    fun session_mode_keeps_the_folder_unlocked_after_navigating_away() {
        folder_lock_store.set_lock_mode(folder_lock_mode_session)
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.set_active_folder_token("token_a")
        folder_lock_store.set_active_folder_token("inbox")
        assertEquals("unlock_a", folder_lock_store.unlock_token_for_id("id_a"))
    }
}
