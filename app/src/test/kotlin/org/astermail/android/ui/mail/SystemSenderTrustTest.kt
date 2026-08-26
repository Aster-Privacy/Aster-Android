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
        system_origin: Boolean = false,
        display_sender_email: String? = null,
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
        system_origin = system_origin,
        display_sender_email = display_sender_email,
        spf_result = spf,
        dkim_result = dkim,
        dmarc_result = dmarc,
    )

    private fun row(
        sender_email: String,
        is_external: Boolean,
        system_origin: Boolean = false,
    ): Email = Email(
        id = "1",
        sender_name = "Aster",
        sender_email = sender_email,
        subject = "",
        preview = "",
        received_at = 0L,
        is_read = true,
        is_starred = false,
        has_attachment = false,
        is_external = is_external,
        system_origin = system_origin,
    )

    @Test
    fun server_generated_system_mail_is_trusted() {
        assertTrue(
            is_aster_system_sender(
                message("security@astermail.org", is_external = false, system_origin = true),
            ),
        )
        assertTrue(
            is_aster_system_sender(
                message("noreply@aster.cx", is_external = false, system_origin = true),
            ),
        )
    }

    @Test
    fun spoofed_inbound_mail_claiming_an_aster_address_is_not_trusted() {
        assertFalse(
            is_aster_system_sender(
                message("no-reply@astermail.org", is_external = true, spf = "fail", dmarc = "fail"),
            ),
        )
        assertFalse(is_aster_system_sender(message("security@astermail.org", is_external = true)))
    }

    @Test
    fun inbound_mail_is_never_trusted_even_when_it_passes_dmarc() {
        assertFalse(
            is_aster_system_sender(
                message(
                    "security@astermail.org",
                    is_external = true,
                    spf = "pass",
                    dkim = "pass",
                    dmarc = "pass",
                ),
            ),
        )
    }

    @Test
    fun a_flag_carried_on_an_external_message_is_ignored() {
        assertFalse(
            is_aster_system_sender(
                message("no-reply@astermail.org", is_external = true, system_origin = true),
            ),
        )
    }

    @Test
    fun internal_mail_a_user_composed_is_not_trusted() {
        assertFalse(is_aster_system_sender(message("no-reply@astermail.org", is_external = false)))
        assertFalse(is_aster_system_sender(row("no-reply@astermail.org", is_external = false)))
    }

    @Test
    fun server_origin_on_a_personal_address_is_not_trusted() {
        assertFalse(
            is_aster_system_sender(
                message("user@astermail.org", is_external = false, system_origin = true),
            ),
        )
    }

    @Test
    fun other_domains_are_never_trusted() {
        assertFalse(
            is_aster_system_sender(
                message("security@astermail.org.evil.com", is_external = false, system_origin = true),
            ),
        )
        assertFalse(
            is_aster_system_sender(
                message("no-reply@example.com", is_external = false, system_origin = true),
            ),
        )
    }

    @Test
    fun address_matching_ignores_case_and_padding() {
        assertTrue(
            is_aster_system_sender(
                message(" Security@AsterMail.org ", is_external = false, system_origin = true),
            ),
        )
    }

    @Test
    fun inbox_rows_follow_the_same_rule() {
        assertTrue(row("no-reply@astermail.org", is_external = false, system_origin = true).let(::is_aster_system_sender))
        assertFalse(row("no-reply@astermail.org", is_external = true, system_origin = true).let(::is_aster_system_sender))
    }

    @Test
    fun a_substituted_display_address_drops_the_system_avatar() {
        val msg = message(
            "no-reply@astermail.org",
            is_external = false,
            system_origin = true,
            display_sender_email = "billing@astermail.org",
        )
        assertTrue(is_aster_system_sender(msg))
        assertFalse(system_avatar_authenticated(msg))
    }

    @Test
    fun the_authenticated_display_address_keeps_the_system_avatar() {
        assertTrue(
            system_avatar_authenticated(
                message("no-reply@astermail.org", is_external = false, system_origin = true),
            ),
        )
    }
}
