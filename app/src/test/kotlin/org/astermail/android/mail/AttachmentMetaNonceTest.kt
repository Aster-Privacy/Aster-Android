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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttachmentMetaNonceTest {

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun b64(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)

    @Test
    fun keeps_a_real_twelve_byte_nonce() {
        val nonce = b64(ByteArray(12) { (it + 1).toByte() })
        assertEquals(nonce, MailRepository.server_meta_nonce(nonce))
    }

    @Test
    fun replaces_the_pbkdf2_marker_with_a_twelve_byte_placeholder() {
        val marker = b64(byteArrayOf(1))
        val out = MailRepository.server_meta_nonce(marker)
        val decoded = java.util.Base64.getDecoder().decode(out)
        assertEquals(12, decoded.size)
        assertTrue(decoded.all { it == 0.toByte() })
    }

    @Test
    fun replaces_unparseable_nonces_with_the_placeholder() {
        val out = MailRepository.server_meta_nonce("")
        assertEquals(12, java.util.Base64.getDecoder().decode(out).size)
    }

    @Test
    fun recognizes_the_placeholder_nonce() {
        assertTrue(MailRepository.is_placeholder_meta_nonce(ByteArray(12)))
        assertFalse(MailRepository.is_placeholder_meta_nonce(ByteArray(11)))
        assertFalse(MailRepository.is_placeholder_meta_nonce(ByteArray(12) { 7 }))
        assertFalse(MailRepository.is_placeholder_meta_nonce(byteArrayOf(1)))
    }
}
