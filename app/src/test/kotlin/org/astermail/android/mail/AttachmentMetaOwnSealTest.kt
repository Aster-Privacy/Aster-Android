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
import org.astermail.android.R
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.send.ExternalAttachmentPayload
import org.astermail.android.api.send.SendAttachmentPayload
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.PgpSignatureStatus
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttachmentMetaOwnSealTest {

    private val passphrase = "attachment passphrase"

    private val own_key by lazy {
        PgpKeyGenerator.generate("Owner", "owner@astermail.org", passphrase.toCharArray())
    }
    private val other_key by lazy {
        PgpKeyGenerator.generate("Other", "other@astermail.org", passphrase.toCharArray())
    }

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var keys_api: KeysApi
    private lateinit var repo: MailRepository

    private var identity_key: String? = null
    private var previous_keys: List<String> = emptyList()
    private var format_writes = true

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        InboundAttachmentKeyStore.clear()
        identity_key = own_key.armored_private_key
        previous_keys = emptyList()
        format_writes = true

        val context = mockk<android.content.Context>(relaxed = true)
        every { context.getString(R.string.attachment_unnamed) } returns "Attachment"
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } answers { identity_key }
        every { session_key_store.get_previous_keys() } answers { previous_keys }
        every { session_key_store.get_passphrase() } answers { passphrase.toByteArray(Charsets.UTF_8) }
        every { session_key_store.get_user_email() } returns "owner@astermail.org"
        every { session_key_store.has_ratchet_keys() } returns false
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
            context = context,
            auth_repository = mockk(relaxed = true),
        )
    }

    @After
    fun teardown() {
        InboundAttachmentKeyStore.clear()
        unmockkAll()
    }

    private fun attachment(name: String, bytes: ByteArray) = ExternalAttachmentPayload(
        data = java.util.Base64.getEncoder().encodeToString(bytes),
        filename = name,
        content_type = "application/pdf",
        size_bytes = bytes.size.toLong(),
        content_id = "cid-1",
    )

    private fun build(vararg attachments: ExternalAttachmentPayload): List<SendAttachmentPayload> =
        runBlocking { repo.build_internal_attachments(emptyList(), attachments.toList()) }

    private fun text(b64: String): String =
        String(java.util.Base64.getDecoder().decode(b64), Charsets.UTF_8)

    private fun nonce(payload: SendAttachmentPayload): ByteArray =
        java.util.Base64.getDecoder().decode(payload.sender_meta_nonce)

    private fun open(payload: SendAttachmentPayload) =
        repo.decrypt_attachment_meta(payload.sender_encrypted_meta, payload.sender_meta_nonce, "item-1", 0, 3L)

    @Test
    fun seals_sender_meta_to_the_identity_key_with_a_zero_nonce() {
        val payload = build(attachment("report.pdf", byteArrayOf(1, 2, 3))).single()
        val armored = text(payload.sender_encrypted_meta)

        assertTrue(armored.startsWith("-----BEGIN PGP MESSAGE-----"))
        assertFalse(armored.contains("report.pdf"))
        assertArrayEquals(ByteArray(12), nonce(payload))
    }

    @Test
    fun sealed_sender_meta_opens_through_the_reader() {
        val meta = open(build(attachment("report.pdf", byteArrayOf(1, 2, 3))).single())

        assertFalse(meta.is_placeholder)
        assertEquals("report.pdf", meta.filename)
        assertEquals("application/pdf", meta.content_type)
        assertEquals("cid-1", meta.content_id)
        assertTrue(meta.session_key != null)
    }

    @Test
    fun sealed_sender_meta_is_signed_by_the_owner() {
        val payload = build(attachment("report.pdf", byteArrayOf(4))).single()
        val result = PgpDecryptor.decrypt_with_own_keys_status(
            text(payload.sender_encrypted_meta),
            listOf(own_key.armored_private_key),
            passphrase.toCharArray(),
        )!!

        assertEquals(PgpSignatureStatus.VALID, result.signature)
        assertTrue(result.plaintext!!.contains("report.pdf"))
    }

    @Test
    fun sealed_sender_meta_opens_after_the_key_moves_to_previous_keys() {
        val payload = build(attachment("report.pdf", byteArrayOf(5))).single()
        identity_key = other_key.armored_private_key
        previous_keys = listOf(own_key.armored_private_key)

        assertEquals("report.pdf", open(payload).filename)
    }

    @Test
    fun checks_the_flag_once_for_several_attachments() {
        val payloads = build(attachment("a.pdf", byteArrayOf(1)), attachment("b.pdf", byteArrayOf(2)))

        coVerify(exactly = 1) { keys_api.get_account_key_format_writes() }
        payloads.forEach {
            assertTrue(text(it.sender_encrypted_meta).startsWith("-----BEGIN PGP MESSAGE-----"))
        }
    }

    @Test
    fun writes_legacy_meta_when_the_flag_is_off() {
        format_writes = false
        val payload = build(attachment("report.pdf", byteArrayOf(6))).single()

        assertFalse(text(payload.sender_encrypted_meta).contains("BEGIN PGP"))
        assertEquals("report.pdf", open(payload).filename)
    }

    @Test
    fun writes_legacy_meta_when_the_capability_request_throws() {
        coEvery { keys_api.get_account_key_format_writes() } throws RuntimeException("offline")
        val payload = build(attachment("report.pdf", byteArrayOf(7))).single()

        assertFalse(text(payload.sender_encrypted_meta).contains("BEGIN PGP"))
        assertEquals("report.pdf", open(payload).filename)
    }

    @Test
    fun writes_legacy_meta_when_the_key_is_not_pgp() {
        identity_key = """{"kty":"EC"}"""
        val payload = build(attachment("report.pdf", byteArrayOf(8))).single()

        assertFalse(text(payload.sender_encrypted_meta).contains("BEGIN PGP"))
        assertEquals("report.pdf", open(payload).filename)
    }

    @Test
    fun writes_legacy_meta_without_an_identity_key() {
        identity_key = null
        val payload = build(attachment("report.pdf", byteArrayOf(9))).single()

        assertFalse(text(payload.sender_encrypted_meta).contains("BEGIN PGP"))
        coVerify(exactly = 0) { keys_api.get_account_key_format_writes() }
    }
}
