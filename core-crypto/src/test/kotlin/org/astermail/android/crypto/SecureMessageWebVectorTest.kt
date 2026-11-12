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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class SecureMessageWebVectorTest {

    private val vector: Map<String, String> = javaClass.classLoader!!
        .getResourceAsStream("secure_message_web_vector.txt")!!
        .bufferedReader()
        .readLines()
        .filter { it.isNotBlank() }
        .associate { line ->
            val index = line.indexOf('=')
            line.substring(0, index) to line.substring(index + 1)
        }

    private fun value(key: String): String = vector.getValue(key)

    private fun bytes(key: String): ByteArray = Base64.getDecoder().decode(value(key))

    private fun text(key: String): String = String(bytes(key), Charsets.UTF_8)

    private fun open_web_message(): OpenedSecureMessage = SecureMessageCrypto.open(
        password = text("password_b64"),
        kdf_salt = value("kdf_salt"),
        kem_ciphertext = value("kem_ciphertext"),
        encrypted_kem_seed = value("encrypted_kem_seed"),
        kem_seed_nonce = value("kem_seed_nonce"),
        encrypted_subject = SecureMessageField(value("subject_ciphertext"), value("subject_nonce")),
        encrypted_body = SecureMessageField(value("body_ciphertext"), value("body_nonce")),
        attachments_bundle = value("attachments_bundle"),
    )

    @Test
    fun the_proof_derived_here_matches_the_web_client() {
        assertEquals(
            value("auth_proof"),
            SecureMessageCrypto.derive_auth_proof(text("password_b64"), value("kdf_salt")),
        )
    }

    @Test
    fun a_message_sealed_by_the_web_client_opens_here() {
        val opened = open_web_message()

        assertEquals(text("subject_b64"), opened.subject)
        assertEquals(text("body_b64"), opened.body)
    }

    @Test
    fun attachments_sealed_by_the_web_client_open_here() {
        val attachments = open_web_message().attachments

        assertEquals(2, attachments.size)
        assertEquals(text("attachment_0_name_b64"), attachments[0].filename)
        assertEquals(value("attachment_0_type"), attachments[0].content_type)
        assertArrayEquals(bytes("attachment_0_data"), attachments[0].data)
        assertEquals(text("attachment_1_name_b64"), attachments[1].filename)
        assertEquals(value("attachment_1_type"), attachments[1].content_type)
        assertArrayEquals(bytes("attachment_1_data"), attachments[1].data)
    }
}
