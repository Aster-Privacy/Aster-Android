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
import org.astermail.android.crypto.storage_rotation.absorb_vault_markers
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MailLegacyRetirementTest {

    private val envelope_json = """{"subject":"Stored","body_text":"kept on the server"}"""
    private val old_identity_key = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nretired armor\n-----END PGP PRIVATE KEY BLOCK-----"
    private val current_identity_key = "-----BEGIN PGP PRIVATE KEY BLOCK-----\ncurrent armor\n-----END PGP PRIVATE KEY BLOCK-----"

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
        session_key_store.put_user_id("user-a")
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

    private fun seal(identity_key: String, version: String): Pair<String, ByteArray> {
        val key = MessageDigest.getInstance("SHA-256").digest((identity_key + version).toByteArray(Charsets.UTF_8))
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val sealed = cipher.doFinal(envelope_json.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(sealed) to nonce
    }

    private fun retired_vault(): JSONObject =
        JSONObject().put("mail_legacy_retired", JSONObject().put("v", 1).put("at", "2026-11-17T00:00:00Z"))

    @Test
    fun previous_identity_keys_stop_opening_mail_once_retired() {
        session_key_store.put_identity_key(current_identity_key)
        session_key_store.put_previous_keys(listOf(old_identity_key))
        val (ciphertext, nonce) = seal(old_identity_key, "astermail-envelope-v1")

        absorb_vault_markers(session_key_store, retired_vault())

        assertTrue(session_key_store.mail_legacy_retired())
        assertThrows(IllegalStateException::class.java) {
            repo.decrypt_envelope_identity_key(ciphertext, nonce)
        }
    }

    @Test
    fun public_placeholder_identity_key_never_opens_mail_once_retired() {
        session_key_store.put_identity_key("undefined")
        val (ciphertext, nonce) = seal("undefined", "astermail-envelope-v1")

        absorb_vault_markers(session_key_store, retired_vault())

        assertThrows(IllegalStateException::class.java) {
            repo.decrypt_envelope_identity_key(ciphertext, nonce)
        }
    }

    @Test
    fun current_identity_key_still_opens_mail_once_retired() {
        session_key_store.put_identity_key(current_identity_key)
        session_key_store.put_previous_keys(listOf(old_identity_key))
        val (ciphertext, nonce) = seal(current_identity_key, "astermail-envelope-v1")

        absorb_vault_markers(session_key_store, retired_vault())

        assertEquals(envelope_json, String(repo.decrypt_envelope_identity_key(ciphertext, nonce), Charsets.UTF_8))
    }

    @Test
    fun retirement_stays_on_when_a_later_vault_drops_the_marker() {
        absorb_vault_markers(session_key_store, retired_vault())
        absorb_vault_markers(session_key_store, JSONObject())

        assertTrue(session_key_store.mail_legacy_retired())
    }

    @Test
    fun retirement_pin_belongs_to_one_account() {
        absorb_vault_markers(session_key_store, retired_vault())
        session_key_store.put_user_id("user-b")

        assertFalse(session_key_store.mail_legacy_retired())
    }

    @Test
    fun login_salt_check_covers_the_current_and_retired_salts_of_this_account() {
        val current = ByteArray(16) { 1 }
        val retired = ByteArray(16) { 2 }
        session_key_store.put_password_salt(current)
        session_key_store.retire_password_salts("user-a", listOf(retired))

        assertTrue(session_key_store.is_login_salt(current.copyOf()))
        assertTrue(session_key_store.is_login_salt(retired.copyOf()))
        assertFalse(session_key_store.is_login_salt(ByteArray(16) { 3 }))
        assertFalse(session_key_store.is_login_salt(ByteArray(0)))

        session_key_store.put_user_id("user-b")
        assertFalse(session_key_store.is_login_salt(retired.copyOf()))
    }

    @Test
    fun malformed_markers_do_not_retire() {
        for (marker in listOf(
            JSONObject().put("v", 0).put("at", "x"),
            JSONObject().put("v", 1),
            JSONObject().put("v", 1.5).put("at", "x"),
            JSONObject().put("v", "1").put("at", "x"),
        )) {
            absorb_vault_markers(session_key_store, JSONObject().put("mail_legacy_retired", marker))
        }

        assertFalse(session_key_store.mail_legacy_retired())
    }
}
