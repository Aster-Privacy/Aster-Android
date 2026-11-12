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

package org.astermail.android.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedMimeSenderTest {

    private val primary = "owner@astermail.org"

    @Test
    fun primary_address_may_sign() {
        assertTrue(MailRepository.sender_may_sign_outbound(primary, primary))
        assertTrue(MailRepository.sender_may_sign_outbound("Owner <OWNER@astermail.org>", primary))
    }

    @Test
    fun alias_and_custom_domain_senders_do_not_sign_with_the_account_key() {
        assertFalse(MailRepository.sender_may_sign_outbound("shopping@aster.cx", primary))
        assertFalse(MailRepository.sender_may_sign_outbound("Shop <shopping@aster.cx>", primary))
        assertFalse(MailRepository.sender_may_sign_outbound("hello@example.com", primary))
    }

    @Test
    fun unknown_primary_never_signs() {
        assertFalse(MailRepository.sender_may_sign_outbound(primary, null))
        assertFalse(MailRepository.sender_may_sign_outbound(primary, " "))
    }
}
