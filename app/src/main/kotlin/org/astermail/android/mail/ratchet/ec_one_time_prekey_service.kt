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

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import org.astermail.android.api.ratchet.EcPrekeyStatusResponse
import org.astermail.android.api.ratchet.EcPrekeyUpload
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.UploadEcPrekeysRequest
import org.astermail.android.crypto.ratchet.RatchetCrypto

const val EC_PREKEY_BATCH_MAX = 50
const val EC_PREKEY_RECHECK_MS = 6L * 60 * 60 * 1000
const val EC_KEY_ID_ATTEMPTS = 8

fun planned_ec_prekey_upload_count(status: EcPrekeyStatusResponse?): Int {
    if (status == null || status.accepted != true) return 0
    val available = status.available ?: return 0
    val target = status.target ?: return 0
    val max_upload = status.max_upload ?: return 0
    if (available < 0 || target <= 0 || max_upload <= 0) return 0
    return maxOf(0, minOf(target - available, max_upload, EC_PREKEY_BATCH_MAX))
}

fun ec_one_time_key_id_from_bytes(bytes: ByteArray): Int {
    require(bytes.size == 4) { "key id needs four bytes" }
    val value = ((bytes[0].toInt() and 0xff) shl 24) or
        ((bytes[1].toInt() and 0xff) shl 16) or
        ((bytes[2].toInt() and 0xff) shl 8) or
        (bytes[3].toInt() and 0xff)
    val masked = value and X3dh.ONE_TIME_PREKEY_ID_MAX
    return if (masked == 0) 1 else masked
}

class EcOneTimePrekeyService(
    private val store: EcOneTimePrekeyStore,
    private val ratchet_api: RatchetApi,
    private val sync_key: () -> ByteArray?,
    private val now_ms: () -> Long = { System.currentTimeMillis() },
    private val random_key_id: () -> Int = { ec_one_time_key_id_from_bytes(RatchetCrypto.random_bytes(4)) },
) {

    private val in_progress = AtomicBoolean(false)

    @Volatile
    private var not_accepted_until = 0L

    fun reset() {
        not_accepted_until = 0L
    }

    private fun allocate_key_id(taken: MutableSet<Int>): Int? {
        repeat(EC_KEY_ID_ATTEMPTS) {
            val candidate = random_key_id()
            if (X3dh.is_valid_one_time_prekey_id(candidate) &&
                candidate !in taken &&
                !store.has_local(candidate)
            ) {
                taken.add(candidate)
                return candidate
            }
        }
        return null
    }

    private fun generate(key_id: Int, wrapping_key: ByteArray): EcPrekeyUpload? {
        val pair = RatchetCrypto.generate_p256_keypair()
        val scalar = RatchetCrypto.private_to_raw_d(pair.private_key)
        return try {
            val wrapped = EcOneTimeSecretWrap.wrap(scalar, wrapping_key) ?: return null
            if (!store.save(key_id, scalar)) return null
            EcPrekeyUpload(
                key_id = key_id,
                public_key = RatchetCrypto.b64_encode(pair.public_raw),
                encrypted_secret = wrapped.encrypted_secret,
                secret_nonce = wrapped.secret_nonce,
            )
        } finally {
            scalar.fill(0)
        }
    }

    suspend fun replenish(): Int {
        if (!in_progress.compareAndSet(false, true)) return 0
        val saved_ids = mutableListOf<Int>()
        var wrapping_key: ByteArray? = null
        try {
            if (now_ms() < not_accepted_until) return 0
            if (!store.storage_available()) return 0

            val status = ratchet_api.fetch_ec_prekey_status()
            val wanted = planned_ec_prekey_upload_count(status)
            if (status?.accepted != true) {
                not_accepted_until = now_ms() + EC_PREKEY_RECHECK_MS
                return 0
            }
            if (wanted <= 0) return 0

            wrapping_key = sync_key() ?: return 0
            val taken = mutableSetOf<Int>()
            val prekeys = mutableListOf<EcPrekeyUpload>()
            for (index in 0 until wanted) {
                val key_id = allocate_key_id(taken) ?: break
                val prekey = generate(key_id, wrapping_key) ?: break
                saved_ids.add(key_id)
                prekeys.add(prekey)
            }
            if (prekeys.isEmpty()) return 0

            if (!ratchet_api.upload_ec_prekeys(UploadEcPrekeysRequest(prekeys))) {
                saved_ids.forEach { store.discard_local(it) }
                return 0
            }
            return prekeys.size
        } catch (cancelled: CancellationException) {
            saved_ids.forEach { store.discard_local(it) }
            throw cancelled
        } catch (_: Throwable) {
            saved_ids.forEach { store.discard_local(it) }
            return 0
        } finally {
            wrapping_key?.fill(0)
            in_progress.set(false)
        }
    }
}
