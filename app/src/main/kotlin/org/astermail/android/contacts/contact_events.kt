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

package org.astermail.android.contacts

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

enum class ContactEventKind(val id: String) {
    BIRTHDAY("birthday"),
    ANNIVERSARY("anniversary"),
    CUSTOM("custom"),
    ;

    companion object {
        fun from_id(id: String): ContactEventKind? {
            val lowered = id.trim().lowercase(Locale.ROOT)
            return entries.firstOrNull { it.id == lowered }
        }
    }
}

data class ContactEvent(
    val kind: ContactEventKind,
    val date: String,
    val label: String = "",
)

const val EVENTS_KEY = "events"
const val MAX_CONTACT_EVENTS = 20
const val MAX_EVENT_LABEL = 64
const val MIN_EVENT_YEAR = 1800
const val VCARD_UNLABELED_DATE = "Other"

private val strict_event_date = Regex("""^(?:(\d{4})|-)-(\d{2})-(\d{2})$""")

fun current_event_year(): Int = LocalDate.now().year

fun valid_event_date(date: String, current_year: Int = current_event_year()): Boolean {
    val match = strict_event_date.matchEntire(date) ?: return false
    val year_text = match.groupValues[1]
    val year = if (year_text.isEmpty()) null else year_text.toInt()
    if (year != null && year !in MIN_EVENT_YEAR..(current_year + 1)) return false
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    if (month !in 1..12) return false
    return day in 1..ContactDates.days_in_month(year, month)
}

fun event_label_length(label: String): Int = label.codePointCount(0, label.length)

fun normalize_event(event: ContactEvent, current_year: Int = current_event_year()): ContactEvent? {
    val date = event.date.trim()
    if (!valid_event_date(date, current_year)) return null
    if (event.kind != ContactEventKind.CUSTOM) return ContactEvent(event.kind, date)
    val label = event.label.trim()
    if (label.isEmpty() || event_label_length(label) > MAX_EVENT_LABEL) return null
    return ContactEvent(ContactEventKind.CUSTOM, date, label)
}

fun event_dedupe_key(event: ContactEvent): String =
    event.kind.id + "\u0000" + event.label.lowercase(Locale.ROOT) + "\u0000" + event.date

fun normalize_events(events: List<ContactEvent>, current_year: Int = current_event_year()): List<ContactEvent> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<ContactEvent>()
    var has_birthday = false
    for (event in events) {
        if (out.size >= MAX_CONTACT_EVENTS) break
        val clean = normalize_event(event, current_year) ?: continue
        if (clean.kind == ContactEventKind.BIRTHDAY && has_birthday) continue
        if (!seen.add(event_dedupe_key(clean))) continue
        if (clean.kind == ContactEventKind.BIRTHDAY) has_birthday = true
        out.add(clean)
    }
    return out
}

fun merge_events(first: List<ContactEvent>, second: List<ContactEvent>): List<ContactEvent> =
    normalize_events(first + second)

fun decode_event(obj: JSONObject): ContactEvent? {
    val kind = ContactEventKind.from_id(obj.opt("kind") as? String ?: return null) ?: return null
    val date = obj.opt("date") as? String ?: return null
    val label = obj.opt("label") as? String ?: ""
    return ContactEvent(kind, date, label)
}

fun decode_events(arr: JSONArray?, current_year: Int = current_event_year()): List<ContactEvent> {
    if (arr == null) return emptyList()
    val out = mutableListOf<ContactEvent>()
    for (i in 0 until arr.length()) {
        val entry = arr.opt(i) as? JSONObject ?: continue
        decode_event(entry)?.let { out.add(it) }
    }
    return normalize_events(out, current_year)
}

fun decode_contact_events(obj: JSONObject, current_year: Int = current_event_year()): List<ContactEvent>? {
    if (!obj.has(EVENTS_KEY)) return null
    return decode_events(obj.opt(EVENTS_KEY) as? JSONArray, current_year)
}

fun legacy_birthday_date(birthday: String, current_year: Int = current_event_year()): String? {
    if (birthday.isBlank()) return null
    val parts = ContactDates.parse(birthday) ?: return null
    val date = ContactDates.from_parts(parts)
    return date.takeIf { valid_event_date(it, current_year) }
}

