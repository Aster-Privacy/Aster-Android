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
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Before
import org.junit.Test

class RatchetStateStoreDurabilityTest {

    private lateinit var editor: SharedPreferences.Editor
    private lateinit var prefs: SharedPreferences
    private lateinit var store: RatchetStateStore

    @Before
    fun setup() {
        editor = mockk(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.commit() } returns true
        prefs = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } returns prefs
        store = RatchetStateStore(mockk(relaxed = true), SessionKeyStore(null))
    }

    @After
    fun teardown() {
        unmockkObject(SecurePrefs)
    }

    private fun sample_state(): RatchetState = RatchetState(
        conversation_id = "conv_1",
        dh_keypair = RatchetDhKeyPair(public_key = "pub", secret_key = "sec"),
        root_key = "root",
    )

    @Test
    fun `save writes ratchet state synchronously`() = runTest {
        store.save(sample_state())

        verify(exactly = 1) { editor.commit() }
        verify(exactly = 0) { editor.apply() }
    }

    @Test
    fun `delete removes ratchet state synchronously`() = runTest {
        store.delete("conv_1")

        verify(exactly = 1) { editor.commit() }
        verify(exactly = 0) { editor.apply() }
    }
}
