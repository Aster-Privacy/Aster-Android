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
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.json.JSONArray
import org.json.JSONObject

const val SIGNED_PREKEY_ROTATION_ENABLED = false
const val SIGNED_PREKEY_ROTATION_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000
const val SIGNED_PREKEY_RETAINED_LIMIT = max_retained_ratchet_key_sets

data class FreshSignedPrekey(
    val signed_prekey_jwk: String,
    val signed_prekey_public: String,
)

enum class SignedPrekeyRotationOutcome { DISABLED, NOT_DUE, ROTATED, FAILED }

class SignedPrekeyRotationDeps(
    val now: () -> Long,
    val last_rotated_at: () -> Long?,
    val vault: () -> JSONObject?,
    val persist: suspend (next: JSONObject, base: JSONObject) -> JSONObject?,
    val publish: suspend (vault: JSONObject) -> Boolean,
    val record_rotation: (rotated_at: Long) -> Unit,
    val enabled: Boolean = SIGNED_PREKEY_ROTATION_ENABLED,
    val generate: suspend () -> FreshSignedPrekey = { generate_signed_prekey() },
)

fun signed_prekey_rotation_due(
    last_rotated_at: Long?,
    now: Long,
    enabled: Boolean = SIGNED_PREKEY_ROTATION_ENABLED,
): Boolean {
    if (!enabled) return false
    if (last_rotated_at == null) return true
    val elapsed = now - last_rotated_at
    return elapsed < 0 || elapsed >= SIGNED_PREKEY_ROTATION_INTERVAL_MS
}

fun generate_signed_prekey(): FreshSignedPrekey {
    val pair = RatchetCrypto.generate_p256_keypair()
    return FreshSignedPrekey(
        signed_prekey_jwk = RatchetCrypto.p256_private_to_jwk(pair.private_key),
        signed_prekey_public = RatchetCrypto.b64_encode(pair.public_raw),
    )
}

fun apply_signed_prekey_rotation(vault: JSONObject, fresh: FreshSignedPrekey): JSONObject? {
    val identity = vault.optString("ratchet_identity_key", "").trim()
    if (identity.isBlank()) return null
    if (fresh.signed_prekey_jwk.isBlank() || fresh.signed_prekey_public.isBlank()) return null
    if (fresh.signed_prekey_public == vault.optString("ratchet_signed_prekey_public", "")) return null

    val next = JSONObject(vault.toString())
    retain_vault_key_set(next, identity, fresh.signed_prekey_jwk)
    next.optJSONArray("ratchet_previous_keys")?.let { retained ->
        if (retained.length() > SIGNED_PREKEY_RETAINED_LIMIT) {
            val capped = JSONArray()
            for (index in 0 until SIGNED_PREKEY_RETAINED_LIMIT) capped.put(retained.get(index))
            next.put("ratchet_previous_keys", capped)
        }
    }
    next.put("ratchet_signed_prekey", fresh.signed_prekey_jwk)
    next.put("ratchet_signed_prekey_public", fresh.signed_prekey_public)
    return next
}

suspend fun rotate_signed_prekey_if_due(deps: SignedPrekeyRotationDeps): SignedPrekeyRotationOutcome {
    if (!deps.enabled) return SignedPrekeyRotationOutcome.DISABLED
    val now = deps.now()
    if (!signed_prekey_rotation_due(deps.last_rotated_at(), now, deps.enabled)) {
        return SignedPrekeyRotationOutcome.NOT_DUE
    }
    return try {
        val vault = deps.vault() ?: return SignedPrekeyRotationOutcome.FAILED
        val fresh = deps.generate()
        val next = apply_signed_prekey_rotation(vault, fresh) ?: return SignedPrekeyRotationOutcome.FAILED
        val persisted = deps.persist(next, vault) ?: return SignedPrekeyRotationOutcome.FAILED
        if (!deps.publish(persisted)) return SignedPrekeyRotationOutcome.FAILED
        deps.record_rotation(now)
        SignedPrekeyRotationOutcome.ROTATED
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        SignedPrekeyRotationOutcome.FAILED
    }
}
