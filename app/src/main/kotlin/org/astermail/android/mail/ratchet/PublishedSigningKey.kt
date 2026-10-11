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

import java.math.BigInteger
import java.util.Locale
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator

sealed class PublishedSigningKeyChoice {
    data class Selected(val armored_secret_key: String) : PublishedSigningKeyChoice()
    object NotHeld : PublishedSigningKeyChoice()
    object NoCandidates : PublishedSigningKeyChoice()
}

object PublishedSigningKey {

    fun fingerprint_of(armored_secret_key: String): String? = try {
        val secret_ring = PGPSecretKeyRing(
            PGPUtil.getDecoderStream(armored_secret_key.byteInputStream()),
            BcKeyFingerprintCalculator(),
        )
        String.format(Locale.US, "%040X", BigInteger(1, secret_ring.publicKey.fingerprint))
    } catch (_: Throwable) {
        null
    }

    fun normalize_fingerprint(fingerprint: String): String =
        fingerprint.filterNot { it.isWhitespace() || it == ':' }.uppercase(Locale.US)

    fun candidates(identity_key: String?, previous_keys: List<String>?): List<String> =
        buildList {
            identity_key?.let { add(it) }
            previous_keys?.let { addAll(it) }
        }
            .filter { PrekeyBindingSigner.looks_like_armored_private_key(it) }
            .distinct()

    fun choose(candidates: List<String>, published_fingerprint: String?): PublishedSigningKeyChoice {
        if (candidates.isEmpty()) return PublishedSigningKeyChoice.NoCandidates
        val published = published_fingerprint?.let { normalize_fingerprint(it) }.orEmpty()
        if (published.isEmpty()) return PublishedSigningKeyChoice.Selected(candidates.first())
        val match = candidates.firstOrNull { fingerprint_of(it) == published }
            ?: return PublishedSigningKeyChoice.NotHeld
        return PublishedSigningKeyChoice.Selected(match)
    }
}
