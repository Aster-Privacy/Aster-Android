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

package org.astermail.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAlertChannelTest {

    @Test
    fun every_sound_and_vibrate_combination_maps_to_its_own_channel() {
        val ids = listOf(
            MailPollingWorker.notification_channel_id(sound = true, vibrate = true),
            MailPollingWorker.notification_channel_id(sound = true, vibrate = false),
            MailPollingWorker.notification_channel_id(sound = false, vibrate = true),
            MailPollingWorker.notification_channel_id(sound = false, vibrate = false),
        )

        assertEquals(4, ids.distinct().size)
    }

    @Test
    fun the_default_combination_keeps_the_original_channel() {
        assertEquals(
            MailPollingWorker.CHANNEL_ID,
            MailPollingWorker.notification_channel_id(sound = true, vibrate = true),
        )
    }

    @Test
    fun turning_off_an_alert_moves_off_the_default_channel() {
        assertTrue(
            MailPollingWorker.notification_channel_id(sound = false, vibrate = true) !=
                MailPollingWorker.CHANNEL_ID,
        )
        assertTrue(
            MailPollingWorker.notification_channel_id(sound = true, vibrate = false) !=
                MailPollingWorker.CHANNEL_ID,
        )
    }

    @Test
    fun the_cleanup_list_covers_every_channel_the_app_can_pick() {
        val ids = MailPollingWorker.alert_channel_ids()

        for (sound in listOf(true, false)) {
            for (vibrate in listOf(true, false)) {
                assertTrue(MailPollingWorker.notification_channel_id(sound, vibrate) in ids)
            }
        }
        assertEquals(ids.size, ids.distinct().size)
    }
}
