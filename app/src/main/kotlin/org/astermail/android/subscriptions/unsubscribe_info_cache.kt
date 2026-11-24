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
import javax.inject.Inject
import javax.inject.Singleton
import org.astermail.android.api.subscriptions.MailingListSubscription
import org.astermail.android.ui.mail.UnsubscribeInfo
import org.json.JSONObject

private const val INFO_PREFS_NAME = "aster_unsubscribe_info"
private const val INFO_PREFIX = "info_"
private const val PRIMED_PREFIX = "primed_"
private const val PRIMED_MARKER = "\u0000primed"
private const val MAX_INFO_FIELD_LENGTH = 2048

private val safe_unsubscribe_schemes = setOf("http", "https", "mailto")

internal fun is_safe_unsubscribe_target(url: String?): Boolean {
    if (url.isNullOrBlank() || url.length > MAX_INFO_FIELD_LENGTH) return false
    val scheme = url.substringBefore(':', "").trim().lowercase(java.util.Locale.ROOT)
    if (scheme !in safe_unsubscribe_schemes) return false
    if (scheme == "mailto") return url.substringAfter(':').substringBefore('?').contains('@')
    return url.substringAfter("://", "").substringBefore('/').isNotBlank()
}

internal fun better_unsubscribe_info(
    existing: UnsubscribeInfo?,
    candidate: UnsubscribeInfo,
    candidate_wins_tie: Boolean = false,
): UnsubscribeInfo {
    if (existing == null || !existing.has_unsubscribe) return candidate
    if (!candidate.has_unsubscribe) return existing
    fun rank(info: UnsubscribeInfo): Int = when (info.method) {
        "one-click" -> 3
        "mailto" -> 2
        "link" -> 1
        else -> 0
    }
    val candidate_rank = rank(candidate)
    val existing_rank = rank(existing)
    return if (candidate_rank > existing_rank || (candidate_wins_tie && candidate_rank == existing_rank)) {
        candidate
    } else {
        existing
    }
}

internal fun fallback_unsubscribe_info(item: MailingListSubscription): UnsubscribeInfo? {
    val link = item.unsubscribe_link?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_INFO_FIELD_LENGTH }
        ?: return null
    return if (link.startsWith("mailto:", ignoreCase = true)) {
        val address = link.substring("mailto:".length)
        if (!address.substringBefore('?').contains('@')) return null
        UnsubscribeInfo(
            has_unsubscribe = true,
            unsubscribe_mailto = address,
            method = "mailto",
        )
    } else if (is_safe_unsubscribe_target(link)) {
        UnsubscribeInfo(
            has_unsubscribe = true,
            unsubscribe_link = link,
            unsubscribe_page_url = link,
            method = "link",
        )
    } else {
        null
    }
}

internal fun encode_unsubscribe_info(info: UnsubscribeInfo): String {
    val json = JSONObject()
    fun put(key: String, value: String?) {
        if (!value.isNullOrBlank() && value.length <= MAX_INFO_FIELD_LENGTH) json.put(key, value)
    }
    json.put("k", info.method)
    put("l", info.unsubscribe_link)
    put("m", info.unsubscribe_mailto)
    put("u", info.unsubscribe_page_url)
    put("h", info.list_unsubscribe_header)
    put("p", info.list_unsubscribe_post)
    return json.toString()
}

internal fun decode_unsubscribe_info(raw: String?): UnsubscribeInfo? {
    if (raw.isNullOrBlank()) return null
    return try {
        val json = JSONObject(raw)
        fun read(key: String): String? = json.optString(key, "").takeIf { it.isNotBlank() }
        val method = read("k") ?: "none"
        val info = UnsubscribeInfo(
            has_unsubscribe = method != "none",
            unsubscribe_link = read("l"),
            unsubscribe_mailto = read("m"),
            unsubscribe_page_url = read("u"),
            method = method,
            list_unsubscribe_header = read("h"),
            list_unsubscribe_post = read("p"),
        )
        info.takeIf { it.has_unsubscribe }
    } catch (_: org.json.JSONException) {
        null
    }
}

@Singleton
class UnsubscribeInfoCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val unsubscribed_store: UnsubscribedSendersStore,
) {
    private val lock = Any()

    private fun prefs() = context.getSharedPreferences(INFO_PREFS_NAME, Context.MODE_PRIVATE)

    fun get(sender_email: String): UnsubscribeInfo? {
        if (!sender_email.contains('@')) return null
        val key = INFO_PREFIX + unsubscribed_store.token_for(sender_email)
        return decode_unsubscribe_info(prefs().getString(key, null))
    }

    fun put_all(entries: Map<String, UnsubscribeInfo>) {
        if (entries.isEmpty()) return
        synchronized(lock) {
            val stored = prefs()
            val editor = stored.edit()
            var changed = false
            for ((sender_email, info) in entries) {
                if (!info.has_unsubscribe || !sender_email.contains('@')) continue
                val key = INFO_PREFIX + unsubscribed_store.token_for(sender_email)
                val existing = decode_unsubscribe_info(stored.getString(key, null))
                val chosen = better_unsubscribe_info(existing, info, candidate_wins_tie = true)
                if (chosen == existing) continue
                editor.putString(key, encode_unsubscribe_info(chosen))
                changed = true
            }
            if (changed) editor.apply()
        }
    }

    fun is_primed(): Boolean =
        prefs().getBoolean(PRIMED_PREFIX + unsubscribed_store.token_for(PRIMED_MARKER), false)

    fun mark_primed() {
        prefs().edit()
            .putBoolean(PRIMED_PREFIX + unsubscribed_store.token_for(PRIMED_MARKER), true)
            .apply()
    }
}
