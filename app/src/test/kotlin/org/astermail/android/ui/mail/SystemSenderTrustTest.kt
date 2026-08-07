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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSenderTrustTest {

    private fun message(
        sender_email: String,
        is_external: Boolean,
        spf: String? = null,
        dkim: String? = null,
        dmarc: String? = null,
    ): ThreadMessage = ThreadMessage(
        id = "1",
        sender_name = "Aster",
        sender_email = sender_email,
        to_label = "me",
        timestamp = 0L,
        body = "",
        is_external = is_external,
        spf_result = spf,
        dkim_result = dkim,
        dmarc_result = dmarc,
    )

    @Test
    fun internally_delivered_system_mail_stays_trusted() {
        assertTrue(is_aster_system_sender(message("security@astermail.org", is_external = false)))
        assertTrue(is_aster_system_sender(message("noreply@aster.cx", is_external = false)))
    }

    @Test
    fun spoofed_inbound_mail_claiming_an_aster_address_is_not_trusted() {
        assertFalse(
            is_aster_system_sender(
                message("security@astermail.org", is_external = true, spf = "fail", dmarc = "fail"),
            ),
        )
        assertFalse(
            is_aster_system_sender(
                message("security@astermail.org", is_external = true),
            ),
        )
    }

    @Test
    fun inbound_mail_that_passes_dmarc_is_trusted() {
        assertTrue(
            is_aster_system_sender(
                message("security@astermail.org", is_external = true, spf = "pass", dkim = "pass", dmarc = "pass"),
            ),
        )
    }

    @Test
    fun other_domains_are_never_trusted() {
        assertFalse(is_aster_system_sender(message("security@astermail.org.evil.com", is_external = false)))
        assertFalse(is_aster_system_sender(message("hello@example.com", is_external = false)))
        assertFalse(
            is_aster_system_sender(
                message("hello@example.com", is_external = true, spf = "pass", dkim = "pass", dmarc = "pass"),
            ),
        )
    }

    @Test
    fun address_matching_ignores_case_and_padding() {
        assertTrue(is_aster_system_sender(message(" Security@AsterMail.org ", is_external = false)))
    }
}
