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

package org.astermail.android.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletedSubscriptionsTest {
    private val deleted_at = "2026-11-18T10:00:00Z"
    private val tombstones = mapOf("news@shop.example" to deleted_at)

    @Test
    fun hides_a_sender_whose_last_mail_predates_the_deletion() {
        assertTrue(is_deleted_subscription(tombstones, "News@Shop.example ", "2026-11-17T09:00:00Z"))
        assertTrue(is_deleted_subscription(tombstones, "news@shop.example", deleted_at))
    }

    @Test
    fun brings_a_sender_back_when_new_mail_arrives_after_the_deletion() {
        assertFalse(is_deleted_subscription(tombstones, "news@shop.example", "2026-11-18T10:00:01Z"))
        assertFalse(is_deleted_subscription(tombstones, "news@shop.example", "2026-11-18T12:00:00+01:00"))
    }

    @Test
    fun hides_a_sender_with_no_usable_received_time() {
        assertTrue(is_deleted_subscription(tombstones, "news@shop.example", ""))
        assertTrue(is_deleted_subscription(tombstones, "news@shop.example", "not a date"))
    }

    @Test
    fun ignores_senders_that_were_never_deleted() {
        assertFalse(is_deleted_subscription(tombstones, "other@shop.example", "2026-01-01T00:00:00Z"))
    }

    @Test
    fun round_trips_and_caps_the_tombstone_list() {
        val merged = with_deleted_subscriptions(
            mapOf("old@example.com" to "2026-01-01T00:00:00Z"),
            listOf(" New@Example.com", "bad-address"),
            deleted_at,
            max_entries = 1,
        )
        assertEquals(mapOf("new@example.com" to deleted_at), merged)
        assertEquals(merged, decode_deleted_subscriptions(encode_deleted_subscriptions(merged)))
        assertEquals(emptyMap<String, String>(), decode_deleted_subscriptions("garbage\nx@y notadate"))
    }
}
