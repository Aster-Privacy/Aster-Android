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

import org.astermail.android.ui.mail.extract_delivered_to
import org.astermail.android.ui.mail.resolve_received_on_address
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveredToTest {

    @Test
    fun extracts_first_topmost_delivered_to() {
        val headers = listOf(
            "Delivered-To" to "shopping@astermail.org",
            "Delivered-To" to "other@astermail.org",
        )
        assertEquals("shopping@astermail.org", extract_delivered_to(headers))
    }

    @Test
    fun strips_angle_brackets_and_lowercases() {
        val headers = listOf("delivered-to" to "<Kid.Alias@Aster.CX>")
        assertEquals("kid.alias@aster.cx", extract_delivered_to(headers))
    }

    @Test
    fun returns_null_without_header_or_address() {
        assertNull(extract_delivered_to(emptyList()))
        assertNull(extract_delivered_to(listOf("To" to "a@b.c")))
        assertNull(extract_delivered_to(listOf("Delivered-To" to "garbage")))
    }

    @Test
    fun surfaces_aster_address_for_forwarded_mail() {
        val headers = listOf(
            "Delivered-To" to "myalias@astermail.org",
            "X-SimpleLogin-Type" to "Forward",
        )
        val result = resolve_received_on_address(
            headers,
            listOf("testing123.glitzy618@aleeas.com"),
            "sender_at_proton_me_abc@simplelogin.co",
        )
        assertEquals("myalias@astermail.org", result)
    }

    @Test
    fun suppresses_when_in_cc() {
        val headers = listOf("Delivered-To" to "myalias@astermail.org")
        assertNull(
            resolve_received_on_address(
                headers,
                listOf("other@example.com", "MyAlias@astermail.org"),
                "sender@example.com",
            ),
        )
    }

    @Test
    fun suppresses_when_already_visible() {
        val headers = listOf("Delivered-To" to "myalias@astermail.org")
        assertNull(
            resolve_received_on_address(
                headers,
                listOf("MyAlias@astermail.org"),
                "sender@example.com",
            ),
        )
        assertNull(
            resolve_received_on_address(
                headers,
                emptyList(),
                "myalias@astermail.org",
            ),
        )
    }
}
