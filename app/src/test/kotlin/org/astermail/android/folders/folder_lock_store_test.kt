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

class folder_lock_store_test {
    private fun label(
        id: String,
        token: String,
        protected: Boolean = true,
        password_set: Boolean = true,
    ) = LabelItem(
        id = id,
        label_token = token,
        is_password_protected = protected,
        password_set = password_set,
    )

    @Before
    fun reset_before() = folder_lock_store.lock_all()

    @After
    fun reset_after() = folder_lock_store.lock_all()

    @Test
    fun protected_folder_starts_locked() {
        assertTrue(requires_unlock(label("a", "token_a")))
    }

    @Test
    fun folder_without_password_is_not_protected() {
        assertFalse(is_folder_protected(label("a", "token_a", password_set = false)))
        assertFalse(is_folder_protected(label("a", "token_a", protected = false)))
        assertFalse(requires_unlock(label("a", "token_a", password_set = false)))
    }

    @Test
    fun mark_unlocked_only_unlocks_that_folder() {
        folder_lock_store.mark_unlocked("a")
        assertFalse(requires_unlock(label("a", "token_a")))
        assertTrue(requires_unlock(label("b", "token_b")))
    }

    @Test
    fun lock_relocks_single_folder() {
        folder_lock_store.mark_unlocked("a")
        folder_lock_store.lock("a")
        assertTrue(requires_unlock(label("a", "token_a")))
    }

    @Test
    fun lock_all_relocks_every_folder() {
        folder_lock_store.mark_unlocked("a")
        folder_lock_store.mark_unlocked("b")
        folder_lock_store.lock_all()
        assertTrue(requires_unlock(label("a", "token_a")))
        assertTrue(requires_unlock(label("b", "token_b")))
    }

    @Test
    fun protected_tokens_exclude_unlocked_folders() {
        val labels = listOf(
            label("a", "token_a"),
            label("b", "token_b"),
            label("c", "token_c", protected = false),
        )
        assertEquals(setOf("token_a", "token_b"), protected_folder_tokens(labels))
        folder_lock_store.mark_unlocked("a")
        assertEquals(setOf("token_b"), protected_folder_tokens(labels))
    }

    @Test
    fun revision_changes_on_lock_state_change() {
        val start = folder_lock_store.revision.value
        folder_lock_store.mark_unlocked("a")
        assertTrue(folder_lock_store.revision.value > start)
        val after_unlock = folder_lock_store.revision.value
        folder_lock_store.lock_all()
        assertTrue(folder_lock_store.revision.value > after_unlock)
    }
}
