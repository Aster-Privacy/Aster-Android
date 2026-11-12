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
import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SavedSenderKeyTest {

    private val values = mutableMapOf<String, Any?>()
    private var commit_result = true
    private var unreadable_prefix: String? = null
    private var listing_unreadable = false

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
        every { editor.commit() } answers { commit_result }
        every { editor.apply() } returns Unit
        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers {
            val prefix = unreadable_prefix
            if (prefix != null && firstArg<String>().startsWith(prefix)) throw SecurityException("unreadable")
            values[firstArg()] as? String ?: secondArg<String?>()
        }
        every { prefs.getBoolean(any(), any()) } answers {
            values[firstArg()] as? Boolean ?: secondArg<Boolean>()
        }
        every { prefs.getInt(any(), any()) } answers {
            values[firstArg()] as? Int ?: secondArg<Int>()
        }
        every { prefs.contains(any()) } answers { values.containsKey(firstArg<String>()) }
        every { prefs.all } answers {
            if (listing_unreadable) throw SecurityException("unreadable")
            values.toMap()
        }
        return prefs
    }

    private fun open_store(): RatchetIdentityPinStore =
        RatchetIdentityPinStore(mockk(relaxed = true), SessionKeyStore(null))

    private fun owner_entries(): Map<String, Any?> = values.filterKeys { it.startsWith("owner_") }

    @Before
    fun setup() {
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } returns fake_prefs()
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun teardown() {
        unmockkObject(SecurePrefs)
        unmockkStatic(Base64::class)
    }

    @Test
    fun `the first key seen for a sender is saved and later matches`() = runTest {
        val store = open_store()

        val first = store.check_sender_key("peer@astermail.org", "AAAA", 1L)
        val again = store.check_sender_key("peer@astermail.org", "aaaa", 2L)

        assertEquals(SavedKeyCheck.FIRST, first)
        assertEquals(SavedKeyCheck.MATCH, again)
        assertTrue(RatchetIdentityPinRules.signature_trusted(again))
        assertEquals("AAAA", values["owner_peer@astermail.org"])
        assertEquals(SavedKeyState.FOUND, store.sender_key_state("peer@astermail.org"))
        assertTrue(store.unacknowledged_changes.value.isEmpty())
    }

    @Test
    fun `a served key that differs from the saved one is not trusted`() = runTest {
        val store = open_store()
        store.check_sender_key("peer@astermail.org", "AAAA", 1L)

        val check = store.check_sender_key("Peer@astermail.org", "BBBB", 2L)

        assertEquals(SavedKeyCheck.CHANGED, check)
        assertFalse(RatchetIdentityPinRules.signature_trusted(check))
        assertEquals("AAAA", values["owner_peer@astermail.org"])
        val pending = store.unacknowledged_changes.value
        assertEquals(1, pending.size)
        assertEquals("peer@astermail.org", pending[0].sender_email)
        assertEquals("AAAA", pending[0].previous_fingerprint)
        assertEquals("BBBB", pending[0].current_fingerprint)
    }

    @Test
    fun `a changed key stays untrusted until the change is accepted`() = runTest {
        val store = open_store()
        store.check_sender_key("peer@astermail.org", "AAAA", 1L)
        store.check_sender_key("peer@astermail.org", "BBBB", 2L)

        assertEquals(SavedKeyCheck.CHANGED, store.check_sender_key("peer@astermail.org", "BBBB", 3L))
        assertEquals(1, store.unacknowledged_changes.value.size)

        store.acknowledge_sender("P.eer+tag@astermail.org")

        assertTrue(store.unacknowledged_changes.value.isEmpty())
        assertEquals(SavedKeyCheck.MATCH, store.check_sender_key("peer@astermail.org", "BBBB", 4L))
    }

    @Test
    fun `an unreadable saved key is never treated as first contact`() = runTest {
        values["owner_peer@astermail.org"] = "AAAA"
        unreadable_prefix = "owner_"
        val store = open_store()

        val check = store.check_sender_key("peer@astermail.org", "BBBB", 1L)

        assertEquals(SavedKeyCheck.UNREADABLE, check)
        assertFalse(RatchetIdentityPinRules.signature_trusted(check))
        assertEquals(SavedKeyState.UNREADABLE, store.sender_key_state("peer@astermail.org"))
        assertEquals(mapOf<String, Any?>("owner_peer@astermail.org" to "AAAA"), owner_entries())
        assertFalse(store.pin_owner_key_if_absent("peer@astermail.org", "BBBB"))
        assertThrows(IllegalStateException::class.java) { store.owner_key_pin("peer@astermail.org") }
        assertTrue(store.unacknowledged_changes.value.isEmpty())
    }

    @Test
    fun `an unreadable key listing is never treated as first contact`() = runTest {
        listing_unreadable = true
        val store = open_store()

        val check = store.check_sender_key("peer@astermail.org", "AAAA", 1L)

        assertEquals(SavedKeyCheck.UNREADABLE, check)
        assertTrue(owner_entries().isEmpty())
        assertFalse(store.pin_owner_key_if_absent("peer@astermail.org", "AAAA"))

        listing_unreadable = false

        assertEquals(SavedKeyCheck.FIRST, store.check_sender_key("peer@astermail.org", "AAAA", 2L))
    }

    @Test
    fun `a key that cannot be saved is not trusted`() = runTest {
        commit_result = false
        val store = open_store()

        val check = store.check_sender_key("peer@astermail.org", "AAAA", 1L)

        assertEquals(SavedKeyCheck.UNREADABLE, check)
        assertFalse(RatchetIdentityPinRules.signature_trusted(check))
    }

    @Test
    fun `dotted and tagged spellings share one saved key`() = runTest {
        val store = open_store()

        assertEquals(SavedKeyCheck.FIRST, store.check_sender_key("jane.doe+news@astermail.org", "AAAA", 1L))
        assertEquals(SavedKeyCheck.MATCH, store.check_sender_key("janedoe@astermail.org", "AAAA", 2L))
        assertEquals(SavedKeyCheck.MATCH, store.check_sender_key("J.a.n.e.Doe@astermail.org", "AAAA", 3L))
        assertEquals(SavedKeyCheck.CHANGED, store.check_sender_key("jane.doe@astermail.org", "BBBB", 4L))
        assertEquals("AAAA", store.owner_key_pin("ja.nedoe+x@astermail.org"))
        assertEquals(setOf("owner_janedoe@astermail.org"), owner_entries().keys)
    }

    @Test
    fun `outside addresses are not folded together`() = runTest {
        val store = open_store()

        assertEquals(SavedKeyCheck.FIRST, store.check_sender_key("jane.doe@example.com", "AAAA", 1L))
        assertEquals(SavedKeyCheck.FIRST, store.check_sender_key("janedoe@example.com", "BBBB", 2L))
        assertEquals(SavedKeyCheck.MATCH, store.check_sender_key("Jane.Doe@example.com", "AAAA", 3L))
    }

    @Test
    fun `a key saved under an earlier spelling is still found`() = runTest {
        values["owner_jane.doe@astermail.org"] = "AAAA"
        val store = open_store()

        assertEquals("AAAA", store.owner_key_pin("jane.doe@astermail.org"))
        assertEquals(SavedKeyCheck.MATCH, store.check_sender_key("janedoe@astermail.org", "AAAA", 1L))
        assertEquals(SavedKeyCheck.CHANGED, store.check_sender_key("jane.doe@astermail.org", "BBBB", 2L))
        assertEquals("AAAA", values["owner_jane.doe@astermail.org"])
        assertEquals("AAAA", values["owner_janedoe@astermail.org"])
        assertTrue(store.pin_owner_key_if_absent("janedoe@astermail.org", "BBBB"))
        assertEquals("AAAA", store.owner_key_pin("janedoe@astermail.org"))
    }

    @Test
    fun `a key saved under an unusual earlier spelling is still found`() = runTest {
        values["owner_j.an.edoe+old@astermail.org"] = "AAAA"
        val store = open_store()

        assertEquals(SavedKeyCheck.MATCH, store.check_sender_key("janedoe@astermail.org", "AAAA", 1L))
        assertEquals(SavedKeyCheck.CHANGED, store.check_sender_key("jane.doe@astermail.org", "BBBB", 2L))
    }

    @Test
    fun `earlier spellings that disagree are not trusted until accepted`() = runTest {
        values["owner_jane.doe@astermail.org"] = "AAAA"
        values["owner_janedoe@astermail.org"] = "BBBB"
        val store = open_store()

        assertEquals(SavedKeyState.CONFLICT, store.sender_key_state("janedoe@astermail.org"))
        assertEquals(SavedKeyCheck.CHANGED, store.check_sender_key("janedoe@astermail.org", "BBBB", 1L))
        assertFalse(store.pin_owner_key_if_absent("janedoe@astermail.org", "BBBB"))

        store.acknowledge_sender("janedoe@astermail.org")

        assertEquals(SavedKeyCheck.MATCH, store.check_sender_key("jane.doe@astermail.org", "BBBB", 2L))
        assertEquals("BBBB", values["owner_jane.doe@astermail.org"])
    }

    @Test
    fun `an earlier prekey confirmation and sender history carry over`() = runTest {
        val identity = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        val other = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        values["binding_jane.doe@astermail.org"] = true
        val store = open_store()
        store.remember_sender_identity("janedoe@astermail.org", identity)
        values["hist_jane.doe@astermail.org"] = values.remove("hist_janedoe@astermail.org")
        val reopened = open_store()

        assertTrue(reopened.is_prekey_binding_verified("janedoe@astermail.org"))
        assertEquals(
            SenderIdentityContinuity.KNOWN,
            reopened.sender_continuity("", "janedoe+tag@astermail.org", identity),
        )
        assertEquals(
            SenderIdentityContinuity.CHANGED,
            reopened.sender_continuity("", "janedoe@astermail.org", other),
        )
    }

    @Test
    fun `an unreadable sender history is not first contact`() = runTest {
        val identity = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        unreadable_prefix = "hist_"
        val store = open_store()

        assertEquals(SenderIdentityContinuity.CHANGED, store.sender_continuity("", "peer@astermail.org", identity))
        assertNull(values["hist_peer@astermail.org"])
    }

    @Test
    fun `saved key rules never trust an unreadable or changed key`() {
        assertEquals(
            SavedKeyCheck.FIRST,
            RatchetIdentityPinRules.check_saved_key(SavedKeyState.ABSENT, null, "AAAA"),
        )
        assertEquals(
            SavedKeyCheck.MATCH,
            RatchetIdentityPinRules.check_saved_key(SavedKeyState.FOUND, "aaaa", "AAAA"),
        )
        assertEquals(
            SavedKeyCheck.CHANGED,
            RatchetIdentityPinRules.check_saved_key(SavedKeyState.FOUND, "AAAA", "BBBB"),
        )
        assertEquals(
            SavedKeyCheck.CHANGED,
            RatchetIdentityPinRules.check_saved_key(SavedKeyState.CONFLICT, "AAAA", "AAAA"),
        )
        assertEquals(
            SavedKeyCheck.UNREADABLE,
            RatchetIdentityPinRules.check_saved_key(SavedKeyState.UNREADABLE, null, "AAAA"),
        )
        assertEquals(
            SavedKeyCheck.UNREADABLE,
            RatchetIdentityPinRules.check_saved_key(SavedKeyState.FOUND, "AAAA", " "),
        )
        assertFalse(RatchetIdentityPinRules.signature_trusted(SavedKeyCheck.CHANGED))
        assertFalse(RatchetIdentityPinRules.signature_trusted(SavedKeyCheck.UNREADABLE))
        assertFalse(RatchetIdentityPinRules.signature_trusted(null))
    }

    @Test
    fun `unreadable storage never reads as a new sender`() {
        assertEquals(
            SenderIdentityContinuity.CHANGED,
            RatchetIdentityPinRules.stored_continuity(null, null, true, "AAAA"),
        )
        assertEquals(
            SenderIdentityContinuity.CHANGED,
            RatchetIdentityPinRules.stored_continuity(emptyList(), null, false, "AAAA"),
        )
        assertEquals(
            SenderIdentityContinuity.KNOWN,
            RatchetIdentityPinRules.stored_continuity(null, "AAAA", true, "AAAA"),
        )
        assertEquals(
            SenderIdentityContinuity.NEW,
            RatchetIdentityPinRules.stored_continuity(emptyList(), null, true, "AAAA"),
        )
    }
}
