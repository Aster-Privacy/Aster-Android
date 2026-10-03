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

import org.astermail.android.api.ApiError
import org.astermail.android.api.mail.MailItem
import org.astermail.android.storage.actions.PendingMailActionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingMailActionsTest {

    private fun item(
        id: String,
        thread_token: String? = null,
        is_read: Boolean = false,
        is_starred: Boolean = false,
        is_trashed: Boolean = false,
        is_archived: Boolean = false,
        is_spam: Boolean = false,
        labels: List<String> = emptyList(),
    ) = InboxItem(
        id = id,
        thread_token = thread_token,
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@example.com",
        subject = "Subject",
        preview = "Preview",
        timestamp = "2026-09-30T00:00:00Z",
        is_read = is_read,
        is_starred = is_starred,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = is_trashed,
        is_archived = is_archived,
        is_spam = is_spam,
        labels = labels,
        raw_item = MailItem(id = id, item_type = "received"),
    )

    private var next_id = 1L

    private fun action(kind: PendingActionKind, payload: PendingActionPayload) =
        PendingMailAction(next_id++, "account", kind, payload, 0L, 0)

    @Test
    fun network_failures_are_transient() {
        assertTrue(is_transient_failure(java.net.UnknownHostException("offline")))
        assertTrue(is_transient_failure(java.net.SocketTimeoutException("slow")))
        assertTrue(is_transient_failure(ApiError.NetworkError))
        assertTrue(is_transient_failure(ApiError.ServerError(503)))
        assertTrue(is_transient_failure(ApiError.RateLimited()))
        assertTrue(is_transient_failure(IllegalStateException("wrapped", java.io.IOException("reset"))))
    }

    @Test
    fun rejected_requests_are_permanent() {
        assertFalse(is_transient_failure(ApiError.NotFoundError))
        assertFalse(is_transient_failure(ApiError.ValidationError(listOf("bad"))))
        assertFalse(is_transient_failure(ApiError.ForbiddenError()))
        assertFalse(is_transient_failure(IllegalStateException("partial")))
        assertFalse(is_transient_failure(null))
    }

    @Test
    fun client_errors_from_the_server_are_permanent() {
        assertFalse(is_transient_failure(ApiError.ServerError(405)))
        assertFalse(is_transient_failure(ApiError.ServerError(415)))
        assertTrue(is_transient_failure(ApiError.ServerError(500)))
    }

    @Test
    fun only_lost_connections_count_as_connectivity_failures() {
        assertTrue(is_connectivity_failure(java.net.UnknownHostException("offline")))
        assertTrue(is_connectivity_failure(java.net.ConnectException("refused")))
        assertTrue(is_connectivity_failure(java.net.NoRouteToHostException("no route")))
        assertTrue(is_connectivity_failure(ApiError.NetworkError))
        assertTrue(is_connectivity_failure(IllegalStateException("wrapped", java.net.UnknownHostException("offline"))))
        assertFalse(is_connectivity_failure(java.net.SocketTimeoutException("read timed out")))
        assertFalse(is_connectivity_failure(ApiError.ServerError(503)))
        assertFalse(is_connectivity_failure(ApiError.RateLimited()))
        assertFalse(is_connectivity_failure(null))
    }

    @Test
    fun server_failures_stop_retrying_after_the_cap() {
        val fresh = PendingMailAction(1, "account", PendingActionKind.archive, PendingActionPayload(ids = listOf("a")), 0L, 0)
        val worn = fresh.copy(attempts = PENDING_ACTION_MAX_SERVER_ATTEMPTS - 1)
        assertFalse(pending_action_exhausted(fresh, ApiError.ServerError(503)))
        assertTrue(pending_action_exhausted(worn, ApiError.ServerError(503)))
        assertFalse(pending_action_exhausted(worn, java.net.UnknownHostException("offline")))
        assertFalse(pending_action_exhausted(worn, java.net.SocketTimeoutException("slow")))
        assertTrue(pending_action_exhausted(worn, ApiError.RateLimited()))
    }

    @Test
    fun payload_survives_a_round_trip_through_storage() {
        val payload = PendingActionPayload(
            ids = listOf("a", "b"),
            threads = listOf("t1"),
            covered = listOf("a"),
            token = "tok",
            from_token = "old",
            folder = "inbox",
            value = PENDING_PATCH_MODE,
        )
        val row = PendingMailActionEntity(
            id = 7,
            account_id = "account",
            kind = PendingActionKind.trash.name,
            payload = encode_pending_payload(payload),
            created_at_ms = 42,
            attempts = 2,
        )
        val decoded = row.to_pending_action()!!
        assertEquals(PendingActionKind.trash, decoded.kind)
        assertEquals(payload, decoded.payload)
        assertEquals(2, decoded.attempts)
    }

    @Test
    fun unknown_kinds_are_skipped() {
        val row = PendingMailActionEntity(
            account_id = "account",
            kind = "teleport",
            payload = "{}",
            created_at_ms = 0,
        )
        assertNull(row.to_pending_action())
    }

    @Test
    fun actions_expire_after_thirty_days() {
        val queued = action(PendingActionKind.archive, PendingActionPayload(ids = listOf("a")))
        assertFalse(pending_action_expired(queued, PENDING_ACTION_MAX_AGE_MS))
        assertTrue(pending_action_expired(queued, PENDING_ACTION_MAX_AGE_MS + 1))
    }

    @Test
    fun archived_items_leave_the_inbox() {
        val items = listOf(item("a"), item("b"))
        val result = apply_pending_actions(
            "inbox",
            items,
            listOf(action(PendingActionKind.archive, PendingActionPayload(ids = listOf("a")))),
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun archive_then_undo_keeps_the_item() {
        val items = listOf(item("a"))
        val result = apply_pending_actions(
            "inbox",
            items,
            listOf(
                action(PendingActionKind.archive, PendingActionPayload(ids = listOf("a"))),
                action(PendingActionKind.unarchive, PendingActionPayload(ids = listOf("a"))),
            ),
        )
        assertEquals(listOf("a"), result.map { it.id })
        assertFalse(result.first().is_archived)
    }

    @Test
    fun read_and_star_flags_are_patched_in_place() {
        val items = listOf(item("a"), item("b"))
        val result = apply_pending_actions(
            "inbox",
            items,
            listOf(
                action(PendingActionKind.mark_read, PendingActionPayload(ids = listOf("a"))),
                action(PendingActionKind.star, PendingActionPayload(ids = listOf("b"))),
            ),
        )
        assertTrue(result.first { it.id == "a" }.is_read)
        assertTrue(result.first { it.id == "b" }.is_starred)
    }

    @Test
    fun trashing_a_thread_removes_every_message_in_it() {
        val items = listOf(item("a", thread_token = "t1"), item("b", thread_token = "t1"), item("c"))
        val result = apply_pending_actions(
            "inbox",
            items,
            listOf(action(PendingActionKind.trash, PendingActionPayload(threads = listOf("t1")))),
        )
        assertEquals(listOf("c"), result.map { it.id })
    }

    @Test
    fun trashed_items_show_up_patched_in_the_trash_folder() {
        val items = listOf(item("a", is_trashed = true))
        val result = apply_pending_actions(
            "trash",
            items,
            listOf(action(PendingActionKind.mark_read, PendingActionPayload(ids = listOf("a")))),
        )
        assertEquals(1, result.size)
        assertTrue(result.first().is_read)
    }

    @Test
    fun moving_to_a_folder_leaves_the_source() {
        val items = listOf(item("a", labels = listOf("old")))
        val action = action(
            PendingActionKind.move_to_folder,
            PendingActionPayload(ids = listOf("a"), token = "new", from_token = "old"),
        )
        assertTrue(apply_pending_actions("label:old", items, listOf(action)).isEmpty())
        assertEquals(listOf("new"), apply_pending_actions("label:new", items, listOf(action)).first().labels)
    }

    @Test
    fun permanent_deletes_and_emptied_folders_disappear() {
        val items = listOf(item("a", is_trashed = true), item("b", is_trashed = true))
        val deleted = apply_pending_actions(
            "trash",
            items,
            listOf(action(PendingActionKind.delete_permanent, PendingActionPayload(ids = listOf("a")))),
        )
        assertEquals(listOf("b"), deleted.map { it.id })
        val emptied = apply_pending_actions(
            "trash",
            items,
            listOf(action(PendingActionKind.empty_trash, PendingActionPayload())),
        )
        assertTrue(emptied.isEmpty())
    }

    @Test
    fun scope_actions_apply_to_the_whole_folder() {
        val items = listOf(item("a"), item("b"))
        val result = apply_pending_actions(
            "inbox",
            items,
            listOf(action(PendingActionKind.scope_action, PendingActionPayload(folder = "inbox", value = "mark_read"))),
        )
        assertTrue(result.all { it.is_read })
    }

    @Test
    fun snoozed_items_hide_outside_the_snoozed_folder() {
        val items = listOf(item("a"))
        val snooze = action(PendingActionKind.snooze, PendingActionPayload(ids = listOf("a"), value = "2026-10-01T09:00:00Z"))
        assertTrue(apply_pending_actions("inbox", items, listOf(snooze)).isEmpty())
        assertEquals(1, apply_pending_actions("snoozed", items, listOf(snooze)).size)
    }

    @Test
    fun items_without_matching_actions_are_untouched() {
        val items = listOf(item("a"))
        val result = apply_pending_actions(
            "inbox",
            items,
            listOf(action(PendingActionKind.archive, PendingActionPayload(ids = listOf("z")))),
        )
        assertTrue(result.first() === items.first())
    }
}
