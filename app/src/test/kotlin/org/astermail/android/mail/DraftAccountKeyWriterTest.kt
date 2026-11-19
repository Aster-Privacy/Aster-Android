//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.crypto.AccountDataWriter
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DraftAccountKeyWriterTest {

    private val draft_key = ByteArray(32) { (it * 5 + 1).toByte() }
    private val json = """{"subject":"Plan","body_html":"<p>Draft</p>"}"""

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var keys_api: KeysApi
    private lateinit var repo: MailRepository

    private var has_write_key = true
    private var format_writes = true
    private var decrypt_keks: List<String> = emptyList()

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        has_write_key = true
        format_writes = true
        decrypt_keks = emptyList()

        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } returns "draft_identity_key"
        every { session_key_store.get_previous_keys() } returns emptyList()
        every { session_key_store.get_passphrase() } answers { "draft passphrase".toByteArray(Charsets.UTF_8) }
        every { session_key_store.get_data_kek() } returns null
        every { session_key_store.get_decrypt_keks() } answers { decrypt_keks }
        every { session_key_store.get_password_salt() } answers { ByteArray(16) { 3 } }
        every { session_key_store.get_retired_password_salts() } returns emptyList()
        every { session_key_store.has_account_write_kek(AccountDataWriter.DRAFT_CONTEXT) } answers { has_write_key }
        every { session_key_store.get_account_write_kek(AccountDataWriter.DRAFT_CONTEXT) } answers {
            if (has_write_key) draft_key.copyOf() else null
        }
        keys_api = mockk(relaxed = true)
        coEvery { keys_api.get_account_key_format_writes() } answers { format_writes }

        repo = MailRepository(
            mail_api = mockk(relaxed = true),
            send_api = mockk(relaxed = true),
            snooze_api = mockk(relaxed = true),
            labels_api = mockk(relaxed = true),
            keys_api = keys_api,
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

    private fun decode(b64: String): ByteArray = java.util.Base64.getDecoder().decode(b64)

    private fun open_with_draft_key(sealed: Pair<String, String>): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(draft_key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, decode(sealed.second)),
        )
        return String(cipher.doFinal(decode(sealed.first)), Charsets.UTF_8)
    }

    private fun opens_with_identity_material(material: String, sealed: Pair<String, String>): Boolean {
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest((material + "astermail-envelope-v1").toByteArray(Charsets.UTF_8))
        return runCatching {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(key, "AES"),
                javax.crypto.spec.GCMParameterSpec(128, decode(sealed.second)),
            )
            String(cipher.doFinal(decode(sealed.first)), Charsets.UTF_8) == json
        }.getOrDefault(false)
    }

    private fun is_legacy_passphrase_format(sealed: Pair<String, String>): Boolean =
        decode(sealed.second).contentEquals(byteArrayOf(1))

    @Test
    fun writes_drafts_with_the_account_key_when_the_flag_is_on() = runBlocking {
        val sealed = repo.encrypt_draft_envelope(json)

        assertEquals(12, decode(sealed.second).size)
        assertEquals(json, open_with_draft_key(sealed))
    }

    @Test
    fun uses_a_fresh_nonce_for_each_draft() = runBlocking {
        val first = repo.encrypt_draft_envelope(json)
        val second = repo.encrypt_draft_envelope(json)

        assertTrue(first.second != second.second)
        assertTrue(first.first != second.first)
    }

    @Test
    fun keeps_the_legacy_format_when_the_flag_is_off() = runBlocking {
        format_writes = false

        assertTrue(is_legacy_passphrase_format(repo.encrypt_draft_envelope(json)))
    }

    @Test
    fun keeps_the_legacy_format_without_a_write_key_and_skips_the_flag() = runBlocking {
        has_write_key = false

        assertTrue(is_legacy_passphrase_format(repo.encrypt_draft_envelope(json)))
        coVerify(exactly = 0) { keys_api.get_account_key_format_writes() }
    }

    @Test
    fun keeps_the_legacy_format_when_the_capability_request_throws() = runBlocking {
        coEvery { keys_api.get_account_key_format_writes() } throws RuntimeException("offline")

        assertTrue(is_legacy_passphrase_format(repo.encrypt_draft_envelope(json)))
    }

    @Test
    fun a_draft_written_with_the_account_key_opens_on_this_client() = runBlocking {
        val sealed = repo.encrypt_draft_envelope(json)
        decrypt_keks = listOf(java.util.Base64.getEncoder().encodeToString(draft_key))

        val opened = repo.decrypt_envelope_identity_key(sealed.first, decode(sealed.second))

        assertEquals(json, String(opened, Charsets.UTF_8))
    }

    @Test
    fun never_writes_a_draft_under_a_placeholder_identity_key() = runBlocking {
        has_write_key = false
        every { session_key_store.get_passphrase() } returns null
        every { session_key_store.mail_legacy_retired() } returns false
        for (material in listOf("null", "", " Undefined ")) {
            every { session_key_store.get_identity_key() } returns material

            val outcome = runCatching { repo.encrypt_draft_envelope(json) }

            assertTrue(
                "a draft under '$material' should be refused, got ${outcome.getOrNull()}",
                outcome.exceptionOrNull() is IllegalStateException,
            )
            assertEquals("no key material available", outcome.exceptionOrNull()?.message)
        }
    }

    @Test
    fun still_writes_a_legacy_draft_under_a_real_identity_key() = runBlocking {
        has_write_key = false
        every { session_key_store.get_passphrase() } returns null
        every { session_key_store.mail_legacy_retired() } returns false

        val sealed = repo.encrypt_draft_envelope(json)

        assertTrue(opens_with_identity_material("draft_identity_key", sealed))
    }
}
