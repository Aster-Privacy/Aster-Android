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

package org.astermail.android.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class token_store_save_test {

    @Test
    fun a_failed_commit_keeps_the_rotated_tokens_readable() = runBlocking {
        val prefs = fake_prefs(commit_result = false)
        val store = TokenStore(prefs)

        store.save("access_1", "refresh_1")

        assertEquals("access_1", store.access_token)
        assertEquals("refresh_1", store.refresh_token)
        assertTrue(store.has_unsaved_tokens)
    }

    @Test
    fun another_instance_on_the_same_prefs_sees_unsaved_tokens() = runBlocking {
        val prefs = fake_prefs(commit_result = false)
        TokenStore(prefs).save("access_2", "refresh_2")

        val other = TokenStore(prefs)

        assertEquals("refresh_2", other.refresh_token)
    }

    @Test
    fun a_successful_commit_leaves_nothing_unsaved() = runBlocking {
        val prefs = fake_prefs()
        val store = TokenStore(prefs)

        store.save("access_3", "refresh_3")

        assertFalse(store.has_unsaved_tokens)
        assertEquals("refresh_3", prefs.values["refresh_token"])
        assertEquals("refresh_3", store.refresh_token)
    }

    @Test
    fun clear_drops_unsaved_tokens() = runBlocking {
        val prefs = fake_prefs(commit_result = false)
        val store = TokenStore(prefs)
        store.save("access_4", "refresh_4")

        store.clear()

        assertFalse(store.has_unsaved_tokens)
        assertNull(store.refresh_token)
    }
}
