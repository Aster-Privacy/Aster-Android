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

package org.astermail.android.imports

import android.content.Context
import org.astermail.android.api.external_accounts.ExternalAccount
import org.json.JSONObject

const val external_account_alert_after_ms = 24L * 60 * 60 * 1000

private const val max_tracked_accounts = 100

enum class ExternalAccountHealthState {
    PAUSED,
    NEEDS_REAUTH,
    SYNCING,
    FAILING,
    NEVER_SYNCED,
    HEALTHY,
}

data class ExternalAccountHealthRecord(
    val id: String,
    val email: String,
    val protocol: String,
    val is_enabled: Boolean,
    val needs_reauth: Boolean,
    val last_sync_at: String?,
    val last_sync_status: String?,
    val created_at: String?,
    val problem_since: Long?,
)

data class ExternalAccountAlert(
    val record: ExternalAccountHealthRecord,
    val state: ExternalAccountHealthState,
)

private val failed_statuses = setOf("error", "failed", "quota_exceeded")

private val active_statuses = setOf("syncing", "pending", "purging")

internal fun parse_sync_timestamp(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
}

fun external_account_state(
    is_enabled: Boolean,
    needs_reauth: Boolean,
    last_sync_status: String?,
    last_sync_at: String?,
): ExternalAccountHealthState {
    if (needs_reauth) return ExternalAccountHealthState.NEEDS_REAUTH
    if (!is_enabled) return ExternalAccountHealthState.PAUSED
    val status = last_sync_status?.lowercase().orEmpty()
    if (status in active_statuses) return ExternalAccountHealthState.SYNCING
    if (status in failed_statuses) return ExternalAccountHealthState.FAILING
    if (parse_sync_timestamp(last_sync_at) == null) return ExternalAccountHealthState.NEVER_SYNCED
    return ExternalAccountHealthState.HEALTHY
}

fun external_account_state(account: ExternalAccount): ExternalAccountHealthState =
    external_account_state(
        is_enabled = account.is_enabled,
        needs_reauth = account.needs_reauth,
        last_sync_status = account.last_sync_status,
        last_sync_at = account.last_sync_at,
    )

fun is_problem_state(state: ExternalAccountHealthState): Boolean =
    state == ExternalAccountHealthState.NEEDS_REAUTH || state == ExternalAccountHealthState.FAILING

fun external_account_alerts(
    records: List<ExternalAccountHealthRecord>,
    now: Long,
): List<ExternalAccountAlert> = records.mapNotNull { record ->
    val state = external_account_state(
        is_enabled = record.is_enabled,
        needs_reauth = record.needs_reauth,
        last_sync_status = record.last_sync_status,
        last_sync_at = record.last_sync_at,
    )
    val needs_attention = when (state) {
        ExternalAccountHealthState.NEEDS_REAUTH -> record.is_enabled
        ExternalAccountHealthState.FAILING -> {
            val failing_since = listOfNotNull(
                parse_sync_timestamp(record.last_sync_at) ?: parse_sync_timestamp(record.created_at),
                record.problem_since,
            ).minOrNull()
            failing_since != null && now - failing_since >= external_account_alert_after_ms
        }
        else -> false
    }
    if (needs_attention) ExternalAccountAlert(record, state) else null
}

fun external_account_alert_signature(alerts: List<ExternalAccountAlert>): String =
    alerts.map { "${it.record.id}:${it.state.name.lowercase()}" }.sorted().joinToString("|")

fun external_account_health_records(
    accounts: List<ExternalAccount>,
    emails: Map<String, String>,
    problem_since: Map<String, Long>,
): List<ExternalAccountHealthRecord> = accounts.map { account ->
    ExternalAccountHealthRecord(
        id = account.id,
        email = account.oauth_email?.ifBlank { null } ?: emails[account.account_token].orEmpty(),
        protocol = account.protocol,
        is_enabled = account.is_enabled,
        needs_reauth = account.needs_reauth,
        last_sync_at = account.last_sync_at,
        last_sync_status = account.last_sync_status,
        created_at = account.created_at.ifBlank { null },
        problem_since = problem_since[account.id],
    )
}

internal fun next_problem_since(
    previous: Map<String, Long>,
    accounts: List<ExternalAccount>,
    now: Long,
): Map<String, Long> {
    val listed = accounts.map { it.id }.toSet()
    val next = previous.filterKeys { it !in listed }.toMutableMap()
    for (account in accounts) {
        val state = external_account_state(account)
        val since = when {
            is_problem_state(state) -> previous[account.id] ?: now
            state == ExternalAccountHealthState.SYNCING -> previous[account.id]
            else -> null
        }
        if (since != null) next[account.id] = since
    }
    return if (next.size <= max_tracked_accounts) {
        next
    } else {
        next.entries.sortedByDescending { it.value }.take(max_tracked_accounts).associate { it.key to it.value }
    }
}

fun should_show_external_account_banner(
    alerts: List<ExternalAccountAlert>,
    dismissed_signature: String,
): Boolean = alerts.isNotEmpty() && external_account_alert_signature(alerts) != dismissed_signature

object ExternalAccountHealthStore {

    private const val PREFS_NAME = "aster_external_account_health"
    private const val KEY_PROBLEM_SINCE = "problem_since"

    @Volatile
    private var generation = 0

    fun generation(): Int = generation

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun read(context: Context): Map<String, Long> {
        val raw = prefs(context).getString(KEY_PROBLEM_SINCE, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                for (key in obj.keys()) {
                    val value = obj.optLong(key, 0L)
                    if (value > 0L) put(key, value)
                }
            }
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun track(
        context: Context,
        accounts: List<ExternalAccount>,
        expected_generation: Int,
        now: Long = System.currentTimeMillis(),
    ): Map<String, Long>? {
        if (expected_generation != generation) return null
        val previous = read(context)
        val next = next_problem_since(previous, accounts, now)
        if (next != previous) {
            val editor = prefs(context).edit()
            if (next.isEmpty()) {
                editor.remove(KEY_PROBLEM_SINCE)
            } else {
                editor.putString(KEY_PROBLEM_SINCE, JSONObject(next as Map<*, *>).toString())
            }
            editor.apply()
        }
        return next
    }

    @Synchronized
    fun reset(context: Context) {
        generation += 1
        runCatching { prefs(context).edit().clear().apply() }
    }
}
