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

package org.astermail.android.di

import org.astermail.android.storage.InMemoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultBindingFloorStoreTest {

    @Test
    fun the_floor_is_written_and_read_per_account() {
        val prefs = InMemoryPrefs()
        val store = SecureVaultBindingFloorStore(prefs = { prefs })
        assertEquals(false, store.is_bound("user-a"))
        assertTrue(store.save("user-a", true))
        assertEquals(true, store.is_bound("user-a"))
        assertEquals(false, store.is_bound("user-b"))
        assertTrue(store.save("user-a", false))
        assertEquals(false, store.is_bound("user-a"))
    }

    @Test
    fun a_missing_store_reads_as_unknown_and_refuses_writes() {
        val store = SecureVaultBindingFloorStore(prefs = { null })
        assertNull(store.is_bound("user-a"))
        assertFalse(store.save("user-a", true))
        val broken = SecureVaultBindingFloorStore(prefs = { throw IllegalStateException("closed") })
        assertNull(broken.is_bound("user-a"))
        assertFalse(broken.save("user-a", true))
    }

    @Test
    fun the_mirror_keeps_the_floor_when_the_secure_store_is_lost() {
        val mirror = InMemoryPrefs()
        val store = SecureVaultBindingFloorStore(prefs = { InMemoryPrefs() }, backup = { mirror })
        assertTrue(store.save("user-a", true))
        val unreadable = SecureVaultBindingFloorStore(
            prefs = { throw IllegalStateException("key lost") },
            backup = { mirror },
        )
        assertEquals(true, unreadable.is_bound("user-a"))
        val wiped = SecureVaultBindingFloorStore(prefs = { InMemoryPrefs() }, backup = { mirror })
        assertEquals(true, wiped.is_bound("user-a"))
        assertFalse(mirror.all.keys.any { it.contains("user-a") })
    }

    @Test
    fun admitted_vaults_are_kept_per_account_in_order() {
        val mirror = InMemoryPrefs()
        val store = SecureVaultBindingFloorStore(prefs = { InMemoryPrefs() }, backup = { mirror })
        assertTrue(store.save_admitted("user-a", linkedSetOf("d1", "d2")))
        assertEquals(listOf("d1", "d2"), store.admitted("user-a")?.toList())
        assertEquals(emptySet<String>(), store.admitted("user-b"))
        val unreadable = SecureVaultBindingFloorStore(prefs = { null }, backup = { mirror })
        assertEquals(listOf("d1", "d2"), unreadable.admitted("user-a")?.toList())
        assertNull(SecureVaultBindingFloorStore(prefs = { throw IllegalStateException("closed") }).admitted("user-a"))
    }

    @Test
    fun a_floor_written_before_the_mirror_existed_is_copied_to_it_on_read() {
        val secure = InMemoryPrefs()
        assertTrue(SecureVaultBindingFloorStore(prefs = { secure }).save("user-a", true))
        val mirror = InMemoryPrefs()
        assertEquals(true, SecureVaultBindingFloorStore(prefs = { secure }, backup = { mirror }).is_bound("user-a"))
        val unreadable = SecureVaultBindingFloorStore(
            prefs = { throw IllegalStateException("key lost") },
            backup = { mirror },
        )
        assertEquals(true, unreadable.is_bound("user-a"))
    }

    @Test
    fun admitted_vaults_written_before_the_mirror_existed_are_copied_to_it_on_read() {
        val secure = InMemoryPrefs()
        assertTrue(SecureVaultBindingFloorStore(prefs = { secure }).save_admitted("user-a", linkedSetOf("d1", "d2")))
        val mirror = InMemoryPrefs()
        val store = SecureVaultBindingFloorStore(prefs = { secure }, backup = { mirror })
        assertEquals(listOf("d1", "d2"), store.admitted("user-a")?.toList())
        val unreadable = SecureVaultBindingFloorStore(prefs = { null }, backup = { mirror })
        assertEquals(listOf("d1", "d2"), unreadable.admitted("user-a")?.toList())
    }
}
