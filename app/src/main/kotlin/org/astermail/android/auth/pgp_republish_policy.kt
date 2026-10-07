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

package org.astermail.android.auth

import org.astermail.android.api.ApiError
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory

private const val PGP_KEY_VERSION_4 = 4
private const val PGP_ALGORITHM_X25519 = 25
private const val PGP_ALGORITHM_X448 = 26
private const val PGP_ALGORITHM_ED25519 = 27
private const val PGP_ALGORITHM_ED448 = 28

sealed interface PublishedPgpKey {
    data object Absent : PublishedPgpKey
    data object Unknown : PublishedPgpKey
    data class Present(val fingerprint: String) : PublishedPgpKey
}

enum class PgpRepublishDecision {
    REPUBLISH,
    SKIP_NON_STANDARD_KEY,
    SKIP_PUBLISHED_KEY_DIFFERS,
    SKIP_PUBLISHED_STATE_UNKNOWN,
}

class PgpRepublishBlocked(val decision: PgpRepublishDecision) :
    IllegalStateException("pgp republish skipped: ${decision.name.lowercase()}")

fun pgp_key_packet_is_non_standard(version: Int, algorithm: Int): Boolean =
    version == PGP_KEY_VERSION_4 && algorithm in setOf(
        PGP_ALGORITHM_X25519,
        PGP_ALGORITHM_X448,
        PGP_ALGORITHM_ED25519,
        PGP_ALGORITHM_ED448,
    )

fun armored_pgp_key_is_non_standard(armored_key: String): Boolean = try {
    val factory = BcPGPObjectFactory(PGPUtil.getDecoderStream(armored_key.byteInputStream()))
    val keys = mutableListOf<PGPPublicKey>()
    var next = factory.nextObject()
    while (next != null) {
        when (next) {
            is PGPSecretKeyRing -> next.publicKeys.forEach { keys.add(it) }
            is PGPPublicKeyRing -> next.publicKeys.forEach { keys.add(it) }
        }
        next = factory.nextObject()
    }
    keys.any { pgp_key_packet_is_non_standard(it.version, it.algorithm) }
} catch (_: Throwable) {
    false
}

fun published_pgp_key_lookup_is_transient(error: Throwable): Boolean = when (error) {
    is ApiError.NetworkError,
    is ApiError.ServerError,
    is java.io.IOException,
    -> true
    else -> false
}

fun decide_pgp_republish(
    local_fingerprint: String,
    local_key_non_standard: Boolean,
    published: PublishedPgpKey,
): PgpRepublishDecision {
    if (local_key_non_standard) return PgpRepublishDecision.SKIP_NON_STANDARD_KEY
    return when (published) {
        PublishedPgpKey.Absent -> PgpRepublishDecision.REPUBLISH
        PublishedPgpKey.Unknown -> PgpRepublishDecision.SKIP_PUBLISHED_STATE_UNKNOWN
        is PublishedPgpKey.Present -> when {
            published.fingerprint.isBlank() -> PgpRepublishDecision.SKIP_PUBLISHED_STATE_UNKNOWN
            published.fingerprint.equals(local_fingerprint, ignoreCase = true) ->
                PgpRepublishDecision.REPUBLISH
            else -> PgpRepublishDecision.SKIP_PUBLISHED_KEY_DIFFERS
        }
    }
}
