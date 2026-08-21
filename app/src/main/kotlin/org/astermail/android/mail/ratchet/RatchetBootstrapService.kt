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
import org.astermail.android.api.keys.CurrentVaultResult
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.UploadPrekeyBundleRequest
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore

private const val UPLOADED_FLAG_PREFS = "ratchet_bootstrap"
private const val UPLOADED_FLAG_PREFIX = "uploaded_"
private const val UPLOADED_GENERATION_PREFIX = "uploaded_generation_"
private const val BUNDLE_UPLOAD_GENERATION = 3

@Singleton
class RatchetBootstrapService @Inject constructor(
    private val session_key_store: SessionKeyStore,
    private val ratchet_api: RatchetApi,
    private val keys_api: KeysApi,
    @ApplicationContext private val context: Context,
) {

    private val in_flight = AtomicBoolean(false)

    private val capability_reporter by lazy {
        EnvelopeCapabilityReporter(
            store = SharedPrefsEnvelopeCapabilityStore(context),
            ratchet_api = ratchet_api,
        )
    }

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
            if (!sync_vault_with_server()) {
                debug_log("aborted: could not verify the server vault")
                return
            }
            reconcile_session_keys_with_vault()
            val keys = ensure_local_ratchet_keys()
            if (keys == null) {
                debug_log("aborted: ensure_local_ratchet_keys returned null")
                return
            }
            debug_log("local ratchet keys ready for user $user_id")
            val pq_identity_generated = persist_ratchet_identity_to_vault_if_needed()

            val capability = runCatching {
                capability_reporter.report_if_due(user_id, keys.identity_public_b64)
            }
                .onFailure { debug_log("report_envelope_capability threw: ${it.javaClass.simpleName}: ${it.message}") }
                .getOrNull()
            if (capability != null) {
                debug_log(
                    "envelope capability reported: min=${capability.min_supported_marker} " +
                        "pq=${capability.pq_hybrid_enabled} identity=${capability.identity_verified}",
                )
            }

            val identity_unconfirmed = capability != null && !capability.identity_verified

            val stored_generation = uploaded_generation(user_id)
            if (stored_generation >= BUNDLE_UPLOAD_GENERATION &&
                !pq_identity_generated &&
                !identity_unconfirmed
            ) {
                debug_log("skipped upload: already uploaded for user $user_id")
                return
            }

            val pq_identity_public = session_key_store.get_ratchet_pq_identity_public()
            if (stored_generation > 0 && pq_identity_public.isNullOrBlank()) {
                debug_log("skipped upload: no local pq identity to publish for user $user_id")
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
                        pq_kem_public_key = pq_identity_public?.takeIf { it.isNotBlank() },
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

    private suspend fun sync_vault_with_server(): Boolean {
        val result = runCatching { keys_api.fetch_current_vault() }
            .getOrDefault(CurrentVaultResult.Unavailable)
        return when (result) {
            is CurrentVaultResult.Available -> adopt_server_vault(result.encrypted_vault, result.vault_nonce)
            CurrentVaultResult.Missing -> true
            CurrentVaultResult.Unavailable -> false
        }
    }

    private fun adopt_server_vault(encrypted_vault_b64: String, vault_nonce_b64: String): Boolean {
        val cached = session_key_store.get_encrypted_vault()
        if (cached != null && cached.first == encrypted_vault_b64 && cached.second == vault_nonce_b64) {
            return true
        }
        val passphrase = session_key_store.get_passphrase() ?: return false
        val decryptable = runCatching {
            val plain = CryptoNative.decrypt_vault_with_password(
                base64_decode(encrypted_vault_b64),
                base64_decode(vault_nonce_b64),
                passphrase,
            )
            plain.fill(0)
            true
        }.getOrDefault(false)
        if (!decryptable) {
            debug_log("server vault did not decrypt with the stored passphrase")
            return false
        }
        session_key_store.put_encrypted_vault(encrypted_vault_b64, vault_nonce_b64)
        debug_log("adopted newer server vault into the session cache")
        return true
    }

    private suspend fun reconcile_session_keys_with_vault() {
        val vault_state = read_vault_ratchet_identity()
        if (vault_state !is VaultRatchetIdentity.Present) return
        val local_identity_jwk = session_key_store.get_ratchet_identity_jwk()
        if (local_identity_jwk == null || local_identity_jwk == vault_state.identity_jwk) return

        retain_replaced_local_identity_in_vault(local_identity_jwk)

        val identity_public_b64 = runCatching {
            RatchetCrypto.b64_encode(RatchetCrypto.p256_public_raw_from_private_jwk(vault_state.identity_jwk))
        }.getOrNull() ?: return
        val vault_spk_jwk = vault_state.signed_prekey_jwk
        val vault_spk_public = vault_state.signed_prekey_public_b64
        if (vault_spk_jwk != null && vault_spk_public != null) {
            session_key_store.put_ratchet_keys(
                identity_jwk = vault_state.identity_jwk,
                identity_public_b64 = identity_public_b64,
                signed_prekey_jwk = vault_spk_jwk,
                signed_prekey_public_b64 = vault_spk_public,
            )
        } else {
            val fresh_prekey = RatchetCrypto.generate_p256_keypair()
            session_key_store.put_ratchet_keys(
                identity_jwk = vault_state.identity_jwk,
                identity_public_b64 = identity_public_b64,
                signed_prekey_jwk = RatchetCrypto.p256_private_to_jwk(fresh_prekey.private_key),
                signed_prekey_public_b64 = RatchetCrypto.b64_encode(fresh_prekey.public_raw),
            )
        }
        adopt_vault_pq_identity()
        debug_log("adopted the vault ratchet identity over stale session keys")
    }

    private fun adopt_vault_pq_identity() {
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
        val pq_secret = vault_json.optString("ratchet_pq_identity_key", "")
            .ifBlank {
                vault_json.optString("ratchet_pq_identity_seed", "")
                    .takeIf { it.isNotBlank() }
                    ?.let { expand_pq_identity_secret(it) }
                    .orEmpty()
            }
        val pq_public = vault_json.optString("ratchet_pq_identity_public", "")
        if (pq_secret.isNotBlank() && pq_public.isNotBlank()) {
            session_key_store.put_ratchet_pq_identity(pq_secret, pq_public)
        }
    }

    private suspend fun retain_replaced_local_identity_in_vault(replaced_identity_jwk: String) {
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
        if (vault_json.optString("ratchet_identity_key", "") == replaced_identity_jwk) return
        val previous = vault_json.optJSONArray("ratchet_previous_keys") ?: org.json.JSONArray()
        for (i in 0 until previous.length()) {
            val existing = previous.optJSONObject(i)?.optString("ratchet_identity_key", "")
            if (existing == replaced_identity_jwk) return
        }
        val entry = org.json.JSONObject().put("ratchet_identity_key", replaced_identity_jwk)
        runCatching { RatchetCrypto.p256_public_raw_from_private_jwk(replaced_identity_jwk) }
            .getOrNull()
            ?.let { entry.put("ratchet_identity_public", base64_encode(it)) }
        val vault_pq_secret = vault_json.optString("ratchet_pq_identity_key", "")
        session_key_store.get_ratchet_pq_identity_secret()
            ?.takeIf { it.isNotBlank() && it != vault_pq_secret }
            ?.let { entry.put("ratchet_pq_identity_key", it) }
        val merged = org.json.JSONArray().put(entry)
        for (i in 0 until minOf(previous.length(), 31)) {
            merged.put(previous.get(i))
        }
        vault_json.put("ratchet_previous_keys", merged)

        val updated_plain = vault_json.toString().toByteArray(Charsets.UTF_8)
        val sealed = runCatching {
            CryptoNative.encrypt_vault_with_password(updated_plain, passphrase)
        }.getOrNull()
        updated_plain.fill(0)
        if (sealed == null) return

        val new_ct_b64 = base64_encode(sealed.encrypted_vault)
        val new_nonce_b64 = base64_encode(sealed.vault_nonce)
        val ok = runCatching {
            keys_api.update_vault(
                new_ct_b64,
                new_nonce_b64,
                session_key_store.get_user_id(),
                collect_vault_key_fingerprints(vault_json),
            )
        }.getOrDefault(false)
        if (ok) {
            session_key_store.put_encrypted_vault(new_ct_b64, new_nonce_b64)
            debug_log("retained the replaced local ratchet identity in the vault")
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
                val vault_spk_jwk = vault_state.signed_prekey_jwk
                val vault_spk_public = vault_state.signed_prekey_public_b64
                if (vault_spk_jwk != null && vault_spk_public != null) {
                    session_key_store.put_ratchet_keys(
                        identity_jwk = vault_state.identity_jwk,
                        identity_public_b64 = identity_public_b64,
                        signed_prekey_jwk = vault_spk_jwk,
                        signed_prekey_public_b64 = vault_spk_public,
                    )
                    return LocalRatchetKeys(identity_public_b64, vault_spk_public)
                }
                val fresh_prekey = RatchetCrypto.generate_p256_keypair()
                val fresh_prekey_jwk = RatchetCrypto.p256_private_to_jwk(fresh_prekey.private_key)
                val fresh_prekey_public = RatchetCrypto.b64_encode(fresh_prekey.public_raw)
                session_key_store.put_ratchet_keys(
                    identity_jwk = vault_state.identity_jwk,
                    identity_public_b64 = identity_public_b64,
                    signed_prekey_jwk = fresh_prekey_jwk,
                    signed_prekey_public_b64 = fresh_prekey_public,
                )
                return LocalRatchetKeys(identity_public_b64, fresh_prekey_public)
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
            val signed_prekey_jwk: String?,
            val signed_prekey_public_b64: String?,
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

        val keys = parse_vault_ratchet_keys(vault_json)
        val identity_jwk = keys.identity_jwk ?: return VaultRatchetIdentity.Empty
        return if (keys.has_signed_prekey) {
            VaultRatchetIdentity.Present(identity_jwk, keys.signed_prekey_jwk, keys.signed_prekey_public_b64)
        } else {
            VaultRatchetIdentity.Present(identity_jwk, null, null)
        }
    }

    private suspend fun persist_ratchet_identity_to_vault_if_needed(): Boolean {
        val identity_jwk = session_key_store.get_ratchet_identity_jwk() ?: return false
        val spk_jwk = session_key_store.get_ratchet_signed_prekey_jwk() ?: return false
        val spk_pub = session_key_store.get_ratchet_signed_prekey_public_b64() ?: return false

        val (encrypted_vault_b64, vault_nonce_b64) = session_key_store.get_encrypted_vault() ?: return false
        val passphrase = session_key_store.get_passphrase() ?: return false

        val vault_json = runCatching {
            val plain = CryptoNative.decrypt_vault_with_password(
                base64_decode(encrypted_vault_b64),
                base64_decode(vault_nonce_b64),
                passphrase,
            )
            val json = org.json.JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            json
        }.getOrNull() ?: return false

        val vault_identity_jwk = vault_json.optString("ratchet_identity_key", "")
        if (vault_identity_jwk.isNotBlank() && vault_identity_jwk != identity_jwk) {
            debug_log("skipped vault write: the vault holds a different ratchet identity")
            return false
        }

        val identity_current = vault_json.optString("ratchet_identity_key", "") == identity_jwk &&
            vault_json.optString("ratchet_signed_prekey", "") == spk_jwk &&
            vault_json.optString("ratchet_signed_prekey_public", "") == spk_pub

        val generated = generate_pq_identity_into(vault_json)

        if (identity_current && generated == null) return false

        if (!identity_current) {
            retain_replaced_ratchet_identity(vault_json, identity_jwk)
        }

        vault_json.put("ratchet_identity_key", identity_jwk)
        vault_json.put("ratchet_signed_prekey", spk_jwk)
        vault_json.put("ratchet_signed_prekey_public", spk_pub)
        runCatching { RatchetCrypto.p256_public_raw_from_private_jwk(identity_jwk) }
            .getOrNull()
            ?.let { vault_json.put("ratchet_identity_public", base64_encode(it)) }

        val updated_plain = vault_json.toString().toByteArray(Charsets.UTF_8)
        val sealed = runCatching {
            CryptoNative.encrypt_vault_with_password(updated_plain, passphrase)
        }.getOrNull()
        updated_plain.fill(0)
        if (sealed == null) return false

        val new_ct_b64 = base64_encode(sealed.encrypted_vault)
        val new_nonce_b64 = base64_encode(sealed.vault_nonce)

        val ok = runCatching {
            keys_api.update_vault(
                new_ct_b64,
                new_nonce_b64,
                session_key_store.get_user_id(),
                collect_vault_key_fingerprints(vault_json),
            )
        }.getOrDefault(false)
        if (!ok) return false

        session_key_store.put_encrypted_vault(new_ct_b64, new_nonce_b64)

        if (generated == null) return false

        session_key_store.put_ratchet_pq_identity(generated.secret_b64, generated.public_b64)
        debug_log("generated pq identity for user ${session_key_store.get_user_id()}")
        return true
    }

    private fun retain_replaced_ratchet_identity(
        vault_json: org.json.JSONObject,
        new_identity_jwk: String,
    ) {
        val replaced_jwk = vault_json.optString("ratchet_identity_key", "")
        if (replaced_jwk.isBlank() || replaced_jwk == new_identity_jwk) return
        val previous = vault_json.optJSONArray("ratchet_previous_keys") ?: org.json.JSONArray()
        for (i in 0 until previous.length()) {
            val existing = previous.optJSONObject(i)?.optString("ratchet_identity_key", "")
            if (existing == replaced_jwk) return
        }
        val entry = org.json.JSONObject().put("ratchet_identity_key", replaced_jwk)
        vault_json.optString("ratchet_pq_identity_key", "")
            .takeIf { it.isNotBlank() }
            ?.let { entry.put("ratchet_pq_identity_key", it) }
        vault_json.optString("ratchet_pq_identity_seed", "")
            .takeIf { it.isNotBlank() }
            ?.let { entry.put("ratchet_pq_identity_seed", it) }
        val merged = org.json.JSONArray().put(entry)
        for (i in 0 until minOf(previous.length(), 31)) {
            merged.put(previous.get(i))
        }
        vault_json.put("ratchet_previous_keys", merged)
    }

    private data class GeneratedPqIdentity(val secret_b64: String, val public_b64: String)

    private fun generate_pq_identity_into(vault_json: org.json.JSONObject): GeneratedPqIdentity? {
        val existing_public = vault_json.optString("ratchet_pq_identity_public", "")
        val existing_seed = vault_json.optString("ratchet_pq_identity_seed", "")
        val existing_secret = vault_json.optString("ratchet_pq_identity_key", "")
            .ifBlank { existing_seed.takeIf { it.isNotBlank() }?.let { expand_pq_identity_secret(it) }.orEmpty() }
        if (existing_secret.isNotBlank() && existing_public.isNotBlank()) {
            if (session_key_store.get_ratchet_pq_identity_secret().isNullOrBlank()) {
                session_key_store.put_ratchet_pq_identity(existing_secret, existing_public)
            }
            return null
        }

        val pair = runCatching { RatchetCrypto.ml_kem_768_generate_keypair() }
            .onFailure { debug_log("ml_kem keygen threw: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull() ?: return null

        val secret_b64 = RatchetCrypto.b64_encode(pair.secret_key)
        val public_b64 = RatchetCrypto.b64_encode(pair.public_key)
        val seed_b64 = RatchetCrypto.b64_encode(pair.seed)
        pair.secret_key.fill(0)
        pair.seed.fill(0)

        vault_json.put("ratchet_pq_identity_key", secret_b64)
        vault_json.put("ratchet_pq_identity_public", public_b64)
        vault_json.put("ratchet_pq_identity_seed", seed_b64)

        return GeneratedPqIdentity(secret_b64, public_b64)
    }

    private fun signed_prekey_signature(identity_public_b64: String, signed_prekey_public_b64: String): String {
        val pgp_signature = pgp_prekey_signature(identity_public_b64, signed_prekey_public_b64)
        if (pgp_signature != null) {
            return pgp_signature
        }
        val input = (identity_public_b64 + signed_prekey_public_b64).toByteArray(Charsets.UTF_8)
        return RatchetCrypto.b64_encode(RatchetCrypto.sha256(input))
    }

    private fun pgp_prekey_signature(identity_public_b64: String, signed_prekey_public_b64: String): String? {
        val identity_key = session_key_store.get_identity_key() ?: return null
        if (!PrekeyBindingSigner.looks_like_armored_private_key(identity_key)) return null
        val passphrase_bytes = session_key_store.get_passphrase() ?: return null
        val passphrase = String(passphrase_bytes, Charsets.UTF_8).toCharArray()
        return runCatching {
            val armored = PrekeyBindingSigner.sign_cleartext(
                armored_secret_key = identity_key,
                passphrase = passphrase,
                text = PrekeyBindingSigner.canonical_binding(identity_public_b64, signed_prekey_public_b64),
            )
            base64_encode(armored.toByteArray(Charsets.UTF_8))
        }
            .onFailure { debug_log("pgp prekey signature threw: ${it.javaClass.simpleName}: ${it.message}") }
            .also { passphrase.fill(' ') }
            .getOrNull()
    }

    private fun uploaded_generation(user_id: String): Int {
        val prefs = context.getSharedPreferences(UPLOADED_FLAG_PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getInt(UPLOADED_GENERATION_PREFIX + user_id, 0)
        if (stored > 0) {
            return stored
        }
        return if (prefs.getBoolean(UPLOADED_FLAG_PREFIX + user_id, false)) 1 else 0
    }

    private fun mark_uploaded(user_id: String) {
        val prefs = context.getSharedPreferences(UPLOADED_FLAG_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(UPLOADED_FLAG_PREFIX + user_id, true)
            .putInt(UPLOADED_GENERATION_PREFIX + user_id, BUNDLE_UPLOAD_GENERATION)
            .apply()
    }

    private fun base64_decode(s: String): ByteArray = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
    private fun base64_encode(bytes: ByteArray): String = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
}
