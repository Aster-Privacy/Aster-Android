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

package org.astermail.android.api.keys

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundWriteSwitchesTest {

    @After
    fun teardown() {
        BoundWriteSwitches.clear()
    }

    @Test
    fun both_switches_are_off_before_the_server_answers() {
        BoundWriteSwitches.clear()
        assertFalse(BoundWriteSwitches.vault())
        assertFalse(BoundWriteSwitches.attachment())
    }

    @Test
    fun a_server_that_omits_the_switches_leaves_them_off() {
        BoundWriteSwitches.record("""{"format_writes":true}""", 1_000L)
        assertFalse(BoundWriteSwitches.vault(1_000L))
        assertFalse(BoundWriteSwitches.attachment(1_000L))
    }

    @Test
    fun each_switch_follows_its_own_field() {
        BoundWriteSwitches.record("""{"bound_vault_writes":true,"bound_attachment_writes":false}""", 1_000L)
        assertTrue(BoundWriteSwitches.vault(1_000L))
        assertFalse(BoundWriteSwitches.attachment(1_000L))
        BoundWriteSwitches.record("""{"bound_vault_writes":false,"bound_attachment_writes":true}""", 2_000L)
        assertFalse(BoundWriteSwitches.vault(2_000L))
        assertTrue(BoundWriteSwitches.attachment(2_000L))
    }

    @Test
    fun only_a_json_true_turns_a_switch_on() {
        BoundWriteSwitches.record("""{"bound_vault_writes":"true","bound_attachment_writes":1}""", 1_000L)
        assertFalse(BoundWriteSwitches.vault(1_000L))
        assertFalse(BoundWriteSwitches.attachment(1_000L))
        BoundWriteSwitches.record("not json", 1_000L)
        assertFalse(BoundWriteSwitches.vault(1_000L))
    }

    @Test
    fun a_stale_answer_counts_as_off() {
        BoundWriteSwitches.record("""{"bound_vault_writes":true,"bound_attachment_writes":true}""", 1_000L)
        assertTrue(BoundWriteSwitches.vault(1_000L + BoundWriteSwitches.FRESH_MS - 1))
        assertFalse(BoundWriteSwitches.vault(1_000L + BoundWriteSwitches.FRESH_MS))
        assertFalse(BoundWriteSwitches.attachment(999L))
    }
}
