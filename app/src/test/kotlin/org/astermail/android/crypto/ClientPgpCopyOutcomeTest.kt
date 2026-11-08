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

package org.astermail.android.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientPgpCopyOutcomeTest {

    private val entity = "Subject: hi\r\n\r\nhello".toByteArray()
    private val armor = "-----BEGIN PGP MESSAGE-----\n\nabc\n-----END PGP MESSAGE-----"

    @Test
    fun one_copy_per_recipient_key() {
        val outcome = ClientPgpCopies.encrypt_copies_outcome(
            entity,
            linkedMapOf("bob@example.com" to "k1", "eve@example.com" to "k2"),
            0L,
        ) { _, key -> "$armor$key" }
        assertTrue(outcome is ClientPgpCopyOutcome.Copies)
        assertEquals(
            listOf("bob@example.com", "eve@example.com"),
            (outcome as ClientPgpCopyOutcome.Copies).copies.keys.toList(),
        )
    }

    @Test
    fun encryption_error_fails_without_too_large() {
        val outcome = ClientPgpCopies.encrypt_copies_outcome(entity, mapOf("bob@example.com" to "k"), 0L) { _, _ -> null }
        assertEquals(ClientPgpCopyOutcome.Failed(too_large = false), outcome)
    }

    @Test
    fun output_without_armor_fails() {
        val outcome = ClientPgpCopies.encrypt_copies_outcome(entity, mapOf("bob@example.com" to "k"), 0L) { _, _ -> "plain" }
        assertEquals(ClientPgpCopyOutcome.Failed(too_large = false), outcome)
    }

    @Test
    fun oversize_attachments_fail_as_too_large() {
        val outcome = ClientPgpCopies.encrypt_copies_outcome(
            entity,
            mapOf("bob@example.com" to "k"),
            ClientPgpCopies.MAX_COPY_ATTACHMENT_BYTES + 1,
        ) { _, _ -> armor }
        assertEquals(ClientPgpCopyOutcome.Failed(too_large = true), outcome)
    }

    @Test
    fun oversize_copy_fails_as_too_large() {
        val huge = armor + "x".repeat((ClientPgpCopies.copy_limit_bytes(0L) + 1).toInt())
        val outcome = ClientPgpCopies.encrypt_copies_outcome(entity, mapOf("bob@example.com" to "k"), 0L) { _, _ -> huge }
        assertEquals(ClientPgpCopyOutcome.Failed(too_large = true), outcome)
    }

    @Test
    fun no_keys_fails() {
        val outcome = ClientPgpCopies.encrypt_copies_outcome(entity, emptyMap(), 0L) { _, _ -> armor }
        assertEquals(ClientPgpCopyOutcome.Failed(too_large = false), outcome)
    }
}
