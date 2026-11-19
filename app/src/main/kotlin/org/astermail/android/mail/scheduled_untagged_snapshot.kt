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

import org.astermail.android.storage.ScheduledUntaggedSnapshot
import org.astermail.android.storage.snapshot_id_list
import org.astermail.android.storage.snapshot_time_ms
import org.json.JSONObject

const val SCHEDULED_UNTAGGED_FIELD = "scheduled_untagged"
const val LEGACY_SCHEDULED_UNTAGGED_FIELD = "scheduled_untagged_ids"

fun vault_scheduled_untagged(
    vault: JSONObject?,
    now_ms: Long = System.currentTimeMillis(),
): ScheduledUntaggedSnapshot? {
    if (vault == null) return null
    val legacy = vault_legacy_scheduled_ids(vault)
    if (vault_has_shared_scheduled_snapshot(vault)) {
        val shared = ScheduledUntaggedSnapshot.from_json(vault.opt(SCHEDULED_UNTAGGED_FIELD))
            ?: return ScheduledUntaggedSnapshot.EMPTY
        if (legacy == null) return shared
        return shared.copy(ids = shared.ids.filterTo(LinkedHashSet()) { it in legacy })
    }
    return legacy?.let { ScheduledUntaggedSnapshot.taken_at(it, now_ms) }
}

fun vault_has_shared_scheduled_snapshot(vault: JSONObject?): Boolean =
    vault?.has(SCHEDULED_UNTAGGED_FIELD) == true

fun vault_needs_scheduled_snapshot_migration(vault: JSONObject?): Boolean =
    vault != null && !vault_has_shared_scheduled_snapshot(vault) && vault.has(LEGACY_SCHEDULED_UNTAGGED_FIELD)

private fun vault_legacy_scheduled_ids(vault: JSONObject): Set<String>? {
    if (!vault.has(LEGACY_SCHEDULED_UNTAGGED_FIELD)) return null
    return snapshot_id_list(vault.opt(LEGACY_SCHEDULED_UNTAGGED_FIELD)) ?: emptySet()
}

fun with_scheduled_untagged_snapshot(vault: JSONObject, candidate: ScheduledUntaggedSnapshot): Boolean {
    if (vault_has_shared_scheduled_snapshot(vault)) return false
    val legacy = vault_legacy_scheduled_ids(vault)
    val written = if (legacy == null) candidate else candidate.copy(ids = legacy)
    vault.put(SCHEDULED_UNTAGGED_FIELD, written.to_json())
    return true
}

fun earliest_scheduled_untagged(server: Any?, next: Any?): Any? {
    val server_present = server != null
    val server_taken = ScheduledUntaggedSnapshot.from_json(server)?.let { snapshot_time_ms(it.at) }
    if (server_present && server_taken == null) return server
    val next_taken = ScheduledUntaggedSnapshot.from_json(next)?.let { snapshot_time_ms(it.at) }
    return when {
        server_taken != null && next_taken != null -> if (next_taken < server_taken) next else server
        server_taken != null -> server
        next_taken != null -> next
        else -> null
    }
}
