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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FolderLockGuardTest {
    private val protected_folder = LabelItem(
        id = "label_protected",
        label_token = "token_protected",
        encrypted_name = "Private",
        is_password_protected = true,
        password_set = true,
    )
    private val open_folder = LabelItem(
        id = "label_open",
        label_token = "token_open",
        encrypted_name = "Newspaper",
    )
    private val labels = listOf(protected_folder, open_folder)

    @Before
    fun reset() {
        folder_lock_store.lock_all()
    }

    @Test
    fun protected_folder_is_locked_until_unlocked() {
        assertTrue(requires_unlock(protected_folder))
        assertEquals(protected_folder, locked_active_folder(labels, "token_protected"))
    }

    @Test
    fun open_folder_is_never_locked() {
        assertFalse(requires_unlock(open_folder))
        assertNull(locked_active_folder(labels, "token_open"))
    }

    @Test
    fun cancelling_the_prompt_leaves_the_folder_locked() {
        assertEquals(protected_folder, locked_active_folder(labels, "token_protected"))
        assertEquals(protected_folder, locked_active_folder(labels, "token_protected"))
        assertTrue("token_protected" in protected_folder_tokens(labels))
    }

    @Test
    fun successful_unlock_opens_the_folder() {
        folder_lock_store.mark_unlocked(protected_folder.id)
        assertNull(locked_active_folder(labels, "token_protected"))
        assertFalse("token_protected" in protected_folder_tokens(labels))
    }

    @Test
    fun sign_out_relocks_every_folder() {
        folder_lock_store.mark_unlocked(protected_folder.id)
        folder_lock_store.lock_all()
        assertEquals(protected_folder, locked_active_folder(labels, "token_protected"))
    }

    @Test
    fun password_protected_without_password_set_is_not_locked() {
        val half_configured = protected_folder.copy(password_set = false)
        assertFalse(requires_unlock(half_configured))
        assertNull(locked_active_folder(listOf(half_configured), "token_protected"))
    }

    @Test
    fun unknown_or_blank_token_is_not_treated_as_locked() {
        assertNull(locked_active_folder(labels, ""))
        assertNull(locked_active_folder(labels, "token_missing"))
    }

    @Test
    fun unlocking_one_folder_does_not_unlock_another() {
        val second = protected_folder.copy(id = "label_second", label_token = "token_second")
        folder_lock_store.mark_unlocked(protected_folder.id)
        assertEquals(second, locked_active_folder(listOf(protected_folder, second), "token_second"))
    }
}
