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
        pq_key_id: Int? = 3,
        sender_identity_key: String? = "sender",
        recipient_identity_key: String? = "recipient",
    ) = BootstrapData(
        ephemeral_key = "eph",
        pq_ciphertext = pq_ciphertext,
        pq_key_id = pq_key_id,
        sender_identity_key = sender_identity_key,
        recipient_identity_key = recipient_identity_key,
    )

    private fun bundle(kem_identity_key: String) = PrekeyBundleResponse(
        user_id = "u",
        kem_identity_key = kem_identity_key,
        signed_prekey = "spk",
        signed_prekey_signature = "sig",
    )

    @Test
    fun a_session_needs_both_the_ciphertext_and_the_key_id_to_count_as_post_quantum() {
        assertEquals(PostQuantumRecipientStatus.SUPPORTED, session_post_quantum_status(bootstrap()))
        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, session_post_quantum_status(bootstrap(pq_key_id = null)))
        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, session_post_quantum_status(bootstrap(pq_ciphertext = null)))
    }

    @Test
    fun a_session_is_reused_only_when_both_identities_are_unchanged() {
        assertTrue(session_reused_for_send(bootstrap(), "sender", bundle("recipient")))
        assertTrue(session_reused_for_send(bootstrap(), "sender", null))
        assertFalse(session_reused_for_send(bootstrap(), "new_sender", bundle("recipient")))
        assertFalse(session_reused_for_send(bootstrap(), "sender", bundle("new_recipient")))
    }
}
