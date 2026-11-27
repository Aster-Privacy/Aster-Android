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

import android.content.Context
import org.astermail.android.ui.mail.is_aster_system_address

data class SignInAlertMarker(
    val session_id: String,
    val alert_time_ms: Long,
    val recorded_at_ms: Long,
)

object NotificationDedupe {
    const val SEEN_WINDOW_MS = 10L * 60L * 1000L
    const val SIGN_IN_MAIL_WINDOW_MS = 5L * 60L * 1000L
    const val SEEN_MAX_ENTRIES = 64
    const val LOGIN_ALERT_EVENT = "login_alert"
    const val NEW_MAIL_EVENT = "new_mail"
    const val GUARD_MARK_EVENT = "guard_mark"
    const val GUARD_ANY_ACCOUNT = "*"
    const val SYSTEM_MAIL_EVENT = "system_mail"
    val GUARD_NOTICE_EVENTS = setOf("change_held", "rescue_started")

    private const val PREFS_NAME = "notification_dedupe_prefs"
    private const val KEY_SEEN_EVENTS = "seen_events"
    private const val KEY_SIGN_IN_SESSION = "sign_in_session_id"
    private const val KEY_SIGN_IN_ALERT_MS = "sign_in_alert_ms"
    private const val KEY_SIGN_IN_RECORDED_MS = "sign_in_recorded_ms"
    private const val ENTRY_SEPARATOR = '\n'
    private const val FIELD_SEPARATOR = '\u0001'

    fun event_key(event: String, event_id: String): String = "$event$FIELD_SEPARATOR$event_id"

    fun parse_seen(raw: String): Map<String, Long> {
        if (raw.isBlank()) return emptyMap()
        val parsed = LinkedHashMap<String, Long>()
        raw.split(ENTRY_SEPARATOR).forEach { line ->
            if (line.isBlank()) return@forEach
            val split = line.lastIndexOf(FIELD_SEPARATOR)
            if (split <= 0 || split == line.length - 1) return@forEach
            val key = line.substring(0, split)
            val stamp = line.substring(split + 1).toLongOrNull() ?: return@forEach
            parsed[key] = stamp
        }
        return parsed
    }

    fun encode_seen(entries: Map<String, Long>): String =
        entries.entries.joinToString(ENTRY_SEPARATOR.toString()) { "${it.key}$FIELD_SEPARATOR${it.value}" }

    fun prune_seen(entries: Map<String, Long>, now_ms: Long): Map<String, Long> {
        val live = entries.filterValues { stamp -> is_within_window(stamp, now_ms, SEEN_WINDOW_MS) }
        if (live.size <= SEEN_MAX_ENTRIES) return live
        return live.entries
            .sortedBy { it.value }
            .takeLast(SEEN_MAX_ENTRIES)
            .associate { it.key to it.value }
    }

    fun is_seen(entries: Map<String, Long>, key: String, now_ms: Long): Boolean {
        val stamp = entries[key] ?: return false
        return is_within_window(stamp, now_ms, SEEN_WINDOW_MS)
    }

    fun record_seen(entries: Map<String, Long>, key: String, now_ms: Long): Map<String, Long> {
        val updated = LinkedHashMap(prune_seen(entries, now_ms))
        updated.remove(key)
        updated[key] = now_ms
        return prune_seen(updated, now_ms)
    }

    fun is_within_window(stamp_ms: Long, now_ms: Long, window_ms: Long): Boolean {
        if (stamp_ms <= 0L) return false
        val elapsed = now_ms - stamp_ms
        return elapsed >= 0L && elapsed < window_ms
    }

