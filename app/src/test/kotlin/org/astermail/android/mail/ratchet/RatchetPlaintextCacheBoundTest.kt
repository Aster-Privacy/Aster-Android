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
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.storage.SecurePrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetPlaintextCacheBoundTest {

    private val stored = linkedMapOf<String, String>()
    private lateinit var state_store: RatchetStateStore
    private lateinit var cache: RatchetPlaintextCache

    private fun fake_prefs(): SharedPreferences {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } answers {
            stored[firstArg()] ?: secondArg()
        }
        every { prefs.all } answers { stored.toMap() }
        every { prefs.edit() } answers {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            val pending_puts = linkedMapOf<String, String>()
            val pending_removes = mutableListOf<String>()
            var pending_clear = false
            every { editor.putString(any(), any()) } answers {
                pending_puts[firstArg()] = secondArg()
                editor
            }
            every { editor.remove(any()) } answers {
                pending_removes.add(firstArg())
                editor
            }
            every { editor.clear() } answers {
                pending_clear = true
                editor
            }
            every { editor.commit() } answers {
                if (pending_clear) stored.clear()
                pending_removes.forEach { stored.remove(it) }
                stored.putAll(pending_puts)
                true
            }
            editor
        }
        return prefs
    }

    @Before
    fun set_up() {
        stored.clear()
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } returns fake_prefs()
        state_store = mockk(relaxed = true)
        every { state_store.derive_state_encryption_key() } answers { ByteArray(32) { 3 } }
        every { state_store.state_encryption_key_candidates() } answers { listOf(ByteArray(32) { 3 }) }
        cache = RatchetPlaintextCache(mockk(relaxed = true), state_store)
    }

    @After
    fun tear_down() {
        unmockkObject(SecurePrefs)
        unmockkStatic(android.util.Base64::class)
    }

    @Test
    fun `the cache stops growing once it reaches its bound`() = runTest {
        val overflow = 5
        repeat(RatchetPlaintextCache.max_entries + overflow) { index ->
            cache.put("message_$index", "body_$index")
        }

        val entries = stored.keys.filter { it.startsWith("ratchet_plaintext_") }
        assertEquals(RatchetPlaintextCache.max_entries, entries.size)
        assertTrue(stored.containsKey("aster_plaintext_index_v1"))
    }

    @Test
    fun `the oldest entries are evicted and the newest survive`() = runTest {
        val overflow = 3
        repeat(RatchetPlaintextCache.max_entries + overflow) { index ->
            cache.put("message_$index", "body_$index")
        }

        repeat(overflow) { index ->
            assertNull(cache.get("message_$index"))
        }

        assertEquals("body_$overflow", cache.get("message_$overflow"))
        val newest = RatchetPlaintextCache.max_entries + overflow - 1
        assertEquals("body_$newest", cache.get("message_$newest"))
    }

    @Test
    fun `rewriting the same message does not consume a new slot`() = runTest {
        repeat(10) { cache.put("message_repeat", "body_$it") }

        val entries = stored.keys.filter { it.startsWith("ratchet_plaintext_") }
        assertEquals(1, entries.size)
        assertNotNull(cache.get("message_repeat"))
        assertEquals("body_9", cache.get("message_repeat"))
    }
}
