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

package org.astermail.android.api.preferences

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderPreferencesTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun an_unset_sort_order_becomes_a_value_the_web_client_can_show() {
        val merged = merge_decrypted_preferences(json, """{"show_aster_branding":true}""", null)

        assertEquals(INBOX_SORT_NEWEST_FIRST, merged.inbox_sort_order)
        assertEquals(CONVERSATION_ORDER_ASCENDING, merged.conversation_order)
    }

    @Test
    fun a_legacy_oldest_conversation_order_keeps_the_inbox_oldest_first() {
        val merged = merge_decrypted_preferences(json, """{"conversation_order":"oldest"}""", null)

        assertEquals(INBOX_SORT_OLDEST_FIRST, merged.inbox_sort_order)
        assertEquals(CONVERSATION_ORDER_ASCENDING, merged.conversation_order)
        assertTrue(resolve_inbox_sort_oldest_first(merged))
    }

    @Test
    fun a_web_conversation_order_survives_the_merge() {
        val merged = merge_decrypted_preferences(
            json,
            """{"conversation_order":"desc","inbox_sort_order":"oldest_first"}""",
            null,
        )

        assertEquals(INBOX_SORT_OLDEST_FIRST, merged.inbox_sort_order)
        assertEquals(CONVERSATION_ORDER_DESCENDING, merged.conversation_order)
    }

    @Test
    fun the_encoded_blob_never_carries_a_value_the_web_client_rejects() {
        val merged = merge_decrypted_preferences(json, """{"conversation_order":"newest"}""", null)
        val encoded = encode_preferences_preserving_unknown(
            json,
            merged,
            """{"conversation_order":"newest","unknown_key":1}""",
        )

        assertTrue(encoded.contains(""""conversation_order":"asc""""))
        assertTrue(encoded.contains(""""inbox_sort_order":"newest_first""""))
        assertTrue(encoded.contains(""""unknown_key":1"""))
    }
}
