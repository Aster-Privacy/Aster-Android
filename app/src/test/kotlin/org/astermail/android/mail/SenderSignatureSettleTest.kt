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

import org.astermail.android.crypto.PgpDecryptionResult
import org.astermail.android.crypto.PgpSignatureStatus
import org.astermail.android.mail.ratchet.SavedKeyCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SenderSignatureSettleTest {

    private val valid = PgpDecryptionResult("body", PgpSignatureStatus.VALID)

    private fun settle(check: SavedKeyCheck?, own: Boolean = false): PgpSignatureStatus? =
        settle_sender_signature(valid, { check }, { own })?.signature

    @Test
    fun `a signature checked against the saved key stays verified`() {
        assertEquals(PgpSignatureStatus.VALID, settle(SavedKeyCheck.MATCH))
        assertEquals(PgpSignatureStatus.VALID, settle(SavedKeyCheck.FIRST))
    }

    @Test
    fun `a served key that differs from the saved one is not verified`() {
        assertEquals(PgpSignatureStatus.UNVERIFIED, settle(SavedKeyCheck.CHANGED))
    }

    @Test
    fun `an unreadable or missing saved key check is not verified`() {
        assertEquals(PgpSignatureStatus.UNVERIFIED, settle(SavedKeyCheck.UNREADABLE))
        assertEquals(PgpSignatureStatus.UNVERIFIED, settle(null))
    }

    @Test
    fun `a signature made with the account keys stays verified`() {
        assertEquals(PgpSignatureStatus.VALID, settle(SavedKeyCheck.CHANGED, own = true))
    }

    @Test
    fun `the body is kept when the signature is no longer verified`() {
        assertEquals("body", settle_sender_signature(valid, { SavedKeyCheck.CHANGED }, { false })?.plaintext)
    }

    @Test
    fun `other signature states pass through without a saved key check`() {
        var consulted = false
        val check = {
            consulted = true
            SavedKeyCheck.MATCH
        }
        listOf(PgpSignatureStatus.NONE, PgpSignatureStatus.UNVERIFIED, PgpSignatureStatus.INVALID).forEach { status ->
            val result = PgpDecryptionResult("body", status)
            assertEquals(result, settle_sender_signature(result, check, { true }))
        }
        assertNull(settle_sender_signature(null, check, { true }))
        assertFalse(consulted)
    }
}
