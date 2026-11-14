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

class NotificationSenderLabelTest {

    private fun label(trusted: String, email: String?): String =
        MailPollingWorker.notification_sender_label(trusted, email)

    @Test
    fun trusted_name_is_used_when_present() {
        assertEquals("Alice", label("  Alice ", "alice@example.com"))
    }

    @Test
    fun empty_trusted_name_falls_back_to_the_address() {
        assertEquals("alice@exämple.com", label("", " alice@exämple.com "))
    }

    @Test
    fun no_sender_at_all_is_blank_so_the_worker_retries() {
        assertEquals("", label("", ""))
        assertEquals("", label("", null))
    }

    @Test
    fun text_without_an_address_is_not_shown_as_the_sender() {
        assertEquals("", label("", "not an address"))
        assertEquals("", label("", "@example.com"))
        assertEquals("", label("", "alice@"))
    }
}
