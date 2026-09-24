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

package org.astermail.android.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendBlockersTest {

    private fun blocker(
        recipients: List<String>,
        via_connected_account: Boolean = false,
        has_expiry: Boolean = false,
        has_expiry_password: Boolean = false,
        expiration_locked: Boolean = false,
        expiry_password_locked: Boolean = false,
        require_encryption: Boolean = false,
    ) = send_blocker_for(
        recipients = recipients,
        via_connected_account = via_connected_account,
        has_expiry = has_expiry,
        has_expiry_password = has_expiry_password,
        expiration_locked = expiration_locked,
        expiry_password_locked = expiry_password_locked,
        require_encryption = require_encryption,
    )

    @Test
    fun `mixed recipients are blocked before sending`() {
        assertEquals(
            SendBlocker.MIXED_RECIPIENTS,
            blocker(listOf("friend@astermail.org", "someone@example.com")),
        )
    }

    @Test
    fun `a connected account can send to mixed recipients`() {
        assertNull(blocker(listOf("friend@astermail.org", "someone@example.com"), via_connected_account = true))
    }

    @Test
    fun `a plain send is not blocked`() {
        assertNull(blocker(listOf("someone@example.com")))
        assertNull(blocker(listOf("friend@astermail.org")))
    }

    @Test
    fun `an expiry on a plan without expiration is blocked`() {
        assertEquals(
            SendBlocker.EXPIRATION_LOCKED,
            blocker(listOf("someone@example.com"), has_expiry = true, expiration_locked = true),
        )
    }

    @Test
    fun `a password on a plan without protected messages is blocked`() {
        assertEquals(
            SendBlocker.EXPIRY_PASSWORD_LOCKED,
            blocker(
                listOf("someone@example.com"),
                has_expiry = true,
                has_expiry_password = true,
                expiry_password_locked = true,
            ),
        )
    }

    @Test
    fun `a password for internal recipients is caught instead of dropped`() {
        assertEquals(
            SendBlocker.EXPIRY_PASSWORD_INTERNAL,
            blocker(listOf("friend@astermail.org"), has_expiry = true, has_expiry_password = true),
        )
        assertNull(blocker(listOf("friend@astermail.org"), has_expiry = true))
    }

    @Test
    fun `an external expiry with required encryption is blocked`() {
        assertEquals(
            SendBlocker.EXPIRY_NEEDS_SECURE_MESSAGE,
            blocker(listOf("someone@example.com"), has_expiry = true, require_encryption = true),
        )
        assertNull(blocker(listOf("friend@astermail.org"), has_expiry = true, require_encryption = true))
        assertNull(blocker(listOf("someone@example.com"), require_encryption = true))
    }

    @Test
    fun `the password field follows recipients and plan`() {
        assertEquals(ExpiryPasswordMode.HIDDEN, expiry_password_mode_for(listOf("friend@aster.cx"), false))
        assertEquals(ExpiryPasswordMode.LOCKED, expiry_password_mode_for(listOf("someone@example.com"), true))
        assertEquals(ExpiryPasswordMode.AVAILABLE, expiry_password_mode_for(listOf("someone@example.com"), false))
        assertEquals(ExpiryPasswordMode.AVAILABLE, expiry_password_mode_for(emptyList(), false))
    }
}
