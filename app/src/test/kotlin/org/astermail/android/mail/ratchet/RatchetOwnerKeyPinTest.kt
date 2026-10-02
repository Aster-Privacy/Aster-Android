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
package org.astermail.android.mail.ratchet

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetOwnerKeyPinTest {

    private val values = mutableMapOf<String, Any?>()
    private lateinit var store: RatchetIdentityPinStore

    private fun fake_prefs(): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg<String?>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            values[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg<String>())
            editor
        }
        every { editor.clear() } answers {
            values.clear()
            editor
        }
        every { editor.commit() } returns true
        every { editor.apply() } returns Unit
        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers {
            values[firstArg()] as? String ?: secondArg<String?>()
        }
        every { prefs.getBoolean(any(), any()) } answers {
            values[firstArg()] as? Boolean ?: secondArg<Boolean>()
        }
        every { prefs.getInt(any(), any()) } answers {
            values[firstArg()] as? Int ?: secondArg<Int>()
        }
        every { prefs.contains(any()) } answers { values.containsKey(firstArg<String>()) }
        every { prefs.all } answers { values.toMap() }
        return prefs
    }

    @Before
    fun setup() {
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } returns fake_prefs()
        store = RatchetIdentityPinStore(mockk(relaxed = true), SessionKeyStore(null))
    }

    @After
    fun teardown() {
        unmockkObject(SecurePrefs)
    }

    @Test
    fun `first verified owner key is pinned once`() = runTest {
        assertNull(store.owner_key_pin("peer@astermail.org"))
        store.pin_owner_key_if_absent("Peer@astermail.org", "AAAA")
        store.pin_owner_key_if_absent("peer@astermail.org", "BBBB")

        assertEquals("AAAA", store.owner_key_pin("peer@astermail.org"))
    }

    @Test
    fun `changed owner key waits for trust and then re-pins`() = runTest {
        store.pin_owner_key_if_absent("peer@astermail.org", "AAAA")
        store.flag_owner_key_change("peer@astermail.org", "BBBB", 1L)

        assertEquals("AAAA", store.owner_key_pin("peer@astermail.org"))
        val pending = store.unacknowledged_changes.value
        assertEquals(1, pending.size)
        assertEquals("peer@astermail.org", pending[0].sender_email)

        store.acknowledge_sender("Peer@astermail.org")

        assertEquals("BBBB", store.owner_key_pin("peer@astermail.org"))
        assertTrue(store.unacknowledged_changes.value.isEmpty())
    }

    @Test
    fun `same owner key raises no change`() = runTest {
        store.pin_owner_key_if_absent("peer@astermail.org", "AAAA")
        store.flag_owner_key_change("peer@astermail.org", "aaaa", 1L)

        assertTrue(store.unacknowledged_changes.value.isEmpty())
    }
}
