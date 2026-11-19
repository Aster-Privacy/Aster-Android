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

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RetirementPinStorageTest {

    private val secure = HashMap<String, fake_prefs>()
    private val plain = HashMap<String, fake_prefs>()
    private lateinit var context: Context

    @Before
    fun setup() {
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } answers { secure.getOrPut(secondArg()) { fake_prefs() } }
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } answers { plain.getOrPut(firstArg()) { fake_prefs() } }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun retirement_and_folder_pins_are_kept_in_keystore_backed_prefs() {
        val store = SessionKeyStore(context)
        store.put_user_id("user-a")

        store.pin_mail_legacy_retired("user-a")
        store.raise_folder_key_pin("Folder-1", 1)

        assertEquals(true, secure["aster_mail_legacy_retired_secure"]?.values?.get("user-a"))
        assertEquals(1, secure["aster_folder_key_pins_secure"]?.values?.get("folder-1"))
        assertTrue(plain.values.none { it.values.containsKey("user-a") || it.values.containsKey("folder-1") })
    }

    private fun reset_secure_files() {
        secure.clear()
    }

    @Test
    fun a_reset_pin_file_reads_as_retired_and_keeps_folder_pins_at_their_highest() {
        val first = SessionKeyStore(context)
        first.put_user_id("user-a")
        first.pin_mail_legacy_retired("user-a")
        first.raise_folder_key_pin("Folder-1", 2)
        assertEquals(2, first.folder_key_pin("Folder-1"))

        reset_secure_files()
        val after_reset = SessionKeyStore(context)
        after_reset.put_user_id("user-a")

        assertTrue(after_reset.mail_legacy_retired())
        assertTrue(after_reset.folder_key_pin("Folder-1") >= 2)
    }

    @Test
    fun the_vault_copy_restores_pins_after_a_reset() {
        io.mockk.mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        val first = SessionKeyStore(context)
        first.put_user_id("user-a")
        first.raise_folder_key_pin("Folder-1", 2)
        reset_secure_files()
        val store = SessionKeyStore(context)
        store.put_user_id("user-a")
        val vault = org.json.JSONObject()
            .put("folder_key_pins", org.json.JSONObject().put("folder-1", 2))
        val encoder = java.util.Base64.getEncoder()

        org.astermail.android.crypto.storage_rotation.absorb_rotated_vault(
            store,
            vault,
            org.astermail.android.crypto.storage_rotation.SealedVault(
                encoder.encodeToString(ByteArray(64) { 1 }),
                encoder.encodeToString(ByteArray(12) { 2 }),
            ),
        )

        assertEquals(2, store.folder_key_pin("Folder-1"))
        assertEquals(0, store.folder_key_pin("Folder-2"))
    }

    @Test
    fun a_pin_that_cannot_be_saved_reports_it_and_stays_in_memory() {
        every { SecurePrefs.open(any(), any()) } answers {
            secure.getOrPut(secondArg()) { fake_prefs(commit_result = false) }
        }
        val store = SessionKeyStore(context)
        store.put_user_id("user-a")

        val saved = store.raise_folder_key_pin("Folder-1", 2)

        assertFalse(saved)
        assertEquals(2, store.folder_key_pin("Folder-1"))
    }

    @Test
    fun a_retired_account_stays_retired_before_the_user_is_known() {
        val store = SessionKeyStore(null)

        store.pin_mail_legacy_retired("user-a")

        assertTrue(store.mail_legacy_retired())
    }
}
