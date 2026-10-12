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
    fun parses_an_added_reaction_frame() {
        val frame = parse_reaction_frame(
            """{"type":"new_reaction","mail_item_id":"r","target_mail_item_id":"t","thread_token":"dG9rZW4=","event":"added"}""",
        )

        assertEquals("r", frame?.mail_item_id)
        assertEquals("t", frame?.target_mail_item_id)
        assertEquals("dG9rZW4=", frame?.thread_token)
        assertEquals(true, frame?.is_added)
        assertEquals(false, frame?.clears_unseen)
    }

    @Test
    fun only_an_added_reaction_event_is_notifiable() {
        for (event in listOf("removed", "seen", "hidden", "other")) {
            val frame = parse_reaction_frame("""{"type":"new_reaction","mail_item_id":"r","event":"$event"}""")
            assertEquals(event, frame?.event)
            assertEquals(false, frame?.is_added)
            assertEquals(event == "seen" || event == "hidden", frame?.clears_unseen)
        }
    }

    @Test
    fun a_reaction_frame_without_an_event_keeps_the_event_empty() {
        val frame = parse_reaction_frame("""{"type":"new_reaction","mail_item_id":"r","target_mail_item_id":null}""")

        assertNull(frame?.event)
        assertNull(frame?.target_mail_item_id)
        assertNull(frame?.thread_token)
        assertEquals(false, frame?.is_added)
    }

    @Test
    fun reaction_parsing_ignores_other_frames() {
        assertNull(parse_reaction_frame("""{"type":"new_mail","mail_item_id":"a","event":"added"}"""))
        assertNull(parse_reaction_frame("not json"))
    }

    @Test
    fun parses_session_frames() {
        assertEquals(LiveSyncFrame.auth_success, parse_live_sync_frame("""{"type":"auth_success"}"""))
        assertEquals(LiveSyncFrame.auth_error, parse_live_sync_frame("""{"type":"auth_error","message":"x"}"""))
        assertEquals(LiveSyncFrame.session_revoked, parse_live_sync_frame("""{"type":"session_revoked"}"""))
        assertEquals(LiveSyncFrame.ping, parse_live_sync_frame("""{"type":"ping"}"""))
    }

    @Test
    fun parses_contacts_changed() {
        assertEquals(LiveSyncFrame.contacts_changed, parse_live_sync_frame("""{"type":"contacts_changed"}"""))
    }

    @Test
    fun parses_folder_and_tag_changes() {
        assertEquals(LiveSyncFrame.folders_changed, parse_live_sync_frame("""{"type":"folders_changed"}"""))
        assertEquals(LiveSyncFrame.tags_changed, parse_live_sync_frame("""{"type":"tags_changed"}"""))
    }

    @Test
    fun ignores_unknown_and_malformed_frames() {
        assertNull(parse_live_sync_frame("""{"type":"prekey_low"}"""))
        assertNull(parse_live_sync_frame("not json"))
        assertNull(parse_live_sync_frame("{\"type\":\"mail_mutation\",\"pad\":\"" + "x".repeat(70_000) + "\"}"))
    }
}
