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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.api.ratchet.AddressBundleUpload
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.UploadAddressBundleRequest
import org.astermail.android.crypto.ratchet.AddressIdentity
import org.astermail.android.crypto.ratchet.AddressKeySet
import org.astermail.android.crypto.ratchet.AddressKeySource
import org.astermail.android.crypto.ratchet.DerivedAddressKey
import org.astermail.android.crypto.same_address_ignoring_dots
import org.astermail.android.storage.SessionKeyStore

enum class AddressEntryState { ACTIVE, INACTIVE, FAILED }

enum class DirectoryTrust { ACCOUNT, DOWNGRADED, ENTRY, ENDORSED, REPLACED, INVALID }

data class ResolvedRecipientBundle(
    val bundle: PrekeyBundleResponse,
    val scoped: Boolean,
    val trust: DirectoryTrust,
)

fun is_directory_trust_rejected(trust: DirectoryTrust): Boolean =
    trust == DirectoryTrust.REPLACED || trust == DirectoryTrust.DOWNGRADED || trust == DirectoryTrust.INVALID

@Singleton
open class AddressIdentityDirectory @Inject constructor(
    private val session_key_store: SessionKeyStore,
    private val ratchet_api: RatchetApi,
    private val identity_pins: RatchetIdentityPinStore,
) {
    private data class PublishedState(
        val fingerprint: String,
        val state: AddressEntryState,
        val retry_at: Long,
    )

    private data class UploadOutcome(val state: AddressEntryState, val retry_ms: Long)

    internal var now_ms: () -> Long = { System.currentTimeMillis() }

    private val publish_mutex = Mutex()
    private val published_states = HashMap<String, PublishedState>()

    @Volatile
    private var unsupported_until = 0L

    fun clear() {
        synchronized(published_states) { published_states.clear() }
        unsupported_until = 0L
        AddressIdentity.clear_cache()
    }

    fun current_source(): AddressKeySource? {
        val identity_jwk = session_key_store.get_ratchet_identity_jwk()?.takeIf { it.isNotBlank() } ?: return null
        val identity_public = session_key_store.get_ratchet_identity_public_b64()?.takeIf { it.isNotBlank() }
            ?: return null
        val signed_prekey_jwk = session_key_store.get_ratchet_signed_prekey_jwk()?.takeIf { it.isNotBlank() }
            ?: return null
        val signed_prekey_public = session_key_store.get_ratchet_signed_prekey_public_b64()
            ?.takeIf { it.isNotBlank() } ?: return null
        return AddressKeySource(
            identity_jwk = identity_jwk,
            identity_public = identity_public,
            signed_prekey_jwk = signed_prekey_jwk,
            signed_prekey_public = signed_prekey_public,
            pq_identity_secret = session_key_store.get_ratchet_pq_identity_secret()?.takeIf { it.isNotBlank() },
            pq_identity_public = session_key_store.get_ratchet_pq_identity_public()?.takeIf { it.isNotBlank() },
        )
    }

    fun previous_sources(): List<AddressKeySource> {
        val raw = session_key_store.get_ratchet_previous_keys_json()
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val entries = org.json.JSONArray(raw)
            (0 until entries.length()).mapNotNull { index ->
                val entry = entries.optJSONObject(index) ?: return@mapNotNull null
                val identity_jwk = entry.optString("ratchet_identity_key", "")
                val identity_public = entry.optString("ratchet_identity_public", "")
                val signed_prekey_jwk = entry.optString("ratchet_signed_prekey", "")
                val signed_prekey_public = entry.optString("ratchet_signed_prekey_public", "")
                if (identity_jwk.isBlank() || signed_prekey_jwk.isBlank() || signed_prekey_public.isBlank()) {
                    return@mapNotNull null
                }
                AddressKeySource(
                    identity_jwk = identity_jwk,
                    identity_public = identity_public,
                    signed_prekey_jwk = signed_prekey_jwk,
                    signed_prekey_public = signed_prekey_public,
                    pq_identity_secret = entry.optString("ratchet_pq_identity_key", "").ifBlank { null },
                    pq_identity_seed = entry.optString("ratchet_pq_identity_seed", "").ifBlank { null },
                    pq_identity_public = entry.optString("ratchet_pq_identity_public", "").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun previous_identity_jwks(): List<String> {
        val raw = session_key_store.get_ratchet_previous_keys_json()
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val entries = org.json.JSONArray(raw)
            (0 until entries.length()).mapNotNull { index ->
                entries.optJSONObject(index)?.optString("ratchet_identity_key", "")?.takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    fun account_identity_jwks(): List<String> =
        (listOfNotNull(session_key_store.get_ratchet_identity_jwk()?.takeIf { it.isNotBlank() }) +
            previous_identity_jwks()).distinct()

    private fun key_fingerprint(): String {
        val previous_count = runCatching {
            org.json.JSONArray(session_key_store.get_ratchet_previous_keys_json().orEmpty().ifBlank { "[]" }).length()
        }.getOrDefault(0)
        return listOf(
            session_key_store.get_ratchet_identity_public_b64().orEmpty(),
            session_key_store.get_ratchet_signed_prekey_public_b64().orEmpty(),
            session_key_store.get_ratchet_pq_identity_public().orEmpty(),
            previous_count.toString(),
        ).joinToString("|")
    }

    fun is_primary_address(address: String?): Boolean {
        val primary = session_key_store.get_user_email()
        return address.isNullOrBlank() || primary.isNullOrBlank() || same_address_ignoring_dots(address, primary)
    }

    private suspend fun upload_entry(form: String): UploadOutcome {
        val entry = AddressIdentity.sign_entry(current_source(), previous_identity_jwks(), form)
            ?: return UploadOutcome(AddressEntryState.FAILED, FAILED_RETRY_MS)
        val result = ratchet_api.upload_address_bundle(
            UploadAddressBundleRequest(
                address = entry.address,
                kem_identity_key = entry.kem_identity_key,
                signed_prekey = entry.signed_prekey,
                signed_prekey_signature = entry.signed_prekey_signature,
                pq_kem_public_key = entry.pq_kem_public_key,
            ),
        )
        return when (result) {
            is AddressBundleUpload.Accepted -> if (result.active) {
                UploadOutcome(AddressEntryState.ACTIVE, ACTIVE_REFRESH_MS)
            } else {
                UploadOutcome(AddressEntryState.INACTIVE, INACTIVE_RETRY_MS)
            }
            AddressBundleUpload.Unsupported -> {
                unsupported_until = now_ms() + INACTIVE_RETRY_MS
                UploadOutcome(AddressEntryState.INACTIVE, INACTIVE_RETRY_MS)
            }
            AddressBundleUpload.Rejected -> UploadOutcome(AddressEntryState.INACTIVE, INACTIVE_RETRY_MS)
            AddressBundleUpload.Failed -> UploadOutcome(AddressEntryState.FAILED, FAILED_RETRY_MS)
        }
    }

    private suspend fun publish_forms(exact: String, fingerprint: String): AddressEntryState {
        val forms = AddressIdentity.entry_forms(exact)
        val first = forms.firstOrNull() ?: return AddressEntryState.INACTIVE
        val result = try {
            val outcome = upload_entry(first)
            for (form in forms.drop(1)) {
                if (now_ms() < unsupported_until) break
                try {
                    upload_entry(form)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Throwable) {
                }
            }
            outcome
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            UploadOutcome(AddressEntryState.FAILED, FAILED_RETRY_MS)
        }
        synchronized(published_states) {
            published_states[exact] = PublishedState(fingerprint, result.state, now_ms() + result.retry_ms)
        }
        return result.state
    }

    suspend fun address_entry_state(address: String): AddressEntryState {
        val exact = AddressIdentity.normalize(address)
        if (exact.isEmpty() || session_key_store.get_ratchet_identity_jwk().isNullOrBlank()) {
            return AddressEntryState.FAILED
        }
        val fingerprint = key_fingerprint()
        cached_state(exact, fingerprint)?.let { return it }
        if (now_ms() < unsupported_until) return AddressEntryState.INACTIVE
        return publish_mutex.withLock {
            cached_state(exact, fingerprint)?.let { return@withLock it }
            if (now_ms() < unsupported_until) return@withLock AddressEntryState.INACTIVE
            publish_forms(exact, fingerprint)
        }
    }

    private fun cached_state(exact: String, fingerprint: String): AddressEntryState? {
        val known = synchronized(published_states) { published_states[exact] } ?: return null
        return if (known.fingerprint == fingerprint && now_ms() < known.retry_at) known.state else null
    }

    suspend fun publish_address_entries(addresses: List<String>) {
        val unique = addresses
            .map { AddressIdentity.normalize(it) }
            .filter { it.isNotEmpty() && !is_primary_address(it) }
            .distinct()
        for (address in unique) {
            if (now_ms() < unsupported_until) return
            try {
                address_entry_state(address)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
            }
        }
    }

    fun account_sender_identity(): DerivedAddressKey? {
        val jwk = session_key_store.get_ratchet_identity_jwk()?.takeIf { it.isNotBlank() } ?: return null
        val public = session_key_store.get_ratchet_identity_public_b64()?.takeIf { it.isNotBlank() } ?: return null
        return DerivedAddressKey(jwk, public)
    }

    suspend fun resolve_sender_identity(sender_email: String): DerivedAddressKey? {
        if (is_primary_address(sender_email)) return account_sender_identity()
        return when (address_entry_state(sender_email)) {
            AddressEntryState.FAILED -> null
            AddressEntryState.INACTIVE -> account_sender_identity()
            AddressEntryState.ACTIVE ->
                AddressIdentity.derive_sender_identity(session_key_store.get_ratchet_identity_jwk(), sender_email)
        }
    }

    fun own_address_identity(sender_email: String, sender_identity: DerivedAddressKey): String? {
        if (is_primary_address(sender_email)) return null
        val account = session_key_store.get_ratchet_identity_public_b64()
        return sender_identity.identity_public.takeIf { it != account }
    }

    fun receiver_key_sets(address: String?): List<AddressKeySet> {
        if (address.isNullOrBlank()) return emptyList()
        return runCatching {
            AddressIdentity.receiver_key_sets(current_source(), previous_sources(), address)
        }.getOrDefault(emptyList())
    }

    fun is_own_address_identity_key(addresses: List<String>, identity_key: String?): Boolean {
        if (identity_key.isNullOrBlank() || addresses.isEmpty()) return false
        return runCatching {
            AddressIdentity.is_own_address_identity_key(account_identity_jwks(), addresses, identity_key)
        }.getOrDefault(false)
    }

    private sealed class Classification {
        data class Trusted(val trust: DirectoryTrust) : Classification()
        object Fallback : Classification()
    }

    private fun classify_scoped(
        conversation_id: String,
        email: String,
        bundle: PrekeyBundleResponse,
        session_identity: String?,
    ): Classification {
        val pinned = try {
            identity_pins.pinned_fingerprint(conversation_id)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            return Classification.Fallback
        }
        val rooted = runCatching { identity_pins.is_address_rooted(conversation_id) }.getOrDefault(false)
        val verification = AddressIdentity.verify_entry(
            email,
            bundle.kem_identity_key,
            bundle.signed_prekey,
            bundle.signed_prekey_signature,
            bundle.pq_kem_public_key,
        )
        if (!verification.valid) {
            return if (rooted) Classification.Trusted(DirectoryTrust.INVALID) else Classification.Fallback
        }
        if (pinned == null) {
            if (!session_identity.isNullOrBlank()) {
                return if (session_identity == bundle.kem_identity_key) {
                    Classification.Trusted(DirectoryTrust.ENTRY)
                } else {
                    Classification.Fallback
                }
            }
            val owner_pin = try {
                identity_pins.owner_key_pin(email)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                return Classification.Fallback
            }
            return if (owner_pin == null) Classification.Trusted(DirectoryTrust.ENTRY) else Classification.Fallback
        }
        if (pinned == identity_pins.fingerprint_of(bundle.kem_identity_key)) {
            return Classification.Trusted(DirectoryTrust.ENTRY)
        }
        if (verification.endorsers.any { identity_pins.fingerprint_of(it) == pinned }) {
            return Classification.Trusted(DirectoryTrust.ENDORSED)
        }
        return if (rooted) Classification.Trusted(DirectoryTrust.REPLACED) else Classification.Fallback
    }

    suspend fun resolve_recipient_bundle(
        username: String,
        email: String,
        conversation_id: String,
        session_identity: String? = null,
    ): ResolvedRecipientBundle? {
        val normalized = AddressIdentity.normalize(email)
        if (normalized.isEmpty()) {
            val account = ratchet_api.fetch_prekey_bundle(username, email) ?: return null
            return ResolvedRecipientBundle(account, scoped = false, trust = DirectoryTrust.ACCOUNT)
        }
        val first = ratchet_api.fetch_address_prekey_bundle(username, email)
            ?: ratchet_api.fetch_prekey_bundle(username, email)
            ?: return null
        if (first.address_scoped != true) {
            val rooted = runCatching { identity_pins.is_address_rooted(conversation_id) }.getOrDefault(false)
            return ResolvedRecipientBundle(
                first,
                scoped = false,
                trust = if (rooted) DirectoryTrust.DOWNGRADED else DirectoryTrust.ACCOUNT,
            )
        }
        return when (val classification = classify_scoped(conversation_id, normalized, first, session_identity)) {
            is Classification.Trusted -> ResolvedRecipientBundle(first, scoped = true, trust = classification.trust)
            Classification.Fallback -> {
                val account = ratchet_api.fetch_prekey_bundle(username, email) ?: return null
                if (account.address_scoped == true) return null
                ResolvedRecipientBundle(account, scoped = false, trust = DirectoryTrust.ACCOUNT)
            }
        }
    }

    suspend fun is_sender_entry_endorsed(
        username: String,
        sender_email: String,
        sender_identity_key: String,
        known_fingerprints: List<String>,
    ): Boolean {
        if (sender_identity_key.isBlank() || known_fingerprints.isEmpty()) return false
        val entry = try {
            ratchet_api.fetch_address_prekey_bundle(username, sender_email)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            null
        } ?: return false
        if (entry.address_scoped != true || entry.kem_identity_key != sender_identity_key) return false
        val verification = AddressIdentity.verify_entry(
            AddressIdentity.normalize(sender_email),
            entry.kem_identity_key,
            entry.signed_prekey,
            entry.signed_prekey_signature,
            entry.pq_kem_public_key,
        )
        if (!verification.valid) return false
        if (verification.endorsers.any { identity_pins.fingerprint_of(it) in known_fingerprints }) return true
        val account = try {
            ratchet_api.fetch_prekey_bundle(username, sender_email)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            null
        } ?: return false
        if (account.address_scoped == true) return false
        return identity_pins.fingerprint_of(account.kem_identity_key) in known_fingerprints
    }

    companion object {
        const val ADDRESS_IDENTITY_VERSION: Int = 1
        const val INACTIVE_RETRY_MS: Long = 10L * 60 * 1000
        const val ACTIVE_REFRESH_MS: Long = 6L * 60 * 60 * 1000
        const val FAILED_RETRY_MS: Long = 60L * 1000
    }
}
