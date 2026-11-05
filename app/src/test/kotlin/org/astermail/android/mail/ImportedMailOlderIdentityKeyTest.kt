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

package org.astermail.android.mail

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.auth.vault_identity_key
import org.astermail.android.auth.vault_older_identity_keys
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ImportedMailOlderIdentityKeyTest {

    private val envelope_json = """{"subject":"Imported","body_text":"hello from the archive"}"""
    private val old_identity_key = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nold armor with one address\n-----END PGP PRIVATE KEY BLOCK-----"
    private val new_identity_key = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nnew armor with two addresses\n-----END PGP PRIVATE KEY BLOCK-----"

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        session_key_store = SessionKeyStore(null)
        repo = MailRepository(
            mail_api = mockk(relaxed = true),
            send_api = mockk(relaxed = true),
            snooze_api = mockk(relaxed = true),
            labels_api = mockk(relaxed = true),
            keys_api = mockk(relaxed = true),
            session_key_store = session_key_store,
            scheduled_api = mockk(relaxed = true),
            ratchet_decryptor = mockk(relaxed = true),
            ratchet_encryptor = mockk(relaxed = true),
            ratchet_plaintext_cache = mockk(relaxed = true),
            system_folder_bootstrap = mockk(relaxed = true),
            pending_send_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            thread_snapshot_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            context = mockk(relaxed = true),
            auth_repository = mockk(relaxed = true),
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun seal_import(identity_key: String, plaintext: String): Pair<String, ByteArray> {
        val key = MessageDigest.getInstance("SHA-256")
            .digest((identity_key + "astermail-import-v1").toByteArray(Charsets.UTF_8))
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(sealed) to nonce
    }

    private fun vault_after_address_added(): JSONObject =
        JSONObject()
            .put("identity_key", new_identity_key)
            .put("previous_keys", JSONArray())
            .put("legacy_identity_keys", JSONArray(listOf(old_identity_key)))

    @Test
    fun mail_imported_under_an_older_identity_key_opens_after_absorbing_the_vault() {
        val vault = vault_after_address_added()
        session_key_store.put_identity_key(vault_identity_key(vault))
        session_key_store.put_previous_keys(vault_older_identity_keys(vault))
        val (ciphertext, nonce) = seal_import(old_identity_key, envelope_json)

        val opened = repo.decrypt_envelope_identity_key(ciphertext, nonce)

        assertEquals(envelope_json, String(opened, Charsets.UTF_8))
    }

    @Test
    fun mail_imported_under_an_older_identity_key_fails_with_only_previous_keys() {
        val vault = vault_after_address_added()
        session_key_store.put_identity_key(vault_identity_key(vault))
        session_key_store.put_previous_keys(emptyList())
        val (ciphertext, nonce) = seal_import(old_identity_key, envelope_json)

        assertThrows(IllegalStateException::class.java) {
            repo.decrypt_envelope_identity_key(ciphertext, nonce)
        }
    }

    @Test
    fun mail_imported_under_the_current_identity_key_still_opens() {
        val vault = vault_after_address_added()
        session_key_store.put_identity_key(vault_identity_key(vault))
        session_key_store.put_previous_keys(vault_older_identity_keys(vault))
        val (ciphertext, nonce) = seal_import(new_identity_key, envelope_json)

        val opened = repo.decrypt_envelope_identity_key(ciphertext, nonce)

        assertEquals(envelope_json, String(opened, Charsets.UTF_8))
    }
}
