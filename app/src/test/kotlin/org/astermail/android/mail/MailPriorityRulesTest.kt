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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.astermail.android.api.send.ExternalSendRequest
import org.astermail.android.api.send.QueueEmailRequest
import org.astermail.android.api.send.SimpleSendRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailPriorityRulesTest {

    private val json = Json {
        explicitNulls = false
        encodeDefaults = true
    }

    private fun headers(vararg pairs: Pair<String, String>): List<Pair<String, String>> = pairs.toList()

    @Test
    fun `reads X-Priority levels`() {
        assertEquals(MailPriority.HIGH, MailPriorityRules.from_headers(headers("X-Priority" to "1")))
        assertEquals(MailPriority.HIGH, MailPriorityRules.from_headers(headers("X-Priority" to "2 (High)")))
        assertEquals(MailPriority.NORMAL, MailPriorityRules.from_headers(headers("X-Priority" to "3 (Normal)")))
        assertEquals(MailPriority.LOW, MailPriorityRules.from_headers(headers("X-Priority" to "4")))
        assertEquals(MailPriority.LOW, MailPriorityRules.from_headers(headers("X-Priority" to " 5 (Lowest)")))
    }

    @Test
    fun `matches header names without regard to case`() {
        assertEquals(MailPriority.HIGH, MailPriorityRules.from_headers(headers("x-priority" to "1")))
        assertEquals(MailPriority.LOW, MailPriorityRules.from_headers(headers("IMPORTANCE" to "Low")))
    }

    @Test
    fun `falls back to Importance and then Priority`() {
        assertEquals(MailPriority.HIGH, MailPriorityRules.from_headers(headers("Importance" to "high")))
        assertEquals(MailPriority.HIGH, MailPriorityRules.from_headers(headers("Priority" to "urgent")))
        assertEquals(MailPriority.LOW, MailPriorityRules.from_headers(headers("Priority" to "non-urgent")))
        assertEquals(
            MailPriority.LOW,
            MailPriorityRules.from_headers(headers("Priority" to "urgent", "Importance" to "low")),
        )
    }

    @Test
    fun `X-Priority wins over the other headers`() {
        assertEquals(
            MailPriority.LOW,
            MailPriorityRules.from_headers(headers("Importance" to "high", "Priority" to "urgent", "X-Priority" to "5")),
        )
    }

    @Test
    fun `keeps the first valid value of a repeated header`() {
        assertEquals(
            MailPriority.HIGH,
            MailPriorityRules.from_headers(headers("X-Priority" to "1", "X-Priority" to "5")),
        )
    }

    @Test
    fun `unknown or missing values read as normal`() {
        assertEquals(MailPriority.NORMAL, MailPriorityRules.from_headers(emptyList()))
        assertEquals(MailPriority.NORMAL, MailPriorityRules.from_headers(headers("X-Priority" to "9")))
        assertEquals(MailPriority.NORMAL, MailPriorityRules.from_headers(headers("Importance" to "whenever")))
        assertEquals(MailPriority.NORMAL, MailPriorityRules.from_headers(headers("Subject" to "high")))
    }

    @Test
    fun `outgoing headers read back as the same priority`() {
        MailPriority.entries.forEach { priority ->
            assertEquals(priority, MailPriorityRules.from_headers(MailPriorityRules.headers(priority)))
        }
        assertTrue(MailPriorityRules.headers(MailPriority.NORMAL).isEmpty())
        assertEquals(
            listOf("X-Priority" to "1 (Highest)", "Importance" to "high"),
            MailPriorityRules.headers(MailPriority.HIGH),
        )
        assertEquals(
            listOf("X-Priority" to "5 (Lowest)", "Importance" to "low"),
            MailPriorityRules.headers(MailPriority.LOW),
        )
    }

    @Test
    fun `only high and low go out on the wire`() {
        assertEquals("high", MailPriorityRules.outgoing(MailPriority.HIGH))
        assertEquals("low", MailPriorityRules.outgoing(MailPriority.LOW))
        assertNull(MailPriorityRules.outgoing(MailPriority.NORMAL))
        assertEquals(MailPriority.HIGH, MailPriorityRules.from_wire("high"))
        assertEquals(MailPriority.NORMAL, MailPriorityRules.from_wire("normal"))
        assertEquals(MailPriority.NORMAL, MailPriorityRules.from_wire(null))
        assertEquals(MailPriority.NORMAL, MailPriorityRules.from_wire("urgent"))
    }

    @Test
    fun `normal priority adds no field to a send request`() {
        val simple = json.encodeToJsonElement(
            SimpleSendRequest.serializer(),
            SimpleSendRequest(
                to = listOf("bob@astermail.org"),
                subject = "s",
                body = "b",
                encrypted_envelope = "e",
                envelope_nonce = "n",
                priority = MailPriorityRules.outgoing(MailPriority.NORMAL),
            ),
        ).jsonObject
        assertFalse(simple.containsKey("priority"))
        val queued = json.encodeToJsonElement(
            QueueEmailRequest.serializer(),
            QueueEmailRequest(
                to = listOf("bob@astermail.org"),
                subject = "s",
                body = "b",
                delay_seconds = 0,
                encrypted_envelope = "e",
                envelope_nonce = "n",
            ),
        ).jsonObject
        assertFalse(queued.containsKey("priority"))
    }

    @Test
    fun `high and low priority go out as the request field`() {
        val external = json.encodeToJsonElement(
            ExternalSendRequest.serializer(),
            ExternalSendRequest(
                encrypted_recipients = "r",
                encrypted_subject = "s",
                encrypted_body = "b",
                ephemeral_key = "k",
                nonce = "n",
                priority = MailPriorityRules.outgoing(MailPriority.HIGH),
            ),
        ).jsonObject
        assertEquals("\"high\"", external["priority"].toString())
        val queued = json.encodeToJsonElement(
            QueueEmailRequest.serializer(),
            QueueEmailRequest(
                to = listOf("bob@astermail.org"),
                subject = "s",
                body = "b",
                delay_seconds = 0,
                encrypted_envelope = "e",
                envelope_nonce = "n",
                priority = MailPriorityRules.outgoing(MailPriority.LOW),
            ),
        ).jsonObject
        assertEquals("\"low\"", queued["priority"].toString())
    }
}
