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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInAlertDedupeTest {

    private val now = 1_700_000_000_000L
    private val system_sender = "no-reply@astermail.org"

    private fun marker(
        alert_time_ms: Long = 0L,
        recorded_at_ms: Long = now,
    ) = SignInAlertMarker(
        session_id = "1d2b0a6c-0000-4000-8000-000000000001",
        alert_time_ms = alert_time_ms,
        recorded_at_ms = recorded_at_ms,
    )

    @Test
    fun event_keys_stay_distinct_per_event_and_id() {
        val a = NotificationDedupe.event_key(NotificationDedupe.LOGIN_ALERT_EVENT, "session_a")
        val b = NotificationDedupe.event_key(NotificationDedupe.LOGIN_ALERT_EVENT, "session_b")
        val c = NotificationDedupe.event_key(NotificationDedupe.NEW_MAIL_EVENT, "session_a")
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(a, NotificationDedupe.event_key(NotificationDedupe.LOGIN_ALERT_EVENT, "session_a"))
    }

    @Test
    fun a_recorded_event_is_seen_again_inside_the_window() {
        val key = NotificationDedupe.event_key(NotificationDedupe.LOGIN_ALERT_EVENT, "s")
        val entries = NotificationDedupe.record_seen(emptyMap(), key, now)
        assertTrue(NotificationDedupe.is_seen(entries, key, now))
        assertTrue(NotificationDedupe.is_seen(entries, key, now + NotificationDedupe.SEEN_WINDOW_MS - 1L))
    }

    @Test
    fun a_recorded_event_expires_after_the_window() {
        val key = NotificationDedupe.event_key(NotificationDedupe.LOGIN_ALERT_EVENT, "s")
        val entries = NotificationDedupe.record_seen(emptyMap(), key, now)
        assertFalse(NotificationDedupe.is_seen(entries, key, now + NotificationDedupe.SEEN_WINDOW_MS))
        assertFalse(NotificationDedupe.is_seen(entries, key, now + NotificationDedupe.SEEN_WINDOW_MS * 3))
    }

    @Test
    fun an_unrecorded_event_is_never_seen() {
        val entries = NotificationDedupe.record_seen(emptyMap(), "a", now)
        assertFalse(NotificationDedupe.is_seen(entries, "b", now))
        assertFalse(NotificationDedupe.is_seen(emptyMap(), "a", now))
    }

    @Test
    fun a_clock_that_moves_backwards_does_not_hide_an_event_forever() {
        val entries = NotificationDedupe.record_seen(emptyMap(), "a", now)
        assertFalse(NotificationDedupe.is_seen(entries, "a", now - 60_000L))
    }

    @Test
    fun the_seen_set_stays_bounded_and_keeps_the_newest() {
        var entries = emptyMap<String, Long>()
        val total = NotificationDedupe.SEEN_MAX_ENTRIES + 20
        repeat(total) { index ->
            entries = NotificationDedupe.record_seen(entries, "key_" + index, now + index)
        }
        val latest = now + total - 1
        assertEquals(NotificationDedupe.SEEN_MAX_ENTRIES.toLong(), entries.size.toLong())
        assertTrue(NotificationDedupe.is_seen(entries, "key_" + (total - 1), latest))
        assertFalse(NotificationDedupe.is_seen(entries, "key_0", latest))
    }

    @Test
    fun re_recording_a_key_refreshes_it_without_duplicating() {
        var entries = NotificationDedupe.record_seen(emptyMap(), "a", now)
        entries = NotificationDedupe.record_seen(entries, "a", now + 1_000L)
        assertEquals(1L, entries.size.toLong())
        assertEquals(now + 1_000L, entries.getValue("a"))
    }

    @Test
    fun the_seen_set_survives_an_encode_and_parse_round_trip() {
        var entries = emptyMap<String, Long>()
        entries = NotificationDedupe.record_seen(
            entries,
            NotificationDedupe.event_key(NotificationDedupe.LOGIN_ALERT_EVENT, "session a"),
            now,
        )
        entries = NotificationDedupe.record_seen(
            entries,
            NotificationDedupe.event_key(NotificationDedupe.NEW_MAIL_EVENT, "item-1"),
            now + 5L,
        )
        val restored = NotificationDedupe.parse_seen(NotificationDedupe.encode_seen(entries))
        assertEquals(entries, restored)
    }

    @Test
    fun a_corrupt_stored_seen_set_parses_to_the_usable_entries() {
        assertEquals(emptyMap<String, Long>(), NotificationDedupe.parse_seen(""))
        assertEquals(emptyMap<String, Long>(), NotificationDedupe.parse_seen("garbage"))
        val encoded = NotificationDedupe.encode_seen(mapOf("a" to now))
        assertEquals(mapOf("a" to now), NotificationDedupe.parse_seen("garbage\n" + encoded + "\n"))
    }

    @Test
    fun rfc3339_alert_times_parse_to_the_same_instant_the_server_sent() {
        assertEquals(
            NotificationDedupe.parse_time_ms("2026-08-28T10:15:30Z"),
            NotificationDedupe.parse_time_ms("2026-08-28T12:15:30+02:00"),
        )
        assertNull(NotificationDedupe.parse_time_ms(""))
        assertNull(NotificationDedupe.parse_time_ms("not a time"))
    }

    @Test
    fun the_sign_in_alert_email_is_suppressed_when_its_timestamp_matches_the_push() {
        val alert_time = "2026-08-28T10:15:30.123456Z"
        val alert_ms = NotificationDedupe.parse_time_ms(alert_time)
        assertTrue(
            NotificationDedupe.is_sign_in_alert_mail(
                marker(alert_time_ms = alert_ms ?: 0L),
                system_sender,
                alert_time,
                now,
            ),
        )
    }

    @Test
    fun another_system_email_with_a_different_timestamp_still_notifies() {
        val alert_ms = NotificationDedupe.parse_time_ms("2026-08-28T10:15:30Z")
        assertFalse(
            NotificationDedupe.is_sign_in_alert_mail(
                marker(alert_time_ms = alert_ms ?: 0L),
                system_sender,
                "2026-08-28T11:00:00Z",
                now,
            ),
        )
    }

    @Test
    fun a_normal_sender_is_never_suppressed() {
        val alert_time = "2026-08-28T10:15:30Z"
        val alert_ms = NotificationDedupe.parse_time_ms(alert_time)
        assertFalse(
            NotificationDedupe.is_sign_in_alert_mail(
                marker(alert_time_ms = alert_ms ?: 0L),
                "friend@example.com",
                alert_time,
                now,
            ),
        )
        assertFalse(
            NotificationDedupe.is_probable_sign_in_alert_mail(marker(), "friend@example.com", now),
        )
    }

    @Test
    fun nothing_is_suppressed_without_a_recent_sign_in_push() {
        assertFalse(NotificationDedupe.is_sign_in_alert_mail(null, system_sender, "", now))
        assertFalse(NotificationDedupe.is_probable_sign_in_alert_mail(null, system_sender, now))
        assertFalse(
            NotificationDedupe.is_probable_sign_in_alert_mail(
                marker(recorded_at_ms = now - NotificationDedupe.SIGN_IN_MAIL_WINDOW_MS),
                system_sender,
                now,
            ),
        )
        assertTrue(
            NotificationDedupe.is_probable_sign_in_alert_mail(
                marker(recorded_at_ms = now - NotificationDedupe.SIGN_IN_MAIL_WINDOW_MS + 1L),
                system_sender,
                now,
            ),
        )
    }

    @Test
    fun an_unparsable_envelope_timestamp_falls_back_to_the_window_check() {
        assertTrue(
            NotificationDedupe.is_sign_in_alert_mail(marker(), system_sender, "", now),
        )
        assertTrue(
            NotificationDedupe.is_sign_in_alert_mail(
                marker(alert_time_ms = NotificationDedupe.parse_time_ms("2026-08-28T10:15:30Z") ?: 0L),
                system_sender,
                "whenever",
                now,
            ),
        )
    }

    @Test
    fun the_login_alert_notification_id_is_stable_and_below_the_mail_band() {
        val session = "1d2b0a6c-0000-4000-8000-000000000001"
        val id = LoginAlertNotifier.notification_id(session)
        assertEquals(id.toLong(), LoginAlertNotifier.notification_id(session).toLong())
        assertNotEquals(id.toLong(), LoginAlertNotifier.notification_id(session + "-other").toLong())
        assertTrue(id < MailPollingWorker.MESSAGE_ID_BASE)
        assertTrue(id > MailPollingWorker.NOTIFICATION_ID)
    }

    @Test
    fun every_login_alert_action_gets_its_own_request_code() {
        val first = LoginAlertNotifier.notification_id("session_a")
        val second = LoginAlertNotifier.notification_id("session_b")
        val codes = listOf(
            LoginAlertNotifier.action_request_code(first, 0),
            LoginAlertNotifier.action_request_code(first, 1),
            LoginAlertNotifier.action_request_code(second, 0),
            LoginAlertNotifier.action_request_code(second, 1),
        )
        assertEquals(codes.size.toLong(), codes.toSet().size.toLong())
        assertTrue(codes.all { it > 0 })
    }
}
