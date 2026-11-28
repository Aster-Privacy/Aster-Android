// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSenderAddressTest {

    @Test
    fun `automated aster addresses are system senders`() {
        assertTrue(is_system_sender_address("noreply@astermail.org"))
        assertTrue(is_system_sender_address(" No-Reply@aster.cx "))
        assertTrue(is_system_sender_address("updates@astermail.org"))
    }

    @Test
    fun `official person addresses keep the full sheet`() {
        assertFalse(is_system_sender_address("support@astermail.org"))
        assertFalse(is_system_sender_address("hello@astermail.org"))
    }

    @Test
    fun `other domains and regular users are not system senders`() {
        assertFalse(is_system_sender_address("noreply@example.com"))
        assertFalse(is_system_sender_address("kosei@astermail.org"))
        assertFalse(is_system_sender_address("noreply"))
    }
}
