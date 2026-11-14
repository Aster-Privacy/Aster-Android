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

import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPostQuantumStatusTest {

    private fun bootstrap(
        pq_ciphertext: String? = "ct",
        pq_key_id: Int? = X3dh.PQ_IDENTITY_KEY_ID,
        sender_identity_key: String? = "sender",
        recipient_identity_key: String? = "recipient",
        recipient_pq_identity_key: String? = "pq",
    ) = BootstrapData(
        ephemeral_key = "eph",
        pq_ciphertext = pq_ciphertext,
        pq_key_id = pq_key_id,
        sender_identity_key = sender_identity_key,
        recipient_identity_key = recipient_identity_key,
        recipient_pq_identity_key = recipient_pq_identity_key,
    )

    private fun bundle(kem_identity_key: String) = PrekeyBundleResponse(
        user_id = "u",
        kem_identity_key = kem_identity_key,
        signed_prekey = "spk",
        signed_prekey_signature = "sig",
    )

    @Test
    fun a_session_counts_as_post_quantum_only_on_the_signed_identity_key() {
        assertEquals(PostQuantumRecipientStatus.SUPPORTED, session_post_quantum_status(bootstrap(), "pq", true))
        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, session_post_quantum_status(bootstrap(), "pq", false))
        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, session_post_quantum_status(bootstrap(pq_key_id = 3), "pq", true))
        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, session_post_quantum_status(bootstrap(pq_key_id = null), "pq", true))
        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, session_post_quantum_status(bootstrap(pq_ciphertext = null), "pq", true))
        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, session_post_quantum_status(bootstrap(), "other", true))
        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, session_post_quantum_status(bootstrap(), null, true))
        assertEquals(
            PostQuantumRecipientStatus.UNSUPPORTED,
            session_post_quantum_status(bootstrap(recipient_pq_identity_key = null), null, true),
        )
    }

    @Test
    fun a_one_time_key_session_restarts_only_once_the_owner_signs_the_identity_key() {
        assertTrue(session_restarts_for_signed_pq_key(bootstrap(pq_key_id = 3), "pq", true))
        assertFalse(session_restarts_for_signed_pq_key(bootstrap(pq_key_id = 3), "pq", false))
        assertFalse(session_restarts_for_signed_pq_key(bootstrap(pq_key_id = 3), null, true))
        assertFalse(session_restarts_for_signed_pq_key(bootstrap(), "pq", true))
        assertFalse(session_restarts_for_signed_pq_key(bootstrap(pq_ciphertext = null, pq_key_id = null), "pq", true))
    }

    @Test
    fun a_session_is_reused_only_when_both_identities_are_unchanged() {
        assertTrue(session_reused_for_send(bootstrap(), "sender", bundle("recipient")))
        assertTrue(session_reused_for_send(bootstrap(), "sender", null))
        assertFalse(session_reused_for_send(bootstrap(), "new_sender", bundle("recipient")))
        assertFalse(session_reused_for_send(bootstrap(), "sender", bundle("new_recipient")))
    }
}
