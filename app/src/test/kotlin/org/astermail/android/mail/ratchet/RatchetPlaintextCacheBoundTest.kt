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
import org.junit.Assert.assertFalse
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

    @Test
    fun `recovery lane plaintext stays flagged as unauthenticated`() = runTest {
        cache.put("lane", "from the lane", sender_unauthenticated = true)
        cache.put("chain", "from the chain")

        assertEquals(setOf("lane"), cache.unauthenticated_ids.value)
        assertEquals("from the lane", cache.get("lane"))

        val reopened = RatchetPlaintextCache(mockk(relaxed = true), state_store)
        assertEquals(setOf("lane"), reopened.unauthenticated_ids.value)

        cache.put("lane", "verified later")
        assertTrue(cache.unauthenticated_ids.value.isEmpty())
    }

    @Test
    fun `a recovery lane message reports its sender as unauthenticated`() = runTest {
        cache.put("lane", "from the lane", sender_unauthenticated = true)
        cache.put("chain", "from the chain")

        assertTrue(cache.is_sender_unauthenticated("lane"))
        assertFalse(cache.is_sender_unauthenticated("chain"))
        assertFalse(cache.is_sender_unauthenticated(null))
        assertFalse(cache.is_sender_unauthenticated(""))
    }

    @Test
    fun `the unauthenticated flag is kept when the plaintext cannot be stored`() = runTest {
        every { state_store.derive_state_encryption_key() } returns null

        cache.put("lane", "from the lane", sender_unauthenticated = true)
        cache.put("chain", "from the chain")

        assertNull(stored["ratchet_plaintext_lane"])
        assertTrue(cache.is_sender_unauthenticated("lane"))
        assertFalse(cache.is_sender_unauthenticated("chain"))
    }

    @Test
    fun `evicting or clearing drops the unauthenticated flag`() = runTest {
        cache.put("message_0", "body_0", sender_unauthenticated = true)
        repeat(RatchetPlaintextCache.max_entries) { index ->
            cache.put("message_${index + 1}", "body_${index + 1}")
        }
        assertNull(cache.get("message_0"))
        assertTrue(cache.unauthenticated_ids.value.isEmpty())

        cache.put("lane", "body", sender_unauthenticated = true)
        cache.clear()
        assertTrue(cache.unauthenticated_ids.value.isEmpty())
    }

    @Test
    fun `the unconfirmed sender flag persists and defaults to false`() = runTest {
        cache.put("first", "body", unconfirmed_sender = true)
        cache.put("second", "body")

        assertTrue(cache.is_sender_unconfirmed("first"))
        assertFalse(cache.is_sender_unconfirmed("second"))
        assertFalse(cache.is_sender_unauthenticated("first"))

        val reopened = RatchetPlaintextCache(mockk(relaxed = true), state_store)
        assertEquals(setOf("first"), reopened.unconfirmed_sender_ids.value)

        cache.put("first", "body")
        assertFalse(cache.is_sender_unconfirmed("first"))
    }

    @Test
    fun `old rows without the unconfirmed key read as confirmed`() = runTest {
        stored.remove("aster_plaintext_unconfirmed_sender_v1")
        val reopened = RatchetPlaintextCache(mockk(relaxed = true), state_store)
        assertTrue(reopened.unconfirmed_sender_ids.value.isEmpty())
        assertFalse(reopened.is_sender_unconfirmed("anything"))
    }

    @Test
    fun `only flagged entries read as unconfirmed and confirming clears the flag`() = runTest {
        cache.put("older", "body")
        cache.put("older_flagged", "body", unconfirmed_sender = true)

        val reopened = RatchetPlaintextCache(mockk(relaxed = true), state_store)
        assertFalse(reopened.is_sender_unconfirmed("older"))
        assertTrue(reopened.is_sender_unconfirmed("older_flagged"))
        assertEquals("body", reopened.get("older"))

        reopened.confirm_sender("older_flagged")
        assertFalse(reopened.is_sender_unconfirmed("older_flagged"))
        assertFalse(RatchetPlaintextCache(mockk(relaxed = true), state_store).is_sender_unconfirmed("older_flagged"))
    }

    @Test
    fun `the unconfirmed flag is kept when the plaintext cannot be stored`() = runTest {
        every { state_store.derive_state_encryption_key() } returns null

        cache.put("first", "body", unconfirmed_sender = true)

        assertTrue(cache.is_sender_unconfirmed("first"))
    }

    private val alice = PlaintextBinding("alice@astermail.org", "conv_a")

    @Test
    fun `a bound entry round trips and is stored with the bound marker`() = runTest {
        cache.put("bound", "hello", binding = alice)

        assertTrue(stored["ratchet_plaintext_bound"]!!.startsWith("b1|"))
        assertEquals("hello", cache.get("bound"))
        assertEquals("hello", cache.get("bound", "Alice@AsterMail.org"))
        assertNull(cache.get("bound", "mallory@astermail.org"))
    }

    @Test
    fun `a write for another sender never replaces a bound entry`() = runTest {
        cache.put("bound", "original", binding = alice)

        cache.put("bound", "swapped", binding = PlaintextBinding("mallory@astermail.org", "conv_a"))
        cache.put("bound", "swapped", binding = PlaintextBinding("alice@astermail.org", "conv_other"))
        cache.put("bound", "swapped")
        cache.put("bound", "swapped", sender_unauthenticated = true, binding = alice.copy(conversation_id = "conv_x"))

        assertEquals("original", cache.get("bound"))
        assertFalse(cache.is_sender_unauthenticated("bound"))
    }

    @Test
    fun `a verified write replaces an unverified entry from another sender`() = runTest {
        cache.put("lane", "from the lane", sender_unauthenticated = true, binding = PlaintextBinding("mallory@astermail.org", "conv_a"))

        cache.put("lane", "verified", binding = alice)

        assertEquals("verified", cache.get("lane", "alice@astermail.org"))
        assertFalse(cache.is_sender_unauthenticated("lane"))
    }

    @Test
    fun `the same sender can upgrade its own unverified entry`() = runTest {
        cache.put("lane", "from the lane", sender_unauthenticated = true, binding = alice)

        cache.put("lane", "verified", binding = alice)

        assertEquals("verified", cache.get("lane"))
        assertFalse(cache.is_sender_unauthenticated("lane"))
    }

    @Test
    fun `legacy unbound entries stay readable for any sender`() = runTest {
        cache.put("legacy", "old body")

        assertFalse(stored["ratchet_plaintext_legacy"]!!.startsWith("b1|"))
        assertEquals("old body", cache.get("legacy", "alice@astermail.org"))

        cache.put("legacy", "new body", binding = alice)
        assertEquals("new body", cache.get("legacy", "alice@astermail.org"))
    }
}
