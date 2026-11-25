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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `retired state keys never include keys derived from a public constant`() {
        io.mockk.mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        val derived = org.astermail.android.crypto.StorageWriteKey.public_mail_kek_raws().flatMap { raw ->
            listOf(
                org.astermail.android.crypto.ratchet.RatchetCrypto.hkdf_sha256(
                    ikm = raw,
                    salt = "Aster Mail_Ratchet_State_Sync".toByteArray(Charsets.UTF_8),
                    info = "ratchet_state_sync_key_v3".toByteArray(Charsets.UTF_8),
                    length = 32,
                ),
                org.astermail.android.crypto.ratchet.RatchetCrypto.hkdf_sha256(
                    ikm = raw,
                    salt = "Aster Mail_Ratchet_State_Encryption".toByteArray(Charsets.UTF_8),
                    info = "ratchet_state_key".toByteArray(Charsets.UTF_8),
                    length = 32,
                ),
            )
        }

        val retired = store.retired_state_key_candidates()

        io.mockk.unmockkStatic(android.util.Base64::class)
        assertFalse(
            "retired ratchet state must not open under a public constant",
            retired.any { key -> derived.any { it.contentEquals(key) } },
        )
    }

    @Test
    fun `state master keys never include the stored login proof`() {
        io.mockk.mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        val proof = ByteArray(32) { (it * 3 + 5).toByte() }
        val data_kek = ByteArray(32) { (it * 7 + 1).toByte() }
        val keys = SessionKeyStore(null)
        keys.put(proof.copyOf())
        keys.put_data_kek(data_kek.copyOf())
        keys.put_legacy_keks(listOf(java.util.Base64.getEncoder().encodeToString(proof)))

        val candidates = RatchetStateStore(mockk(relaxed = true), keys).master_key_candidates()

        io.mockk.unmockkStatic(android.util.Base64::class)
        assertFalse(candidates.any { it.contentEquals(proof) })
        assertTrue(candidates.any { it.contentEquals(data_kek) })
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
