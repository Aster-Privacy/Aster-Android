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

package org.astermail.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionAuthTest {

    @Test
    fun actions_skip_the_unlock_step_while_other_mail_notifications_remain() {
        for (count in listOf(2, 3, 5, 12)) {
            assertFalse(NotificationActionAuthPolicy.requires_auth(count))
        }
    }

    @Test
    fun the_last_mail_notification_asks_for_unlock_so_the_shade_closes() {
        assertTrue(NotificationActionAuthPolicy.requires_auth(1))
        assertTrue(NotificationActionAuthPolicy.requires_auth(0))
    }

    @Test
    fun a_tap_on_the_locked_device_switches_every_notification_to_unlock_first() {
        for (count in listOf(1, 2, 5)) {
            assertTrue(NotificationActionAuthPolicy.requires_auth(count, after_locked_tap = true))
        }
    }

    @Test
    fun unlocked_taps_apply_right_away() {
        for (sdk in listOf(26, 30, 31, 35)) {
            for (posted_with_auth in listOf(true, false)) {
                assertEquals(
                    NotificationActionGate.Apply,
                    NotificationActionAuthPolicy.gate(device_locked = false, posted_with_auth, sdk),
                )
            }
        }
    }

    @Test
    fun locked_taps_without_system_unlock_never_change_mail() {
        assertEquals(
            NotificationActionGate.RequireUnlock,
            NotificationActionAuthPolicy.gate(device_locked = true, posted_with_auth = false, sdk = 31),
        )
        assertEquals(
            NotificationActionGate.RequireUnlock,
            NotificationActionAuthPolicy.gate(device_locked = true, posted_with_auth = false, sdk = 36),
        )
        assertEquals(
            NotificationActionGate.Ignore,
            NotificationActionAuthPolicy.gate(device_locked = true, posted_with_auth = true, sdk = 30),
        )
        assertEquals(
            NotificationActionGate.Ignore,
            NotificationActionAuthPolicy.gate(device_locked = true, posted_with_auth = false, sdk = 26),
        )
    }

    @Test
    fun system_unlocked_taps_apply_even_if_the_lock_state_lags() {
        assertEquals(
            NotificationActionGate.Apply,
            NotificationActionAuthPolicy.gate(device_locked = true, posted_with_auth = true, sdk = 31),
        )
    }

    @Test
    fun a_second_notification_frees_the_first_from_the_unlock_step() {
        val plan = NotificationActionAuthPolicy.reposts(mapOf(2001 to true), active_count = 2)
        assertEquals(mapOf(2001 to false), plan)
    }

    @Test
    fun handling_all_but_one_makes_the_remaining_one_ask_for_unlock() {
        val plan = NotificationActionAuthPolicy.reposts(mapOf(2002 to false), active_count = 1)
        assertEquals(mapOf(2002 to true), plan)
    }

    @Test
    fun notifications_already_in_the_right_state_are_left_alone() {
        val current = mapOf(2001 to false, 2002 to false, 2003 to false)
        assertTrue(NotificationActionAuthPolicy.reposts(current, active_count = 3).isEmpty())
        assertTrue(NotificationActionAuthPolicy.reposts(mapOf(2001 to true), active_count = 1).isEmpty())
    }

    @Test
    fun a_locked_tap_reposts_only_the_notifications_that_still_skip_unlock() {
        val current = mapOf(2001 to false, 2002 to true, 2003 to false)
        val plan = NotificationActionAuthPolicy.reposts(current, active_count = 3, after_locked_tap = true)
        assertEquals(mapOf(2001 to true, 2003 to true), plan)
    }

    @Test
    fun the_tapped_notification_is_reposted_first() {
        val order = NotificationActionAuthPolicy.repost_order(listOf(2001, 2002, 2003), first = 2003)
        assertEquals(listOf(2003, 2001, 2002), order)
        assertEquals(listOf(2001, 2002), NotificationActionAuthPolicy.repost_order(listOf(2001, 2002), first = 0))
    }
}
