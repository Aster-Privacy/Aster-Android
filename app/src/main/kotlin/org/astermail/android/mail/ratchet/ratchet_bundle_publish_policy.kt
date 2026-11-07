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

sealed class RatchetBootstrapOutcome {
    object Published : RatchetBootstrapOutcome()
    object AlreadyPublished : RatchetBootstrapOutcome()
    data class Deferred(val reason: String) : RatchetBootstrapOutcome()
    data class Failed(val reason: String) : RatchetBootstrapOutcome()
    object InFlight : RatchetBootstrapOutcome()

    val bundle_confirmed: Boolean
        get() = this == Published || this == AlreadyPublished
}

data class PublishedRatchetBundleView(
    val identity_public_b64: String?,
    val signed_prekey_public_b64: String?,
    val pq_identity_public_b64: String?,
)

fun published_bundle_matches_local(
    published: PublishedRatchetBundleView?,
    identity_public_b64: String,
    signed_prekey_public_b64: String,
    pq_identity_public_b64: String,
): Boolean {
    if (published == null) return false
    if (published.pq_identity_public_b64.isNullOrBlank()) return false
    return published.identity_public_b64 == identity_public_b64 &&
        published.signed_prekey_public_b64 == signed_prekey_public_b64 &&
        published.pq_identity_public_b64 == pq_identity_public_b64
}

fun local_pq_identity_ready(pq_identity_secret_b64: String?, pq_identity_public_b64: String?): Boolean =
    !pq_identity_secret_b64.isNullOrBlank() && !pq_identity_public_b64.isNullOrBlank()

fun ratchet_bootstrap_retry_delay_ms(attempt: Int): Long {
    val exponent = attempt.coerceIn(0, RATCHET_BOOTSTRAP_MAX_EXPONENT)
    return (RATCHET_BOOTSTRAP_BASE_DELAY_MS shl exponent).coerceAtMost(RATCHET_BOOTSTRAP_MAX_DELAY_MS)
}

const val RATCHET_BOOTSTRAP_BASE_DELAY_MS: Long = 5_000L
const val RATCHET_BOOTSTRAP_MAX_DELAY_MS: Long = 5L * 60 * 1000
const val RATCHET_BOOTSTRAP_FOREGROUND_DEBOUNCE_MS: Long = 30_000L
private const val RATCHET_BOOTSTRAP_MAX_EXPONENT: Int = 10
