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

package org.astermail.android.notifications

import org.astermail.android.guard.GUARD_NOTICE_FIELD
import org.astermail.android.guard.guard_alert_marks
import org.astermail.android.guard.guard_event
import org.astermail.android.guard.guard_notice_payload
import org.astermail.android.guard.guard_push
import org.astermail.android.guard.handle_guard_push
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardNoticeMailRaceTest {

    private val account = "7f3c2a10-1111-4222-8333-944455556666"
    private val change_id = "84511aa3-8829-4793-bd3c-6206ef7493dd"
    private val item_id = "fd4c753b-0de6-4fa4-aeec-23f1a0434ae2"
    private val system_sender = "no-reply@astermail.org"
    private val now = 1_700_000_000_000L
    private val notification_id = MailPollingWorker.message_notification_id(item_id.hashCode())

    private class store(var entries: Map<String, Long> = emptyMap(), var now: Long = 1_700_000_000_000L) {
        val claimed = mutableListOf<String>()
        val cancelled = mutableSetOf<Int>()
        val posted = mutableSetOf<Int>()

        fun claim(key: String): Boolean {
            if (NotificationDedupe.is_seen(entries, key, now)) return false
            entries = NotificationDedupe.record_seen(entries, key, now)
            claimed += key
            return true
        }

        val marks = object : guard_alert_marks {
            override fun recent(account_id: String, event: guard_event): Boolean =
                NotificationDedupe.has_guard_mark(entries, account_id, event.name, now)

            override fun note(account_id: String, event: guard_event) {
                entries = NotificationDedupe.record_guard_mark(entries, account_id, event.name, now)
            }
        }

        fun replace_notice_mail() {
            val (remaining, taken) = NotificationDedupe.take_system_mail_entries(entries, now)
            entries = remaining
            cancelled += taken
            posted.removeAll(taken)
        }

        fun claim_notice_mail(sender: String): Boolean {
            val remaining = NotificationDedupe.take_guard_notice_mark(entries, sender, now) ?: return false
            entries = remaining
            return true
        }

        fun show_mail(item_id: String, notification_id: Int, sender: String, between: () -> Unit = {}) {
            val (updated, allowed) = NotificationDedupe.track_system_mail_entries(
                entries,
                item_id,
                notification_id,
                sender,
                now,
            )
            entries = updated
            if (!allowed) return
            val key = NotificationDedupe.system_mail_key(item_id, notification_id)
            val tracked = entries.containsKey(key)
            between()
            posted += notification_id
            if (tracked && !entries.containsKey(key)) {
                posted -= notification_id
                cancelled += notification_id
            }
        }
    }

    private fun guard_payload(event: String = "change_held", id: String = change_id): JSONObject =
        JSONObject().put("type", "guard").put("event", event).put("id", id).put("account_id", account)

    private fun notice_mail_payload(event: String = "change_held", id: String = change_id): JSONObject =
        JSONObject()
            .put("type", "new_mail")
            .put("item_id", item_id)
            .put(
                GUARD_NOTICE_FIELD,
                JSONObject().put("event", event).put("id", id).put("account_id", account),
            )

    private fun guard(s: store, payload: JSONObject = guard_payload(), shown: MutableList<guard_push>) =
        handle_guard_push(
            payload,
            known_accounts = { listOf(account) },
            claim = s::claim,
            enqueue = { shown += it },
            marks = s.marks,
            replace_notice_mail = s::replace_notice_mail,
        )

    @Test
    fun guard_push_first_then_mail_push_shows_one_notification() {
        val s = store()
        val shown = mutableListOf<guard_push>()
        assertEquals(PushResult.Shown, guard(s, shown = shown))
        s.now += 150L
        assertTrue(s.claim_notice_mail(system_sender))
        assertEquals(1, shown.size)
        assertTrue(s.posted.isEmpty())
    }

    @Test
    fun mail_push_first_then_guard_push_replaces_the_mail_notification() {
        val s = store()
        val shown = mutableListOf<guard_push>()
        assertFalse(s.claim_notice_mail(system_sender))
        s.show_mail(item_id, notification_id, system_sender)
        assertEquals(setOf(notification_id), s.posted)
        s.now += 150L
        assertEquals(PushResult.Shown, guard(s, shown = shown))
        assertEquals(1, shown.size)
        assertTrue(s.posted.isEmpty())
        assertEquals(setOf(notification_id), s.cancelled)
    }

    @Test
    fun guard_push_landing_between_check_and_post_still_leaves_one_notification() {
        val s = store()
        val shown = mutableListOf<guard_push>()
        assertFalse(s.claim_notice_mail(system_sender))
        s.show_mail(item_id, notification_id, system_sender) { guard(s, shown = shown) }
        assertEquals(1, shown.size)
        assertTrue(s.posted.isEmpty())
    }

    @Test
    fun guard_push_landing_after_the_check_but_before_tracking_suppresses_the_mail() {
        val s = store()
        val shown = mutableListOf<guard_push>()
        assertFalse(s.claim_notice_mail(system_sender))
        guard(s, shown = shown)
        s.show_mail(item_id, notification_id, system_sender)
        assertEquals(1, shown.size)
        assertTrue(s.posted.isEmpty())
    }

    @Test
    fun replacing_the_notice_mail_clears_the_mark_so_later_mail_still_notifies() {
        val s = store()
        s.show_mail(item_id, notification_id, system_sender)
        guard(s, shown = mutableListOf())
        s.now += 60_000L
        assertFalse(s.claim_notice_mail(system_sender))
        val later_item = "aa4c753b-0de6-4fa4-aeec-23f1a0434ae2"
        val later_id = MailPollingWorker.message_notification_id(later_item.hashCode())
        s.show_mail(later_item, later_id, system_sender)
        assertEquals(setOf(later_id), s.posted)
    }

    @Test
    fun ordinary_mail_is_never_tracked_or_replaced() {
        val s = store()
        s.show_mail(item_id, notification_id, "friend@example.com")
        guard(s, shown = mutableListOf())
        assertEquals(setOf(notification_id), s.posted)
        assertTrue(s.cancelled.isEmpty())
    }

    @Test
    fun old_system_mail_outside_the_window_is_not_replaced() {
        val s = store()
        s.show_mail(item_id, notification_id, system_sender)
        s.now += NotificationDedupe.SIGN_IN_MAIL_WINDOW_MS
        guard(s, shown = mutableListOf())
        assertEquals(setOf(notification_id), s.posted)
    }

    @Test
    fun events_without_a_notice_never_replace_mail() {
        val s = store()
        s.show_mail(item_id, notification_id, system_sender)
        guard(s, guard_payload(event = "sign_in_request"), mutableListOf())
        guard(s, guard_payload(event = "change_applied"), mutableListOf())
        assertEquals(setOf(notification_id), s.posted)
    }

    @Test
    fun rescue_started_replaces_its_notice_mail() {
        val s = store()
        s.show_mail(item_id, notification_id, system_sender)
        guard(s, guard_payload(event = "rescue_started"), mutableListOf())
        assertTrue(s.posted.isEmpty())
    }

    @Test
    fun tagged_notice_mail_becomes_the_guard_payload() {
        val routed = guard_notice_payload(notice_mail_payload())
        assertNotNull(routed)
        assertEquals("guard", routed!!.getString("type"))
        assertEquals("change_held", routed.getString("event"))
        assertEquals(change_id, routed.getString("id"))
        assertEquals(account, routed.getString("account_id"))
    }

    @Test
    fun untagged_or_invalid_notice_mail_stays_ordinary_mail() {
        assertNull(guard_notice_payload(JSONObject().put("type", "new_mail").put("item_id", item_id)))
        assertNull(guard_notice_payload(notice_mail_payload(event = "change_applied")))
        assertNull(guard_notice_payload(notice_mail_payload(event = "made_up")))
        assertNull(guard_notice_payload(notice_mail_payload(id = "not-a-uuid")))
        assertNull(guard_notice_payload(notice_mail_payload().put("type", "wake")))
    }

    @Test
    fun tagged_mail_and_guard_push_converge_on_one_event_in_either_order() {
        for (mail_first in listOf(true, false)) {
            val s = store()
            val shown = mutableListOf<guard_push>()
            val routed = guard_notice_payload(notice_mail_payload())!!
            val results = if (mail_first) {
                listOf(guard(s, routed, shown), guard(s, shown = shown))
            } else {
                listOf(guard(s, shown = shown), guard(s, routed, shown))
            }
            assertEquals(listOf(PushResult.Shown, PushResult.Ignore), results)
            assertEquals(1, shown.size)
            assertEquals(listOf("guard:change_held:$change_id"), s.claimed)
        }
    }

    @Test
    fun system_mail_key_round_trips_the_notification_id() {
        val key = NotificationDedupe.system_mail_key(item_id, notification_id)
        assertEquals(notification_id, NotificationDedupe.system_mail_notification_id(key))
        assertNull(NotificationDedupe.system_mail_notification_id(NotificationDedupe.event_key("guard", "x")))
    }
}
