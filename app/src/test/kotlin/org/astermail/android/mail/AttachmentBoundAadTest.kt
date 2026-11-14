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
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class AttachmentBoundAadTest {

    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { (it + 100).toByte() }
    private val plaintext = "bound attachment vector".toByteArray()
    private val bound_vector = "KnSrCB3JN+pKAzyAtwAEiWK0Y2n/A4EnWy1F74Tph6cRl+lEq4yU"

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        InboundAttachmentKeyStore.clear()
    }

    @After
    fun teardown() {
        InboundAttachmentKeyStore.clear()
        unmockkAll()
    }

    private fun b64(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun seal(aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    @Test
    fun aad_matches_the_web_and_bridge_label() {
        assertArrayEquals(
            "aster-attachment-v2|att=3|part=data".toByteArray(),
            MailRepository.attachment_data_aad(3),
        )
    }

    @Test
    fun opens_the_shared_bound_vector() {
        val out = MailRepository.decrypt_attachment_bytes(bound_vector, b64(nonce), b64(key), "item-1", 3)
        assertArrayEquals(plaintext, out)
    }

    @Test
    fun writes_the_unbound_form_while_the_switch_is_off() {
        assertArrayEquals(seal(null), MailRepository.seal_attachment_bytes(plaintext, key, nonce, 3, false))
    }

    @Test
    fun writes_the_shared_bound_vector_when_the_switch_is_on() {
        assertArrayEquals(
            java.util.Base64.getDecoder().decode(bound_vector),
            MailRepository.seal_attachment_bytes(plaintext, key, nonce, 3, true),
        )
    }

    @Test
    fun still_opens_attachments_written_without_aad() {
        val out = MailRepository.decrypt_attachment_bytes(b64(seal(null)), b64(nonce), b64(key), "item-1", 3)
        assertArrayEquals(plaintext, out)
    }

    @Test
    fun refuses_a_bound_attachment_moved_to_another_slot() {
        assertThrows(Exception::class.java) {
            MailRepository.decrypt_attachment_bytes(bound_vector, b64(nonce), b64(key), "item-1", 4)
        }
    }

    @Test
    fun refuses_a_bound_attachment_without_its_slot() {
        assertThrows(Exception::class.java) {
            MailRepository.decrypt_attachment_bytes(bound_vector, b64(nonce), b64(key), "item-1", null)
        }
    }

    @Test
    fun refuses_a_tampered_bound_attachment() {
        val sealed = java.util.Base64.getDecoder().decode(bound_vector)
        sealed[0] = (sealed[0].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            MailRepository.decrypt_attachment_bytes(b64(sealed), b64(nonce), b64(key), "item-1", 3)
        }
    }

    @Test
    fun bound_writer_output_round_trips() {
        val sealed = seal(MailRepository.attachment_data_aad(7))
        val out = MailRepository.decrypt_attachment_bytes(b64(sealed), b64(nonce), b64(key), "item-1", 7)
        assertArrayEquals(plaintext, out)
    }
}
