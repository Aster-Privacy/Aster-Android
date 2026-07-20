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

package org.astermail.android.compose

import org.astermail.android.ui.compose.compute_received_on_alias
import org.astermail.android.ui.compose.reply_from_mismatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyFromMismatchTest {

    @Test
    fun flags_reply_from_different_alias() {
        assertTrue(
            reply_from_mismatch(
                mode = "reply",
                received_on_alias = "testing7363672g@aster.cx",
                from_alias = "2nd.testing.mail8383@astermail.org",
            ),
        )
    }

    @Test
    fun flags_reply_all_from_primary_when_received_on_alias() {
        assertTrue(
            reply_from_mismatch(
                mode = "reply_all",
                received_on_alias = "shopping@aster.cx",
                from_alias = "me@astermail.org",
            ),
        )
    }

    @Test
    fun accepts_reply_from_received_alias_case_insensitive() {
        assertFalse(
            reply_from_mismatch(
                mode = "reply",
                received_on_alias = "Testing7363672g@Aster.CX",
                from_alias = "testing7363672g@aster.cx",
            ),
        )
    }

    @Test
    fun ignores_new_forward_and_draft_modes() {
        for (mode in listOf("new", "forward", "draft", null)) {
            assertFalse(
                reply_from_mismatch(
                    mode = mode,
                    received_on_alias = "a@aster.cx",
                    from_alias = "b@astermail.org",
                ),
            )
        }
    }

    @Test
    fun ignores_unknown_received_alias() {
        assertFalse(
            reply_from_mismatch(
                mode = "reply",
                received_on_alias = null,
                from_alias = "b@astermail.org",
            ),
        )
        assertFalse(
            reply_from_mismatch(
                mode = "reply",
                received_on_alias = "  ",
                from_alias = "b@astermail.org",
            ),
        )
    }

    @Test
    fun delivered_to_beats_visible_recipients_in_received_on_resolution() {
        val received = compute_received_on_alias(
            recipient_addresses = listOf(
                "testing7363672g@aster.cx",
                "simplelogin.alias.traffic496@simplelogin.com",
            ),
            alias_options = listOf("me@astermail.org", "testing7363672g@aster.cx"),
            user_email = "me@astermail.org",
        )
        assertEquals("testing7363672g@aster.cx", received)
        assertTrue(
            reply_from_mismatch(
                mode = "reply",
                received_on_alias = received,
                from_alias = "me@astermail.org",
            ),
        )
        assertFalse(
            reply_from_mismatch(
                mode = "reply",
                received_on_alias = received,
                from_alias = "testing7363672g@aster.cx",
            ),
        )
    }
}
