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

import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLockScreenVisibilityTest {

    @Test
    fun private_notifications_off_leaves_lock_screen_content_to_the_system() {
        val policy = MailPollingWorker.message_lock_screen_policy(private_mode = false)
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, policy.visibility)
        assertFalse(policy.redacted_public_version)
    }

    @Test
    fun private_notifications_on_shows_the_redacted_version_on_the_lock_screen() {
        val policy = MailPollingWorker.message_lock_screen_policy(private_mode = true)
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, policy.visibility)
        assertTrue(policy.redacted_public_version)
    }

    @Test
    fun message_notifications_are_never_forced_public() {
        for (private_mode in listOf(true, false)) {
            assertNotEquals(
                NotificationCompat.VISIBILITY_PUBLIC,
                MailPollingWorker.message_lock_screen_policy(private_mode).visibility,
            )
        }
    }
}
