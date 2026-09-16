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

import org.junit.Assert.assertEquals
import org.junit.Test

class PostQuantumDowngradeTest {

    private fun kem_key(): ByteArray = ByteArray(X3dh.ML_KEM_768_EK_LEN) { 7 }

    @Test
    fun a_recipient_with_a_usable_one_time_key_is_supported() {
        val status = classify_post_quantum_bundle(
            pq_capable = true,
            pq_prekey_pair = 4 to kem_key(),
            pq_identity_raw = null,
        )

        assertEquals(PostQuantumRecipientStatus.SUPPORTED, status)
    }

    @Test
    fun a_recipient_with_only_an_identity_key_is_supported() {
        val status = classify_post_quantum_bundle(
            pq_capable = null,
            pq_prekey_pair = null,
            pq_identity_raw = kem_key(),
        )

        assertEquals(PostQuantumRecipientStatus.SUPPORTED, status)
    }

    @Test
    fun a_recipient_that_never_published_post_quantum_keys_is_unsupported() {
        val status = classify_post_quantum_bundle(
            pq_capable = false,
            pq_prekey_pair = null,
            pq_identity_raw = null,
        )

        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, status)
    }

    @Test
    fun an_older_server_that_reports_nothing_is_unsupported() {
        val status = classify_post_quantum_bundle(
            pq_capable = null,
            pq_prekey_pair = null,
            pq_identity_raw = null,
        )

        assertEquals(PostQuantumRecipientStatus.UNSUPPORTED, status)
    }

    @Test
    fun a_stripped_bundle_from_a_capable_recipient_is_a_downgrade() {
        val status = classify_post_quantum_bundle(
            pq_capable = true,
            pq_prekey_pair = null,
            pq_identity_raw = null,
        )

        assertEquals(PostQuantumRecipientStatus.DOWNGRADED, status)
    }

    @Test
    fun a_truncated_key_from_a_capable_recipient_is_a_downgrade() {
        val status = classify_post_quantum_bundle(
            pq_capable = true,
            pq_prekey_pair = 4 to ByteArray(32),
            pq_identity_raw = ByteArray(32),
        )

        assertEquals(PostQuantumRecipientStatus.DOWNGRADED, status)
    }

    @Test
    fun the_default_coverage_reports_nothing_to_warn_about() {
        val coverage = PostQuantumCoverage()

        assertEquals(emptyList<String>(), coverage.missing)
        assertEquals(emptyList<String>(), coverage.downgraded)
    }
}
