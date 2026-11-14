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
import org.astermail.android.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.AddressIdentity
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.crypto.ratchet.RecoveryLane
import org.astermail.android.storage.SessionKeyStore

open class RatchetEncryptionException(
    val recipient: String?,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

open class RatchetIdentityPinException(
    recipient: String?,
    message: String,
) : RatchetEncryptionException(recipient, message)

class RecipientKeyUntrustedException(
    recipient: String?,
    message: String,
) : RatchetIdentityPinException(recipient, message)

class PostQuantumUnavailableException(
    val recipients: List<String>,
) : Exception("post-quantum key agreement unavailable for ${recipients.joinToString(", ")}")

enum class PostQuantumRecipientStatus { SUPPORTED, UNSUPPORTED, DOWNGRADED }

data class PostQuantumCoverage(
    val missing: List<String> = emptyList(),
    val downgraded: List<String> = emptyList(),
)

internal fun classify_post_quantum_bundle(
    pq_capable: Boolean?,
    pq_prekey_pair: Pair<Int, ByteArray>?,
    pq_identity_raw: ByteArray?,
    signature_covers_pq_identity: Boolean,
): PostQuantumRecipientStatus = when {
    signature_covers_pq_identity && pq_identity_raw?.size == X3dh.ML_KEM_768_EK_LEN ->
        PostQuantumRecipientStatus.SUPPORTED
    X3dh.supports_pq(pq_prekey_pair, pq_identity_raw) -> PostQuantumRecipientStatus.UNSUPPORTED
    pq_capable == true -> PostQuantumRecipientStatus.DOWNGRADED
    else -> PostQuantumRecipientStatus.UNSUPPORTED
}

internal fun classify_prekey_bundle(
    bundle: PrekeyBundleResponse,
    signature_covers_pq_identity: Boolean,
): PostQuantumRecipientStatus {
    val pq_prekey_pair = bundle.pq_prekey?.let {
        runCatching { it.key_id to RatchetCrypto.b64_decode(it.public_key) }.getOrNull()
    }
    val pq_identity_raw = bundle.pq_kem_public_key
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { RatchetCrypto.b64_decode(it) }.getOrNull() }
    return classify_post_quantum_bundle(
        bundle.pq_capable,
        pq_prekey_pair,
        pq_identity_raw,
        signature_covers_pq_identity,
    )
}

internal fun session_reused_for_send(
    bootstrap: BootstrapData,
    sender_identity_public: String?,
    bundle: PrekeyBundleResponse?,
): Boolean {
    if (bootstrap.sender_identity_key != sender_identity_public) return false
    return bundle == null || bundle.kem_identity_key == bootstrap.recipient_identity_key
}

internal fun session_uses_unsigned_pq_prekey(bootstrap: BootstrapData): Boolean =
    bootstrap.pq_ciphertext != null && bootstrap.pq_key_id != X3dh.PQ_IDENTITY_KEY_ID

internal fun session_restarts_for_signed_pq_key(
    bootstrap: BootstrapData,
    advertised_pq_identity: String?,
    signature_covers_pq_identity: Boolean,
): Boolean =
    signature_covers_pq_identity &&
        !advertised_pq_identity.isNullOrBlank() &&
        session_uses_unsigned_pq_prekey(bootstrap)

internal fun session_post_quantum_status(
    bootstrap: BootstrapData,
    advertised_pq_identity: String?,
    signature_covers_pq_identity: Boolean,
): PostQuantumRecipientStatus =
    if (signature_covers_pq_identity &&
        bootstrap.pq_ciphertext != null &&
        bootstrap.pq_key_id == X3dh.PQ_IDENTITY_KEY_ID &&
        !advertised_pq_identity.isNullOrBlank() &&
        advertised_pq_identity == bootstrap.recipient_pq_identity_key
    ) {
        PostQuantumRecipientStatus.SUPPORTED
    } else {
        PostQuantumRecipientStatus.UNSUPPORTED
    }

private class RecipientEncryption(
    val data: RatchetRecipientData,
    val post_quantum_signed: Boolean,
)

