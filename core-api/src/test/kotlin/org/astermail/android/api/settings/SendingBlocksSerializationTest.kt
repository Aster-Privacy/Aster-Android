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

package org.astermail.android.api.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.astermail.android.api.scheduled.ScheduledSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendingBlocksSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun an_older_server_without_primary_reads_as_primary_allowed() {
        val body = json.decodeFromString(SendingBlocksResponse.serializer(), """{"alias_ids":["a1"],"domain_address_ids":[]}""")
        assertFalse(body.primary)
        assertEquals(listOf("a1"), body.alias_ids)
    }

    @Test
    fun a_blocked_primary_is_read_from_the_server() {
        val body = json.decodeFromString(SendingBlocksResponse.serializer(), """{"primary":true,"alias_ids":[],"domain_address_ids":[]}""")
        assertTrue(body.primary)
    }

    @Test
    fun a_primary_change_omits_the_id() {
        val encoded = json.encodeToJsonElement(
            UpdateSendingBlocksRequest.serializer(),
            UpdateSendingBlocksRequest(
                changes = listOf(
                    SendingBlockChange(kind = sending_block_kind_primary, sending_disabled = true),
                    SendingBlockChange(kind = sending_block_kind_alias, id = "a1", sending_disabled = false),
                ),
            ),
        ).jsonObject
        val changes = encoded["changes"]!!.jsonArray
        assertFalse("id" in changes[0].jsonObject)
        assertEquals("\"primary\"", changes[0].jsonObject["kind"].toString())
        assertEquals("\"a1\"", changes[1].jsonObject["id"].toString())
    }

    @Test
    fun a_scheduled_summary_carries_the_failure_reason() {
        val summary = json.decodeFromString(
            ScheduledSummary.serializer(),
            """{"id":"s1","scheduled_at":"2026-11-25T10:00:00Z","failure_reason":"ALIAS_SENDING_DISABLED"}""",
        )
        assertEquals("ALIAS_SENDING_DISABLED", summary.failure_reason)
        assertNull(json.decodeFromString(ScheduledSummary.serializer(), """{"id":"s2","scheduled_at":"2026-11-25T10:00:00Z"}""").failure_reason)
    }
}
