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

package org.astermail.android.ui.mail

import org.astermail.android.billing.STORAGE_ADDON_NUDGE_PERCENT
import org.astermail.android.billing.storage_addon_quote

enum class StorageNudgeTier(val rank: Int) { almost(1), critical(2), full(3) }

const val STORAGE_NUDGE_CRITICAL_PERCENT = 95.0
const val STORAGE_NUDGE_FULL_PERCENT = 100.0
const val STORAGE_NUDGE_SNOOZE_MS = 7L * 24 * 60 * 60 * 1000
const val STORAGE_NUDGE_PREFS = "aster_storage_nudge"

private const val DISMISS_KEY_PREFIX = "dismissed:"

fun storage_nudge_percent(used_bytes: Long, total_bytes: Long): Double {
    if (used_bytes <= 0L || total_bytes <= 0L) return 0.0
    return used_bytes.toDouble() / total_bytes.toDouble() * 100.0
}

fun storage_nudge_tier(percent: Double): StorageNudgeTier? = when {
    percent.isNaN() -> null
    percent >= STORAGE_NUDGE_FULL_PERCENT -> StorageNudgeTier.full
    percent >= STORAGE_NUDGE_CRITICAL_PERCENT -> StorageNudgeTier.critical
    percent >= STORAGE_ADDON_NUDGE_PERCENT -> StorageNudgeTier.almost
    else -> null
}

fun pick_storage_nudge_offer(
    quotes: List<storage_addon_quote>,
    used_bytes: Long,
    total_bytes: Long,
): storage_addon_quote? {
    val purchasable = quotes
        .filter { it.storage_bytes > 0L && (it.monthly_cents ?: 0) > 0 }
        .sortedBy { it.storage_bytes }
    if (purchasable.isEmpty()) return null
    return purchasable.firstOrNull {
        storage_nudge_percent(used_bytes, total_bytes + it.storage_bytes) < STORAGE_ADDON_NUDGE_PERCENT
    } ?: purchasable.last()
}

fun storage_nudge_dismiss_key(account_id: String?): String =
    DISMISS_KEY_PREFIX + (account_id?.takeIf { it.isNotBlank() } ?: "default")

fun storage_nudge_dismiss_value(tier: StorageNudgeTier, now_ms: Long): String =
    "${tier.name}:${now_ms + STORAGE_NUDGE_SNOOZE_MS}"

fun is_storage_nudge_dismissed(stored: String?, tier: StorageNudgeTier, now_ms: Long): Boolean {
    if (tier == StorageNudgeTier.full || stored.isNullOrBlank()) return false
    val parts = stored.split(':')
    if (parts.size != 2) return false
    val stored_tier = StorageNudgeTier.entries.firstOrNull { it.name == parts[0] } ?: return false
    val until = parts[1].toLongOrNull() ?: return false
    if (until <= now_ms) return false
    return stored_tier.rank >= tier.rank
}
