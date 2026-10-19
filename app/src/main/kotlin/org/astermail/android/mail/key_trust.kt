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

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.astermail.android.api.keys.ExternalKeyInfo

const val EXTERNAL_KEY_TRUST_CACHE_TTL_MS = 5 * 60_000L

class ExternalKeyTrustCache(
    private val ttl_ms: Long = EXTERNAL_KEY_TRUST_CACHE_TTL_MS,
    private val now_ms: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val change: RecipientKeyChange?,
        val has_key: Boolean,
        val checked_at_ms: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun is_fresh(entry: Entry, now: Long): Boolean {
        val age = now - entry.checked_at_ms
        return age in 0..ttl_ms
    }

    fun fresh_changes(emails: List<String>): List<RecipientKeyChange>? {
        if (emails.isEmpty()) return emptyList()
        val now = now_ms()
        val changes = mutableListOf<RecipientKeyChange>()
        for (email in emails) {
            val entry = entries[email] ?: return null
            if (!is_fresh(entry, now)) return null
            entry.change?.let(changes::add)
        }
        return changes
    }

    fun all_fresh_without_key(emails: List<String>): Boolean {
        val now = now_ms()
        return emails.all { email ->
            val entry = entries[email]
            entry != null && is_fresh(entry, now) && !entry.has_key && entry.change == null
        }
    }

    fun store(emails: List<String>, changes: List<RecipientKeyChange>, keyed: Set<String>) {
        val now = now_ms()
        entries.entries.removeIf { !is_fresh(it.value, now) }
        val by_email = changes.associateBy { it.email }
        for (email in emails) {
            entries[email] = Entry(by_email[email], email in keyed, now)
        }
    }

    fun forget(email: String) {
        entries.remove(email.trim().lowercase(Locale.ROOT))
    }

    fun clear() {
        entries.clear()
    }
}

data class RecipientKeyChange(
    val email: String,
    val prior_fingerprint: String,
    val new_fingerprint: String,
)

fun external_key_trust_candidates(recipients: List<String>): List<String> =
    recipients
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.contains('@') && !is_internal_recipient(it) }
        .distinct()

fun keyed_recipients_from_discovery(keys: List<ExternalKeyInfo>): Set<String> =
    keys.filter { it.found }.map { it.email.trim().lowercase(Locale.ROOT) }.toSet()

fun key_changes_from_discovery(keys: List<ExternalKeyInfo>): List<RecipientKeyChange> =
    keys.mapNotNull { info ->
        val change = info.fingerprint_change ?: return@mapNotNull null
        if (change.prior_fingerprint.isBlank() || change.new_fingerprint.isBlank()) {
            return@mapNotNull null
        }
        RecipientKeyChange(
            email = info.email.trim().lowercase(Locale.ROOT),
            prior_fingerprint = change.prior_fingerprint,
            new_fingerprint = change.new_fingerprint,
        )
    }
