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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveSyncFrameTest {

    @Test
    fun parses_read_state_mutation() {
        val frame = """{"type":"mail_mutation","action":"update_metadata","item_ids":["a"]}"""
        assertEquals(LiveSyncFrame.mail_mutation, parse_live_sync_frame(frame))
    }

    @Test
    fun treats_reactions_as_new_mail() {
        assertEquals(LiveSyncFrame.new_mail, parse_live_sync_frame("""{"type":"new_reaction","mail_item_id":"a"}"""))
        assertEquals(LiveSyncFrame.new_mail, parse_live_sync_frame("""{"type":"new_mail","mail_item_id":"a"}"""))
    }

    @Test
    fun parses_session_frames() {
        assertEquals(LiveSyncFrame.auth_success, parse_live_sync_frame("""{"type":"auth_success"}"""))
        assertEquals(LiveSyncFrame.auth_error, parse_live_sync_frame("""{"type":"auth_error","message":"x"}"""))
        assertEquals(LiveSyncFrame.session_revoked, parse_live_sync_frame("""{"type":"session_revoked"}"""))
        assertEquals(LiveSyncFrame.ping, parse_live_sync_frame("""{"type":"ping"}"""))
    }

    @Test
    fun ignores_unknown_and_malformed_frames() {
        assertNull(parse_live_sync_frame("""{"type":"prekey_low"}"""))
        assertNull(parse_live_sync_frame("not json"))
        assertNull(parse_live_sync_frame("{\"type\":\"mail_mutation\",\"pad\":\"" + "x".repeat(70_000) + "\"}"))
    }
}
