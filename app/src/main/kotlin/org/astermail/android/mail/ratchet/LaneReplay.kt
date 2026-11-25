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

package org.astermail.android.mail.ratchet

import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.json.JSONArray
import org.json.JSONObject

data class LaneReplayEntry(val k: String, val m: String, val t: Long)

data class LaneReplayRecord(val f: Long, val e: List<LaneReplayEntry>) {
    fun to_json(): JSONObject = JSONObject()
        .put("f", f)
        .put(
            "e",
            JSONArray().apply {
                e.forEach { put(JSONObject().put("k", it.k).put("m", it.m).put("t", it.t)) }
            },
        )
}

data class LaneReplayClaim(val freshness: LaneFreshness, val record: LaneReplayRecord, val changed: Boolean)

enum class BootstrapClaim { FIRST, SAME_MESSAGE, REPLAYED, UNKNOWN }

data class BootstrapRecordClaim(val claim: BootstrapClaim, val record: LaneReplayRecord, val changed: Boolean)

object LaneReplay {
    const val MAX_ENTRIES = 1024
    const val MAX_TOTAL_ENTRIES = 4096
    const val MAX_SENDERS = 256
    const val GLOBAL_FLOOR_KEY = "*"
    private const val DIGEST_BYTES = 16
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

    val EMPTY = LaneReplayRecord(0, emptyList())

    private val entry_order = compareBy<LaneReplayEntry>({ it.t }, { it.k }, { it.m })

    private fun short_digest(value: String): String =
        RatchetCrypto.b64_encode(
            RatchetCrypto.sha256(value.toByteArray(Charsets.UTF_8)).copyOfRange(0, DIGEST_BYTES),
        )

    fun key(conversation_id: String, snonce: String): String = short_digest("$conversation_id\n$snonce")

    fun message(message_id: String): String = short_digest(message_id)

    fun bootstrap_key(
        sender_identity_key: String,
        ephemeral_key: String,
        pq_ciphertext: String?,
        dh_public: String,
        message_number: Int,
    ): String = short_digest(
        listOf(
            "bootstrap",
            sender_identity_key,
            ephemeral_key,
            pq_ciphertext.orEmpty(),
            dh_public,
            message_number.toString(),
        ).joinToString("\n"),
    )

    fun claim_bootstrap(stored: Any?, key: String, message: String, now: Long): BootstrapRecordClaim {
        val record = normalize(stored)
        val prior = record.e.firstOrNull { it.k == key }
        if (prior != null) {
            return BootstrapRecordClaim(
                if (prior.m == message) BootstrapClaim.SAME_MESSAGE else BootstrapClaim.REPLAYED,
                record,
                changed = false,
            )
        }
        val claim = if (record.f > 0) BootstrapClaim.UNKNOWN else BootstrapClaim.FIRST
        return BootstrapRecordClaim(
            claim,
            cap(LaneReplayRecord(record.f, record.e + LaneReplayEntry(key, message, maxOf(now, 1)))),
            changed = true,
        )
    }

    fun is_count(value: Long): Boolean = value in 0..MAX_SAFE_INTEGER

    private fun count_of(value: Any?): Long? {
        val number = value as? Number ?: return null
        val whole = when (number) {
            is Int, is Long, is Short, is Byte -> number.toLong()
            else -> {
                val real = number.toDouble()
                if (real.isNaN() || real.isInfinite() || real != Math.floor(real)) return null
                real.toLong()
            }
        }
        return whole.takeIf { is_count(it) }
    }

    fun normalize(stored: Any?): LaneReplayRecord {
        val record = when (stored) {
            is LaneReplayRecord -> return stored
            is JSONObject -> stored
            is String -> runCatching { JSONObject(stored) }.getOrNull()
            else -> null
        } ?: return EMPTY
        val seen = HashSet<String>()
        val entries = ArrayList<LaneReplayEntry>()
        val list = record.opt("e") as? JSONArray
        if (list != null) {
            for (index in 0 until list.length()) {
                val entry = list.opt(index) as? JSONObject ?: continue
                val k = entry.opt("k") as? String
                val m = entry.opt("m") as? String
                val t = count_of(entry.opt("t"))
                if (k.isNullOrEmpty() || m.isNullOrEmpty() || t == null || !seen.add(k)) continue
                entries.add(LaneReplayEntry(k, m, t))
            }
        }
        return LaneReplayRecord(count_of(record.opt("f")) ?: 0, entries)
    }

    fun cap(record: LaneReplayRecord): LaneReplayRecord {
        val sorted = record.e.sortedWith(entry_order)
        if (sorted.size <= MAX_ENTRIES) return LaneReplayRecord(record.f, sorted)
        val dropped = sorted.subList(0, sorted.size - MAX_ENTRIES)
        val highest = dropped.maxOf { it.t }
        return LaneReplayRecord(
            maxOf(record.f, highest, 1),
            sorted.subList(sorted.size - MAX_ENTRIES, sorted.size).toList(),
        )
    }

