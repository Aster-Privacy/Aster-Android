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

import org.astermail.android.billing.storage_addon_quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageNudgeTest {
    private val gb = 1_073_741_824L

    private fun quote(id: String, size_gb: Long, cents: Int?) = storage_addon_quote(
        addon_id = id,
        name = "$size_gb GB",
        storage_bytes = size_gb * gb,
        monthly_cents = cents,
        yearly_cents = null,
    )

    @Test
    fun percent_is_zero_for_empty_or_invalid_totals() {
        assertEquals(0.0, storage_nudge_percent(5, 0), 0.0)
        assertEquals(0.0, storage_nudge_percent(0, 10), 0.0)
        assertEquals(0.0, storage_nudge_percent(-5, 10), 0.0)
        assertEquals(90.0, storage_nudge_percent(9, 10), 0.0001)
    }

    @Test
    fun tiers_follow_the_thresholds() {
        assertNull(storage_nudge_tier(0.0))
        assertNull(storage_nudge_tier(79.9))
        assertNull(storage_nudge_tier(Double.NaN))
        assertEquals(StorageNudgeTier.almost, storage_nudge_tier(80.0))
        assertEquals(StorageNudgeTier.almost, storage_nudge_tier(94.9))
        assertEquals(StorageNudgeTier.critical, storage_nudge_tier(95.0))
        assertEquals(StorageNudgeTier.full, storage_nudge_tier(100.0))
        assertEquals(StorageNudgeTier.full, storage_nudge_tier(140.0))
    }

    @Test
    fun offer_is_null_without_purchasable_addons() {
        assertNull(pick_storage_nudge_offer(emptyList(), 9 * gb, 10 * gb))
        assertNull(pick_storage_nudge_offer(listOf(quote("free", 10, 0), quote("none", 10, null)), 9 * gb, 10 * gb))
    }

    @Test
    fun offer_is_the_smallest_addon_that_relieves_pressure() {
        val catalog = listOf(quote("big", 500, 999), quote("small", 10, 199), quote("mid", 100, 499))
        assertEquals("small", pick_storage_nudge_offer(catalog, 9 * gb, 10 * gb)?.addon_id)
        assertEquals("mid", pick_storage_nudge_offer(catalog, 80 * gb, 80 * gb)?.addon_id)
        assertEquals("big", pick_storage_nudge_offer(catalog, 5000 * gb, 5000 * gb)?.addon_id)
    }

    @Test
    fun dismissal_hides_the_tier_for_a_week() {
        val stored = storage_nudge_dismiss_value(StorageNudgeTier.almost, 1_000L)
        assertTrue(is_storage_nudge_dismissed(stored, StorageNudgeTier.almost, 1_001L))
        assertFalse(is_storage_nudge_dismissed(stored, StorageNudgeTier.almost, 1_000L + STORAGE_NUDGE_SNOOZE_MS))
    }

    @Test
    fun a_higher_tier_shows_again() {
        val stored = storage_nudge_dismiss_value(StorageNudgeTier.almost, 0L)
        assertFalse(is_storage_nudge_dismissed(stored, StorageNudgeTier.critical, 1L))
        val critical = storage_nudge_dismiss_value(StorageNudgeTier.critical, 0L)
        assertTrue(is_storage_nudge_dismissed(critical, StorageNudgeTier.almost, 1L))
    }

    @Test
    fun a_full_mailbox_is_never_hidden() {
        val stored = storage_nudge_dismiss_value(StorageNudgeTier.full, 0L)
        assertFalse(is_storage_nudge_dismissed(stored, StorageNudgeTier.full, 1L))
    }

    @Test
    fun corrupt_values_are_ignored() {
        assertFalse(is_storage_nudge_dismissed(null, StorageNudgeTier.almost, 1L))
        assertFalse(is_storage_nudge_dismissed("", StorageNudgeTier.almost, 1L))
        assertFalse(is_storage_nudge_dismissed("almost", StorageNudgeTier.almost, 1L))
        assertFalse(is_storage_nudge_dismissed("nope:99", StorageNudgeTier.almost, 1L))
        assertFalse(is_storage_nudge_dismissed("almost:soon", StorageNudgeTier.almost, 1L))
    }

    @Test
    fun dismiss_keys_are_per_account() {
        assertEquals("dismissed:user-a", storage_nudge_dismiss_key("user-a"))
        assertEquals("dismissed:default", storage_nudge_dismiss_key(null))
        assertEquals("dismissed:default", storage_nudge_dismiss_key(" "))
    }
}
