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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class draft_autosave_policy_test {
    private fun allowed(
        auto_save_enabled: Boolean = true,
        is_sending: Boolean = false,
        sent: Boolean = false,
        discarded: Boolean = false,
        has_content: Boolean = true,
    ) = draft_autosave_allowed(auto_save_enabled, is_sending, sent, discarded, has_content)

    @Test
    fun editing_an_open_message_saves() {
        assertTrue(allowed())
    }

    @Test
    fun nothing_saves_once_send_starts() {
        assertFalse(allowed(is_sending = true))
    }

    @Test
    fun nothing_saves_after_the_message_is_sent() {
        assertFalse(allowed(sent = true))
        assertFalse(allowed(sent = true, is_sending = true))
    }

    @Test
    fun nothing_saves_after_discard() {
        assertFalse(allowed(discarded = true))
    }

    @Test
    fun empty_or_disabled_drafts_never_save() {
        assertFalse(allowed(has_content = false))
        assertFalse(allowed(auto_save_enabled = false))
    }

    @Test
    fun a_failed_send_lets_autosave_resume() {
        assertTrue(allowed(is_sending = false, sent = false))
    }
}
