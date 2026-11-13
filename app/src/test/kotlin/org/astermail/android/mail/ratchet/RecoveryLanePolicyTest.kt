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

package org.astermail.android.mail.ratchet

import kotlinx.coroutines.test.runTest
import org.astermail.android.crypto.ratchet.RecoveryLane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryLanePolicyTest {

    private val identity = "identity-a"
    private val session = BootstrapData(
        ephemeral_key = "ek",
        recipient_identity_key = identity,
        recipient_pq_identity_key = "pq-session",
    )

    @Test
    fun `bundle pq key wins when present`() {
        assertEquals("pq-bundle", RecoveryLanePolicy.lane_pq_identity_key(identity, "pq-bundle", session))
    }

    @Test
    fun `missing bundle pq key falls back to the session key`() {
        assertEquals("pq-session", RecoveryLanePolicy.lane_pq_identity_key(identity, null, session))
        assertEquals("pq-session", RecoveryLanePolicy.lane_pq_identity_key(identity, "", session))
    }

    @Test
    fun `session key is never used for a different identity`() {
        assertNull(RecoveryLanePolicy.lane_pq_identity_key("identity-b", null, session))
        assertNull(RecoveryLanePolicy.lane_pq_identity_key(identity, null, null))
        assertNull(RecoveryLanePolicy.lane_pq_identity_key(identity, null, session.copy(recipient_pq_identity_key = "")))
    }

    @Test
    fun `session restarts only when the advertised pq key differs`() {
        assertFalse(RecoveryLanePolicy.session_lacks_advertised_pq_key(session, null))
        assertFalse(RecoveryLanePolicy.session_lacks_advertised_pq_key(session, ""))
        assertFalse(RecoveryLanePolicy.session_lacks_advertised_pq_key(session, "pq-session"))
        assertTrue(RecoveryLanePolicy.session_lacks_advertised_pq_key(session, "pq-new"))
        assertTrue(RecoveryLanePolicy.session_lacks_advertised_pq_key(session.copy(recipient_pq_identity_key = null), "pq-new"))
    }

    @Test
    fun `a post-quantum body needs a post-quantum lane`() {
        val classical = RecoveryLane.RecipientKeys(identity, null)
        val hybrid = RecoveryLane.RecipientKeys(identity, "pq")
        assertTrue(RecoveryLanePolicy.lane_matches_body(false, classical))
        assertTrue(RecoveryLanePolicy.lane_matches_body(true, hybrid))
        assertFalse(RecoveryLanePolicy.lane_matches_body(true, classical))
    }

    @Test
    fun `downgraded lane routes to pgp only when non post-quantum mail is allowed`() = runTest {
        val sealed = seal_internal_body(
            recipients = listOf("a@aster.cx"),
            allow_non_post_quantum = true,
            seal_ratchet = { throw RecoveryLaneDowngradeException("a@aster.cx") },
            seal_pgp = { "pgp" },
        )
        assertEquals("pgp", sealed)

        val refused = runCatching {
            seal_internal_body(
                recipients = listOf("a@aster.cx"),
                allow_non_post_quantum = false,
                seal_ratchet = { throw RecoveryLaneDowngradeException("a@aster.cx") },
                seal_pgp = { "pgp" },
            )
        }.exceptionOrNull()
        assertTrue(refused is PostQuantumUnavailableException)
    }

    @Test
    fun `other ratchet errors still propagate`() = runTest {
        val thrown = runCatching {
            seal_internal_body(
                recipients = listOf("a@aster.cx"),
                allow_non_post_quantum = true,
                seal_ratchet = { throw RatchetEncryptionException("a@aster.cx", "boom") },
                seal_pgp = { "pgp" },
            )
        }.exceptionOrNull()
        assertEquals("boom", thrown?.message)
    }
}
