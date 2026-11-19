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

package org.astermail.android.storage

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import org.json.JSONArray
import org.json.JSONObject

const val SCHEDULED_UNTAGGED_SNAPSHOT_VERSION = 1
const val MAX_SCHEDULED_SNAPSHOT_IDS = 5000

private val SNAPSHOT_TIME_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,3})?Z$""")
private val CHANGE_TIME_PATTERN =
    Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$""")
private val STRICT_OFFSET_TIME: DateTimeFormatter =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME.withResolverStyle(ResolverStyle.STRICT)
private val SNAPSHOT_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

data class ScheduledUntaggedSnapshot(val at: String, val ids: Set<String>) {

    fun to_json(): JSONObject = JSONObject()
        .put("v", SCHEDULED_UNTAGGED_SNAPSHOT_VERSION)
        .put("at", at)
        .put("ids", JSONArray(ids.sorted()))

    fun allows(id: String, updated_at: String?): Boolean {
        if (id !in ids) return false
        val taken_ms = snapshot_time_ms(at) ?: return false
        val changed_ms = change_time_ms(updated_at) ?: return false
        return changed_ms <= taken_ms
    }

    companion object {
        val EMPTY = ScheduledUntaggedSnapshot("1970-01-01T00:00:00.000Z", emptySet())

        fun taken_at(ids: Set<String>, now_ms: Long): ScheduledUntaggedSnapshot =
            ScheduledUntaggedSnapshot(SNAPSHOT_TIME_FORMAT.format(Instant.ofEpochMilli(now_ms)), ids)

        fun from_json(value: Any?): ScheduledUntaggedSnapshot? {
            val snapshot = value as? JSONObject ?: return null
            val version = snapshot.opt("v") as? Number ?: return null
            if (version.toDouble() != SCHEDULED_UNTAGGED_SNAPSHOT_VERSION.toDouble()) return null
            val at = snapshot.opt("at") as? String ?: return null
            if (snapshot_time_ms(at) == null) return null
            val ids = snapshot_id_list(snapshot.opt("ids")) ?: return null
            return ScheduledUntaggedSnapshot(at, ids)
        }
    }
}

fun snapshot_time_ms(at: String): Long? {
    if (!SNAPSHOT_TIME_PATTERN.matches(at)) return null
    return strict_time_ms(at)
}

fun change_time_ms(value: String?): Long? {
    if (value == null || !CHANGE_TIME_PATTERN.matches(value)) return null
    return strict_time_ms(value)
}

private fun strict_time_ms(value: String): Long? =
    runCatching { OffsetDateTime.parse(value, STRICT_OFFSET_TIME).toInstant().toEpochMilli() }.getOrNull()

fun snapshot_id_list(value: Any?): Set<String>? {
    val array = value as? JSONArray ?: return null
    if (array.length() > MAX_SCHEDULED_SNAPSHOT_IDS) return null
    val ids = LinkedHashSet<String>()
    for (index in 0 until array.length()) {
        val id = array.opt(index) as? String ?: return null
        if (id.isEmpty()) return null
        ids.add(id)
    }
    return ids
}