    fun merge(primary: Any?, secondary: Any?): LaneReplayRecord {
        val first = normalize(primary)
        val second = normalize(secondary)
        val keys = first.e.mapTo(HashSet()) { it.k }
        return cap(
            LaneReplayRecord(
                maxOf(first.f, second.f),
                first.e + second.e.filterNot { it.k in keys },
            ),
        )
    }

    fun global_floor(map: Any?): Long = normalize((map as? JSONObject)?.opt(GLOBAL_FLOOR_KEY)).f

    fun sender_record(map: Any?, sender_email: String): LaneReplayRecord {
        val json = map as? JSONObject ?: return EMPTY
        if (sender_email.isEmpty() || sender_email == GLOBAL_FLOOR_KEY) return EMPTY
        val record = normalize(json.opt(sender_email))
        val floor = global_floor(json)
        return if (floor > record.f) LaneReplayRecord(floor, record.e) else record
    }

    private fun recency(record: LaneReplayRecord): Long = maxOf(record.f, record.e.maxOfOrNull { it.t } ?: 0)

    private fun holds_anything(record: LaneReplayRecord): Boolean = record.f > 0 || record.e.isNotEmpty()

    fun bound(records: Map<String, LaneReplayRecord>, floor: Long): Pair<Map<String, LaneReplayRecord>, Long> {
        var global = floor
        val ranked = records.entries.sortedWith(compareBy({ recency(it.value) }, { it.key }))
        val excess = ranked.size - MAX_SENDERS
        val kept = LinkedHashMap<String, LaneReplayRecord>()
        ranked.forEachIndexed { index, (sender, record) ->
            if (index < excess) {
                if (holds_anything(record)) global = maxOf(global, recency(record), 1)
            } else {
                kept[sender] = record
            }
        }
        val all = kept.flatMap { (sender, record) -> record.e.map { sender to it } }
        if (all.size > MAX_TOTAL_ENTRIES) {
            val dropped = all.sortedWith(
                compareBy<Pair<String, LaneReplayEntry>>({ it.second.t }, { it.first }, { it.second.k }, { it.second.m }),
            ).subList(0, all.size - MAX_TOTAL_ENTRIES)
            val raised = HashMap<String, Long>()
            val cut = HashMap<String, HashSet<String>>()
            for ((sender, entry) in dropped) {
                raised[sender] = maxOf(raised[sender] ?: 0, entry.t)
                cut.getOrPut(sender) { HashSet() }.add(entry.k)
            }
            for ((sender, keys) in cut) {
                val record = kept.getValue(sender)
                kept[sender] = LaneReplayRecord(
                    maxOf(record.f, raised.getValue(sender), 1),
                    record.e.filterNot { it.k in keys },
                )
            }
        }
        return kept to global
    }

    fun merge_maps(primary: Any?, secondary: Any?): JSONObject {
        val left = primary as? JSONObject ?: JSONObject()
        val right = secondary as? JSONObject ?: JSONObject()
        val senders = LinkedHashSet<String>()
        left.keys().forEach { senders.add(it) }
        right.keys().forEach { senders.add(it) }
        val records = LinkedHashMap<String, LaneReplayRecord>()
        for (sender in senders) {
            if (sender.isEmpty() || sender == GLOBAL_FLOOR_KEY) continue
            records[sender] = merge(left.opt(sender), right.opt(sender))
        }
        val (kept, global) = bound(records, maxOf(global_floor(left), global_floor(right)))
        val merged = JSONObject()
        if (global > 0) merged.put(GLOBAL_FLOOR_KEY, LaneReplayRecord(global, emptyList()).to_json())
        kept.forEach { (sender, record) -> merged.put(sender, record.to_json()) }
        return merged
    }

    fun claim(stored: Any?, key: String, message: String, sent_at: Long): LaneReplayClaim {
        val record = normalize(stored)
        val prior = record.e.firstOrNull { it.k == key }
        if (prior != null) {
            return LaneReplayClaim(
                if (prior.m == message) LaneFreshness.FRESH else LaneFreshness.REPLAYED,
                record,
                changed = false,
            )
        }
        if (!is_count(sent_at) || (record.f > 0 && sent_at <= record.f)) {
            return LaneReplayClaim(LaneFreshness.REPLAYED, record, changed = false)
        }
        return LaneReplayClaim(
            LaneFreshness.FRESH,
            cap(LaneReplayRecord(record.f, record.e + LaneReplayEntry(key, message, sent_at))),
            changed = true,
        )
    }
}
