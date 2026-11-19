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

package org.astermail.android.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrongBoxKeysTest {

    @Test
    fun strongbox_is_requested_first_when_the_device_has_it() {
        val asked = mutableListOf<Boolean>()

        val made = StrongBoxKeys.create(available = true) { strongbox ->
            asked += strongbox
            if (strongbox) "strongbox" else "tee"
        }

        assertEquals("strongbox", made)
        assertEquals(listOf(true), asked)
    }

    @Test
    fun a_strongbox_failure_falls_back_to_the_regular_keystore() {
        val asked = mutableListOf<Boolean>()

        val made = StrongBoxKeys.create(available = true) { strongbox ->
            asked += strongbox
            if (strongbox) throw java.security.ProviderException("strongbox unavailable")
            "tee"
        }

        assertEquals("tee", made)
        assertEquals(listOf(true, false), asked)
    }

    @Test
    fun strongbox_is_never_requested_without_the_feature() {
        val asked = mutableListOf<Boolean>()

        val made = StrongBoxKeys.create(available = false) { strongbox ->
            asked += strongbox
            "tee"
        }

        assertEquals("tee", made)
        assertEquals(listOf(false), asked)
    }

    @Test
    fun a_strongbox_key_that_fails_its_first_use_is_replaced_by_a_regular_key() {
        val asked = mutableListOf<Boolean>()
        val discarded = mutableListOf<String>()

        val made = StrongBoxKeys.create(
            available = true,
            probe = { key: String -> key != "strongbox" },
            discard = { discarded += "strongbox" },
        ) { strongbox ->
            asked += strongbox
            if (strongbox) "strongbox" else "tee"
        }

        assertEquals("tee", made)
        assertEquals(listOf(true, false), asked)
        assertEquals(listOf("strongbox"), discarded)
    }

    @Test
    fun a_probe_that_throws_counts_as_a_failed_first_use() {
        val made = StrongBoxKeys.create(
            available = true,
            probe = { _: String -> throw java.security.ProviderException("keymaster error") },
        ) { strongbox -> if (strongbox) "strongbox" else "tee" }

        assertEquals("tee", made)
    }

    @Test
    fun an_existing_key_is_never_probed_or_discarded() {
        val discarded = mutableListOf<String>()

        val made = StrongBoxKeys.create(
            available = true,
            fresh = false,
            probe = { _: String -> false },
            discard = { discarded += "key" },
        ) { strongbox -> if (strongbox) "strongbox" else "tee" }

        assertEquals("strongbox", made)
        assertEquals(emptyList<String>(), discarded)
    }

    @Test
    fun the_round_trip_probe_accepts_a_working_key_and_rejects_a_broken_one() {
        val working = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertTrue(StrongBoxKeys.round_trips(working))
        val broken = javax.crypto.spec.SecretKeySpec(ByteArray(7), "AES")
        assertFalse(runCatching { StrongBoxKeys.round_trips(broken) }.getOrDefault(false))
    }

    @Test
    fun a_key_waiting_for_the_user_still_counts_as_usable() {
        val broken = javax.crypto.spec.SecretKeySpec(ByteArray(7), "AES")
        assertTrue(StrongBoxKeys.starts_operation(broken) { true })
        assertFalse(StrongBoxKeys.starts_operation(broken) { false })
    }
}
