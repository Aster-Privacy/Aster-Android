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
import kotlinx.coroutines.runBlocking
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.storage_rotation.SealedVault
import org.astermail.android.crypto.storage_rotation.absorb_rotated_vault
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class RetiredMailKeysTest {

    private val envelope_json = """{"subject":"Stored","body_text":"kept on the server"}"""
    private val old_identity_key = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nold armor\n-----END PGP PRIVATE KEY BLOCK-----"
    private val current_identity_key = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nnew armor\n-----END PGP PRIVATE KEY BLOCK-----"
    private val password_marker_nonce = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1))

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getMimeDecoder().decode(firstArg<String>())
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
            auth_repository = dagger.Lazy { mockk<AuthRepository>(relaxed = true) },
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun random_bytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun aes_gcm(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    private fun password_envelope(salt: ByteArray, key: ByteArray): String {
        val iv = random_bytes(12)
        return b64(salt + iv + aes_gcm(key, iv, envelope_json.toByteArray(Charsets.UTF_8)))
    }

    private fun identity_envelope(identity_key: String): Pair<String, ByteArray> {
        val key = MessageDigest.getInstance("SHA-256")
            .digest((identity_key + "astermail-envelope-v1").toByteArray(Charsets.UTF_8))
        val nonce = random_bytes(12)
        return b64(aes_gcm(key, nonce, envelope_json.toByteArray(Charsets.UTF_8))) to nonce
    }

    private fun read_retired_vault(vault: JSONObject = JSONObject()) {
        vault.put("mail_legacy_retired", JSONObject().put("v", 1).put("at", "2026-11-17T00:00:00Z"))
        absorb_rotated_vault(session_key_store, vault, SealedVault(b64(random_bytes(64)), b64(random_bytes(12))))
    }

    @Test
    fun a_retired_vault_stops_previous_identity_keys_opening_mail() {
        session_key_store.put_identity_key(current_identity_key)
        session_key_store.put_previous_keys(listOf(old_identity_key))
        val (ciphertext, nonce) = identity_envelope(old_identity_key)

        read_retired_vault()

        assertThrows(Exception::class.java) {
            repo.decrypt_envelope_identity_key(ciphertext, nonce)
        }
    }

    @Test
    fun a_retired_vault_stops_the_public_placeholder_key_opening_mail() {
        session_key_store.put_identity_key("undefined")
        val (ciphertext, nonce) = identity_envelope("undefined")

        read_retired_vault()

        assertThrows(Exception::class.java) {
            repo.decrypt_envelope_identity_key(ciphertext, nonce)
        }
    }

    @Test
    fun a_retired_vault_stops_legacy_keks_opening_password_envelopes() = runBlocking {
        val kek = random_bytes(32)
        val sealed = password_envelope(random_bytes(16), kek)

        read_retired_vault(JSONObject().put("legacy_keks", JSONArray().put(JSONObject().put("k", b64(kek)))))

        assertNull(repo.decrypt_envelope_with_heal(sealed, password_marker_nonce).envelope)
    }

    @Test
    fun a_retired_vault_still_opens_mail_under_the_current_identity_key() {
        session_key_store.put_identity_key(current_identity_key)
        session_key_store.put_previous_keys(listOf(old_identity_key))
        val (ciphertext, nonce) = identity_envelope(current_identity_key)

        read_retired_vault()

        assertEquals(envelope_json, String(repo.decrypt_envelope_identity_key(ciphertext, nonce), Charsets.UTF_8))
    }

    @Test
    fun legacy_keks_open_password_envelopes_before_retirement() = runBlocking {
        val kek = random_bytes(32)
        session_key_store.put_legacy_keks(listOf(b64(kek)))
        val sealed = password_envelope(random_bytes(16), kek)

        assertEquals(
            "kept on the server",
            repo.decrypt_envelope_with_heal(sealed, password_marker_nonce).envelope?.body_text,
        )
    }
}
