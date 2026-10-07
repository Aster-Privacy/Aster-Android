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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendGatesTest {

    private val now = 1_790_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `schedule window allows exactly twenty eight days`() {
        assertFalse(exceeds_sealed_schedule_window(now + 28 * day, now))
        assertTrue(exceeds_sealed_schedule_window(now + 28 * day + 1, now))
        assertFalse(exceeds_sealed_schedule_window(now + 60_000L, now))
    }

    @Test
    fun `schedule block checks the window first`() {
        val block = scheduled_send_block(
            recipients = listOf("a@astermail.org", "b@example.com"),
            scheduled_at_ms = now + 29 * day,
            now_ms = now,
            require_encryption = true,
        )
        assertEquals(ScheduledSendBlock.TOO_FAR_AHEAD, block)
    }

    @Test
    fun `schedule block applies the encryption requirement to the outside half of a mixed schedule`() {
        val block = scheduled_send_block(
            recipients = listOf("a@astermail.org", "b@example.com"),
            scheduled_at_ms = now + day,
            now_ms = now,
            require_encryption = true,
        )
        assertEquals(ScheduledSendBlock.REQUIRES_ENCRYPTION, block)
    }

    @Test
    fun `schedule block allows mixed recipients without the encryption requirement`() {
        assertNull(
            scheduled_send_block(
                recipients = listOf("a@astermail.org", "b@example.com"),
                scheduled_at_ms = now + day,
                now_ms = now,
                require_encryption = false,
            ),
        )
    }

    @Test
    fun `schedule block rejects external schedules when encryption is required`() {
        val block = scheduled_send_block(
            recipients = listOf("b@example.com"),
            scheduled_at_ms = now + day,
            now_ms = now,
            require_encryption = true,
        )
        assertEquals(ScheduledSendBlock.REQUIRES_ENCRYPTION, block)
    }

    @Test
    fun `schedule block allows external schedules without the encryption requirement`() {
        assertNull(
            scheduled_send_block(
                recipients = listOf("b@example.com"),
                scheduled_at_ms = now + day,
                now_ms = now,
                require_encryption = false,
            ),
        )
    }

    @Test
    fun `schedule block allows internal schedules when encryption is required`() {
        assertNull(
            scheduled_send_block(
                recipients = listOf("a@astermail.org", "c@aster.cx"),
                scheduled_at_ms = now + day,
                now_ms = now,
                require_encryption = true,
            ),
        )
    }

    @Test
    fun `hidden bcc keeps only internal bcc not visible in to or cc`() {
        val hidden = hidden_internal_bcc(
            to = listOf("visible@astermail.org"),
            cc = listOf("Copied@Astermail.org"),
            bcc = listOf(
                "VISIBLE@astermail.org",
                "copied@astermail.org",
                "quiet@astermail.org",
                "Quiet@Astermail.org",
                "outside@example.com",
                " ",
            ),
        )
        assertEquals(listOf("quiet@astermail.org"), hidden)
    }

    @Test
    fun `shared targets drop every hidden bcc copy`() {
        val shared = shared_targets(
            to = listOf("a@astermail.org"),
            cc = emptyList(),
            bcc = listOf("hidden@astermail.org", "HIDDEN@astermail.org", "a@astermail.org"),
        )
        assertEquals(listOf("a@astermail.org", "a@astermail.org"), shared)
    }

    @Test
    fun `shared targets are empty when every recipient is a hidden bcc`() {
        assertTrue(shared_targets(emptyList(), emptyList(), listOf("only@astermail.org")).isEmpty())
        assertEquals(
            listOf("only@astermail.org"),
            hidden_internal_bcc(emptyList(), emptyList(), listOf("only@astermail.org")),
        )
    }

    @Test
    fun `replay is blocked when an external recipient key changed`() {
        val changes = listOf(RecipientKeyChange("friend@example.com", "aa", "bb"))
        assertTrue(replay_blocked_by_key_change(listOf("Friend@Example.com"), changes))
    }

    @Test
    fun `replay is not blocked by changes for other addresses`() {
        val changes = listOf(RecipientKeyChange("someone@example.com", "aa", "bb"))
        assertFalse(replay_blocked_by_key_change(listOf("friend@example.com"), changes))
        assertFalse(replay_blocked_by_key_change(listOf("friend@example.com"), emptyList()))
    }

    @Test
    fun `replay ignores internal addresses in the change list`() {
        val changes = listOf(RecipientKeyChange("a@astermail.org", "aa", "bb"))
        assertFalse(replay_blocked_by_key_change(listOf("a@astermail.org"), changes))
    }

    @Test
    fun `weak message passwords are rejected`() {
        listOf("", "a", "password", "Password1", "abcdefghijkl", "a".repeat(20), "abcd1234", "Abcdefg1234").forEach {
            assertFalse(it, is_strong_message_password(it))
        }
    }

    @Test
    fun `strong message passwords are accepted`() {
        listOf(
            "correct horse battery staple",
            "Tr0ub4dor-and-3",
            "abcdefghijk1",
            "AbcdefghijkL",
            "abcdefghijk!",
            "Abcdefghijkl",
        ).forEach {
            assertTrue(it, is_strong_message_password(it))
        }
    }

    @Test
    fun `message password tier follows the shared score`() {
        assertEquals(1, message_password_strength_tier(""))
        assertEquals(1, message_password_strength_tier("password"))
        assertEquals(1, message_password_strength_tier("abcdefgh"))
        assertEquals(4, message_password_strength_tier("Abcdefghij1!"))
        assertEquals(2, message_password_strength_tier("abcdefghijkl"))
        assertEquals(3, message_password_strength_tier("abcdefghijk1"))
        assertEquals(4, message_password_strength_tier("Tr0ub4dor-and-3"))
    }

    @Test
    fun `trust new key appears only when a matching identity change is pending`() {
        assertTrue(identity_change_pending_for(listOf("Bob@AsterMail.org"), listOf("bob@astermail.org")))
        assertFalse(identity_change_pending_for(listOf("bob@astermail.org"), listOf("eve@astermail.org")))
        assertFalse(identity_change_pending_for(listOf("bob@astermail.org"), emptyList()))
        assertFalse(identity_change_pending_for(listOf(""), listOf("")))
    }
}
