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

package org.astermail.android.crypto.storage_rotation

import org.junit.Assert.assertEquals
import org.junit.Test

class SenderHashRefreshTest {

    private val old_key = ByteArray(32) { (it + 1).toByte() }
    private val new_key = ByteArray(32) { (it * 3 + 11).toByte() }

    private fun sign(key: ByteArray, info: String, value: String): String =
        encode_b64(hmac_sha256(derive_hmac_key(key, info), value.toByteArray(Charsets.UTF_8)))

    @Test
    fun `an outbox alias hash from before the rotation is moved to the new key`() {
        val stored = sign(old_key, ALIAS_HMAC_INFO, "johnsmith@astermail.org")

        val refreshed = current_sender_alias_hash(stored, "John.Smith@astermail.org", new_key, listOf(new_key, old_key))

        assertEquals(sign(new_key, ALIAS_HMAC_INFO, "johnsmith@astermail.org"), refreshed)
    }

    @Test
    fun `a domain address hash keeps the formula it was made with`() {
        val stored = sign(old_key, DOMAIN_ADDRESS_HMAC_INFO, "john.smith@example.com")

        val refreshed = current_sender_alias_hash(stored, "John.Smith@Example.com", new_key, listOf(old_key))

        assertEquals(sign(new_key, DOMAIN_ADDRESS_HMAC_INFO, "john.smith@example.com"), refreshed)
    }

    @Test
    fun `a hash already under the current key is left alone`() {
        val stored = sign(new_key, ALIAS_HMAC_INFO, "ada@astermail.org")

        assertEquals(stored, current_sender_alias_hash(stored, "ada@astermail.org", new_key, listOf(old_key)))
    }

    @Test
    fun `a hash that no known key produced is sent unchanged`() {
        val stored = sign(ByteArray(32) { 9 }, ALIAS_HMAC_INFO, "ada@astermail.org")

        assertEquals(stored, current_sender_alias_hash(stored, "ada@astermail.org", new_key, listOf(old_key)))
    }

    @Test
    fun `without a write key or a sender the stored hash is kept`() {
        val stored = sign(old_key, ALIAS_HMAC_INFO, "ada@astermail.org")

        assertEquals(stored, current_sender_alias_hash(stored, "ada@astermail.org", null, listOf(old_key)))
        assertEquals(stored, current_sender_alias_hash(stored, null, new_key, listOf(old_key)))
        assertEquals(null, current_sender_alias_hash(null, "ada@astermail.org", new_key, listOf(old_key)))
    }
}
