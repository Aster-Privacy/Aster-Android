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

import kotlinx.coroutines.CancellationException
import org.astermail.android.api.ratchet.PqSecretResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.StorePqSecretRequest
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore

const val PREKEY_SEAL_LABEL = "aster-opk-seal-v1"
const val PREKEY_SEAL_KEY_LEN = 32

enum class PrekeySealKind(val label: String) {
    EC("ec"),
    PQ("pq"),
}

fun prekey_seal_aad(kind: PrekeySealKind, key_id: Int): ByteArray =
    "$PREKEY_SEAL_LABEL:${kind.label}:$key_id".toByteArray(Charsets.UTF_8)

class PrekeySeal private constructor(
    private val key: ByteArray?,
) {

    fun has_key(): Boolean = key != null

    fun seal(kind: PrekeySealKind, key_id: Int, plaintext: ByteArray): WrappedEcOneTimeSecret? {
        val seal_key = key ?: return null
        return try {
            val nonce = RatchetCrypto.random_bytes(EC_ONE_TIME_NONCE_LEN)
            val sealed = RatchetCrypto.aes_gcm_encrypt(plaintext, seal_key, nonce, prekey_seal_aad(kind, key_id))
            WrappedEcOneTimeSecret(RatchetCrypto.b64_encode(sealed), RatchetCrypto.b64_encode(nonce))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    fun open(kind: PrekeySealKind, key_id: Int, encrypted_secret: String, secret_nonce: String): ByteArray? {
        val seal_key = key ?: return null
        val sealed = runCatching { RatchetCrypto.b64_decode(encrypted_secret) }.getOrNull() ?: return null
        val nonce = runCatching { RatchetCrypto.b64_decode(secret_nonce) }.getOrNull() ?: return null
        if (nonce.size != EC_ONE_TIME_NONCE_LEN) return null
        return try {
            RatchetCrypto.aes_gcm_decrypt(sealed, seal_key, nonce, prekey_seal_aad(kind, key_id))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    fun wipe() {
        key?.fill(0)
    }

    companion object {
        val NONE: PrekeySeal get() = PrekeySeal(null)

        fun from_raw(raw: ByteArray?): PrekeySeal {
            if (raw == null || raw.size != PREKEY_SEAL_KEY_LEN) return PrekeySeal(null)
            val derived = RatchetCrypto.hkdf_sha256(
                ikm = raw,
                salt = ByteArray(32),
                info = PREKEY_SEAL_LABEL.toByteArray(Charsets.UTF_8),
                length = 32,
            )
            return PrekeySeal(derived)
        }

        fun from_store(store: SessionKeyStore): PrekeySeal {
            val raw = store.get_prekey_seal_key()
            return try {
                from_raw(raw)
            } finally {
                raw?.fill(0)
            }
        }
    }
}

class OpenedPrekeySecret(val plain: ByteArray, val legacy: Boolean)

fun open_prekey_secret(
    seal: PrekeySeal,
    kind: PrekeySealKind,
    key_id: Int,
    response: PqSecretResponse,
    legacy_candidates: () -> List<ByteArray>,
): ByteArray? = open_prekey_secret_row(seal, kind, key_id, response, legacy_candidates)?.plain

suspend fun open_and_reseal_pq_secret(
    ratchet_api: RatchetApi,
    seal: PrekeySeal,
    key_id: Int,
    response: PqSecretResponse,
    writes_ready: Boolean,
    legacy_candidates: () -> List<ByteArray>,
): ByteArray? {
    val opened = open_prekey_secret_row(seal, PrekeySealKind.PQ, key_id, response, legacy_candidates) ?: return null
    if (!opened.legacy || !writes_ready) return opened.plain
    val sealed = seal.seal(PrekeySealKind.PQ, key_id, opened.plain) ?: return opened.plain
    try {
        ratchet_api.store_pq_secret(StorePqSecretRequest(key_id, sealed.encrypted_secret, sealed.secret_nonce))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
    }
    return opened.plain
}

fun open_prekey_secret_row(
    seal: PrekeySeal,
    kind: PrekeySealKind,
    key_id: Int,
    response: PqSecretResponse,
    legacy_candidates: () -> List<ByteArray>,
): OpenedPrekeySecret? {
    if (response.key_id != key_id) return null
    seal.open(kind, key_id, response.encrypted_secret, response.secret_nonce)?.let { return OpenedPrekeySecret(it, false) }
    val sealed = runCatching { RatchetCrypto.b64_decode(response.encrypted_secret) }.getOrNull() ?: return null
    val nonce = runCatching { RatchetCrypto.b64_decode(response.secret_nonce) }.getOrNull() ?: return null
    if (nonce.size != EC_ONE_TIME_NONCE_LEN) return null
    val candidates = legacy_candidates()
    try {
        for (candidate in candidates) {
            if (RatchetStateStore.is_public_state_key(candidate)) continue
            val plain = try {
                RatchetCrypto.aes_gcm_decrypt(sealed, candidate, nonce, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (plain != null) return OpenedPrekeySecret(plain, true)
        }
        return null
    } finally {
        candidates.forEach { it.fill(0) }
    }
}
