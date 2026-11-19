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

import org.astermail.android.crypto.ratchet.RatchetCrypto

const val ONE_TIME_PREKEY_GRACE_MS = 6L * 60L * 60L * 1000L

enum class OneTimePrekeyVerdict { OFFERED, NEVER_OFFERED, GRACE, WITHHELD }

interface OneTimePrekeyPeerStore {
    suspend fun one_time_prekey_grace_at(peer_tag: String): Long?
    suspend fun record_one_time_prekey_grace_at(peer_tag: String, grace_at: Long)
}

class OneTimePrekeyWithheldException(
    recipient: String?,
) : RatchetEncryptionException(recipient, "recipient one-time prekey missing")

fun one_time_prekey_peer_tag(identity_raw: ByteArray): String =
    RatchetCrypto.sha256(identity_raw)
        .take(16)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

suspend fun judge_one_time_prekey_offer(
    store: OneTimePrekeyPeerStore,
    peer_tag: String,
    offered: Boolean,
    now_ms: Long,
): OneTimePrekeyVerdict {
    val grace_at = store.one_time_prekey_grace_at(peer_tag)
    if (offered) {
        if (grace_at == null) store.record_one_time_prekey_grace_at(peer_tag, 0L)
        return OneTimePrekeyVerdict.OFFERED
    }
    if (grace_at == null) return OneTimePrekeyVerdict.NEVER_OFFERED
    if (grace_at > now_ms || now_ms - grace_at >= ONE_TIME_PREKEY_GRACE_MS) {
        store.record_one_time_prekey_grace_at(peer_tag, now_ms)
        return OneTimePrekeyVerdict.GRACE
    }
    return OneTimePrekeyVerdict.WITHHELD
}
