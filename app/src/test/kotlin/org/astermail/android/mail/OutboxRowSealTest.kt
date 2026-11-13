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

import org.astermail.android.storage.outbox.PendingSendEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OutboxRowSealTest {

    private val cipher = OutboxFileCipher { ByteArray(32) { 5 } }

    private fun row(id: String = "pend_1", password: String? = "correct horse 42") = PendingSendEntity(
        id = id,
        to_json = "[\"pal@outside.example\"]",
        cc_json = "[]",
        bcc_json = "[]",
        subject = "Quarterly plans",
        body_html = "<p>hello there</p>",
        sender_email = "me@astermail.org",
        sender_display_name = null,
        thread_token = null,
        expires_at = null,
        expiry_password = password,
        attachments_json = "[]",
        sender_alias_hash = null,
        suppress_branding = null,
        draft_id = null,
        fire_at_ms = 0L,
        status = "pending",
        created_at_ms = 0L,
    )

    @Test
    fun `a sealed row opens to the same content`() {
        val sealed = OutboxRowSeal.seal(cipher, row())
        assertFalse(sealed.toString().contains("Quarterly plans"))
        assertFalse(sealed.toString().contains("hello there"))
        assertFalse(sealed.toString().contains("correct horse 42"))
        assertEquals(row(), OutboxRowSeal.open(cipher, sealed))
    }

    @Test
    fun `a row without a password keeps none`() {
        val sealed = OutboxRowSeal.seal(cipher, row(password = null))
        assertNull(sealed.expiry_password)
        assertNull(OutboxRowSeal.open(cipher, sealed).expiry_password)
    }

    @Test
    fun `a row written before sealing still opens`() {
        assertEquals(row(), OutboxRowSeal.open(cipher, row()))
    }

    @Test
    fun `plain text that looks like the marker stays plain`() {
        val plain = row().copy(subject = "aox1:notes", body_html = "aox1:QUJD")
        assertFalse(OutboxRowSeal.is_sealed(plain.subject))
        assertFalse(OutboxRowSeal.is_sealed(plain.body_html))
        assertEquals(plain, OutboxRowSeal.open(cipher, plain))
    }

    @Test
    fun `a sealed field does not open under another row`() {
        val sealed = OutboxRowSeal.seal(cipher, row())
        assertThrows(OutboxPayloadException::class.java) {
            OutboxRowSeal.open(cipher, sealed.copy(id = "pend_2"))
        }
    }

    @Test
    fun `sealed fields cannot be swapped`() {
        val sealed = OutboxRowSeal.seal(cipher, row())
        assertThrows(OutboxPayloadException::class.java) {
            OutboxRowSeal.open(cipher, sealed.copy(subject = sealed.body_html, body_html = sealed.subject))
        }
    }

    @Test
    fun `another key does not open the row`() {
        val sealed = OutboxRowSeal.seal(cipher, row())
        val other = OutboxFileCipher { ByteArray(32) { 6 } }
        assertThrows(OutboxPayloadException::class.java) { OutboxRowSeal.open(other, sealed) }
        val blank = OutboxRowSeal.open_or_blank(other, sealed)
        assertEquals("", blank.subject)
        assertEquals("", blank.body_html)
        assertNull(blank.expiry_password)
        assertEquals(sealed.to_json, blank.to_json)
    }

    @Test
    fun `sealing fails when no key is available`() {
        val keyless = OutboxFileCipher { null }
        assertThrows(java.io.IOException::class.java) { OutboxRowSeal.seal(keyless, row()) }
    }

    @Test
    fun `each seal uses a fresh nonce`() {
        assertNotEquals(OutboxRowSeal.seal(cipher, row()).subject, OutboxRowSeal.seal(cipher, row()).subject)
    }
}
