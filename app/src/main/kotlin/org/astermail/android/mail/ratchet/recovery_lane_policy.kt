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

import org.astermail.android.crypto.ratchet.RecoveryLane

object RecoveryLanePolicy {
    fun lane_pq_identity_key(
        identity_public: String,
        bundle_pq_identity_key: String?,
        bootstrap: BootstrapData?,
    ): String? {
        if (!bundle_pq_identity_key.isNullOrBlank()) return bundle_pq_identity_key
        val session = bootstrap ?: return null
        if (session.recipient_identity_key != identity_public) return null
        return session.recipient_pq_identity_key?.takeIf { it.isNotBlank() }
    }

    fun session_lacks_advertised_pq_key(
        bootstrap: BootstrapData,
        advertised_pq_identity_key: String?,
    ): Boolean =
        !advertised_pq_identity_key.isNullOrBlank() &&
            bootstrap.recipient_pq_identity_key != advertised_pq_identity_key

    fun lane_matches_body(
        body_is_post_quantum: Boolean,
        lane: RecoveryLane.RecipientKeys,
    ): Boolean = !body_is_post_quantum || RecoveryLane.is_post_quantum(lane)
}

class RecoveryLaneDowngradeException(
    recipient: String?,
) : RatchetEncryptionException(recipient, "recovery lane lacks a post-quantum key")
