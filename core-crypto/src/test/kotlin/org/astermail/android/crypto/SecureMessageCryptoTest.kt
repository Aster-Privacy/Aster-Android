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

package org.astermail.android.crypto

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SecureMessageCryptoTest {

    private val password = "correct horse battery staple 42"
    private val subject = "Quarterly numbers"
    private val body = "<p>Revenue is up.</p>"

    private fun decoded_size(value: String): Int = Base64.getDecoder().decode(value).size

    private fun open(
        sealed: SealedSecureMessage,
        with_password: String = password,
    ): OpenedSecureMessage = SecureMessageCrypto.open(
        password = with_password,
        kdf_salt = sealed.kdf_salt,
        kem_ciphertext = sealed.kem_ciphertext,
        encrypted_kem_seed = sealed.encrypted_kem_seed,
        kem_seed_nonce = sealed.kem_seed_nonce,
        encrypted_subject = sealed.encrypted_subject,
        encrypted_body = sealed.encrypted_body,
        attachments_bundle = sealed.attachments_bundle,
    )

    @Test
    fun a_sealed_message_opens_with_its_password() {
        val opened = open(SecureMessageCrypto.seal(password, subject, body))

        assertEquals(subject, opened.subject)
        assertEquals(body, opened.body)
        assertTrue(opened.attachments.isEmpty())
    }

    @Test
    fun a_sealed_message_has_the_field_sizes_the_server_expects() {
        val sealed = SecureMessageCrypto.seal(password, subject, body)

        assertEquals(16, decoded_size(sealed.kdf_salt))
        assertEquals(32, decoded_size(sealed.auth_proof))
        assertEquals(1088, decoded_size(sealed.kem_ciphertext))
        assertEquals(80, decoded_size(sealed.encrypted_kem_seed))
        assertEquals(12, decoded_size(sealed.kem_seed_nonce))
        assertEquals(12, decoded_size(sealed.encrypted_subject.nonce))
        assertEquals(12, decoded_size(sealed.encrypted_body.nonce))
        assertEquals(
            subject.toByteArray(Charsets.UTF_8).size + 16,
            decoded_size(sealed.encrypted_subject.ciphertext),
        )
        assertNull(sealed.attachments_bundle)
    }

    @Test
    fun a_sealed_message_carries_no_readable_content() {
        val sealed = SecureMessageCrypto.seal(password, subject, body)
        val everything = listOf(
            sealed.kdf_salt,
            sealed.auth_proof,
            sealed.kem_ciphertext,
            sealed.encrypted_kem_seed,
            sealed.encrypted_subject.ciphertext,
            sealed.encrypted_body.ciphertext,
        ).joinToString("|") { String(Base64.getDecoder().decode(it), Charsets.ISO_8859_1) }

        assertFalse(everything.contains(password))
        assertFalse(everything.contains(subject))
        assertFalse(everything.contains(body))
    }

    @Test
    fun the_proof_matches_the_one_derived_from_the_password_and_salt() {
        val sealed = SecureMessageCrypto.seal(password, subject, body)

        assertEquals(
            sealed.auth_proof,
            SecureMessageCrypto.derive_auth_proof(password, sealed.kdf_salt),
        )
        assertNotEquals(
            sealed.auth_proof,
            SecureMessageCrypto.derive_auth_proof(password + "x", sealed.kdf_salt),
        )
    }

    @Test
    fun every_seal_uses_fresh_randomness() {
        val first = SecureMessageCrypto.seal(password, subject, body)
        val second = SecureMessageCrypto.seal(password, subject, body)

        assertNotEquals(first.kdf_salt, second.kdf_salt)
        assertNotEquals(first.auth_proof, second.auth_proof)
        assertNotEquals(first.kem_ciphertext, second.kem_ciphertext)
        assertNotEquals(first.encrypted_subject.ciphertext, second.encrypted_subject.ciphertext)
    }

    @Test
    fun a_wrong_password_does_not_open_the_message() {
        val sealed = SecureMessageCrypto.seal(password, subject, body)

        assertThrows(Exception::class.java) { open(sealed, with_password = "another password 99") }
    }

    @Test
    fun the_subject_and_body_cannot_be_swapped() {
        val sealed = SecureMessageCrypto.seal(password, subject, body)
        val swapped = sealed.copy(
            encrypted_subject = sealed.encrypted_body,
            encrypted_body = sealed.encrypted_subject,
        )

        assertThrows(Exception::class.java) { open(swapped) }
    }

    @Test
    fun attachments_round_trip_in_order() {
        val first = SecureMessageAttachment(
            "chart.bin",
            "application/octet-stream",
            byteArrayOf(0, 1, 2, -6, -1),
        )
        val second = SecureMessageAttachment(
            "notes.txt",
            "text/plain",
            "plain notes".toByteArray(Charsets.UTF_8),
        )
        val sealed = SecureMessageCrypto.seal(password, subject, body, listOf(first, second))
        val opened = open(sealed).attachments

        assertEquals(2, opened.size)
        assertEquals("chart.bin", opened[0].filename)
        assertEquals("application/octet-stream", opened[0].content_type)
        assertArrayEquals(first.data, opened[0].data)
        assertEquals("notes.txt", opened[1].filename)
        assertEquals("text/plain", opened[1].content_type)
        assertArrayEquals(second.data, opened[1].data)
    }

    @Test
    fun the_attachment_bundle_is_an_encoded_envelope() {
        val sealed = SecureMessageCrypto.seal(
            password,
            subject,
            body,
            listOf(SecureMessageAttachment("a.txt", "text/plain", byteArrayOf(1, 2, 3))),
        )
        val envelope = JSONObject(
            String(Base64.getDecoder().decode(sealed.attachments_bundle), Charsets.UTF_8),
        )

        assertTrue(envelope.getString("ciphertext").isNotEmpty())
        assertEquals(12, decoded_size(envelope.getString("nonce")))
    }

    @Test
    fun an_empty_password_is_refused() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureMessageCrypto.seal("", subject, body)
        }
    }
}