    fun parse_time_ms(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            java.time.OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        }.getOrNull()
    }

    @Synchronized
    fun claim_event(
        context: Context,
        key: String,
        now_ms: Long = System.currentTimeMillis(),
    ): Boolean {
        if (key.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty())
        if (is_seen(entries, key, now_ms)) return false
        prefs.edit()
            .putString(KEY_SEEN_EVENTS, encode_seen(record_seen(entries, key, now_ms)))
            .commit()
        return true
    }

    @Synchronized
    fun forget_event(context: Context, key: String) {
        if (key.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = LinkedHashMap(parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty()))
        if (entries.remove(key) == null) return
        prefs.edit().putString(KEY_SEEN_EVENTS, encode_seen(entries)).commit()
    }

    fun guard_mark_key(account_id: String, event: String): String =
        event_key(GUARD_MARK_EVENT, "${account_id.lowercase()}:$event")

    fun record_guard_mark(
        entries: Map<String, Long>,
        account_id: String,
        event: String,
        now_ms: Long,
    ): Map<String, Long> {
        val with_account = record_seen(entries, guard_mark_key(account_id, event), now_ms)
        val with_any = record_seen(with_account, guard_mark_key(GUARD_ANY_ACCOUNT, event), now_ms)
        if (event !in GUARD_NOTICE_EVENTS) return with_any
        return record_seen(with_any, guard_mark_key(GUARD_ANY_ACCOUNT, GUARD_ANY_ACCOUNT), now_ms)
    }

    fun take_guard_notice_mark(
        entries: Map<String, Long>,
        sender_email: String,
        now_ms: Long,
    ): Map<String, Long>? {
        if (!is_probable_guard_notice_mail(last_guard_mark_ms(entries), sender_email, now_ms)) return null
        return entries - guard_mark_key(GUARD_ANY_ACCOUNT, GUARD_ANY_ACCOUNT)
    }

    @Synchronized
    fun claim_guard_notice_mail(
        context: Context,
        sender_email: String,
        now_ms: Long = System.currentTimeMillis(),
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty())
        val remaining = take_guard_notice_mark(entries, sender_email, now_ms) ?: return false
        prefs.edit().putString(KEY_SEEN_EVENTS, encode_seen(remaining)).commit()
        return true
    }

    fun has_guard_mark(entries: Map<String, Long>, account_id: String, event: String, now_ms: Long): Boolean =
        is_seen(entries, guard_mark_key(account_id, event), now_ms)

    fun last_guard_mark_ms(entries: Map<String, Long>): Long =
        entries[guard_mark_key(GUARD_ANY_ACCOUNT, GUARD_ANY_ACCOUNT)] ?: 0L

    fun is_probable_guard_notice_mail(
        last_guard_mark_ms: Long,
        sender_email: String,
        now_ms: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!is_within_window(last_guard_mark_ms, now_ms, SIGN_IN_MAIL_WINDOW_MS)) return false
        return is_aster_system_address(sender_email)
    }

    fun system_mail_key(item_id: String, notification_id: Int): String =
        event_key(SYSTEM_MAIL_EVENT, "$notification_id:$item_id")

    fun system_mail_notification_id(key: String): Int? {
        val prefix = event_key(SYSTEM_MAIL_EVENT, "")
        if (!key.startsWith(prefix)) return null
        return key.removePrefix(prefix).substringBefore(':').toIntOrNull()
    }

    fun track_system_mail_entries(
        entries: Map<String, Long>,
        item_id: String,
        notification_id: Int,
        sender_email: String,
        now_ms: Long,
    ): Pair<Map<String, Long>, Boolean> {
        if (item_id.isBlank() || !is_aster_system_address(sender_email)) return entries to true
        take_guard_notice_mark(entries, sender_email, now_ms)?.let { return it to false }
        return record_seen(entries, system_mail_key(item_id, notification_id), now_ms) to true
    }

    fun take_system_mail_entries(entries: Map<String, Long>, now_ms: Long): Pair<Map<String, Long>, Set<Int>> {
        val taken = entries.filter { (key, stamp) ->
            system_mail_notification_id(key) != null && is_within_window(stamp, now_ms, SIGN_IN_MAIL_WINDOW_MS)
        }
        val stale = entries.keys.filter { system_mail_notification_id(it) != null }
        if (taken.isEmpty()) return (entries - stale.toSet()) to emptySet()
        val remaining = entries - stale.toSet() - guard_mark_key(GUARD_ANY_ACCOUNT, GUARD_ANY_ACCOUNT)
        return remaining to taken.keys.mapNotNull { system_mail_notification_id(it) }.toSet()
    }

    @Synchronized
    fun track_system_mail(
        context: Context,
        item_id: String,
        notification_id: Int,
        sender_email: String?,
        now_ms: Long = System.currentTimeMillis(),
    ): Boolean {
        if (sender_email.isNullOrBlank()) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty())
        val (updated, allowed) = track_system_mail_entries(entries, item_id, notification_id, sender_email, now_ms)
        if (updated != entries) prefs.edit().putString(KEY_SEEN_EVENTS, encode_seen(updated)).commit()
        return allowed
    }

    @Synchronized
    fun system_mail_tracked(context: Context, item_id: String, notification_id: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty())
        return entries.containsKey(system_mail_key(item_id, notification_id))
    }

    @Synchronized
    fun take_system_mail(context: Context, now_ms: Long = System.currentTimeMillis()): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty())
        val (remaining, taken) = take_system_mail_entries(entries, now_ms)
        if (remaining != entries) prefs.edit().putString(KEY_SEEN_EVENTS, encode_seen(remaining)).commit()
        return taken
    }

    @Synchronized
    fun clear_guard_notice_mark(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty())
        val key = guard_mark_key(GUARD_ANY_ACCOUNT, GUARD_ANY_ACCOUNT)
        if (!entries.containsKey(key)) return
        prefs.edit().putString(KEY_SEEN_EVENTS, encode_seen(entries - key)).commit()
    }

    @Synchronized
    fun note_guard_alert(
        context: Context,
        account_id: String,
        event: String,
        now_ms: Long = System.currentTimeMillis(),
    ) {
        if (account_id.isBlank() || event.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty())
        prefs.edit()
            .putString(KEY_SEEN_EVENTS, encode_seen(record_guard_mark(entries, account_id, event, now_ms)))
            .commit()
    }

    @Synchronized
    fun guard_alert_recent(
        context: Context,
        account_id: String,
        event: String,
        now_ms: Long = System.currentTimeMillis(),
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return has_guard_mark(parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty()), account_id, event, now_ms)
    }

    @Synchronized
    fun guard_mark_ms(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return last_guard_mark_ms(parse_seen(prefs.getString(KEY_SEEN_EVENTS, "").orEmpty()))
    }

    fun guard_marks(context: Context): org.astermail.android.guard.guard_alert_marks =
        object : org.astermail.android.guard.guard_alert_marks {
            override fun recent(account_id: String, event: org.astermail.android.guard.guard_event): Boolean =
                guard_alert_recent(context, account_id, event.name)

            override fun note(account_id: String, event: org.astermail.android.guard.guard_event) =
                note_guard_alert(context, account_id, event.name)
        }

    @Synchronized
    fun note_sign_in_alert(
        context: Context,
        session_id: String,
        alert_time: String,
        now_ms: Long = System.currentTimeMillis(),
    ) {
        if (session_id.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SIGN_IN_SESSION, session_id)
            .putLong(KEY_SIGN_IN_ALERT_MS, parse_time_ms(alert_time) ?: 0L)
            .putLong(KEY_SIGN_IN_RECORDED_MS, now_ms)
            .commit()
    }

    @Synchronized
    fun sign_in_marker(context: Context): SignInAlertMarker? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val session_id = prefs.getString(KEY_SIGN_IN_SESSION, "").orEmpty()
        if (session_id.isBlank()) return null
        return SignInAlertMarker(
            session_id = session_id,
            alert_time_ms = prefs.getLong(KEY_SIGN_IN_ALERT_MS, 0L),
            recorded_at_ms = prefs.getLong(KEY_SIGN_IN_RECORDED_MS, 0L),
        )
    }

    fun is_probable_sign_in_alert_mail(
        marker: SignInAlertMarker?,
        sender_email: String,
        now_ms: Long = System.currentTimeMillis(),
    ): Boolean {
        if (marker == null) return false
        if (!is_within_window(marker.recorded_at_ms, now_ms, SIGN_IN_MAIL_WINDOW_MS)) return false
        return is_aster_system_address(sender_email)
    }

    fun is_sign_in_alert_mail(
        marker: SignInAlertMarker?,
        sender_email: String,
        sent_at: String,
        now_ms: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!is_probable_sign_in_alert_mail(marker, sender_email, now_ms)) return false
        val alert_time_ms = marker?.alert_time_ms ?: 0L
        if (alert_time_ms <= 0L) return true
        val sent_ms = parse_time_ms(sent_at) ?: return true
        return sent_ms == alert_time_ms
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
