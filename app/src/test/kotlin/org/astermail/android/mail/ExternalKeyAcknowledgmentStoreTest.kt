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

import org.astermail.android.storage.InMemoryPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalKeyAcknowledgmentStoreTest {

    private val fingerprint = "AAAA BBBB CCCC DDDD EEEE FFFF 0000 1111 2222 3333"

    @Test
    fun an_acknowledgment_survives_a_restart() {
        val prefs = InMemoryPrefs()
        ExternalKeyAcknowledgments { prefs }.note("acct", "send-1", "bob@example.com", fingerprint)
        val reopened = ExternalKeyAcknowledgments { prefs }
        assertTrue(reopened.matches("acct", "send-1", "bob@example.com", fingerprint))
        assertTrue(reopened.has_scope("send-1"))
    }

    @Test
    fun a_queued_send_keeps_its_acknowledgment_after_a_restart() {
        val prefs = InMemoryPrefs()
        val first = ExternalKeyAcknowledgments { prefs }
        first.note("acct", "compose-1", "bob@example.com", fingerprint)
        first.move("compose-1", "queued-9")
        val reopened = ExternalKeyAcknowledgments { prefs }
        assertTrue(reopened.matches("acct", "queued-9", "bob@example.com", fingerprint))
        assertFalse(reopened.has_scope("compose-1"))
    }

    @Test
    fun a_used_or_dropped_acknowledgment_stays_gone_after_a_restart() {
        val prefs = InMemoryPrefs()
        val first = ExternalKeyAcknowledgments { prefs }
        first.note("acct", "send-1", "bob@example.com", fingerprint)
        first.note("acct", "send-2", "bob@example.com", fingerprint)
        assertTrue(first.consume("acct", "send-1", "bob@example.com", fingerprint))
        first.drop("send-2")
        val reopened = ExternalKeyAcknowledgments { prefs }
        assertFalse(reopened.matches("acct", "send-1", "bob@example.com", fingerprint))
        assertFalse(reopened.matches("acct", "send-2", "bob@example.com", fingerprint))
    }

    @Test
    fun the_stored_list_is_capped() {
        val prefs = InMemoryPrefs()
        val first = ExternalKeyAcknowledgments { prefs }
        repeat(ExternalKeyAcknowledgments.MAX_SCOPES + 10) { first.note("acct", "send-$it", "bob@example.com", fingerprint) }
        val reopened = ExternalKeyAcknowledgments { prefs }
        assertFalse(reopened.has_scope("send-0"))
        assertTrue(reopened.has_scope("send-${ExternalKeyAcknowledgments.MAX_SCOPES + 9}"))
    }

    @Test
    fun an_unreadable_store_still_works_in_memory() {
        val acknowledgments = ExternalKeyAcknowledgments { throw IllegalStateException("locked") }
        assertTrue(acknowledgments.note("acct", "send-1", "bob@example.com", fingerprint))
        assertTrue(acknowledgments.matches("acct", "send-1", "bob@example.com", fingerprint))
    }
}
