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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class folder_lock_idle_test {
    private val minute_ms = 60 * 1000L
    private var now = 1_000_000_000L

    private val folder_a = label("id_a", "token_a")
    private val folder_b = label("id_b", "token_b")
    private val labels = listOf(folder_a, folder_b)

    private fun label(id: String, token: String) = LabelItem(
        id = id,
        label_token = token,
        is_password_protected = true,
        password_set = true,
    )

    private fun request(path: String, parameters: Map<String, List<String>> = emptyMap()) =
        folder_unlock_request(method = "GET", path = path, parameters = parameters)

    private fun advance(minutes: Long) {
        now += minutes * minute_ms
    }

    @Before
    fun before() {
        folder_lock_store.reset()
        folder_lock_store.lock_all()
        folder_lock_store.set_lock_mode(folder_lock_mode_session)
        folder_lock_store.now_ms = { now }
        folder_lock_store.set_folders(labels)
        folder_lock_store.set_active_folder_token("inbox")
    }

    @After
    fun after() {
        folder_lock_store.reset()
        folder_lock_store.lock_all()
        folder_lock_store.now_ms = System::currentTimeMillis
    }

    @Test
    fun checking_the_lock_state_does_not_extend_the_idle_timeout() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        repeat(29) {
            advance(1)
            assertFalse(requires_unlock(folder_a))
            assertFalse("token_a" in protected_folder_tokens(labels))
            assertNull(locked_active_folder(labels, "inbox"))
            assertTrue(folder_lock_store.is_unlocked("id_a"))
        }
        advance(2)
        assertTrue(requires_unlock(folder_a))
        assertTrue("token_a" in protected_folder_tokens(labels))
        assertNull(folder_lock_store.unlock_token_for_id("id_a"))
    }

    @Test
    fun loading_another_folder_does_not_extend_the_idle_timeout() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.mark_unlocked("id_b", "unlock_b", null)
        folder_lock_store.set_active_folder_token("token_b")
        repeat(6) {
            advance(5)
            folder_lock_store.resolve_unlock_header(
                request("/api/mail/v1/messages", mapOf("label_token" to listOf("token_b"))),
            )
            folder_lock_store.resolve_unlock_header(
                request("/api/mail/v1/messages", mapOf("label_token" to listOf("token_a"))),
            )
            folder_lock_store.resolve_unlock_header(request("/api/mail/v1/labels/id_a"))
        }
        advance(1)
        assertTrue(requires_unlock(folder_a))
        assertFalse(requires_unlock(folder_b))
    }

    @Test
    fun opening_the_folder_extends_the_idle_timeout() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        advance(25)
        folder_lock_store.set_active_folder_token("token_a")
        folder_lock_store.set_active_folder_token("inbox")
        advance(25)
        assertFalse(requires_unlock(folder_a))
        advance(6)
        assertTrue(requires_unlock(folder_a))
    }

    @Test
    fun loading_the_open_folder_extends_the_idle_timeout() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.set_active_folder_token("token_a")
        repeat(4) {
            advance(20)
            assertEquals(
                "unlock_a",
                folder_lock_store.resolve_unlock_header(
                    request("/api/mail/v1/messages", mapOf("label_token" to listOf("token_a"))),
                ),
            )
        }
        assertFalse(requires_unlock(folder_a))
        advance(31)
        assertTrue(requires_unlock(folder_a))
    }

    @Test
    fun opening_a_message_in_the_open_folder_extends_the_idle_timeout() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        folder_lock_store.note_item_folders("item_1", "thread_1", listOf("token_a"))
        folder_lock_store.set_active_folder_token("token_a")
        advance(20)
        assertEquals(
            "unlock_a",
            folder_lock_store.resolve_unlock_header(request("/api/mail/v1/messages/item_1")),
        )
        advance(20)
        assertEquals(
            "unlock_a",
            folder_lock_store.resolve_unlock_header(request("/api/mail/v1/threads/thread_1")),
        )
        advance(20)
        assertFalse(requires_unlock(folder_a))
    }

    @Test
    fun the_absolute_expiry_still_applies_while_the_folder_is_in_use() {
        val expiry = java.time.Instant.ofEpochMilli(now + 10 * minute_ms).toString()
        folder_lock_store.mark_unlocked("id_a", "unlock_a", expiry)
        folder_lock_store.set_active_folder_token("token_a")
        advance(5)
        folder_lock_store.resolve_unlock_header(request("/api/mail/v1/labels/id_a"))
        assertFalse(requires_unlock(folder_a))
        advance(6)
        assertTrue(requires_unlock(folder_a))
        assertNull(folder_lock_store.resolve_unlock_header(request("/api/mail/v1/labels/id_a")))
    }

    @Test
    fun an_expired_folder_is_not_revived_by_opening_it() {
        folder_lock_store.mark_unlocked("id_a", "unlock_a", null)
        advance(31)
        folder_lock_store.set_active_folder_token("token_a")
        assertTrue(requires_unlock(folder_a))
        assertEquals(folder_a, locked_active_folder(labels, "token_a"))
    }
}
