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

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import org.astermail.android.api.ratchet.PqSecretResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore

const val EC_ONE_TIME_SCALAR_LEN = 32
const val EC_ONE_TIME_NONCE_LEN = 12
const val EC_ONE_TIME_MISS_TTL_MS = 10L * 60 * 1000
const val EC_ONE_TIME_MISS_MAX = 500

interface EcOneTimeSecretStorage {
    fun available(): Boolean
    fun put(key_id: Int, scalar: ByteArray): Boolean
    fun get(key_id: Int): ByteArray?
    fun remove(key_id: Int)
}

class SessionEcOneTimeSecretStorage(
    private val session_key_store: SessionKeyStore,
) : EcOneTimeSecretStorage {
    override fun available(): Boolean = session_key_store.ec_one_time_storage_available()

    override fun put(key_id: Int, scalar: ByteArray): Boolean =
        session_key_store.put_ec_one_time_secret(key_id, scalar)

    override fun get(key_id: Int): ByteArray? = session_key_store.get_ec_one_time_secret(key_id)

    override fun remove(key_id: Int) = session_key_store.remove_ec_one_time_secret(key_id)
}

data class WrappedEcOneTimeSecret(
    val encrypted_secret: String,
    val secret_nonce: String,
)

object EcOneTimeSecretWrap {

    fun wrap(scalar: ByteArray, sync_key: ByteArray?): WrappedEcOneTimeSecret? {
        if (sync_key == null || scalar.size != EC_ONE_TIME_SCALAR_LEN) return null
        return try {
            val nonce = RatchetCrypto.random_bytes(EC_ONE_TIME_NONCE_LEN)
            val sealed = RatchetCrypto.aes_gcm_encrypt(scalar, sync_key, nonce, null)
            WrappedEcOneTimeSecret(RatchetCrypto.b64_encode(sealed), RatchetCrypto.b64_encode(nonce))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    fun unwrap(encrypted_secret: String, secret_nonce: String, candidates: List<ByteArray>): ByteArray? {
        val sealed = runCatching { RatchetCrypto.b64_decode(encrypted_secret) }.getOrNull() ?: return null
        val nonce = runCatching { RatchetCrypto.b64_decode(secret_nonce) }.getOrNull() ?: return null
        if (nonce.size != EC_ONE_TIME_NONCE_LEN) return null
        for (candidate in candidates) {
            val plain = try {
                RatchetCrypto.aes_gcm_decrypt(sealed, candidate, nonce, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            } ?: continue
            if (plain.size == EC_ONE_TIME_SCALAR_LEN) return plain
            plain.fill(0)
        }
        return null
    }
}

class EcOneTimePrekeyStore(
    private val storage: EcOneTimeSecretStorage,
    private val ratchet_api: RatchetApi,
    private val sync_key_candidates: () -> List<ByteArray>,
    private val now_ms: () -> Long = { System.currentTimeMillis() },
) {

    private val missed_at = ConcurrentHashMap<Int, Long>()

    fun storage_available(): Boolean = runCatching { storage.available() }.getOrDefault(false)

    fun has_local(key_id: Int): Boolean =
        X3dh.is_valid_one_time_prekey_id(key_id) && runCatching { storage.get(key_id) }.getOrNull() != null

    fun save(key_id: Int, scalar: ByteArray): Boolean {
        if (!X3dh.is_valid_one_time_prekey_id(key_id)) return false
        if (scalar.size != EC_ONE_TIME_SCALAR_LEN) return false
        val saved = runCatching { storage.put(key_id, scalar) }.getOrDefault(false)
        if (saved) missed_at.remove(key_id)
        return saved
    }

    fun discard_local(key_id: Int) {
        runCatching { storage.remove(key_id) }
    }

    fun clear_missing() {
        missed_at.clear()
    }

    private fun recently_missed(key_id: Int): Boolean {
        val at = missed_at[key_id] ?: return false
        if (now_ms() - at in 0 until EC_ONE_TIME_MISS_TTL_MS) return true
        missed_at.remove(key_id)
        return false
    }

    private fun mark_missed(key_id: Int) {
        if (missed_at.size >= EC_ONE_TIME_MISS_MAX) missed_at.clear()
        missed_at[key_id] = now_ms()
    }

    suspend fun load(key_id: Int?): ByteArray? {
        if (key_id == null || !X3dh.is_valid_one_time_prekey_id(key_id)) return null
        val local = runCatching { storage.get(key_id) }.getOrNull()
        if (local != null && local.size == EC_ONE_TIME_SCALAR_LEN) return local
        if (recently_missed(key_id)) return null
        return try {
            val fetched = fetch_remote(key_id)
            if (fetched == null) {
                mark_missed(key_id)
                null
            } else {
                runCatching { save(key_id, fetched) }
                fetched
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            mark_missed(key_id)
            null
        }
    }

    suspend fun load_private_jwk(key_id: Int?): String? {
        val scalar = load(key_id) ?: return null
        return try {
            RatchetCrypto.p256_private_to_jwk(RatchetCrypto.raw_d_to_private(scalar))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } finally {
            scalar.fill(0)
        }
    }

    suspend fun consume(key_id: Int?) {
        if (key_id == null || !X3dh.is_valid_one_time_prekey_id(key_id)) return
        discard_local(key_id)
        try {
            ratchet_api.delete_ec_prekey(key_id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        }
    }

    private suspend fun fetch_remote(key_id: Int): ByteArray? {
        val response: PqSecretResponse = ratchet_api.fetch_ec_prekey_secret(key_id) ?: return null
        val candidates = sync_key_candidates()
        return try {
            EcOneTimeSecretWrap.unwrap(response.encrypted_secret, response.secret_nonce, candidates)
        } finally {
            candidates.forEach { it.fill(0) }
        }
    }
}
