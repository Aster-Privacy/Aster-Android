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

package org.astermail.android.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CachedSenderMemoTest {
    @Test
    fun unknown_address_reads_blank() {
        assertEquals("", cached_sender_memo().read("me@astermail.org"))
    }

    @Test
    fun written_sender_reads_back_ignoring_case() {
        val memo = cached_sender_memo()
        memo.write("Me@AsterMail.org", "alias@astermail.org")
        assertEquals("alias@astermail.org", memo.read("me@astermail.org"))
    }

    @Test
    fun blank_address_is_never_stored() {
        val memo = cached_sender_memo()
        memo.write("", "alias@astermail.org")
        assertEquals("", memo.read(""))
    }

    @Test
    fun oldest_entry_is_evicted_past_capacity() {
        val memo = cached_sender_memo(max_entries = 2)
        memo.write("a@astermail.org", "a1")
        memo.write("b@astermail.org", "b1")
        memo.write("c@astermail.org", "c1")
        assertEquals("", memo.read("a@astermail.org"))
        assertEquals("b1", memo.read("b@astermail.org"))
        assertEquals("c1", memo.read("c@astermail.org"))
    }

    @Test
    fun rewriting_an_entry_refreshes_its_value() {
        val memo = cached_sender_memo(max_entries = 2)
        memo.write("a@astermail.org", "a1")
        memo.write("b@astermail.org", "b1")
        memo.write("a@astermail.org", "a2")
        memo.write("c@astermail.org", "c1")
        assertEquals("a2", memo.read("a@astermail.org"))
        assertEquals("", memo.read("b@astermail.org"))
    }
}
