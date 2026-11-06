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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatchetBundlePublishPolicyTest {

    private val identity = "aWRlbnRpdHk="
    private val spk = "c3Br"
    private val pq = "cHE="

    @Test
    fun missing_bundle_never_matches() {
        assertFalse(published_bundle_matches_local(null, identity, spk, pq))
    }

    @Test
    fun bundle_without_pq_key_needs_republish() {
        val published = PublishedRatchetBundleView(identity, spk, null)
        assertFalse(published_bundle_matches_local(published, identity, spk, pq))
        val blank = PublishedRatchetBundleView(identity, spk, "")
        assertFalse(published_bundle_matches_local(blank, identity, spk, pq))
    }

    @Test
    fun bundle_with_matching_keys_matches() {
        val published = PublishedRatchetBundleView(identity, spk, pq)
        assertTrue(published_bundle_matches_local(published, identity, spk, pq))
    }

    @Test
    fun any_differing_key_needs_republish() {
        assertFalse(published_bundle_matches_local(PublishedRatchetBundleView("b3RoZXI=", spk, pq), identity, spk, pq))
        assertFalse(published_bundle_matches_local(PublishedRatchetBundleView(identity, "b3RoZXI=", pq), identity, spk, pq))
        assertFalse(published_bundle_matches_local(PublishedRatchetBundleView(identity, spk, "b3RoZXI="), identity, spk, pq))
    }

    @Test
    fun pq_identity_requires_both_halves() {
        assertFalse(local_pq_identity_ready(null, pq))
        assertFalse(local_pq_identity_ready("c2VjcmV0", null))
        assertFalse(local_pq_identity_ready("", pq))
        assertTrue(local_pq_identity_ready("c2VjcmV0", pq))
    }

    @Test
    fun retry_delay_backs_off_and_caps() {
        assertEquals(5_000L, ratchet_bootstrap_retry_delay_ms(0))
        assertEquals(10_000L, ratchet_bootstrap_retry_delay_ms(1))
        assertEquals(20_000L, ratchet_bootstrap_retry_delay_ms(2))
        assertEquals(RATCHET_BOOTSTRAP_MAX_DELAY_MS, ratchet_bootstrap_retry_delay_ms(7))
        assertEquals(RATCHET_BOOTSTRAP_MAX_DELAY_MS, ratchet_bootstrap_retry_delay_ms(1_000))
        assertEquals(5_000L, ratchet_bootstrap_retry_delay_ms(-3))
    }

    @Test
    fun only_published_outcomes_confirm_the_bundle() {
        assertTrue(RatchetBootstrapOutcome.Published.bundle_confirmed)
        assertTrue(RatchetBootstrapOutcome.AlreadyPublished.bundle_confirmed)
        assertFalse(RatchetBootstrapOutcome.Deferred("no pq").bundle_confirmed)
        assertFalse(RatchetBootstrapOutcome.Failed("rejected").bundle_confirmed)
        assertFalse(RatchetBootstrapOutcome.InFlight.bundle_confirmed)
    }
}
