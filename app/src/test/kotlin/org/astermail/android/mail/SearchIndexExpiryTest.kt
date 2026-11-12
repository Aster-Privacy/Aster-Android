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

import java.time.Instant
import org.astermail.android.storage.search.DecryptedMailEntity
import org.astermail.android.storage.search.migration_columns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexExpiryTest {

    private val now_ms = Instant.parse("2026-11-12T12:00:00Z").toEpochMilli()

    private fun row(id: String, expires_at: String?) = DecryptedMailEntity(
        id = id,
        thread_token = null,
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@example.com",
        subject = "Subject",
        preview = "Preview",
        timestamp = "2026-11-12T10:00:00Z",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = "",
        indexed_at = 0L,
        expires_at = expires_at,
    )

    @Test
    fun mail_past_its_expiry_counts_as_expired() {
        assertTrue(is_expired_mail("2026-11-12T11:59:59Z", now_ms))
        assertTrue(is_expired_mail("2026-11-12T12:00:00Z", now_ms))
        assertTrue(is_expired_mail("2026-11-12T13:00:00+02:00", now_ms))
        assertTrue(is_expired_mail("2026-11-12T11:00:00", now_ms))
    }

    @Test
    fun mail_without_a_past_expiry_stays() {
        assertFalse(is_expired_mail(null, now_ms))
        assertFalse(is_expired_mail("", now_ms))
        assertFalse(is_expired_mail("  ", now_ms))
        assertFalse(is_expired_mail("not a date", now_ms))
        assertFalse(is_expired_mail("2026-11-12T12:00:01Z", now_ms))
        assertFalse(is_expired_mail("2026-11-12T14:00:00+01:00", now_ms))
    }

    @Test
    fun expired_rows_are_dropped_from_results_and_returned_for_removal() {
        val rows = listOf(
            row("keep_plain", null),
            row("gone", "2026-11-11T00:00:00Z"),
            row("keep_future", "2026-12-01T00:00:00Z"),
            row("gone_too", "2026-11-12T12:00:00Z"),
        )

        val (live, expired_ids) = split_expired_rows(rows, now_ms)

        assertEquals(listOf("keep_plain", "keep_future"), live.map { it.id })
        assertEquals(listOf("gone", "gone_too"), expired_ids)
    }

    @Test
    fun the_index_gains_its_expiry_column_through_a_migration() {
        val added = migration_columns.getValue(23)

        assertTrue(added.any { it.table == "decrypted_mail_cache" && it.name == "expires_at" && it.definition == "TEXT" })
    }
}
