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
import org.junit.Test

class NotificationTextTest {

    @Test
    fun `a readable message shows its subject and preview`() {
        assertEquals(
            "Lunch" to "See you at noon",
            MailPollingWorker.notification_text(" Lunch ", "See you at noon", is_undecryptable = false, is_decrypt_pending = false),
        )
    }

    @Test
    fun `a message still decrypting keeps its subject and hides the preview`() {
        assertEquals(
            "Re: Lunch" to "",
            MailPollingWorker.notification_text("Re: Lunch", "", is_undecryptable = true, is_decrypt_pending = true),
        )
    }

    @Test
    fun `an unreadable message falls back to the neutral new message text`() {
        assertEquals(
            "" to "",
            MailPollingWorker.notification_text("Re: Lunch", "leftover", is_undecryptable = true, is_decrypt_pending = false),
        )
    }
}