fun resolve_contact_events(
    events: List<ContactEvent>?,
    birthday: String,
    current_year: Int = current_event_year(),
): List<ContactEvent> {
    val base = normalize_events(events.orEmpty(), current_year)
    val legacy = legacy_birthday_date(birthday, current_year) ?: return base
    val index = base.indexOfFirst { it.kind == ContactEventKind.BIRTHDAY }
    val merged = if (index >= 0) {
        base.toMutableList().also { it[index] = ContactEvent(ContactEventKind.BIRTHDAY, legacy) }
    } else {
        listOf(ContactEvent(ContactEventKind.BIRTHDAY, legacy)) + base
    }
    return normalize_events(merged, current_year)
}

fun birthday_from_events(events: List<ContactEvent>): String =
    events.firstOrNull { it.kind == ContactEventKind.BIRTHDAY }?.date.orEmpty()

fun encode_event(event: ContactEvent): JSONObject {
    val obj = JSONObject().put("kind", event.kind.id).put("date", event.date)
    if (event.kind == ContactEventKind.CUSTOM) obj.put("label", event.label)
    return obj
}

fun events_json_array(original: JSONArray?, events: List<ContactEvent>): JSONArray {
    val originals = mutableListOf<Pair<JSONObject, ContactEvent?>>()
    if (original != null) {
        for (i in 0 until original.length()) {
            val entry = original.opt(i) as? JSONObject ?: continue
            originals.add(entry to decode_event(entry)?.let { normalize_event(it) })
        }
    }
    val used = BooleanArray(originals.size)
    val out = JSONArray()
    for (event in events) {
        val match = originals.indices.firstOrNull { !used[it] && originals[it].second == event }
        if (match != null) {
            used[match] = true
            out.put(originals[match].first)
        } else {
            out.put(encode_event(event))
        }
    }
    return out
}

fun write_contact_events(obj: JSONObject, events: List<ContactEvent>) {
    val original = obj.opt(EVENTS_KEY) as? JSONArray
    val clean = normalize_events(events)
    if (original != null && original.length() == clean.size && decode_events(original) == clean) return
    if (clean.isEmpty()) {
        obj.remove(EVENTS_KEY)
        return
    }
    obj.put(EVENTS_KEY, events_json_array(original, clean))
}

private fun unwrap_event_label(label: String): String {
    val trimmed = label.trim()
    val prefix = "_$!<"
    val suffix = ">!$" + "_"
    return if (trimmed.startsWith(prefix) && trimmed.endsWith(suffix) && trimmed.length >= prefix.length + suffix.length) {
        trimmed.substring(prefix.length, trimmed.length - suffix.length).trim()
    } else {
        trimmed
    }
}

fun event_from_vcard(property: String, value: String, ab_label: String?): ContactEvent? {
    val parts = ContactDates.parse(value) ?: return null
    val date = ContactDates.from_parts(parts)
    val event = when (property.uppercase(Locale.ROOT)) {
        "BDAY" -> ContactEvent(ContactEventKind.BIRTHDAY, date)
        "ANNIVERSARY" -> ContactEvent(ContactEventKind.ANNIVERSARY, date)
        "X-ABDATE" -> {
            val raw_label = ab_label.orEmpty().trim()
            val label = unwrap_event_label(raw_label)
            val wrapped = label != raw_label
            when {
                wrapped && label.equals("anniversary", ignoreCase = true) -> ContactEvent(ContactEventKind.ANNIVERSARY, date)
                label.isEmpty() -> ContactEvent(ContactEventKind.CUSTOM, date, VCARD_UNLABELED_DATE)
                else -> ContactEvent(ContactEventKind.CUSTOM, date, cap_code_points(label, MAX_EVENT_LABEL))
            }
        }
        else -> return null
    }
    return normalize_event(event)
}

const val CSV_ANNIVERSARY_LABEL = "Anniversary"

fun event_csv_label(event: ContactEvent): String =
    if (event.kind == ContactEventKind.CUSTOM) event.label else CSV_ANNIVERSARY_LABEL

fun event_from_csv(label: String, date: String): ContactEvent? {
    val clean = label.replace(Regex("^\\*\\s*"), "").trim()
    val parts = ContactDates.parse(date) ?: return null
    val value = ContactDates.from_parts(parts)
    val event = when {
        clean.equals("birthday", ignoreCase = true) -> ContactEvent(ContactEventKind.BIRTHDAY, value)
        clean.equals("anniversary", ignoreCase = true) -> ContactEvent(ContactEventKind.ANNIVERSARY, value)
        clean.isEmpty() -> ContactEvent(ContactEventKind.CUSTOM, value, VCARD_UNLABELED_DATE)
        else -> ContactEvent(ContactEventKind.CUSTOM, value, cap_code_points(clean, MAX_EVENT_LABEL))
    }
    return normalize_event(event)
}
