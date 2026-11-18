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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import org.astermail.android.mail.MailRepository

private const val DELETED_PREFS_NAME = "aster_deleted_subscriptions"
private const val DELETED_ENTRY_SEPARATOR = "\n"
internal const val MAX_DELETED_SUBSCRIPTIONS = 1000

internal fun parse_subscription_instant(value: String): Instant? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { Instant.parse(trimmed) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
}

internal fun is_deleted_subscription(
    tombstones: Map<String, String>,
    sender_email: String,
    last_received: String,
): Boolean {
    val deleted_at = tombstones[normalize_sender_email(sender_email)] ?: return false
    val received = parse_subscription_instant(last_received) ?: return true
    val deleted = parse_subscription_instant(deleted_at) ?: return true
    return !received.isAfter(deleted)
}

internal fun with_deleted_subscriptions(
    tombstones: Map<String, String>,
    sender_emails: Collection<String>,
    deleted_at: String,
    max_entries: Int = MAX_DELETED_SUBSCRIPTIONS,
): Map<String, String> {
    val merged = LinkedHashMap(tombstones)
    sender_emails
        .map(::normalize_sender_email)
        .filter { it.contains('@') }
        .forEach { merged[it] = deleted_at }
    if (merged.size <= max_entries) return merged
    return merged.entries
        .sortedByDescending { parse_subscription_instant(it.value) ?: Instant.EPOCH }
        .take(max_entries)
        .associateTo(LinkedHashMap()) { it.key to it.value }
}

internal fun encode_deleted_subscriptions(tombstones: Map<String, String>): String =
    tombstones.entries.joinToString(DELETED_ENTRY_SEPARATOR) { "${it.key} ${it.value}" }

internal fun decode_deleted_subscriptions(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    val result = LinkedHashMap<String, String>()
    raw.split(DELETED_ENTRY_SEPARATOR).forEach { line ->
        val parts = line.trim().split(' ')
        if (parts.size == 2 && parts[0].contains('@') && parse_subscription_instant(parts[1]) != null) {
            result[parts[0]] = parts[1]
        }
    }
    return result
}

@Singleton
class DeletedSubscriptionsStore @Inject constructor(
    private val repository: MailRepository,
    @ApplicationContext private val context: Context,
) {
    private val lock = Any()

    private fun account_key(): String {
        val email = normalize_sender_email(repository.get_user_email().orEmpty())
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(email.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun prefs() = runCatching {
        org.astermail.android.storage.SecurePrefs.open(context, DELETED_PREFS_NAME)
    }.getOrNull()

    fun tombstones(): Map<String, String> = synchronized(lock) {
        decode_deleted_subscriptions(prefs()?.getString(account_key(), null))
    }

    fun record_deleted(sender_emails: Collection<String>, deleted_at: String = Instant.now().toString()) {
        if (sender_emails.isEmpty()) return
        synchronized(lock) {
            val store = prefs() ?: return
            val key = account_key()
            val merged = with_deleted_subscriptions(
                decode_deleted_subscriptions(store.getString(key, null)),
                sender_emails,
                deleted_at,
            )
            store.edit().putString(key, encode_deleted_subscriptions(merged)).apply()
        }
    }

    fun is_deleted(sender_email: String, last_received: String): Boolean =
        is_deleted_subscription(tombstones(), sender_email, last_received)
}
