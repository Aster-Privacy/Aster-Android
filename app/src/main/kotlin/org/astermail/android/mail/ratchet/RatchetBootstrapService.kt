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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.astermail.android.api.ApiError
import org.astermail.android.api.encryption.EncryptionApi
import org.astermail.android.api.keys.CurrentVaultResult
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.OwnRatchetIdentityLookup
import org.astermail.android.api.ratchet.UploadPrekeyBundleRequest
import org.astermail.android.auth.VaultWriteLock
import org.astermail.android.auth.stored_vault_format
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore

private const val UPLOADED_FLAG_PREFS = "ratchet_bootstrap"
private const val UPLOADED_FLAG_PREFIX = "uploaded_"
private const val UPLOADED_GENERATION_PREFIX = "uploaded_generation_"
private const val BUNDLE_UPLOAD_GENERATION = 5

@Singleton
class RatchetBootstrapService @Inject constructor(
    private val session_key_store: SessionKeyStore,
    private val ratchet_api: RatchetApi,
    private val keys_api: KeysApi,
    private val encryption_api: EncryptionApi,
    private val vault_write_lock: VaultWriteLock,
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

    private val confirmed_user_ids = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )
    private val retry_job_lock = Any()
    private var retry_job: Job? = null
    private var retry_user_id: String? = null
    private var last_scheduled_at_ms: Long = 0L

    fun is_bundle_confirmed(user_id: String): Boolean = confirmed_user_ids.contains(user_id)

    fun schedule_until_published(
        scope: CoroutineScope,
        is_foreground: () -> Boolean,
        bypass_debounce: Boolean = false,
        now_ms: () -> Long = { System.currentTimeMillis() },
    ) {
        val user_id = session_key_store.get_user_id() ?: return
        if (is_bundle_confirmed(user_id)) return
        synchronized(retry_job_lock) {
            val same_user = retry_user_id == user_id
            if (same_user && retry_job?.isActive == true) return
            val now = now_ms()
            if (same_user &&
                !bypass_debounce &&
                now - last_scheduled_at_ms in 0 until RATCHET_BOOTSTRAP_FOREGROUND_DEBOUNCE_MS
            ) {
                return
            }
            retry_job?.cancel()
            retry_user_id = user_id
            last_scheduled_at_ms = now
            retry_job = scope.launch { retry_until_published(user_id, is_foreground) }
        }
    }

    private suspend fun retry_until_published(user_id: String, is_foreground: () -> Boolean) {
        var attempt = 0
        while (session_key_store.get_user_id() == user_id) {
            val outcome = try {
                bootstrap_if_needed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                RatchetBootstrapOutcome.Failed("bootstrap threw ${error.javaClass.simpleName}")
            }
            debug_log("bootstrap attempt $attempt outcome=$outcome")
            if (outcome.bundle_confirmed || !is_foreground()) return
            delay(ratchet_bootstrap_retry_delay_ms(attempt))
            attempt += 1
            if (!is_foreground()) return
        }
    }

    suspend fun bootstrap_if_needed(): RatchetBootstrapOutcome {
        if (!in_flight.compareAndSet(false, true)) {
            debug_log("skipped: already in flight")
            return RatchetBootstrapOutcome.InFlight
        }
        try {
            return vault_write_lock.with_lock { bootstrap_holding_vault_lock() }
        } finally {
            in_flight.set(false)
        }
    }

    private suspend fun bootstrap_holding_vault_lock(): RatchetBootstrapOutcome {
        val user_id = session_key_store.get_user_id()
            ?: return RatchetBootstrapOutcome.Deferred("no user_id in session key store")
        if (!sync_vault_with_server()) {
            return RatchetBootstrapOutcome.Deferred("could not verify the server vault")
        }
        reconcile_session_keys_with_vault()
        val ensured_keys = ensure_local_ratchet_keys()
            ?: return RatchetBootstrapOutcome.Deferred("local ratchet keys unavailable")
        debug_log("local ratchet keys ready for user $user_id")
        val published_keys_changed = persist_ratchet_identity_to_vault_if_needed()
        val keys = ensured_keys.copy(
            signed_prekey_public_b64 = session_key_store.get_ratchet_signed_prekey_public_b64()
                ?.takeIf { it.isNotBlank() }
                ?: ensured_keys.signed_prekey_public_b64,
        )

        val pq_public = session_key_store.get_ratchet_pq_identity_public()
            ?.takeIf { local_pq_identity_ready(session_key_store.get_ratchet_pq_identity_secret(), it) }
            ?: return RatchetBootstrapOutcome.Deferred("no local pq identity yet")

        val published = when (val lookup = lookup_own_published_bundle()) {
            is OwnBundleRead.Found -> lookup.view
            OwnBundleRead.Missing -> null
            OwnBundleRead.Unavailable ->
                return RatchetBootstrapOutcome.Deferred("could not read the published bundle")
        }
        val server_matches = published_bundle_matches_local(
            published,
            keys.identity_public_b64,
            keys.signed_prekey_public_b64,
            pq_public,
        )
        val needs_upload = !server_matches ||
            published_keys_changed ||
            uploaded_generation(user_id) < BUNDLE_UPLOAD_GENERATION

        if (needs_upload) {
            val blocked = upload_bundle(keys, pq_public)
            if (blocked != null) return blocked
            mark_uploaded(user_id)
        } else {
            debug_log("server already holds a matching pq bundle for user $user_id")
        }

        confirmed_user_ids.add(user_id)
        report_capability(user_id, keys.identity_public_b64, pq_public, force = needs_upload)
        return if (needs_upload) RatchetBootstrapOutcome.Published else RatchetBootstrapOutcome.AlreadyPublished
    }

    private suspend fun upload_bundle(keys: LocalRatchetKeys, pq_public: String): RatchetBootstrapOutcome? {
        val vault_confirms_keys = cached_vault_json()?.let {
            vault_holds_ratchet_keys(
                it,
                keys.identity_public_b64,
                keys.signed_prekey_public_b64,
                pq_public,
            )
        } ?: false
        if (!vault_confirms_keys) {
            return RatchetBootstrapOutcome.Deferred("the key vault does not hold the keys to publish")
        }
        val signature = signed_prekey_signature(
            identity_public_b64 = keys.identity_public_b64,
            signed_prekey_public_b64 = keys.signed_prekey_public_b64,
            pq_identity_public_b64 = pq_public,
        ) ?: return RatchetBootstrapOutcome.Deferred("no pgp signature available for the prekey bundle")

        val uploaded = try {
            ratchet_api.upload_prekey_bundle(
                UploadPrekeyBundleRequest(
                    kem_identity_key = keys.identity_public_b64,
                    signed_prekey = keys.signed_prekey_public_b64,
                    signed_prekey_signature = signature,
                    one_time_prekeys = emptyList(),
                    expected_version = null,
                    pq_kem_public_key = pq_public,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return RatchetBootstrapOutcome.Failed("upload_prekey_bundle threw ${error.javaClass.simpleName}")
        }
        debug_log("upload_prekey_bundle result=$uploaded")
        return if (uploaded) null else RatchetBootstrapOutcome.Failed("upload_prekey_bundle rejected")
    }

    private suspend fun report_capability(
        user_id: String,
        identity_public_b64: String,
        pq_public: String,
        force: Boolean,
    ) {
        val capability = try {
            capability_reporter.report_if_due(
                user_id,
                identity_public_b64,
                pq_public,
                !session_key_store.get_ratchet_pq_identity_secret().isNullOrBlank(),
                force,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            debug_log("report_envelope_capability threw: ${error.javaClass.simpleName}")
            null
        }
        if (capability != null) {
            debug_log(
                "envelope capability reported: min=${capability.min_supported_marker} " +
                    "pq=${capability.pq_hybrid_enabled} identity=${capability.identity_verified} " +
                    "pq_attested=${capability.pq_identity_attested}",
            )
        }
    }

    private sealed class OwnBundleRead {
        data class Found(val view: PublishedRatchetBundleView) : OwnBundleRead()
        object Missing : OwnBundleRead()
        object Unavailable : OwnBundleRead()
    }

    private suspend fun lookup_own_published_bundle(): OwnBundleRead {
        val email = session_key_store.get_user_email()?.takeIf { it.contains('@') }
            ?: return OwnBundleRead.Unavailable
        val lookup = try {
            ratchet_api.fetch_own_ratchet_identity(email.substringBefore('@'), email)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            debug_log("own bundle lookup threw: ${error.javaClass.simpleName}")
            return OwnBundleRead.Unavailable
        }
        return when (lookup) {
            is OwnRatchetIdentityLookup.Found -> OwnBundleRead.Found(
                PublishedRatchetBundleView(
                    identity_public_b64 = lookup.identity.kem_identity_key,
                    signed_prekey_public_b64 = lookup.identity.signed_prekey,
                    pq_identity_public_b64 = lookup.identity.pq_kem_public_key,
                ),
            )
            OwnRatchetIdentityLookup.NotFound -> OwnBundleRead.Missing
            OwnRatchetIdentityLookup.Unavailable -> OwnBundleRead.Unavailable
        }
    }

    private suspend fun sync_vault_with_server(): Boolean {
        val result = runCatching { keys_api.fetch_current_vault() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
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
        retain_local_ratchet_keys(
            session_key_store,
            vault_state.identity_jwk,
            vault_spk_jwk,
            vault_pq_identity_public(),
        )
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

    private fun cached_vault_json(): org.json.JSONObject? {
        val (encrypted_vault_b64, vault_nonce_b64) = session_key_store.get_encrypted_vault() ?: return null
        val passphrase = session_key_store.get_passphrase() ?: return null
        return runCatching {
            val plain = CryptoNative.decrypt_vault_with_password(
                base64_decode(encrypted_vault_b64),
                base64_decode(vault_nonce_b64),
                passphrase,
            )
            val json = org.json.JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            json
        }.getOrNull()
    }

    private fun vault_pq_identity_public(): String? =
        cached_vault_json()?.optString("ratchet_pq_identity_public", "")?.takeIf { it.isNotBlank() }

    private fun adopt_vault_pq_identity() {
        val vault_json = cached_vault_json() ?: return
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
        val previous = vault_json.optJSONArray("ratchet_previous_keys")
        val entry = org.json.JSONObject().put("ratchet_identity_key", replaced_identity_jwk)
        runCatching { RatchetCrypto.p256_public_raw_from_private_jwk(replaced_identity_jwk) }
            .getOrNull()
            ?.let { entry.put("ratchet_identity_public", base64_encode(it)) }
        session_key_store.get_ratchet_signed_prekey_jwk()
            ?.takeIf { it.isNotBlank() && it != vault_json.optString("ratchet_signed_prekey", "") }
            ?.let { spk ->
                entry.put("ratchet_signed_prekey", spk)
                session_key_store.get_ratchet_signed_prekey_public_b64()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { entry.put("ratchet_signed_prekey_public", it) }
            }
        val vault_pq_secret = vault_json.optString("ratchet_pq_identity_key", "")
        session_key_store.get_ratchet_pq_identity_secret()
            ?.takeIf { it.isNotBlank() && it != vault_pq_secret }
            ?.let { pq_secret ->
                entry.put("ratchet_pq_identity_key", pq_secret)
                session_key_store.get_ratchet_pq_identity_public()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { entry.put("ratchet_pq_identity_public", it) }
            }
        val merged = merge_retained_ratchet_keys(
            org.json.JSONArray().put(entry).toString(),
            previous?.toString(),
        )?.let { org.json.JSONArray(it) } ?: return
        if (retained_key_sets_equal(previous, merged)) return
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
                stored_vault_format(vault_json),
            )
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(false)
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
        val local_spk_jwk = session_key_store.get_ratchet_signed_prekey_jwk() ?: return false
        val local_spk_pub = session_key_store.get_ratchet_signed_prekey_public_b64() ?: return false

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

        val previous_before = vault_json.optJSONArray("ratchet_previous_keys")
            ?.let { org.json.JSONArray(it.toString()) }
        val adopted_prekey = vault_signed_prekey_to_adopt(vault_json, session_key_store)
        if (adopted_prekey != null) {
            fold_local_ratchet_keys_into_vault(vault_json, session_key_store)
        }
        val spk_jwk = adopted_prekey?.jwk ?: local_spk_jwk
        val spk_pub = adopted_prekey?.public_b64 ?: local_spk_pub

        val identity_current = vault_json.optString("ratchet_identity_key", "") == identity_jwk &&
            vault_json.optString("ratchet_signed_prekey", "") == spk_jwk &&
            vault_json.optString("ratchet_signed_prekey_public", "") == spk_pub

        val generated = generate_pq_identity_into(vault_json)
        val previous_changed = !retained_key_sets_equal(
            previous_before,
            vault_json.optJSONArray("ratchet_previous_keys"),
        )

        if (identity_current && generated == null && !previous_changed) {
            return adopted_prekey != null && adopt_vault_signed_prekey(session_key_store, adopted_prekey)
        }

        if (!identity_current) {
            retain_vault_key_set(vault_json, identity_jwk, spk_jwk)
        }

        vault_json.put("ratchet_identity_key", identity_jwk)
        vault_json.put("ratchet_signed_prekey", spk_jwk)
        vault_json.put("ratchet_signed_prekey_public", spk_pub)
        vault_json.put("ratchet_regen_v4_done", true)
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
                stored_vault_format(vault_json),
            )
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(false)
        if (!ok) return false

        session_key_store.put_encrypted_vault(new_ct_b64, new_nonce_b64)

        val prekey_adopted = adopted_prekey != null && adopt_vault_signed_prekey(session_key_store, adopted_prekey)

        if (generated == null) return prekey_adopted

        retain_local_ratchet_keys(session_key_store, null, null, generated.public_b64)
        session_key_store.put_ratchet_pq_identity(generated.secret_b64, generated.public_b64)
        debug_log("generated pq identity for user ${session_key_store.get_user_id()}")
        return true
    }

    private data class GeneratedPqIdentity(val secret_b64: String, val public_b64: String)

    private fun generate_pq_identity_into(vault_json: org.json.JSONObject): GeneratedPqIdentity? {
        val existing_public = vault_json.optString("ratchet_pq_identity_public", "")
        val existing_seed = vault_json.optString("ratchet_pq_identity_seed", "")
        val existing_secret = vault_json.optString("ratchet_pq_identity_key", "")
            .ifBlank { existing_seed.takeIf { it.isNotBlank() }?.let { expand_pq_identity_secret(it) }.orEmpty() }
        if (existing_secret.isNotBlank() && existing_public.isNotBlank()) {
            if (session_key_store.get_ratchet_pq_identity_secret().isNullOrBlank() ||
                session_key_store.get_ratchet_pq_identity_public() != existing_public
            ) {
                retain_local_pq_identity_in_vault(vault_json, session_key_store)
                retain_local_ratchet_keys(session_key_store, null, null, existing_public)
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

        retain_vault_pq_identity(vault_json)
        retain_local_pq_identity_in_vault(vault_json, session_key_store)
        vault_json.put("ratchet_pq_identity_key", secret_b64)
        vault_json.put("ratchet_pq_identity_public", public_b64)
        vault_json.put("ratchet_pq_identity_seed", seed_b64)

        return GeneratedPqIdentity(secret_b64, public_b64)
    }

    private suspend fun signed_prekey_signature(
        identity_public_b64: String,
        signed_prekey_public_b64: String,
        pq_identity_public_b64: String?,
    ): String? {
        val signing_key = published_signing_key() ?: return null
        return pgp_prekey_signature(
            signing_key,
            identity_public_b64,
            signed_prekey_public_b64,
            pq_identity_public_b64,
        )
    }

    private suspend fun published_signing_key(): String? {
        val candidates = PublishedSigningKey.candidates(
            identity_key = session_key_store.get_identity_key(),
            previous_keys = vault_previous_keys() + session_key_store.get_previous_keys().orEmpty(),
        )
        val published_fingerprint = try {
            encryption_api.get_pgp_key_info().fingerprint
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: ApiError.NotFoundError) {
            ""
        } catch (error: Throwable) {
            debug_log("published pgp key lookup threw: ${error.javaClass.simpleName}")
            return null
        }
        return when (val choice = PublishedSigningKey.choose(candidates, published_fingerprint)) {
            is PublishedSigningKeyChoice.Selected -> choice.armored_secret_key
            PublishedSigningKeyChoice.NotHeld -> {
                debug_log("deferred upload: the vault does not hold the published pgp key")
                null
            }
            PublishedSigningKeyChoice.NoCandidates -> null
        }
    }

    private fun vault_previous_keys(): List<String> {
        val (encrypted_vault_b64, vault_nonce_b64) = session_key_store.get_encrypted_vault()
            ?: return emptyList()
        val passphrase = session_key_store.get_passphrase() ?: return emptyList()
        return runCatching {
            val plain = CryptoNative.decrypt_vault_with_password(
                base64_decode(encrypted_vault_b64),
                base64_decode(vault_nonce_b64),
                passphrase,
            )
            val json = org.json.JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            val keys = mutableListOf<String>()
            json.optString("identity_key").takeIf { it.isNotBlank() }?.let { keys.add(it) }
            val previous = json.optJSONArray("previous_keys")
            if (previous != null) {
                for (index in 0 until previous.length()) {
                    previous.optString(index).takeIf { it.isNotBlank() }?.let { keys.add(it) }
                }
            }
            keys.toList()
        }.getOrDefault(emptyList())
    }

    private fun pgp_prekey_signature(
        signing_key: String,
        identity_public_b64: String,
        signed_prekey_public_b64: String,
        pq_identity_public_b64: String?,
    ): String? {
        val passphrase_bytes = session_key_store.get_passphrase() ?: return null
        val passphrase = org.astermail.android.util.passphrase_chars(passphrase_bytes)
        passphrase_bytes.fill(0)
        return runCatching {
            val armored = PrekeyBindingSigner.sign_cleartext(
                armored_secret_key = signing_key,
                passphrase = passphrase,
                text = PrekeyBindingSigner.binding_for_bundle(
                    identity_public_b64,
                    signed_prekey_public_b64,
                    pq_identity_public_b64,
                ),
            )
            base64_encode(armored.toByteArray(Charsets.UTF_8))
        }
            .onFailure { debug_log("pgp prekey signature threw: ${it.javaClass.simpleName}: ${it.message}") }
            .also { passphrase.fill('\u0000') }
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
