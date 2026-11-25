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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSyncHubTest {

    private class recording_listener(val fail: Boolean = false) : live_sync_listener {
        val events = mutableListOf<LiveSyncEvent>()
        val mutations = mutableListOf<Pair<String, List<String>>>()
        var polls = 0

        override fun on_live_sync_event(event: LiveSyncEvent) {
            if (fail) throw IllegalStateException("boom")
            events += event
        }

        override fun on_live_mail_mutation(action: String, item_ids: List<String>) {
            if (fail) throw IllegalStateException("boom")
            mutations += action to item_ids
        }

        override fun on_live_sync_poll() {
            if (fail) throw IllegalStateException("boom")
            polls++
        }
    }

    private val registered = mutableListOf<live_sync_listener>()

    private fun listen(listener: recording_listener): recording_listener {
        live_sync_hub.register(listener)
        registered += listener
        return listener
    }

    @After
    fun tear_down() {
        registered.forEach { live_sync_hub.unregister(it) }
        live_sync_hub.reset_activity()
    }

    @Test
    fun poll_interval_backs_off_and_clamps() {
        assertEquals(30_000L, live_sync_poll_interval_ms(0))
        assertEquals(60_000L, live_sync_poll_interval_ms(1))
        assertEquals(120_000L, live_sync_poll_interval_ms(2))
        assertEquals(120_000L, live_sync_poll_interval_ms(9))
        assertEquals(30_000L, live_sync_poll_interval_ms(-1))
    }

    @Test
    fun a_connected_socket_with_recent_events_never_polls() {
        val now = 1_000_000L
        assertFalse(live_sync_poll_due(true, now - 10_000L, now - 600_000L, 0, now))
    }

    @Test
    fun a_quiet_connected_socket_polls_on_the_interval() {
        val now = 1_000_000L
        assertTrue(live_sync_poll_due(true, now - LIVE_SYNC_QUIET_MS, now - 30_000L, 0, now))
        assertFalse(live_sync_poll_due(true, now - LIVE_SYNC_QUIET_MS, now - 29_000L, 0, now))
        assertTrue(live_sync_poll_due(true, 0L, now - 30_000L, 0, now))
    }

    @Test
    fun a_disconnected_socket_polls_from_the_latest_activity() {
        val now = 1_000_000L
        assertTrue(live_sync_poll_due(false, 0L, now - 30_000L, 0, now))
        assertFalse(live_sync_poll_due(false, now - 10_000L, now - 600_000L, 0, now))
        assertFalse(live_sync_poll_due(false, 0L, now - 59_000L, 1, now))
        assertTrue(live_sync_poll_due(false, 0L, now - 60_000L, 1, now))
    }

    @Test
    fun a_clock_that_moves_backward_polls_instead_of_stalling() {
        val now = 1_000_000L
        assertTrue(live_sync_poll_due(false, 0L, now + 50_000L, 0, now))
    }

    @Test
    fun next_step_resets_on_events_and_grows_on_polls() {
        assertEquals(0, live_sync_next_step(2, event_seen = true, polled = true))
        assertEquals(1, live_sync_next_step(0, event_seen = false, polled = true))
        assertEquals(2, live_sync_next_step(2, event_seen = false, polled = true))
        assertEquals(1, live_sync_next_step(1, event_seen = false, polled = false))
    }

    @Test
    fun dispatch_reaches_every_listener_and_records_activity() {
        val first = listen(recording_listener())
        val second = listen(recording_listener())

        live_sync_hub.dispatch_event(LiveSyncEvent.new_mail, now = 42L)
        live_sync_hub.dispatch_mutation("read", listOf("a"), now = 43L)
        live_sync_hub.dispatch_poll()

        listOf(first, second).forEach {
            assertEquals(listOf(LiveSyncEvent.new_mail), it.events)
            assertEquals(listOf("read" to listOf("a")), it.mutations)
            assertEquals(1, it.polls)
        }
        assertEquals(43L, live_sync_hub.last_event_at())
    }

    @Test
    fun a_failing_listener_does_not_block_the_others() {
        listen(recording_listener(fail = true))
        val healthy = listen(recording_listener())

        live_sync_hub.dispatch_event(LiveSyncEvent.new_mail)
        live_sync_hub.dispatch_poll()

        assertEquals(1, healthy.events.size)
        assertEquals(1, healthy.polls)
    }

    @Test
    fun an_unregistered_listener_hears_nothing() {
        val listener = recording_listener()
        live_sync_hub.register(listener)
        live_sync_hub.unregister(listener)

        live_sync_hub.dispatch_poll()

        assertEquals(0, listener.polls)
    }

    @Test
    fun reset_clears_recorded_activity() {
        live_sync_hub.mark_activity(99L)
        live_sync_hub.reset_activity()
        assertEquals(0L, live_sync_hub.last_event_at())
    }

    private val started_at_ms = 1_800_000_000_000L

    private fun message(id: String, at_ms: Long, body: String = "hello"): ThreadMessageDecrypted {
        val base = build_optimistic_send_message(
            pending_id = id,
            started_at_ms = at_ms,
            sender_email = "me@astermail.org",
            sender_display_name = "Me",
            to = listOf("friend@astermail.org"),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Re: Hi",
            body_html = "<p>$body</p>",
            has_attachments = false,
        )
        val ts = java.time.Instant.ofEpochMilli(at_ms).toString()
        return base.copy(
            id = id,
            timestamp = ts,
            raw_item = base.raw_item.copy(id = id, send_status = null, created_at = ts),
        )
    }

    @Test
    fun revalidate_returns_null_when_nothing_changed() {
        val current = listOf(message("m1", started_at_ms), message("m2", started_at_ms + 1_000L))
        assertNull(merge_revalidated_thread(current, current, "m1", keep_unconfirmed = false))
    }

    @Test
    fun revalidate_appends_a_new_reply() {
        val current = listOf(message("m1", started_at_ms))
        val fetched = current + message("m2", started_at_ms + 1_000L)
        assertEquals(listOf("m1", "m2"), merge_revalidated_thread(current, fetched, "m1", false)?.map { it.id })
    }

    @Test
    fun revalidate_keeps_a_readable_body_over_a_pending_one() {
        val readable = message("m2", started_at_ms + 1_000L)
        val current = listOf(message("m1", started_at_ms), readable)
        val fetched = listOf(
            message("m1", started_at_ms),
            readable.copy(body_text = "", body_html = null, is_body_pending = true),
            message("m3", started_at_ms + 2_000L),
        )
        val merged = merge_revalidated_thread(current, fetched, "m1", false)
        assertEquals(readable, merged?.get(1))
        assertEquals("m3", merged?.last()?.id)
    }

    @Test
    fun revalidate_ignores_an_empty_or_optimistic_only_fetch() {
        val current = listOf(message("m1", started_at_ms))
        assertNull(merge_revalidated_thread(current, emptyList(), "m1", false))
        val pending = build_optimistic_send_message(
            pending_id = "p1",
            started_at_ms = started_at_ms,
            sender_email = "me@astermail.org",
            sender_display_name = "Me",
            to = listOf("friend@astermail.org"),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Re: Hi",
            body_html = "<p>x</p>",
            has_attachments = false,
        )
        assertNull(merge_revalidated_thread(current, listOf(pending), "m1", false))
    }

    @Test
    fun revalidate_keeps_an_unconfirmed_sent_copy_only_inside_the_guard() {
        val sent = message("s1", started_at_ms + 1_000L)
        val current = listOf(message("m1", started_at_ms), sent)
        val fetched = listOf(message("m1", started_at_ms))
        assertNull(merge_revalidated_thread(current, fetched, "m1", keep_unconfirmed = true))
        assertEquals(listOf("m1"), merge_revalidated_thread(current, fetched, "m1", keep_unconfirmed = false)?.map { it.id })
    }

    @Test
    fun revalidate_keeps_the_opened_message_when_the_server_omits_it() {
        val opened = message("m2", started_at_ms + 1_000L)
        val current = listOf(message("m1", started_at_ms), opened)
        val fetched = listOf(message("m1", started_at_ms), message("m3", started_at_ms + 2_000L))
        assertEquals(listOf("m1", "m2", "m3"), merge_revalidated_thread(current, fetched, "m2", false)?.map { it.id })
    }
}