@Singleton
class RatchetEncryptor @Inject constructor(
    private val state_store: RatchetStateStore,
    private val session_key_store: SessionKeyStore,
    private val ratchet_api: RatchetApi,
    private val syncer: RatchetStateSyncer,
    private val conversation_locks: ConversationLocks,
    private val keys_api: KeysApi,
    private val identity_pins: RatchetIdentityPinStore,
    private val address_directory: AddressIdentityDirectory,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    internal var require_verified_prekey_bundle: Boolean = PrekeyBundlePolicy.require_verified_prekey_bundle

    fun owner_key_matches_pin(recipient_email: String, armored_public_key: String): Boolean =
        owner_key_trusted(
            identity_pins.owner_key_pin(recipient_email),
            PrekeyBindingVerifier.owner_fingerprint(armored_public_key),
        )

    suspend fun check_post_quantum_coverage(
        sender_email: String,
        recipients: List<String>,
    ): PostQuantumCoverage {
        if (sender_email.isBlank()) return PostQuantumCoverage()

        val missing = mutableListOf<String>()
        val downgraded = mutableListOf<String>()
        for (recipient_email in recipients) {
            val status = runCatching {
                val conversation_id = X3dh.derive_conversation_id(sender_email, recipient_email)
                val username = recipient_email.substringBefore('@')
                val bootstrap = state_store.load(conversation_id)?.bootstrap
                if (bootstrap != null) {
                    val sender_identity_public = runCatching { address_directory.resolve_sender_identity(sender_email) }
                        .getOrNull()
                        ?.identity_public
                        ?: session_key_store.get_ratchet_identity_public_b64()
                    val sender_changed = bootstrap.sender_identity_key != sender_identity_public
                    val entry = if (sender_changed) {
                        address_directory.resolve_recipient_bundle(username, recipient_email, conversation_id)
                    } else {
                        try {
                            address_directory.resolve_recipient_bundle(
                                username,
                                recipient_email,
                                conversation_id,
                                bootstrap.recipient_identity_key,
                            )
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            throw c
                        } catch (t: Throwable) {
                            null
                        }
                    }
                    if (entry == null) return@runCatching PostQuantumRecipientStatus.UNSUPPORTED
                    val bundle = entry.bundle
                    val covers = pq_binding_covered(conversation_id, username, recipient_email, entry)
                    val renews = !session_reused_for_send(bootstrap, sender_identity_public, bundle) ||
                        RecoveryLanePolicy.session_lacks_advertised_pq_key(bootstrap, bundle.pq_kem_public_key) ||
                        session_restarts_for_signed_pq_key(bootstrap, bundle.pq_kem_public_key, covers)
                    if (renews) {
                        classify_prekey_bundle(bundle, covers)
                    } else {
                        session_post_quantum_status(bootstrap, bundle.pq_kem_public_key, covers)
                    }
                } else {
                    val entry = address_directory.resolve_recipient_bundle(username, recipient_email, conversation_id)
                        ?: return@runCatching PostQuantumRecipientStatus.UNSUPPORTED
                    classify_prekey_bundle(
                        entry.bundle,
                        pq_binding_covered(conversation_id, username, recipient_email, entry),
                    )
                }
            }.getOrElse {
                if (it is CancellationException) throw it
                PostQuantumRecipientStatus.UNSUPPORTED
            }
            if (status == PostQuantumRecipientStatus.SUPPORTED) continue
            missing.add(recipient_email)
            if (status == PostQuantumRecipientStatus.DOWNGRADED) downgraded.add(recipient_email)
        }
        return PostQuantumCoverage(missing = missing, downgraded = downgraded)
    }

    suspend fun encrypt_envelope(
        sender_email: String,
        recipients: List<String>,
        body: String,
        allow_non_post_quantum: Boolean = false,
    ): String? {
        if (recipients.isEmpty()) return null
        session_key_store.get_ratchet_identity_public_b64()
            ?: throw RatchetEncryptionException(null, "missing ratchet identity public key")
        session_key_store.get_ratchet_identity_jwk()
            ?: throw RatchetEncryptionException(null, "missing ratchet identity key")
        val sender_identity = try {
            address_directory.resolve_sender_identity(sender_email)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            throw RatchetEncryptionException(null, "sender address identity unavailable", t)
        } ?: throw RatchetEncryptionException(null, "sender address identity unavailable")
        val sender_identity_public = sender_identity.identity_public
        val sender_identity_jwk = sender_identity.identity_jwk
        val own_address_identity = address_directory.own_address_identity(sender_email, sender_identity)
        val own_address = AddressIdentity.normalize(sender_email)

        val per_recipient = mutableMapOf<String, RatchetRecipientData>()
        val unsigned_recipients = mutableListOf<String>()
        for (recipient_email in recipients) {
            val required_identity = own_address_identity
                ?.takeIf { AddressIdentity.normalize(recipient_email) == own_address }
            val encryption = try {
                encrypt_for_recipient(
                    sender_email,
                    sender_identity_public,
                    sender_identity_jwk,
                    recipient_email,
                    body,
                    required_identity,
                )
            } catch (e: RatchetEncryptionException) {
                throw e
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Throwable) {
                throw RatchetEncryptionException(recipient_email, "ratchet encryption failed for recipient", e)
            } ?: throw RatchetEncryptionException(recipient_email, "no prekey bundle available for recipient")
            val key = recipient_email.lowercase(java.util.Locale.ROOT)
            per_recipient[key] = encryption.data
            if (!encryption.post_quantum_signed) unsigned_recipients.add(key)
        }

        if (!allow_non_post_quantum && unsigned_recipients.isNotEmpty()) {
            throw PostQuantumUnavailableException(unsigned_recipients)
        }

        val envelope = RatchetEnvelope(
            type = "double_ratchet_v2",
            sender_identity_key = sender_identity_public,
            recipients = per_recipient,
        )
        return json.encodeToString(envelope)
    }

    private val verifying_key_cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun pq_binding_signed(
        conversation_id: String,
        username: String,
        recipient_email: String,
        bundle: PrekeyBundleResponse,
    ): Boolean = try {
        verify_prekey_binding(conversation_id, username, recipient_email, bundle)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        false
    }

    private suspend fun pq_binding_covered(
        conversation_id: String,
        username: String,
        recipient_email: String,
        resolved: ResolvedRecipientBundle,
    ): Boolean = when (resolved.trust) {
        DirectoryTrust.ENTRY, DirectoryTrust.ENDORSED -> true
        DirectoryTrust.REPLACED, DirectoryTrust.INVALID -> false
        DirectoryTrust.ACCOUNT, DirectoryTrust.DOWNGRADED ->
            pq_binding_signed(conversation_id, username, recipient_email, resolved.bundle)
    }

    private suspend fun verify_prekey_binding(
        conversation_id: String,
        username: String,
        recipient_email: String,
        bundle: PrekeyBundleResponse,
    ): Boolean {
        val pinned_owner = identity_pins.owner_key_pin(recipient_email)?.takeIf { it.isNotBlank() }
        val signature = bundle.signed_prekey_signature
        val signed = PrekeyBindingVerifier.is_pgp_signature(signature)
        if (!signed && pinned_owner == null) {
            if (prekey_bundle_needs_pgp_fallback(
                    require_verified_prekey_bundle,
                    PrekeyBindingResult.UNSIGNED_LEGACY,
                    null,
                    null,
                )
            ) {
                throw UnverifiedPrekeyBundleException(recipient_email)
            }
            if (BuildConfig.DEBUG) {
                android.util.Log.w("AsterRatchet", "prekey bundle carries a legacy unsigned binding")
            }
            return false
        }

        val verifying_key = fetch_verifying_key(username, recipient_email)
        val verdict = when {
            !signed -> PrekeyBindingVerdict(PrekeyBindingResult.UNSIGNED_LEGACY, false)
            verifying_key == null -> PrekeyBindingVerdict(PrekeyBindingResult.UNVERIFIABLE, false)
            else -> PrekeyBindingVerifier.verify_binding(
                signature_block = signature,
                recipient_public_key_armored = verifying_key,
                kem_identity_key_b64 = bundle.kem_identity_key,
                signed_prekey_b64 = bundle.signed_prekey,
                pq_identity_key_b64 = bundle.pq_kem_public_key,
            )
        }
        val result = verdict.result
        val served_owner = PrekeyBindingVerifier.owner_fingerprint(verifying_key)

        if (prekey_bundle_needs_pgp_fallback(require_verified_prekey_bundle, result, pinned_owner, served_owner)) {
            throw UnverifiedPrekeyBundleException(recipient_email)
        }

        when (RatchetIdentityPinRules.decide_owner_key(pinned_owner, served_owner, result)) {
            OwnerKeyDecision.ACCEPT -> {
                if (result == PrekeyBindingResult.VERIFIED) {
                    runCatching { identity_pins.record_prekey_binding_verified(recipient_email) }
                } else if (BuildConfig.DEBUG) {
                    android.util.Log.w("AsterRatchet", "prekey bundle signature could not be checked: $result")
                }
            }
            OwnerKeyDecision.PIN_FIRST -> {
                if (!identity_pins.pin_owner_key_if_absent(recipient_email, served_owner.orEmpty())) {
                    throw RatchetEncryptionException(recipient_email, "recipient owner key pin unavailable")
                }
                runCatching { identity_pins.record_prekey_binding_verified(recipient_email) }
            }
            OwnerKeyDecision.UNAVAILABLE -> throw RatchetEncryptionException(
                recipient_email,
                "recipient owner key unavailable",
            )
            OwnerKeyDecision.UNTRUSTED -> {
                verifying_key_cache.remove(recipient_email.lowercase(java.util.Locale.ROOT))
                if (pinned_owner != null && served_owner != null &&
                    !served_owner.equals(pinned_owner, ignoreCase = true)
                ) {
                    val observed_at = System.currentTimeMillis()
                    runCatching { identity_pins.flag_owner_key_change(recipient_email, served_owner, observed_at) }
                    runCatching {
                        identity_pins.flag_identity_change(
                            conversation_id = conversation_id,
                            peer_email = recipient_email,
                            identity_key_b64 = bundle.kem_identity_key,
                            observed_at = observed_at,
                        )
                    }
                }
                throw RecipientKeyUntrustedException(
                    recipient_email,
                    "recipient key is not trusted",
                )
            }
        }
        return verdict.covers_pq_identity
    }

    private suspend fun verify_identity_pin(
        conversation_id: String,
        recipient_email: String,
        bundle: PrekeyBundleResponse,
    ) {
        if (bundle.kem_identity_key.isBlank()) {
            throw RatchetIdentityPinException(
                recipient_email,
                "recipient prekey bundle carries no identity key",
            )
        }
        when (identity_pins.evaluate(conversation_id, bundle.kem_identity_key)) {
            IdentityPinOutcome.UNCHANGED -> Unit
            IdentityPinOutcome.FIRST_CONTACT ->
                identity_pins.pin_if_absent(conversation_id, bundle.kem_identity_key)
            IdentityPinOutcome.CHANGED -> {
                runCatching {
                    identity_pins.flag_identity_change(
                        conversation_id = conversation_id,
                        peer_email = recipient_email,
                        identity_key_b64 = bundle.kem_identity_key,
                        observed_at = System.currentTimeMillis(),
                    )
                }
                throw RatchetIdentityPinException(
                    recipient_email,
                    "recipient identity key does not match the pinned identity",
                )
            }
        }
    }

    private suspend fun fetch_verifying_key(username: String, recipient_email: String): String? {
        val cache_key = recipient_email.lowercase(java.util.Locale.ROOT)
        verifying_key_cache[cache_key]?.let { return it }
        repeat(2) {
            val key = runCatching {
                keys_api.get_recipient_public_key(username, recipient_email).public_key
            }.getOrNull()
            if (!key.isNullOrBlank()) {
                verifying_key_cache[cache_key] = key
                return key
            }
        }
        return null
    }

    private suspend fun encrypt_for_recipient(
        sender_email: String,
        sender_identity_public: String,
        sender_identity_jwk: String,
        recipient_email: String,
        body: String,
        required_identity: String? = null,
    ): RecipientEncryption? {
        val conversation_id = X3dh.derive_conversation_id(sender_email, recipient_email)
        val username = recipient_email.substringBefore('@')

        return conversation_locks.with_lock(conversation_id) {
            encrypt_for_recipient_locked(
                conversation_id,
                username,
                sender_identity_public,
                sender_identity_jwk,
                recipient_email,
                body,
                required_identity,
            )
        }
    }

    private suspend fun resolve_own_address_bundle(
        username: String,
        recipient_email: String,
        required_identity: String,
        candidate: ResolvedRecipientBundle?,
    ): PrekeyBundleResponse? {
        val bundle = candidate?.bundle?.takeIf { candidate.scoped && it.kem_identity_key == required_identity }
            ?: ratchet_api.fetch_address_prekey_bundle(username, recipient_email)
                ?.takeIf { it.address_scoped == true && it.kem_identity_key == required_identity }
            ?: return null
        val verification = AddressIdentity.verify_entry(
            AddressIdentity.normalize(recipient_email),
            bundle.kem_identity_key,
            bundle.signed_prekey,
            bundle.signed_prekey_signature,
            bundle.pq_kem_public_key,
        )
        return bundle.takeIf { verification.valid }
    }

    private suspend fun verify_directory_bundle(
        conversation_id: String,
        username: String,
        recipient_email: String,
        resolved: ResolvedRecipientBundle,
    ): Boolean {
        val bundle = resolved.bundle
        return when (resolved.trust) {
            DirectoryTrust.ACCOUNT -> {
                val covers = verify_prekey_binding(conversation_id, username, recipient_email, bundle)
                verify_identity_pin(conversation_id, recipient_email, bundle)
                covers
            }
            DirectoryTrust.DOWNGRADED -> {
                val pinned = try {
                    identity_pins.pinned_fingerprint(conversation_id)
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (_: Throwable) {
                    null
                }
                if (pinned == null || pinned != identity_pins.fingerprint_of(bundle.kem_identity_key)) {
                    reject_directory_bundle(conversation_id, recipient_email, bundle)
                }
                val covers = verify_prekey_binding(conversation_id, username, recipient_email, bundle)
                verify_identity_pin(conversation_id, recipient_email, bundle)
                covers
            }
            DirectoryTrust.REPLACED, DirectoryTrust.INVALID ->
                reject_directory_bundle(conversation_id, recipient_email, bundle)
            DirectoryTrust.ENTRY -> {
                verify_identity_pin(conversation_id, recipient_email, bundle)
                runCatching { identity_pins.mark_address_rooted(conversation_id) }
                true
            }
            DirectoryTrust.ENDORSED -> {
                identity_pins.replace_pin(conversation_id, bundle.kem_identity_key)
                runCatching { identity_pins.mark_address_rooted(conversation_id) }
                true
            }
        }
    }

    private suspend fun reject_directory_bundle(
        conversation_id: String,
        recipient_email: String,
        bundle: PrekeyBundleResponse,
    ): Nothing {
        runCatching {
            identity_pins.flag_identity_change(
                conversation_id = conversation_id,
                peer_email = recipient_email,
                identity_key_b64 = bundle.kem_identity_key,
                observed_at = System.currentTimeMillis(),
            )
        }
        throw RecipientKeyUntrustedException(recipient_email, "recipient key is not trusted")
    }

    private suspend fun encrypt_for_recipient_locked(
        conversation_id: String,
        username: String,
        sender_identity_public: String,
        sender_identity_jwk: String,
        recipient_email: String,
        body: String,
        required_identity: String? = null,
    ): RecipientEncryption? {
        var state = state_store.load(conversation_id)
        if (state != null && state.bootstrap == null) {
            state = null
        }
        if (state != null && required_identity != null &&
            state.bootstrap?.recipient_identity_key != required_identity
        ) {
            state = null
        }

        var bundle: PrekeyBundleResponse? = null
        var resolved: ResolvedRecipientBundle? = null
        var session_pq_signed = false

        if (state != null) {
            val boot = state.bootstrap!!
            val sender_changed = boot.sender_identity_key != sender_identity_public
            var recipient_changed = false
            if (!sender_changed) {
                resolved = try {
                    address_directory.resolve_recipient_bundle(
                        username,
                        recipient_email,
                        conversation_id,
                        boot.recipient_identity_key,
                    )
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    null
                }
                val advertised_entry = resolved
                bundle = advertised_entry?.bundle
                if (advertised_entry != null) {
                    val advertised = advertised_entry.bundle
                    recipient_changed =
                        boot.recipient_identity_key != advertised.kem_identity_key ||
                            RecoveryLanePolicy.session_lacks_advertised_pq_key(boot, advertised.pq_kem_public_key)
                    if (!recipient_changed &&
                        boot.pq_ciphertext != null &&
                        !advertised.pq_kem_public_key.isNullOrBlank()
                    ) {
                        val covers = pq_binding_covered(conversation_id, username, recipient_email, advertised_entry)
                        recipient_changed = session_restarts_for_signed_pq_key(boot, advertised.pq_kem_public_key, covers)
                        session_pq_signed = session_post_quantum_status(boot, advertised.pq_kem_public_key, covers) ==
                            PostQuantumRecipientStatus.SUPPORTED
                    }
                }
            }
            if (sender_changed || recipient_changed) {
                state = null
            }
        }

        var ephemeral_b64: String? = null
        var pq_ciphertext_b64: String? = null
        var pq_key_id: Int? = null
        var x3dh_version: Int? = null
        var one_time_prekey_id: Int? = null
        var signed_prekey_id: String? = null
        var post_quantum_signed = false

        if (state == null) {
            val local_bundle = required_identity?.let { address_directory.local_address_bundle(recipient_email, it) }
            val directory_entry = if (local_bundle != null) null else try {
                address_directory.resolve_recipient_bundle(username, recipient_email, conversation_id)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "prekey bundle fetch threw: ${t.javaClass.simpleName}")
                throw RatchetEncryptionException(recipient_email, "prekey bundle fetch failed", t)
            }

            val resolved_bundle: PrekeyBundleResponse
            val signature_covers_pq_identity: Boolean
            if (required_identity != null) {
                resolved_bundle = local_bundle ?: try {
                    resolve_own_address_bundle(username, recipient_email, required_identity, directory_entry)
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    throw RatchetEncryptionException(recipient_email, "prekey bundle fetch failed", t)
                } ?: return null
                identity_pins.replace_pin(conversation_id, resolved_bundle.kem_identity_key)
                runCatching { identity_pins.mark_address_rooted(conversation_id) }
                signature_covers_pq_identity = true
            } else {
                val entry = directory_entry ?: run {
                    if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "no prekey bundle for recipient")
                    return null
                }
                resolved_bundle = entry.bundle
                signature_covers_pq_identity = verify_directory_bundle(conversation_id, username, recipient_email, entry)
            }

            bundle = resolved_bundle

            val recipient_identity_raw = RatchetCrypto.b64_decode(resolved_bundle.kem_identity_key)
            val recipient_spk_raw = RatchetCrypto.b64_decode(resolved_bundle.signed_prekey)
            val pq_prekey_pair = resolved_bundle.pq_prekey?.let {
                runCatching { it.key_id to RatchetCrypto.b64_decode(it.public_key) }.getOrNull()
            }
            val pq_identity_raw = resolved_bundle.pq_kem_public_key
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { RatchetCrypto.b64_decode(it) }.getOrNull() }

            val negotiated_x3dh_version = X3dh.negotiated_sender_version(
                resolved_bundle.x3dh_max_version,
                identity_pins.highest_advertised_x3dh_version(conversation_id),
            )
            runCatching {
                identity_pins.record_advertised_x3dh_version(conversation_id, negotiated_x3dh_version)
            }

            val one_time_prekey_pair = resolved_bundle.ec_one_time_prekey?.let {
                runCatching { it.key_id to RatchetCrypto.b64_decode(it.public_key) }.getOrNull()
            }

            val x3dh_result = X3dh.perform_sender(
                sender_identity_jwk = sender_identity_jwk,
                recipient_identity_raw = recipient_identity_raw,
                recipient_signed_prekey_raw = recipient_spk_raw,
                recipient_pq_prekey = pq_prekey_pair,
                recipient_pq_identity = pq_identity_raw,
                signature_covers_pq_identity = signature_covers_pq_identity,
                x3dh_version = negotiated_x3dh_version,
                recipient_one_time_prekey = one_time_prekey_pair,
                advertised_x3dh_version = resolved_bundle.x3dh_max_version,
            )

            try {
                state = DoubleRatchet.init_sender(
                    conversation_id = conversation_id,
                    shared_secret = x3dh_result.shared_secret,
                    remote_signed_prekey_raw_b64 = resolved_bundle.signed_prekey,
                )
                ephemeral_b64 = RatchetCrypto.b64_encode(x3dh_result.ephemeral_public_raw)
                if (x3dh_result.pq_ciphertext != null && x3dh_result.pq_key_id != null) {
                    pq_ciphertext_b64 = RatchetCrypto.b64_encode(x3dh_result.pq_ciphertext!!)
                    pq_key_id = x3dh_result.pq_key_id
                }
                x3dh_version = x3dh_result.x3dh_version.takeIf { it > X3dh.VERSION_LEGACY }
                if (x3dh_result.x3dh_version >= X3dh.VERSION_ONE_TIME_BOUND) {
                    one_time_prekey_id = x3dh_result.one_time_prekey_id
                    signed_prekey_id = x3dh_result.signed_prekey_id
                }
                post_quantum_signed = signature_covers_pq_identity &&
                    pq_ciphertext_b64 != null &&
                    x3dh_result.pq_mode == X3dh.PqMode.IDENTITY
                state.bootstrap = BootstrapData(
                    ephemeral_key = ephemeral_b64!!,
                    pq_ciphertext = pq_ciphertext_b64,
                    pq_key_id = pq_key_id,
                    sender_identity_key = sender_identity_public,
                    recipient_identity_key = resolved_bundle.kem_identity_key,
                    recipient_pq_identity_key = resolved_bundle.pq_kem_public_key,
                    x3dh_version = x3dh_version,
                    one_time_prekey_id = one_time_prekey_id,
                    signed_prekey_id = signed_prekey_id,
                )
            } finally {
                x3dh_result.shared_secret.fill(0)
            }
        } else {
            val boot = state.bootstrap!!
            ephemeral_b64 = boot.ephemeral_key
            pq_ciphertext_b64 = boot.pq_ciphertext
            pq_key_id = boot.pq_key_id
            x3dh_version = boot.x3dh_version
            if ((boot.x3dh_version ?: X3dh.VERSION_LEGACY) >= X3dh.VERSION_ONE_TIME_BOUND) {
                one_time_prekey_id = boot.one_time_prekey_id
                signed_prekey_id = boot.signed_prekey_id
            }
            post_quantum_signed = session_pq_signed
        }

        val recovery_keys = resolve_recovery_lane_keys(bundle, state.bootstrap)
            ?: return null
        if (!RecoveryLanePolicy.lane_matches_body(pq_ciphertext_b64 != null, recovery_keys)) {
            throw RecoveryLaneDowngradeException(recipient_email)
        }

        val encrypted = DoubleRatchet.encrypt(state, body)

        val recovery = try {
            RecoveryLane.seal(
                RatchetCrypto.b64_encode(encrypted.message_key),
                conversation_id,
                sender_identity_public,
                recovery_keys,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            throw RatchetEncryptionException(recipient_email, "recovery lane unavailable", t)
        } finally {
            encrypted.message_key.fill(0)
        }

        state_store.save(state)
        syncer.sync(conversation_id, state)

        val data = RatchetRecipientData(
            ephemeral_key = ephemeral_b64,
            header = encrypted.header,
            ciphertext = RatchetCrypto.b64_encode(encrypted.ciphertext),
            nonce = RatchetCrypto.b64_encode(encrypted.nonce),
            pq_ciphertext = pq_ciphertext_b64,
            pq_key_id = pq_key_id,
            x3dh_v = x3dh_version,
            opk_id = one_time_prekey_id,
            spk_id = signed_prekey_id,
            recovery = RecoveryLaneData(
                v = recovery.v,
                epk = recovery.epk,
                kem_ct = recovery.kem_ct,
                ciphertext = recovery.ciphertext,
                nonce = recovery.nonce,
                rid = recovery.rid,
            ),
        )
        return RecipientEncryption(data, post_quantum_signed)
    }

    private fun resolve_recovery_lane_keys(
        bundle: PrekeyBundleResponse?,
        bootstrap: BootstrapData?,
    ): RecoveryLane.RecipientKeys? {
        val identity_public = bundle?.kem_identity_key
            ?: bootstrap?.recipient_identity_key
            ?: return null
        if (identity_public.isBlank()) return null

        val pq_identity_public = RecoveryLanePolicy.lane_pq_identity_key(
            identity_public,
            bundle?.pq_kem_public_key,
            bootstrap,
        )

        return RecoveryLane.RecipientKeys(identity_public, pq_identity_public)
    }
}
