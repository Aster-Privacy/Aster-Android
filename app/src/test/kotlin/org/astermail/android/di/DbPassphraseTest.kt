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

package org.astermail.android.di

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DbPassphraseTest {

    private val generated = ByteArray(32) { it.toByte() }

    private fun encode(bytes: ByteArray): String = bytes.joinToString(",") { it.toString() }

    private fun decode(text: String): ByteArray? =
        runCatching { text.split(",").map { it.toByte() }.toByteArray() }.getOrNull()

    @Test
    fun a_stored_passphrase_is_reused_and_never_regenerated() {
        var writes = 0

        val resolved = StorageModule.resolve_db_passphrase(
            read_encoded = { encode(generated) },
            write_encoded = { writes += 1; true },
            new_key = { throw IllegalStateException("must not generate") },
            encode = ::encode,
            decode = ::decode,
        )

        assertArrayEquals(generated, resolved)
        assertEquals(0, writes)
    }

    @Test
    fun a_fresh_passphrase_is_returned_once_it_is_durably_stored() {
        val stored = HashMap<String, String>()

        val resolved = StorageModule.resolve_db_passphrase(
            read_encoded = { stored["key"] },
            write_encoded = { stored["key"] = it; true },
            new_key = { generated.copyOf() },
            encode = ::encode,
            decode = ::decode,
        )

        assertArrayEquals(generated, resolved)
        assertTrue(stored.containsKey("key"))
    }

    @Test
    fun an_unstorable_passphrase_is_never_used_to_encrypt_the_database() {
        val resolved = StorageModule.resolve_db_passphrase(
            read_encoded = { null },
            write_encoded = { false },
            new_key = { generated.copyOf() },
            encode = ::encode,
            decode = ::decode,
        )

        assertNull(resolved)
    }

    @Test
    fun a_passphrase_written_by_another_opener_wins_over_a_failed_write() {
        val winner = ByteArray(32) { 7 }
        var reads = 0

        val resolved = StorageModule.resolve_db_passphrase(
            read_encoded = {
                reads += 1
                if (reads == 1) null else encode(winner)
            },
            write_encoded = { false },
            new_key = { generated.copyOf() },
            encode = ::encode,
            decode = ::decode,
        )

        assertArrayEquals(winner, resolved)
    }
}
