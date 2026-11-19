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
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.PasswordKdf
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LoginSaltEnvelopeTest {

    private val envelope_json = """{"subject":"Stored","body_text":"kept on the server"}"""
    private val passphrase = "account passphrase".toByteArray(Charsets.UTF_8)
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

    private fun passphrase_envelope(salt: ByteArray): String {
        val key = PasswordKdf.derive_aes_key(passphrase, salt, 310000)
        try {
            return password_envelope(salt, key)
        } finally {
            key.fill(0)
        }
    }

    private suspend fun open(sealed: String): String? =
        repo.decrypt_envelope_with_heal(sealed, password_marker_nonce).envelope?.body_text

    @Test
    fun refuses_an_envelope_keyed_by_the_current_login_salt() = runBlocking {
        val login_salt = random_bytes(16)
        session_key_store.put_passphrase(passphrase.copyOf())
        session_key_store.put_password_salt(login_salt)

        assertNull(open(passphrase_envelope(login_salt)))
    }

    @Test
    fun refuses_an_envelope_keyed_by_a_retired_login_salt() = runBlocking {
        val old_salt = random_bytes(16)
        session_key_store.put_passphrase(passphrase.copyOf())
        session_key_store.put_password_salt(random_bytes(16))
        session_key_store.retire_password_salts("user-a", listOf(old_salt))

        assertNull(open(passphrase_envelope(old_salt)))
    }

    @Test
    fun opens_an_envelope_with_its_own_random_salt() = runBlocking {
        session_key_store.put_passphrase(passphrase.copyOf())
        session_key_store.put_password_salt(random_bytes(16))

        assertEquals("kept on the server", open(passphrase_envelope(random_bytes(16))))
    }

    @Test
    fun a_login_hash_sealed_envelope_opens_without_counting_as_own_sealed() = runBlocking {
        val login_salt = random_bytes(16)
        session_key_store.put_passphrase(passphrase.copyOf())
        session_key_store.put_password_salt(login_salt.copyOf())
        val login_hash = CryptoNative.derive_pbkdf2_hash(passphrase.copyOf(), login_salt, 310000)
        val iv = random_bytes(12)
        val sealed = b64(aes_gcm(login_hash, iv, envelope_json.toByteArray(Charsets.UTF_8)))
        login_hash.fill(0)

        val envelope = repo.decrypt_envelope_with_heal(sealed, b64(iv)).envelope

        assertNotNull(envelope)
        assertEquals("kept on the server", envelope!!.body_text)
        assertFalse(envelope.own_sealed)
    }
}
