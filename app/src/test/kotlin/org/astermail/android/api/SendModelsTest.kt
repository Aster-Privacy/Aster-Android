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

package org.astermail.android.api

import org.astermail.android.api.send.ExternalAttachmentPayload
import org.astermail.android.api.send.ExternalSendRequest
import org.astermail.android.api.send.ExternalSendResponse
import org.astermail.android.api.send.SendAttachmentPayload
import org.astermail.android.api.send.SimpleSendRequest
import org.astermail.android.api.send.SimpleSendResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendModelsTest {

    @Test
    fun `SimpleSendRequest required fields`() {
        val request = SimpleSendRequest(
            to = listOf("bob@astermail.org"),
            subject = "Hello",
            body = "World",
            encrypted_envelope = "enc_env",
            envelope_nonce = "env_n",
        )
        assertEquals(listOf("bob@astermail.org"), request.to)
        assertTrue(request.cc.isEmpty())
        assertTrue(request.bcc.isEmpty())
        assertEquals("Hello", request.subject)
        assertEquals("World", request.body)
        assertFalse(request.is_e2e_encrypted)
        assertEquals("enc_env", request.encrypted_envelope)
        assertEquals("env_n", request.envelope_nonce)
        assertNull(request.folder_token)
        assertNull(request.thread_token)
        assertNull(request.encrypted_metadata)
        assertNull(request.metadata_nonce)
        assertNull(request.sender_email)
        assertNull(request.sender_alias_hash)
        assertNull(request.sender_display_name)
        assertNull(request.expires_at)
        assertTrue(request.attachments.isEmpty())
        assertNull(request.forward_original_mail_id)
    }

    @Test
    fun `SimpleSendRequest with all fields`() {
        val attachment = SendAttachmentPayload(
            encrypted_data = "enc",
            data_nonce = "dn",
            sender_encrypted_meta = "sem",
            sender_meta_nonce = "smn",
            recipient_encrypted_meta = "rem",
            size_bytes = 5000L,
        )
        val request = SimpleSendRequest(
            to = listOf("bob@astermail.org", "carol@astermail.org"),
            cc = listOf("dave@astermail.org"),
            bcc = listOf("eve@astermail.org"),
            subject = "Test",
            body = "<p>Test</p>",
            is_e2e_encrypted = true,
            encrypted_envelope = "enc_env",
            envelope_nonce = "env_n",
            folder_token = "ft_sent",
            thread_token = "tt_1",
            encrypted_metadata = "emeta",
            metadata_nonce = "mnonce",
            sender_email = "alice@astermail.org",
            sender_alias_hash = "alias_hash",
            sender_display_name = "Alice",
            expires_at = "2026-06-01T00:00:00Z",
            attachments = listOf(attachment),
            forward_original_mail_id = "orig_m1",
        )
        assertEquals(2, request.to.size)
        assertEquals(1, request.cc.size)
        assertEquals(1, request.bcc.size)
        assertTrue(request.is_e2e_encrypted)
        assertEquals("ft_sent", request.folder_token)
        assertEquals("alice@astermail.org", request.sender_email)
        assertEquals("Alice", request.sender_display_name)
        assertEquals(1, request.attachments.size)
        assertEquals("orig_m1", request.forward_original_mail_id)
    }

    @Test
    fun `SimpleSendRequest copy`() {
        val original = SimpleSendRequest(
            to = listOf("a@b.c"),
            subject = "S",
            body = "B",
            encrypted_envelope = "e",
            envelope_nonce = "n",
        )
        val copied = original.copy(
            cc = listOf("x@y.z"),
            is_e2e_encrypted = true,
        )
        assertEquals(listOf("x@y.z"), copied.cc)
        assertTrue(copied.is_e2e_encrypted)
        assertEquals(listOf("a@b.c"), copied.to)
    }

    @Test
    fun `SendAttachmentPayload required fields`() {
        val payload = SendAttachmentPayload(
            encrypted_data = "enc",
            data_nonce = "dn",
            sender_encrypted_meta = "sem",
            sender_meta_nonce = "smn",
            size_bytes = 1024L,
        )
        assertEquals("enc", payload.encrypted_data)
        assertEquals("dn", payload.data_nonce)
        assertEquals("sem", payload.sender_encrypted_meta)
        assertEquals("smn", payload.sender_meta_nonce)
        assertNull(payload.recipient_encrypted_meta)
        assertEquals(1024L, payload.size_bytes)
    }

    @Test
    fun `SendAttachmentPayload with recipient meta`() {
        val payload = SendAttachmentPayload(
            encrypted_data = "enc",
            data_nonce = "dn",
            sender_encrypted_meta = "sem",
            sender_meta_nonce = "smn",
            recipient_encrypted_meta = "rem_enc",
            size_bytes = 2048L,
        )
        assertEquals("rem_enc", payload.recipient_encrypted_meta)
        assertEquals(2048L, payload.size_bytes)
    }

    @Test
    fun `SendAttachmentPayload copy`() {
        val original = SendAttachmentPayload(
            encrypted_data = "e", data_nonce = "d",
            sender_encrypted_meta = "s", sender_meta_nonce = "sn",
            size_bytes = 100L,
        )
        val copied = original.copy(size_bytes = 200L, recipient_encrypted_meta = "r")
        assertEquals(200L, copied.size_bytes)
        assertEquals("r", copied.recipient_encrypted_meta)
        assertEquals("e", copied.encrypted_data)
    }

    @Test
    fun `SimpleSendResponse defaults`() {
        val response = SimpleSendResponse()
        assertFalse(response.success)
        assertEquals("", response.message)
        assertNull(response.mail_item_id)
    }

    @Test
    fun `SimpleSendResponse with values`() {
        val response = SimpleSendResponse(
            success = true,
            message = "sent successfully",
            mail_item_id = "m_sent_1",
        )
        assertTrue(response.success)
        assertEquals("sent successfully", response.message)
        assertEquals("m_sent_1", response.mail_item_id)
    }

    @Test
    fun `ExternalSendRequest required fields`() {
        val request = ExternalSendRequest(
            encrypted_recipients = "er",
            encrypted_subject = "es",
            encrypted_body = "eb",
            ephemeral_key = "ek",
            nonce = "n",
        )
        assertEquals("er", request.encrypted_recipients)
        assertEquals("es", request.encrypted_subject)
        assertEquals("eb", request.encrypted_body)
        assertEquals("ek", request.ephemeral_key)
        assertEquals("n", request.nonce)
        assertNull(request.encrypted_envelope)
        assertNull(request.envelope_nonce)
        assertNull(request.folder_token)
        assertNull(request.thread_token)
        assertNull(request.encrypted_metadata)
        assertNull(request.metadata_nonce)
        assertNull(request.sender_email)
        assertNull(request.sender_alias_hash)
        assertNull(request.sender_display_name)
        assertNull(request.expires_at)
        assertNull(request.expiry_password)
        assertTrue(request.acknowledge_server_readable)
        assertTrue(request.attachments.isEmpty())
    }

    @Test
    fun `ExternalSendRequest with all optional fields`() {
        val attachment = ExternalAttachmentPayload(
            data = "raw_data",
            filename = "report.pdf",
            content_type = "application/pdf",
            size_bytes = 50000L,
            content_id = "cid_1",
        )
        val request = ExternalSendRequest(
            encrypted_recipients = "er",
            encrypted_subject = "es",
            encrypted_body = "eb",
            ephemeral_key = "ek",
            nonce = "n",
            encrypted_envelope = "enc_env",
            envelope_nonce = "env_n",
            folder_token = "ft_sent",
            thread_token = "tt_1",
            encrypted_metadata = "emeta",
            metadata_nonce = "mnonce",
            sender_email = "alice@astermail.org",
            sender_alias_hash = "ah",
            sender_display_name = "Alice",
            expires_at = "2026-06-01T00:00:00Z",
            expiry_password = "secret123",
            acknowledge_server_readable = false,
            attachments = listOf(attachment),
        )
        assertEquals("enc_env", request.encrypted_envelope)
        assertEquals("secret123", request.expiry_password)
        assertFalse(request.acknowledge_server_readable)
        assertEquals(1, request.attachments.size)
        assertEquals("report.pdf", request.attachments[0].filename)
    }

    @Test
    fun `ExternalAttachmentPayload required fields`() {
        val payload = ExternalAttachmentPayload(
            data = "base64data",
            filename = "image.png",
            content_type = "image/png",
            size_bytes = 10240L,
        )
        assertEquals("base64data", payload.data)
        assertEquals("image.png", payload.filename)
        assertEquals("image/png", payload.content_type)
        assertEquals(10240L, payload.size_bytes)
        assertNull(payload.content_id)
    }

    @Test
    fun `ExternalAttachmentPayload with content_id`() {
        val payload = ExternalAttachmentPayload(
            data = "d",
            filename = "f.txt",
            content_type = "text/plain",
            size_bytes = 100L,
            content_id = "cid_abc",
        )
        assertEquals("cid_abc", payload.content_id)
    }

    @Test
    fun `ExternalAttachmentPayload copy`() {
        val original = ExternalAttachmentPayload(
            data = "d", filename = "f", content_type = "t", size_bytes = 1L,
        )
        val copied = original.copy(filename = "new_f", size_bytes = 999L)
        assertEquals("new_f", copied.filename)
        assertEquals(999L, copied.size_bytes)
        assertEquals("d", copied.data)
    }

    @Test
    fun `ExternalSendResponse defaults`() {
        val response = ExternalSendResponse()
        assertFalse(response.success)
        assertEquals("", response.message)
        assertNull(response.mail_item_id)
    }

    @Test
    fun `ExternalSendResponse with values`() {
        val response = ExternalSendResponse(
            success = true,
            message = "external mail sent",
            mail_item_id = "ext_m1",
        )
        assertTrue(response.success)
        assertEquals("external mail sent", response.message)
        assertEquals("ext_m1", response.mail_item_id)
    }

    @Test
    fun `SimpleSendRequest with empty recipient lists`() {
        val request = SimpleSendRequest(
            to = emptyList(),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "",
            body = "",
            encrypted_envelope = "",
            envelope_nonce = "",
        )
        assertTrue(request.to.isEmpty())
        assertTrue(request.cc.isEmpty())
        assertTrue(request.bcc.isEmpty())
    }

    @Test
    fun `ExternalSendRequest with empty attachments`() {
        val request = ExternalSendRequest(
            encrypted_recipients = "r",
            encrypted_subject = "s",
            encrypted_body = "b",
            ephemeral_key = "k",
            nonce = "n",
            attachments = emptyList(),
        )
        assertTrue(request.attachments.isEmpty())
    }

    @Test
    fun `SimpleSendResponse equality`() {
        val a = SimpleSendResponse(success = true, message = "ok", mail_item_id = "m1")
        val b = SimpleSendResponse(success = true, message = "ok", mail_item_id = "m1")
        assertEquals(a, b)
    }
}
