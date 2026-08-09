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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.R
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttachmentMetaSealedTest {

    private lateinit var context: android.content.Context
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    private val placeholder = "Attachment"

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

        context = mockk(relaxed = true)
        every { context.getString(R.string.attachment_unnamed) } returns placeholder
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_passphrase() } returns null
        every { session_key_store.get_user_email() } returns "me@astermail.org"
        every { session_key_store.has_ratchet_keys() } returns false

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
            pending_send_dao = mockk(relaxed = true),
            context = context,
            auth_repository = mockk(relaxed = true),
        )
    }

    @After
    fun teardown() {
        InboundAttachmentKeyStore.clear()
        unmockkAll()
    }

    private fun b64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    private fun seal(plaintext: String, key: ByteArray, nonce: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return b64(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    private val session_key = ByteArray(32) { (it + 1).toByte() }
    private val meta_nonce = ByteArray(12) { (it + 40).toByte() }

    @Test
    fun sealed_metadata_round_trips_with_the_envelope_session_key() {
        val sealed_meta = seal(
            """{"filename":"payslip.pdf","content_type":"application/pdf","content_id":"cid-42"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-1",
            """{"attachment_keys":[{"seq":0,"key":"${b64(session_key)}"}]}""",
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-1", 0, 11L)

        assertFalse(meta.is_placeholder)
        assertEquals("payslip.pdf", meta.filename)
        assertEquals("application/pdf", meta.content_type)
        assertEquals("cid-42", meta.content_id)
        assertEquals(b64(session_key), meta.session_key)
        assertEquals(11L, meta.size_bytes)
    }

    @Test
    fun sealed_tag_is_not_split_off_by_hand() {
        val plaintext = """{"filename":"a.txt","content_type":"text/plain"}"""
        val sealed_meta = seal(plaintext, session_key, meta_nonce)
        val raw = java.util.Base64.getDecoder().decode(sealed_meta)

        assertEquals(plaintext.toByteArray(Charsets.UTF_8).size + 16, raw.size)

        val meta = MailRepository.decrypt_sealed_attachment_meta(
            sealed_meta,
            meta_nonce,
            b64(session_key),
        )

        assertEquals("a.txt", meta?.filename)
    }

    @Test
    fun envelope_fields_win_over_the_sealed_row() {
        val sealed_meta = seal(
            """{"filename":"row.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-1",
            """
            {"attachment_keys":[{"seq":0,"key":"${b64(session_key)}",
            "filename":"envelope.pdf","content_type":"application/pdf",
            "content_id":"cid-7","size":4096}]}
            """.trimIndent().replace("\n", ""),
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-1", 0, 11L)

        assertEquals("envelope.pdf", meta.filename)
        assertEquals("cid-7", meta.content_id)
        assertEquals(4096L, meta.size_bytes)
    }

    @Test
    fun zero_nonce_row_is_read_as_legacy_plaintext_json() {
        val plaintext = """{"filename":"legacy.txt","content_type":"text/plain","session_key":""}"""
        val encoded = b64(plaintext.toByteArray(Charsets.UTF_8))

        val meta = repo.decrypt_attachment_meta(encoded, b64(ByteArray(12)), "item-2", 0, 7L)

        assertFalse(meta.is_placeholder)
        assertEquals("legacy.txt", meta.filename)
        assertEquals("text/plain", meta.content_type)
        assertEquals("", meta.session_key)
        assertEquals(7L, meta.size_bytes)
    }

    @Test
    fun empty_nonce_row_is_read_as_legacy_plaintext_json() {
        val plaintext = """{"filename":"empty.txt","content_type":"text/plain"}"""
        val encoded = b64(plaintext.toByteArray(Charsets.UTF_8))

        val from_blank = repo.decrypt_attachment_meta(encoded, "", "item-2", 0, 1L)
        val from_null = repo.decrypt_attachment_meta(encoded, null, "item-2", 0, 1L)

        assertEquals("empty.txt", from_blank.filename)
        assertEquals("empty.txt", from_null.filename)
    }

    @Test
    fun a_nonce_that_is_non_zero_only_in_its_last_byte_is_sealed() {
        val nonce = ByteArray(12).also { it[11] = 1 }
        val sealed_meta = seal(
            """{"filename":"tail.pdf","content_type":"application/pdf"}""",
            session_key,
            nonce,
        )
        InboundAttachmentKeyStore.register("item-3", 0, b64(session_key))

        assertTrue(MailRepository.is_sealed_meta_nonce(nonce))
        assertFalse(MailRepository.is_sealed_meta_nonce(ByteArray(12)))
        assertFalse(MailRepository.is_sealed_meta_nonce(ByteArray(0)))
        assertFalse(MailRepository.is_sealed_meta_nonce(null))

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(nonce), "item-3", 0, 3L)

        assertEquals("tail.pdf", meta.filename)
    }

    @Test
    fun sealed_json_is_never_read_as_plaintext_when_the_nonce_is_zero() {
        val sealed_meta = seal(
            """{"filename":"secret.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register("item-4", 0, b64(session_key))

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(ByteArray(12)), "item-4", 0, 5L)

        assertTrue(meta.is_placeholder)
        assertEquals(placeholder, meta.filename)
    }

    @Test
    fun a_wrong_key_falls_back_to_the_placeholder_without_crashing() {
        val sealed_meta = seal(
            """{"filename":"payslip.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register("item-5", 0, b64(ByteArray(32) { 9 }))

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-5", 0, 12L)

        assertTrue(meta.is_placeholder)
        assertEquals(placeholder, meta.filename)
        assertEquals(MailRepository.DEFAULT_ATTACHMENT_CONTENT_TYPE, meta.content_type)
        assertEquals(12L, meta.size_bytes)
        assertFalse(meta.filename.contains("payslip"))
    }

    @Test
    fun a_sealed_row_without_any_envelope_key_shows_the_placeholder_and_the_row_size() {
        val sealed_meta = seal(
            """{"filename":"payslip.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-6", 0, 990L)

        assertTrue(meta.is_placeholder)
        assertEquals(placeholder, meta.filename)
        assertEquals("", meta.session_key)
        assertEquals(990L, meta.size_bytes)
    }

    @Test
    fun entries_are_matched_by_seq_not_by_array_position() {
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-7",
            """
            {"attachment_keys":[
            {"seq":3,"key":"${b64(ByteArray(32) { 3 })}","filename":"third.pdf","content_type":"application/pdf","size":300},
            {"seq":0,"key":"${b64(ByteArray(32) { 0 })}","filename":"first.pdf","content_type":"application/pdf","size":100},
            {"seq":1,"key":"${b64(ByteArray(32) { 1 })}","filename":"second.pdf","content_type":"application/pdf","size":200}]}
            """.trimIndent().replace("\n", ""),
        )

        assertEquals("first.pdf", InboundAttachmentKeyStore.entry("item-7", 0)?.filename)
        assertEquals("second.pdf", InboundAttachmentKeyStore.entry("item-7", 1)?.filename)
        assertEquals("third.pdf", InboundAttachmentKeyStore.entry("item-7", 3)?.filename)
        assertNull(InboundAttachmentKeyStore.entry("item-7", 2))
        assertEquals(300L, InboundAttachmentKeyStore.entry("item-7", 3)?.size)
    }

    @Test
    fun a_legacy_entry_without_fields_falls_back_to_the_sealed_row() {
        val sealed_meta = seal(
            """{"filename":"row.pdf","content_type":"application/pdf","content_id":"cid-9"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-8",
            """{"attachment_keys":[{"seq":0,"key":"${b64(session_key)}"}]}""",
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-8", 0, 64L)

        assertEquals("row.pdf", meta.filename)
        assertEquals("cid-9", meta.content_id)
        assertEquals(64L, meta.size_bytes)
    }

    @Test
    fun a_missing_content_type_falls_back_to_the_default_type() {
        val sealed_meta = seal("""{"filename":"typeless.bin"}""", session_key, meta_nonce)
        InboundAttachmentKeyStore.register("item-9", 0, b64(session_key))

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-9", 0, 8L)

        assertEquals("typeless.bin", meta.filename)
        assertEquals(MailRepository.DEFAULT_ATTACHMENT_CONTENT_TYPE, meta.content_type)
    }

    @Test
    fun a_missing_content_id_is_not_synthesized() {
        val sealed_meta = seal(
            """{"filename":"plain.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-10",
            """{"attachment_keys":[{"seq":0,"key":"${b64(session_key)}","filename":"plain.pdf","content_type":"application/pdf","size":12}]}""",
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-10", 0, 12L)

        assertNull(meta.content_id)
    }

    @Test
    fun a_missing_envelope_size_falls_back_to_the_row_size() {
        val sealed_meta = seal(
            """{"filename":"sized.pdf","content_type":"application/pdf"}""",
            session_key,
            meta_nonce,
        )
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-11",
            """{"attachment_keys":[{"seq":0,"key":"${b64(session_key)}","filename":"sized.pdf","content_type":"application/pdf"}]}""",
        )

        val meta = repo.decrypt_attachment_meta(sealed_meta, b64(meta_nonce), "item-11", 0, 4242L)

        assertEquals(4242L, meta.size_bytes)
    }

    @Test
    fun garbage_metadata_never_surfaces_ciphertext_as_a_name() {
        val garbage = b64(ByteArray(64) { (it * 7).toByte() })

        val meta = repo.decrypt_attachment_meta(garbage, b64(meta_nonce), "item-12", 0, 64L)

        assertTrue(meta.is_placeholder)
        assertEquals(placeholder, meta.filename)
    }
}
