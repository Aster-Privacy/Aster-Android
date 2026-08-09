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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MailActionMessagesInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun message_scope_uses_message_noun() {
        assertEquals("Moved 1 message to trash", trashed_action_message(context, 1, true))
        assertEquals("Moved 3 messages to trash", trashed_action_message(context, 3, true))
        assertEquals("Archived 1 message", archived_action_message(context, 1, true))
        assertEquals("Archived 3 messages", archived_action_message(context, 3, true))
    }

    @Test
    fun thread_scope_keeps_conversation_noun() {
        assertEquals("Moved 1 conversation(s) to trash", trashed_action_message(context, 1, false))
        assertEquals("Archived 1 conversation(s)", archived_action_message(context, 1, false))
    }

    @Test
    fun batch_keys_do_not_merge_across_scopes() {
        assertNotEquals(batch_action_key("trash", true), batch_action_key("trash", false))
        assertNotEquals(batch_action_key("archive", true), batch_action_key("archive", false))
    }
}
