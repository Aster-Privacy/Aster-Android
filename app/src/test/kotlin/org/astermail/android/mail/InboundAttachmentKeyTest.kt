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
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class InboundAttachmentKeyTest {

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
    }

    @After
    fun teardown() {
        InboundAttachmentKeyStore.clear()
        unmockkAll()
    }

    private fun b64(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun seal(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(plaintext)
    }

    @Test
    fun registers_and_looks_up_a_key() {
        InboundAttachmentKeyStore.register("item-1", 0, "abc")

        assertEquals("abc", InboundAttachmentKeyStore.key("item-1", 0))
        assertNull(InboundAttachmentKeyStore.key("item-1", 1))
        assertNull(InboundAttachmentKeyStore.key("item-2", 0))
        assertNull(InboundAttachmentKeyStore.key(null, 0))
        assertNull(InboundAttachmentKeyStore.key("item-1", null))
    }

    @Test
    fun rejects_blank_identifiers_and_keys() {
        InboundAttachmentKeyStore.register("", 0, "abc")
        InboundAttachmentKeyStore.register("item-1", null, "abc")
        InboundAttachmentKeyStore.register("item-1", 0, "")
        InboundAttachmentKeyStore.register(null, 0, "abc")

        assertEquals(0, InboundAttachmentKeyStore.size())
    }

    @Test
    fun re_registering_overwrites_without_growing() {
        InboundAttachmentKeyStore.register("item-1", 3, "first")
        InboundAttachmentKeyStore.register("item-1", 3, "second")

        assertEquals(1, InboundAttachmentKeyStore.size())
        assertEquals("second", InboundAttachmentKeyStore.key("item-1", 3))
    }

    @Test
    fun evicts_the_oldest_entries_past_the_bound() {
        for (seq in 0 until 5000) {
            InboundAttachmentKeyStore.register("item-1", seq, "k$seq")
        }

        assertEquals(4096, InboundAttachmentKeyStore.size())
        assertNull(InboundAttachmentKeyStore.key("item-1", 0))
        assertNull(InboundAttachmentKeyStore.key("item-1", 903))
        assertEquals("k904", InboundAttachmentKeyStore.key("item-1", 904))
        assertEquals("k4999", InboundAttachmentKeyStore.key("item-1", 4999))
    }

    @Test
    fun harvests_attachment_keys_from_an_envelope() {
        val json = """
            {"subject":"hi","attachment_keys":[{"seq":0,"key":"aaaa"},{"seq":2,"key":"bbbb"}]}
        """.trimIndent()

        InboundAttachmentKeyStore.register_from_envelope_json("item-9", json)

        assertEquals("aaaa", InboundAttachmentKeyStore.key("item-9", 0))
        assertEquals("bbbb", InboundAttachmentKeyStore.key("item-9", 2))
        assertEquals(2, InboundAttachmentKeyStore.size())
    }

    @Test
    fun ignores_envelopes_without_usable_attachment_keys() {
        InboundAttachmentKeyStore.register_from_envelope_json("item-9", """{"subject":"hi"}""")
        InboundAttachmentKeyStore.register_from_envelope_json("item-9", "not json at all")
        InboundAttachmentKeyStore.register_from_envelope_json(null, """{"attachment_keys":[{"seq":0,"key":"a"}]}""")
        InboundAttachmentKeyStore.register_from_envelope_json("item-9", null)
        InboundAttachmentKeyStore.register_from_envelope_json(
            "item-9",
            """{"attachment_keys":[{"key":"no-seq"},{"seq":1,"key":""}]}""",
        )

        assertEquals(0, InboundAttachmentKeyStore.size())
    }

    @Test
    fun clear_drops_every_entry() {
        InboundAttachmentKeyStore.register("item-1", 0, "k")
        InboundAttachmentKeyStore.clear()

        assertEquals(0, InboundAttachmentKeyStore.size())
        assertNull(InboundAttachmentKeyStore.key("item-1", 0))
    }

    @Test
    fun unencrypted_marker_accepts_only_twelve_zero_bytes() {
        assertTrue(MailRepository.is_unencrypted_stored_attachment(b64(ByteArray(12))))
        assertFalse(MailRepository.is_unencrypted_stored_attachment(b64(ByteArray(16))))
        assertFalse(MailRepository.is_unencrypted_stored_attachment(b64(ByteArray(11))))
        assertFalse(MailRepository.is_unencrypted_stored_attachment(b64(ByteArray(12) { 7 })))
        assertFalse(MailRepository.is_unencrypted_stored_attachment(""))
    }

    @Test
    fun missing_key_throws_instead_of_returning_ciphertext() {
        val ciphertext = b64("not-plaintext".toByteArray())
        val nonce = b64(ByteArray(12) { (it + 1).toByte() })

        try {
            MailRepository.decrypt_attachment_bytes(ciphertext, nonce, "", "item-1", 0)
            fail("expected AttachmentKeyUnavailableException")
        } catch (_: AttachmentKeyUnavailableException) {
        }
    }

    @Test
    fun zero_nonce_still_passes_stored_plaintext_through() {
        val payload = "stored-as-is".toByteArray()

        val out = MailRepository.decrypt_attachment_bytes(
            b64(payload),
            b64(ByteArray(12)),
            "",
            "item-1",
            0,
        )

        assertArrayEquals(payload, out)
    }

    @Test
    fun registered_key_decrypts_when_the_metadata_session_key_is_empty() {
        val key = ByteArray(32) { (it + 3).toByte() }
        val nonce = ByteArray(12) { (it + 5).toByte() }
        val plaintext = "attachment-bytes".toByteArray()
        val sealed = seal(plaintext, key, nonce)

        InboundAttachmentKeyStore.register("item-7", 2, b64(key))

        val out = MailRepository.decrypt_attachment_bytes(b64(sealed), b64(nonce), "", "item-7", 2)

        assertArrayEquals(plaintext, out)
    }

    @Test
    fun metadata_session_key_wins_over_the_registry() {
        val key = ByteArray(32) { (it + 3).toByte() }
        val nonce = ByteArray(12) { (it + 5).toByte() }
        val plaintext = "metadata-keyed".toByteArray()
        val sealed = seal(plaintext, key, nonce)

        InboundAttachmentKeyStore.register("item-7", 2, b64(ByteArray(32) { 9 }))

        val out = MailRepository.decrypt_attachment_bytes(
            b64(sealed),
            b64(nonce),
            b64(key),
            "item-7",
            2,
        )

        assertArrayEquals(plaintext, out)
    }
}
