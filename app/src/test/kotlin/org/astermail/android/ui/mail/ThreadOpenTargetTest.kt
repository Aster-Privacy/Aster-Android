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

package org.astermail.android.ui.mail

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadOpenTargetTest {

    private val item_id = "0f4b1c2e-9a44-4d0e-8f2b-6c7d5e3a1b90"

    private val thread_token = "AlF3kQ2m9tZ7pR1sV8xY4cB6nD0gH5jK2lM7qW9eT3U="

    private fun email(id: String, thread_id: String) = Email(
        id = id,
        sender_name = "Aster",
        sender_email = "noreply@astermail.org",
        subject = "Welcome",
        preview = "hi",
        received_at = 1_000L,
        is_read = false,
        is_starred = false,
        has_attachment = false,
        thread_id = thread_id,
    )

    @Test
    fun grouped_thread_keeps_the_token_as_its_key_and_the_message_uuid_as_its_open_target() {
        val rows = group_by_thread(listOf(email(item_id, thread_token)))

        assertEquals(1, rows.size)
        assertEquals(thread_token, rows[0].thread_id)
        assertEquals(item_id, thread_open_target_id(rows[0]))
        assertEquals(item_id, UUID.fromString(thread_open_target_id(rows[0])).toString())
    }

    @Test
    fun open_target_is_the_newest_message_when_a_thread_has_several() {
        val older = email("11111111-1111-4111-8111-111111111111", thread_token).copy(received_at = 1_000L)
        val newer = email("22222222-2222-4222-8222-222222222222", thread_token).copy(received_at = 9_000L)

        val rows = group_by_thread(listOf(older, newer))

        assertEquals(1, rows.size)
        assertEquals(newer.id, thread_open_target_id(rows[0]))
    }

    @Test
    fun open_target_falls_back_to_the_item_id_for_unthreaded_messages() {
        val rows = group_by_thread(listOf(email(item_id, item_id)))

        assertEquals(item_id, rows[0].thread_id)
        assertEquals(item_id, thread_open_target_id(rows[0]))
    }
}
