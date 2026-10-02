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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.scheduled.CreateScheduledRequest
import org.astermail.android.api.scheduled.ScheduledDelivery
import org.astermail.android.api.send.SendAttachmentPayload
import org.astermail.android.api.send.SimpleSendRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedSendContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private fun encode_scheduled(request: CreateScheduledRequest): JsonObject =
        json.parseToJsonElement(json.encodeToString(CreateScheduledRequest.serializer(), request)).jsonObject

    @Test
    fun `internal scheduled request carries a delivery and no ephemeral key`() {
        val request = CreateScheduledRequest(
            encrypted_envelope = "env",
            envelope_nonce = "n1",
            encrypted_recipients = "rec",
            recipients_nonce = "n2",
            recipient_count = 2,
            scheduled_at = "2026-10-03T10:00:00Z",
            folder_token = "sent",
            is_external = false,
            delivery = ScheduledDelivery(
                to = listOf("a@astermail.org"),
                bcc = listOf("hidden@astermail.org"),
                internal_encrypted_body = "sealed_shared",
                recipient_bodies = mapOf("hidden@astermail.org" to "sealed_private"),
            ),
        )
        val body = encode_scheduled(request)
        assertFalse(body.containsKey("ephemeral_key"))
        assertFalse(body.containsKey("base_nonce"))
        val delivery = body["delivery"]!!.jsonObject
        assertEquals("sealed_shared", delivery["internal_encrypted_body"]!!.jsonPrimitive.content)
        assertEquals(
            "sealed_private",
            delivery["recipient_bodies"]!!.jsonObject["hidden@astermail.org"]!!.jsonPrimitive.content,
        )
        assertEquals("hidden@astermail.org", delivery["bcc"]!!.jsonArray.single().jsonPrimitive.content)
        assertTrue(delivery["hosted_recipients"]!!.jsonArray.isEmpty())
        assertFalse(delivery.containsKey("sender_email"))
    }

    @Test
    fun `external scheduled request omits the delivery`() {
        val request = CreateScheduledRequest(
            encrypted_envelope = "env",
            envelope_nonce = "n1",
            encrypted_recipients = "rec",
            recipients_nonce = "n2",
            recipient_count = 1,
            scheduled_at = "2026-10-03T10:00:00Z",
            is_external = true,
            ephemeral_key = "ek",
            base_nonce = "bn",
        )
        val body = encode_scheduled(request)
        assertFalse(body.containsKey("delivery"))
        assertEquals("ek", body["ephemeral_key"]!!.jsonPrimitive.content)
    }

    @Test
    fun `simple send omits private copies when there are none`() {
        val request = SimpleSendRequest(
            to = listOf("a@astermail.org"),
            subject = "",
            body = "sealed",
            encrypted_envelope = "env",
            envelope_nonce = "n",
            attachments = listOf(
                SendAttachmentPayload(
                    encrypted_data = "d",
                    data_nonce = "dn",
                    sender_encrypted_meta = "sm",
                    sender_meta_nonce = "smn",
                    recipient_encrypted_meta = "rm",
                    size_bytes = 1L,
                ),
            ),
        )
        val body = json.parseToJsonElement(json.encodeToString(SimpleSendRequest.serializer(), request)).jsonObject
        assertFalse(body.containsKey("recipient_bodies"))
        assertFalse(body["attachments"]!!.jsonArray.single().jsonObject.containsKey("recipient_metas"))
    }

    @Test
    fun `simple send carries private copies for hidden bcc`() {
        val request = SimpleSendRequest(
            to = listOf("a@astermail.org"),
            bcc = listOf("hidden@astermail.org"),
            subject = "",
            body = "sealed",
            is_e2e_encrypted = true,
            encrypted_envelope = "env",
            envelope_nonce = "n",
            recipient_bodies = mapOf("hidden@astermail.org" to "private"),
            attachments = listOf(
                SendAttachmentPayload(
                    encrypted_data = "d",
                    data_nonce = "dn",
                    sender_encrypted_meta = "sm",
                    sender_meta_nonce = "smn",
                    recipient_encrypted_meta = "rm",
                    recipient_metas = mapOf("hidden@astermail.org" to "pm"),
                    size_bytes = 1L,
                ),
            ),
        )
        val body = json.parseToJsonElement(json.encodeToString(SimpleSendRequest.serializer(), request)).jsonObject
        assertEquals("private", body["recipient_bodies"]!!.jsonObject["hidden@astermail.org"]!!.jsonPrimitive.content)
        assertEquals(
            "pm",
            body["attachments"]!!.jsonArray.single().jsonObject["recipient_metas"]!!
                .jsonObject["hidden@astermail.org"]!!.jsonPrimitive.content,
        )
    }
}
