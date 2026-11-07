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

package org.astermail.android.contacts.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class contact_sync_account_plan_test {

    private val known = mapOf("user-1" to "person@example.com", "user-2" to "work@example.com")

    @Test
    fun keeps_the_account_after_the_session_ends() {
        assertTrue(should_keep_sync_account("user-1", "person@example.com", true, known, emptySet()))
    }

    @Test
    fun does_not_re_add_an_account_that_is_still_present() {
        val add = sync_accounts_to_add(true, mapOf("user-1" to "person@example.com"), emptySet(), setOf("user-1"))
        assertTrue(add.isEmpty())
    }

    @Test
    fun adds_a_signed_in_account_that_is_missing() {
        val add = sync_accounts_to_add(true, mapOf("user-1" to "person@example.com"), emptySet(), emptySet())
        assertEquals(mapOf("user-1" to "person@example.com"), add)
    }

    @Test
    fun does_not_add_an_account_that_is_signed_out() {
        val add = sync_accounts_to_add(true, emptyMap(), emptySet(), emptySet())
        assertTrue(add.isEmpty())
    }

    @Test
    fun removes_the_account_after_a_deliberate_sign_out() {
        val after_sign_out = known - "user-1"
        assertFalse(should_keep_sync_account("user-1", "person@example.com", true, after_sign_out, emptySet()))
    }

    @Test
    fun removes_every_account_when_sync_is_off() {
        assertFalse(should_keep_sync_account("user-1", "person@example.com", false, known, emptySet()))
        assertTrue(sync_accounts_to_add(false, known, emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun removes_a_declined_account() {
        assertFalse(should_keep_sync_account("user-2", "work@example.com", true, known, setOf("user-2")))
        assertTrue(sync_accounts_to_add(true, known, setOf("user-2"), setOf("user-1")).isEmpty())
    }

    @Test
    fun removes_an_account_with_a_changed_address_or_no_id() {
        assertFalse(should_keep_sync_account("user-1", "old@example.com", true, known, emptySet()))
        assertFalse(should_keep_sync_account(null, "person@example.com", true, known, emptySet()))
    }
}
