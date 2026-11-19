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

import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

interface InboundMarkerFloorStore {
    fun floor(account: String): Long?
    fun save(account: String, floor_ms: Long): Boolean
    fun first_seen(account: String): Map<String, Long>? = null
    fun save_first_seen(account: String, seen: Map<String, Long>): Boolean = false
}

class SecureInboundMarkerFloorStore(private val prefs: () -> SharedPreferences?) : InboundMarkerFloorStore {

    override fun floor(account: String): Long? =
        runCatching {
            val stored = prefs() ?: return@runCatching null
            if (stored.contains(entry_key(account))) stored.getLong(entry_key(account), 0L) else null
        }.getOrNull()

    override fun save(account: String, floor_ms: Long): Boolean =
        runCatching {
            val editor = prefs()?.edit() ?: return@runCatching false
            editor.putLong(entry_key(account), floor_ms).commit()
        }.getOrDefault(false)

    override fun first_seen(account: String): Map<String, Long>? =
        runCatching {
            val stored = prefs() ?: return@runCatching null
            parse_seen(stored.getString(seen_key(account), null))
        }.getOrNull()

    override fun save_first_seen(account: String, seen: Map<String, Long>): Boolean =
        runCatching {
            val editor = prefs()?.edit() ?: return@runCatching false
            editor.putString(seen_key(account), seen.entries.joinToString(",") { "${it.key}=${it.value}" }).apply()
            true
        }.getOrDefault(false)

    companion object {
        const val PREFS_NAME = "aster_inbound_marker_floor_v1"

        fun entry_key(account: String): String = "floor_$account"

        fun seen_key(account: String): String = "seen_$account"

        fun parse_seen(value: String?): Map<String, Long> {
            val parsed = LinkedHashMap<String, Long>()
            value?.split(",")?.forEach { pair ->
                val split = pair.indexOf('=')
                if (split <= 0) return@forEach
                val at = pair.substring(split + 1).toLongOrNull() ?: return@forEach
                parsed[pair.substring(0, split)] = at
            }
            return parsed
        }
    }
}

object InboundMarkerFloor {
    const val GRACE_MS = 6L * 60 * 60 * 1000

    const val MAX_SEEN = 512

    private const val DIGEST_HEX_CHARS = 32

    @Volatile
    private var account_source: () -> String? = { null }

    @Volatile
    private var store: InboundMarkerFloorStore? = null

    private val floors = ConcurrentHashMap<String, Long>()

    private val seen = ConcurrentHashMap<String, LinkedHashMap<String, Long>>()

    fun bind(account: () -> String?, floor_store: InboundMarkerFloorStore?) {
        account_source = account
        store = floor_store
        floors.clear()
        seen.clear()
    }

    fun legacy_allowed(floor_ms: Long?, received_at_ms: Long?, now_ms: Long): Boolean {
        if (floor_ms == null) return true
        return (received_at_ms ?: now_ms) < floor_ms + GRACE_MS
    }

    fun first_sighting(floor_ms: Long, now_ms: Long, received_at_ms: Long?): Long =
        if (now_ms >= floor_ms + GRACE_MS && received_at_ms != null && received_at_ms < floor_ms) received_at_ms else now_ms

    fun legacy_allowed_after_first_seen(floor_ms: Long, sighting_ms: Long): Boolean = sighting_ms < floor_ms + GRACE_MS

    fun accepts_legacy(
        received_at_ms: Long?,
        now_ms: Long = System.currentTimeMillis(),
        encrypted_b64: String? = null,
    ): Boolean {
        val account = current_account() ?: return true
        val floor = floor_of(account) ?: return true
        if (encrypted_b64 == null) return legacy_allowed(floor, received_at_ms, now_ms)
        if (!carries_legacy_marker(encrypted_b64)) return legacy_allowed(floor, received_at_ms, now_ms)
        val sighting = first_seen(account, envelope_digest(encrypted_b64), first_sighting(floor, now_ms, received_at_ms))
        return legacy_allowed_after_first_seen(floor, sighting)
    }

    private fun first_seen(account: String, digest: String, sighting_ms: Long): Long {
        val known = seen.computeIfAbsent(account) { key ->
            LinkedHashMap(runCatching { store?.first_seen(key) }.getOrNull().orEmpty())
        }
        synchronized(known) {
            known[digest]?.let { return it }
            known[digest] = sighting_ms
            while (known.size > MAX_SEEN) known.remove(known.keys.first())
            runCatching { store?.save_first_seen(account, LinkedHashMap(known)) }
            return sighting_ms
        }
    }

    private fun carries_legacy_marker(encrypted_b64: String): Boolean {
        val marker = runCatching { java.util.Base64.getDecoder().decode(encrypted_b64.trim().take(4)) }
            .getOrNull()
            ?.firstOrNull()
            ?: return true
        return !InboundEnvelopeDecryptor.is_owner_bound(marker)
    }

    private fun envelope_digest(encrypted_b64: String): String =
        MessageDigest.getInstance("SHA-256").digest(encrypted_b64.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(DIGEST_HEX_CHARS)

    fun note_bound(now_ms: Long = System.currentTimeMillis()) {
        val account = current_account() ?: return
        if (floor_of(account) != null) return
        floors[account] = now_ms
        runCatching { store?.save(account, now_ms) }
    }

    fun reset() {
        floors.clear()
        seen.clear()
        store = null
        account_source = { null }
    }

    private fun floor_of(account: String): Long? =
        floors[account] ?: runCatching { store?.floor(account) }.getOrNull()?.also { floors[account] = it }

    private fun current_account(): String? =
        runCatching { account_source() }.getOrNull()?.takeIf { it.isNotBlank() }
}
