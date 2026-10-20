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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAlertThrottleTest {

    @Test
    fun a_second_mail_inside_the_window_is_posted_quietly() {
        assertTrue(MailPollingWorker.message_alert_silent(throttled = true, is_undecryptable = false, muted_categories = emptySet()))
    }

    @Test
    fun a_mail_outside_the_window_alerts() {
        assertFalse(MailPollingWorker.message_alert_silent(throttled = false, is_undecryptable = false, muted_categories = emptySet()))
    }

    @Test
    fun an_undecryptable_mail_with_muted_categories_stays_quiet() {
        assertTrue(MailPollingWorker.message_alert_silent(throttled = false, is_undecryptable = true, muted_categories = setOf("promotions")))
    }
}
