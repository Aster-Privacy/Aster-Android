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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.UploadPrekeyBundleRequest
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore

private const val UPLOADED_FLAG_PREFS = "ratchet_bootstrap"
private const val UPLOADED_FLAG_PREFIX = "uploaded_"

@Singleton
class RatchetBootstrapService @Inject constructor(
    private val session_key_store: SessionKeyStore,
    private val ratchet_api: RatchetApi,
    private val keys_api: KeysApi,
    @ApplicationContext private val context: Context,
) {

    private val in_flight = AtomicBoolean(false)

    private data class LocalRatchetKeys(
        val identity_public_b64: String,
        val signed_prekey_public_b64: String,
    )

    suspend fun bootstrap_if_needed() {
        if (!in_flight.compareAndSet(false, true)) {
            debug_log("skipped: already in flight")
            return
        }
        try {
            val user_id = session_key_store.get_user_id()
            if (user_id == null) {
                debug_log("aborted: no user_id in session key store")
                return
            }
            val keys = ensure_local_ratchet_keys()
            if (keys == null) {
                debug_log("aborted: ensure_local_ratchet_keys returned null")
                return
            }
            debug_log("local ratchet keys ready for user $user_id")
            persist_ratchet_identity_to_vault_if_needed()

            if (already_uploaded(user_id)) {
                debug_log("skipped upload: already uploaded for user $user_id")
                return
            }

            val signature = signed_prekey_signature(
                identity_public_b64 = keys.identity_public_b64,
                signed_prekey_public_b64 = keys.signed_prekey_public_b64,
            )

            val uploaded = runCatching {
                ratchet_api.upload_prekey_bundle(
                    UploadPrekeyBundleRequest(
                        kem_identity_key = keys.identity_public_b64,
                        signed_prekey = keys.signed_prekey_public_b64,
                        signed_prekey_signature = signature,
                        one_time_prekeys = emptyList(),
                        expected_version = null,
                    ),
                )
            }.onFailure { debug_log("upload_prekey_bundle threw: ${it.javaClass.simpleName}: ${it.message}") }
                .getOrDefault(false)

            debug_log("upload_prekey_bundle result=$uploaded")
            if (uploaded) {
                mark_uploaded(user_id)
            }
        } finally {
            in_flight.set(false)
        }
    }

    private fun debug_log(message: String) {
        if (org.astermail.android.BuildConfig.DEBUG) {
            android.util.Log.w("RatchetBootstrap", message)
        }
    }

    private fun ensure_local_ratchet_keys(): LocalRatchetKeys? {
        val local_identity_jwk = session_key_store.get_ratchet_identity_jwk()
        val local_spk_jwk = session_key_store.get_ratchet_signed_prekey_jwk()
        val local_spk_pub = session_key_store.get_ratchet_signed_prekey_public_b64()
        if (local_identity_jwk != null && local_spk_jwk != null && local_spk_pub != null) {
            val identity_public_b64 = runCatching {
                RatchetCrypto.b64_encode(RatchetCrypto.p256_public_raw_from_private_jwk(local_identity_jwk))
            }.getOrNull() ?: return null
            return LocalRatchetKeys(identity_public_b64, local_spk_pub)
        }

        when (val vault_state = read_vault_ratchet_identity()) {
            is VaultRatchetIdentity.Present -> {
                val identity_public_b64 = runCatching {
                    RatchetCrypto.b64_encode(RatchetCrypto.p256_public_raw_from_private_jwk(vault_state.identity_jwk))
                }.getOrNull() ?: return null
                session_key_store.put_ratchet_keys(
                    identity_jwk = vault_state.identity_jwk,
                    identity_public_b64 = identity_public_b64,
                    signed_prekey_jwk = vault_state.signed_prekey_jwk,
                    signed_prekey_public_b64 = vault_state.signed_prekey_public_b64,
                )
                return LocalRatchetKeys(identity_public_b64, vault_state.signed_prekey_public_b64)
            }
            VaultRatchetIdentity.Unavailable -> return null
            VaultRatchetIdentity.Empty -> Unit
        }

        val identity_pair = RatchetCrypto.generate_p256_keypair()
        val signed_prekey_pair = RatchetCrypto.generate_p256_keypair()

        val identity_jwk = RatchetCrypto.p256_private_to_jwk(identity_pair.private_key)
        val signed_prekey_jwk = RatchetCrypto.p256_private_to_jwk(signed_prekey_pair.private_key)
        val identity_public_b64 = RatchetCrypto.b64_encode(identity_pair.public_raw)
        val signed_prekey_public_b64 = RatchetCrypto.b64_encode(signed_prekey_pair.public_raw)

        session_key_store.put_ratchet_keys(
            identity_jwk = identity_jwk,
            identity_public_b64 = identity_public_b64,
            signed_prekey_jwk = signed_prekey_jwk,
            signed_prekey_public_b64 = signed_prekey_public_b64,
        )

        return LocalRatchetKeys(identity_public_b64, signed_prekey_public_b64)
    }

    private sealed class VaultRatchetIdentity {
        data class Present(
            val identity_jwk: String,
            val signed_prekey_jwk: String,
            val signed_prekey_public_b64: String,
        ) : VaultRatchetIdentity()
        object Empty : VaultRatchetIdentity()
        object Unavailable : VaultRatchetIdentity()
    }

    private fun read_vault_ratchet_identity(): VaultRatchetIdentity {
        val (encrypted_vault_b64, vault_nonce_b64) = session_key_store.get_encrypted_vault()
            ?: return VaultRatchetIdentity.Unavailable
        val passphrase = session_key_store.get_passphrase() ?: return VaultRatchetIdentity.Unavailable

        val vault_json = runCatching {
            val plain = CryptoNative.decrypt_vault_with_password(
                base64_decode(encrypted_vault_b64),
                base64_decode(vault_nonce_b64),
                passphrase,
            )
            val json = org.json.JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            json
        }.getOrNull() ?: return VaultRatchetIdentity.Unavailable

        val identity_jwk = vault_json.optString("ratchet_identity_key", "")
        val spk_jwk = vault_json.optString("ratchet_signed_prekey", "")
        val spk_pub = vault_json.optString("ratchet_signed_prekey_public", "")
        if (identity_jwk.isNotBlank() && spk_jwk.isNotBlank() && spk_pub.isNotBlank()) {
            return VaultRatchetIdentity.Present(identity_jwk, spk_jwk, spk_pub)
        }
        return VaultRatchetIdentity.Empty
    }

    private suspend fun persist_ratchet_identity_to_vault_if_needed() {
        val identity_jwk = session_key_store.get_ratchet_identity_jwk() ?: return
        val spk_jwk = session_key_store.get_ratchet_signed_prekey_jwk() ?: return
        val spk_pub = session_key_store.get_ratchet_signed_prekey_public_b64() ?: return

        val (encrypted_vault_b64, vault_nonce_b64) = session_key_store.get_encrypted_vault() ?: return
        val passphrase = session_key_store.get_passphrase() ?: return

        val vault_json = runCatching {
            val plain = CryptoNative.decrypt_vault_with_password(
                base64_decode(encrypted_vault_b64),
                base64_decode(vault_nonce_b64),
                passphrase,
            )
            val json = org.json.JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            json
        }.getOrNull() ?: return

        val already_current = vault_json.optString("ratchet_identity_key", "") == identity_jwk &&
            vault_json.optString("ratchet_signed_prekey", "") == spk_jwk &&
            vault_json.optString("ratchet_signed_prekey_public", "") == spk_pub
        if (already_current) return

        vault_json.put("ratchet_identity_key", identity_jwk)
        vault_json.put("ratchet_signed_prekey", spk_jwk)
        vault_json.put("ratchet_signed_prekey_public", spk_pub)

        val updated_plain = vault_json.toString().toByteArray(Charsets.UTF_8)
        val sealed = runCatching {
            CryptoNative.encrypt_vault_with_password(updated_plain, passphrase)
        }.getOrNull()
        updated_plain.fill(0)
        if (sealed == null) return

        val new_ct_b64 = base64_encode(sealed.encrypted_vault)
        val new_nonce_b64 = base64_encode(sealed.vault_nonce)

        val ok = runCatching {
            keys_api.update_vault(new_ct_b64, new_nonce_b64, session_key_store.get_user_id())
        }.getOrDefault(false)
        if (!ok) return

        session_key_store.put_encrypted_vault(new_ct_b64, new_nonce_b64)
    }

    private fun signed_prekey_signature(identity_public_b64: String, signed_prekey_public_b64: String): String {
        val input = (identity_public_b64 + signed_prekey_public_b64).toByteArray(Charsets.UTF_8)
        return RatchetCrypto.b64_encode(RatchetCrypto.sha256(input))
    }

    private fun already_uploaded(user_id: String): Boolean {
        val prefs = context.getSharedPreferences(UPLOADED_FLAG_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(UPLOADED_FLAG_PREFIX + user_id, false)
    }

    private fun mark_uploaded(user_id: String) {
        val prefs = context.getSharedPreferences(UPLOADED_FLAG_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(UPLOADED_FLAG_PREFIX + user_id, true).apply()
    }

    private fun base64_decode(s: String): ByteArray = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
    private fun base64_encode(bytes: ByteArray): String = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
}
