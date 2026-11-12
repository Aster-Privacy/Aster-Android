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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore

enum class IdentityPinOutcome {
    FIRST_CONTACT,
    UNCHANGED,
    CHANGED,
}

data class IdentityChange(
    val conversation_id: String,
    val sender_email: String,
    val previous_fingerprint: String,
    val current_fingerprint: String,
    val observed_at: Long,
)

@Singleton
class RatchetIdentityPinStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val session_key_store: SessionKeyStore,
) {

    private val prefs = SecurePrefs.open(context, prefs_name)
    private val mutex = Mutex()
    private val pending_guard = Any()

    @Volatile
    private var loaded_scope: String? = null

    @Volatile
    private var indexed_names: Pair<String, Map<String, Set<String>>>? = null

    private val pending = MutableStateFlow<List<IdentityChange>>(emptyList())
    val unacknowledged_changes: StateFlow<List<IdentityChange>> = pending.asStateFlow()

    init {
        runCatching { refresh_scope() }
    }

    suspend fun record(
        conversation_id: String,
        sender_email: String,
        sender_identity_key_b64: String,
        observed_at: Long,
    ): IdentityPinOutcome {
        if (conversation_id.isBlank() || sender_identity_key_b64.isBlank()) {
            return IdentityPinOutcome.UNCHANGED
        }
        val current = fingerprint(sender_identity_key_b64) ?: return IdentityPinOutcome.UNCHANGED
        val scope = refresh_scope()
        val key = scoped(scope, pin_key(conversation_id))

        return mutex.withLock {
            val stored = read_string(scope, pin_key(conversation_id))
            when (RatchetIdentityPinRules.decide(stored, current)) {
                IdentityPinDecision.PIN_FIRST -> {
                    prefs.edit().putString(key, current).commit()
                    IdentityPinOutcome.FIRST_CONTACT
                }
                IdentityPinDecision.KEEP -> IdentityPinOutcome.UNCHANGED
                IdentityPinDecision.FLAG_DRIFT -> {
                    synchronized(pending_guard) {
                        val updated = pending.value
                            .filterNot { it.conversation_id == conversation_id } +
                            IdentityChange(
                                conversation_id = conversation_id,
                                sender_email = sender_email,
                                previous_fingerprint = stored.orEmpty(),
                                current_fingerprint = current,
                                observed_at = observed_at,
                            )
                        prefs.edit()
                            .putString(scoped(scope, pending_key), encode_pending(updated))
                            .commit()
                        pending.value = updated
                    }
                    IdentityPinOutcome.CHANGED
                }
            }
        }
    }

    fun evaluate(conversation_id: String, identity_key_b64: String): IdentityPinOutcome {
        if (conversation_id.isBlank() || identity_key_b64.isBlank()) return IdentityPinOutcome.CHANGED
        val current = fingerprint(identity_key_b64) ?: return IdentityPinOutcome.CHANGED
        val scope = refresh_scope()
        val stored = read_string(scope, pin_key(conversation_id)) ?: return IdentityPinOutcome.FIRST_CONTACT
        return if (stored == current) IdentityPinOutcome.UNCHANGED else IdentityPinOutcome.CHANGED
    }

    suspend fun pin_if_absent(conversation_id: String, identity_key_b64: String): Unit = mutex.withLock {
        if (conversation_id.isBlank()) return@withLock
        val current = fingerprint(identity_key_b64) ?: return@withLock
        val scope = refresh_scope()
        if (read_string(scope, pin_key(conversation_id)) != null) return@withLock
        prefs.edit().putString(scoped(scope, pin_key(conversation_id)), current).commit()
    }

    suspend fun flag_identity_change(
        conversation_id: String,
        peer_email: String,
        identity_key_b64: String,
        observed_at: Long,
    ): Unit = mutex.withLock {
        if (conversation_id.isBlank()) return@withLock
        val current = fingerprint(identity_key_b64) ?: return@withLock
        val scope = refresh_scope()
        val stored = read_string(scope, pin_key(conversation_id)) ?: return@withLock
        if (stored == current) return@withLock
        synchronized(pending_guard) {
            val updated = pending.value.filterNot { it.conversation_id == conversation_id } +
                IdentityChange(
                    conversation_id = conversation_id,
                    sender_email = peer_email,
                    previous_fingerprint = stored,
                    current_fingerprint = current,
                    observed_at = observed_at,
                )
            prefs.edit().putString(scoped(scope, pending_key), encode_pending(updated)).commit()
            pending.value = updated
        }
    }

    fun sender_continuity(
        conversation_id: String,
        sender_email: String,
        sender_identity_key_b64: String,
    ): SenderIdentityContinuity {
        if (sender_identity_key_b64.isBlank()) return SenderIdentityContinuity.NEW
        val current = fingerprint(sender_identity_key_b64) ?: return SenderIdentityContinuity.NEW
        val scope = refresh_scope()
        val contact = contact_of(sender_email)
        val remembered = if (contact == null) emptyList() else read_history(scope, contact)
        val pinned = if (conversation_id.isBlank()) {
            Result.success(null)
        } else {
            runCatching { read_string(scope, pin_key(conversation_id)) }
        }
        return RatchetIdentityPinRules.stored_continuity(
            remembered_fingerprints = remembered,
            pinned_fingerprint = pinned.getOrNull(),
            pin_readable = pinned.isSuccess,
            current_fingerprint = current,
        )
    }

    suspend fun remember_sender_identity(sender_email: String, sender_identity_key_b64: String): Unit = mutex.withLock {
        val contact = contact_of(sender_email) ?: return@withLock
        val current = fingerprint(sender_identity_key_b64) ?: return@withLock
        val scope = refresh_scope()
        val existing = read_history(scope, contact) ?: return@withLock
        val saved = runCatching { remembered_fingerprints(scope, contact.id) }.getOrNull() ?: return@withLock
        if (existing.lastOrNull() == current && saved == existing) return@withLock
        val updated = RatchetIdentityPinRules.remember_fingerprint(existing, current, max_remembered_identities)
        prefs.edit().putString(scoped(scope, history_key(contact.id)), updated.joinToString(separator)).commit()
    }

    suspend fun flag_sender_rotation(
        conversation_id: String,
        sender_email: String,
        sender_identity_key_b64: String,
        observed_at: Long,
    ): Unit = mutex.withLock {
        if (conversation_id.isBlank()) return@withLock
        val current = fingerprint(sender_identity_key_b64) ?: return@withLock
        val scope = refresh_scope()
        val pinned = read_string(scope, pin_key(conversation_id))
        val remembered = contact_of(sender_email)?.let { read_history(scope, it) }.orEmpty()
        val previous = (listOfNotNull(pinned) + remembered.asReversed())
            .firstOrNull { it.isNotBlank() && it != current }
            ?: return@withLock
        synchronized(pending_guard) {
            val updated = pending.value.filterNot { it.conversation_id == conversation_id } +
                IdentityChange(
                    conversation_id = conversation_id,
                    sender_email = sender_email,
                    previous_fingerprint = previous,
                    current_fingerprint = current,
                    observed_at = observed_at,
                )
            prefs.edit().putString(scoped(scope, pending_key), encode_pending(updated)).commit()
            pending.value = updated
        }
    }

    private fun remembered_fingerprints(scope: String, normalized_email: String): List<String> =
        read_string(scope, history_key(normalized_email))
            ?.split(separator)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun read_history(scope: String, contact: PinContact): List<String>? {
        val stored = read_contact_strings(scope, history_prefix, contact) ?: return null
        val ordered = stored.filterNot { it.first == contact.id } + stored.filter { it.first == contact.id }
        return RatchetIdentityPinRules.merge_remembered(
            ordered.map { entry -> entry.second.split(separator) },
            max_remembered_identities,
        )
    }

    private fun contact_of(email: String): PinContact? =
        normalize_email(email)?.let { PinIdentity.describe(email) }

    private fun stored_contact_names(scope: String): Map<String, Set<String>>? {
        indexed_names?.takeIf { it.first == scope }?.let { return it.second }
        val built = runCatching {
            val own_prefix = if (scope.isEmpty()) null else RatchetIdentityPinRules.account_key_prefix(scope)
            val out = HashMap<String, MutableSet<String>>()
            for (key in prefs.all.keys) {
                val legacy = when {
                    own_prefix != null && key.startsWith(own_prefix) -> key.removePrefix(own_prefix)
                    RatchetIdentityPinRules.is_account_scoped(key) -> continue
                    else -> key
                }
                val prefix = contact_prefixes.firstOrNull { legacy.startsWith(it) } ?: continue
                val name = legacy.removePrefix(prefix)
                if (name.isBlank()) continue
                out.getOrPut(prefix + PinIdentity.canonical_id(name)) { mutableSetOf() }.add(name)
            }
            out
        }.getOrNull() ?: return null
        indexed_names = scope to built
        return built
    }

    private fun contact_names(scope: String, prefix: String, contact: PinContact): List<String>? {
        val stored = stored_contact_names(scope) ?: return null
        return (listOf(contact.id) + contact.spellings + stored[prefix + contact.id].orEmpty()).distinct()
    }

    private fun read_contact_strings(
        scope: String,
        prefix: String,
        contact: PinContact,
    ): List<Pair<String, String>>? = runCatching {
        val names = contact_names(scope, prefix, contact) ?: return null
        names.mapNotNull { name ->
            read_string(scope, prefix + name)?.takeIf { it.isNotBlank() }?.let { name to it }
        }
    }.getOrNull()

    private data class SavedKeyEntry(val state: SavedKeyState, val values: List<String>)

    private fun read_owner_entry(scope: String, contact: PinContact): SavedKeyEntry {
        val stored = read_contact_strings(scope, owner_prefix, contact)
            ?: return SavedKeyEntry(SavedKeyState.UNREADABLE, emptyList())
        val values = stored.map { it.second }.distinctBy { it.uppercase(java.util.Locale.ROOT) }
        return when (values.size) {
            0 -> SavedKeyEntry(SavedKeyState.ABSENT, values)
            1 -> {
                if (stored.none { it.first == contact.id }) {
                    runCatching {
                        prefs.edit().putString(scoped(scope, owner_key(contact.id)), values.first()).apply()
                    }
                }
                SavedKeyEntry(SavedKeyState.FOUND, values)
            }
            else -> SavedKeyEntry(SavedKeyState.CONFLICT, values)
        }
    }

    fun sender_key_state(sender_email: String): SavedKeyState {
        val contact = contact_of(sender_email) ?: return SavedKeyState.ABSENT
        return runCatching { read_owner_entry(refresh_scope(), contact).state }
            .getOrDefault(SavedKeyState.UNREADABLE)
    }

    suspend fun check_sender_key(
        sender_email: String,
        served_fingerprint: String,
        observed_at: Long,
    ): SavedKeyCheck = mutex.withLock {
        val contact = contact_of(sender_email) ?: return@withLock SavedKeyCheck.UNREADABLE
        runCatching {
            val scope = refresh_scope()
            val entry = read_owner_entry(scope, contact)
            val check = RatchetIdentityPinRules.check_saved_key(
                entry.state,
                entry.values.firstOrNull(),
                served_fingerprint,
            )
            when (check) {
                SavedKeyCheck.FIRST -> {
                    val saved = prefs.edit()
                        .putString(scoped(scope, owner_key(contact.id)), served_fingerprint)
                        .commit()
                    if (saved) SavedKeyCheck.FIRST else SavedKeyCheck.UNREADABLE
                }
                SavedKeyCheck.CHANGED -> {
                    flag_owner_change(scope, contact, entry, served_fingerprint, observed_at)
                    SavedKeyCheck.CHANGED
                }
                else -> check
            }
        }.getOrDefault(SavedKeyCheck.UNREADABLE)
    }

    private fun flag_owner_change(
        scope: String,
        contact: PinContact,
        entry: SavedKeyEntry,
        served_fingerprint: String,
        observed_at: Long,
    ) {
        val stored = entry.values.firstOrNull { !it.equals(served_fingerprint, ignoreCase = true) } ?: return
        val marker = owner_change_marker(contact.id)
        synchronized(pending_guard) {
            val known = pending.value.firstOrNull { it.conversation_id == marker }
            if (known != null && known.current_fingerprint.equals(served_fingerprint, ignoreCase = true)) return
            val updated = pending.value.filterNot { it.conversation_id == marker } +
                IdentityChange(
                    conversation_id = marker,
                    sender_email = contact.id,
                    previous_fingerprint = stored,
                    current_fingerprint = served_fingerprint,
                    observed_at = observed_at,
                )
            prefs.edit().putString(scoped(scope, pending_key), encode_pending(updated)).commit()
            pending.value = updated
        }
    }

    suspend fun is_replayed_bootstrap(conversation_id: String, ephemeral_key_b64: String): Boolean {
        if (conversation_id.isBlank() || ephemeral_key_b64.isBlank()) return false
        return mutex.withLock {
            seen_bootstraps(refresh_scope(), conversation_id).contains(ephemeral_key_b64)
        }
    }

    suspend fun record_bootstrap(conversation_id: String, ephemeral_key_b64: String): Unit = mutex.withLock {
        if (conversation_id.isBlank() || ephemeral_key_b64.isBlank()) return@withLock
        val scope = refresh_scope()
        val existing = seen_bootstraps(scope, conversation_id)
        if (existing.contains(ephemeral_key_b64)) return@withLock
        val updated = (existing + ephemeral_key_b64).takeLast(max_tracked_bootstraps)
        prefs.edit().putString(scoped(scope, bootstrap_key(conversation_id)), updated.joinToString(separator)).commit()
    }

    fun highest_x3dh_version(conversation_id: String): Int {
        if (conversation_id.isBlank()) return 0
        return read_int(refresh_scope(), x3dh_key(conversation_id))
    }

    suspend fun record_x3dh_version(conversation_id: String, version: Int): Unit = mutex.withLock {
        if (conversation_id.isBlank() || version <= 0) return@withLock
        val scope = refresh_scope()
        if (read_int(scope, x3dh_key(conversation_id)) >= version) return@withLock
        prefs.edit().putInt(scoped(scope, x3dh_key(conversation_id)), version).commit()
    }

    fun is_post_quantum_established(conversation_id: String): Boolean {
        if (conversation_id.isBlank()) return false
        return read_boolean(refresh_scope(), post_quantum_key(conversation_id))
    }

    suspend fun record_post_quantum(conversation_id: String): Unit = mutex.withLock {
        if (conversation_id.isBlank()) return@withLock
        val scope = refresh_scope()
        if (read_boolean(scope, post_quantum_key(conversation_id))) return@withLock
        prefs.edit().putBoolean(scoped(scope, post_quantum_key(conversation_id)), true).commit()
    }

    fun is_prekey_binding_verified(recipient_email: String): Boolean {
        val contact = contact_of(recipient_email) ?: return false
        val scope = refresh_scope()
        val names = contact_names(scope, binding_prefix, contact) ?: return false
        return names.any { read_boolean(scope, binding_key(it)) }
    }

    suspend fun record_prekey_binding_verified(recipient_email: String): Unit = mutex.withLock {
        val normalized = contact_of(recipient_email)?.id ?: return@withLock
        val scope = refresh_scope()
        if (read_boolean(scope, binding_key(normalized))) return@withLock
        prefs.edit().putBoolean(scoped(scope, binding_key(normalized)), true).commit()
    }

    fun owner_key_pin(recipient_email: String): String? {
        val contact = contact_of(recipient_email) ?: return null
        val entry = read_owner_entry(refresh_scope(), contact)
        return when (entry.state) {
            SavedKeyState.ABSENT -> null
            SavedKeyState.FOUND -> entry.values.first()
            SavedKeyState.CONFLICT -> entry.values.joinToString(separator)
            SavedKeyState.UNREADABLE -> throw IllegalStateException("saved contact key unavailable")
        }
    }

    suspend fun pin_owner_key_if_absent(recipient_email: String, fingerprint: String): Boolean = mutex.withLock {
        val contact = contact_of(recipient_email) ?: return@withLock false
        if (fingerprint.isBlank()) return@withLock false
        val scope = refresh_scope()
        when (read_owner_entry(scope, contact).state) {
            SavedKeyState.FOUND -> true
            SavedKeyState.CONFLICT, SavedKeyState.UNREADABLE -> false
            SavedKeyState.ABSENT -> runCatching {
                prefs.edit().putString(scoped(scope, owner_key(contact.id)), fingerprint).commit()
            }.getOrDefault(false)
        }
    }

    suspend fun flag_owner_key_change(
        recipient_email: String,
        served_fingerprint: String,
        observed_at: Long,
    ): Unit = mutex.withLock {
        val contact = contact_of(recipient_email) ?: return@withLock
        if (served_fingerprint.isBlank()) return@withLock
        val scope = refresh_scope()
        flag_owner_change(scope, contact, read_owner_entry(scope, contact), served_fingerprint, observed_at)
    }

    fun pq_prekey_consumer(key_id: Int): String? =
        prefs.getString(scoped(refresh_scope(), pq_consumed_key(key_id)), null)?.takeIf { it.isNotBlank() }

    suspend fun record_pq_prekey_consumed(key_id: Int, ephemeral_key_b64: String): Unit = mutex.withLock {
        if (ephemeral_key_b64.isBlank()) return@withLock
        val key = scoped(refresh_scope(), pq_consumed_key(key_id))
        if (!prefs.getString(key, null).isNullOrBlank()) return@withLock
        prefs.edit().putString(key, ephemeral_key_b64).commit()
    }

    private fun seen_bootstraps(scope: String, conversation_id: String): List<String> =
        read_string(scope, bootstrap_key(conversation_id))
            ?.split(separator)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun acknowledge(conversation_id: String) {
        val scope = refresh_scope()
        synchronized(pending_guard) {
            val accepted = pending.value.filter { it.conversation_id == conversation_id }
            if (accepted.isEmpty()) return
            val updated = pending.value.filterNot { it.conversation_id == conversation_id }
            pending.value = updated
            adopt(scope, accepted, updated)
        }
    }

    fun acknowledge_sender(sender_email: String) {
        val normalized = normalize_email(sender_email)?.let { PinIdentity.canonical_id(it) } ?: return
        val scope = refresh_scope()
        synchronized(pending_guard) {
            val accepted = pending.value.filter { PinIdentity.canonical_id(it.sender_email) == normalized }
            if (accepted.isEmpty()) return
            val updated = pending.value.filterNot { PinIdentity.canonical_id(it.sender_email) == normalized }
            pending.value = updated
            adopt(scope, accepted, updated)
        }
    }

    fun acknowledge_all() {
        val scope = refresh_scope()
        synchronized(pending_guard) {
            val accepted = pending.value
            if (accepted.isEmpty()) return
            pending.value = emptyList()
            adopt(scope, accepted, emptyList())
        }
    }

    private fun adopt(scope: String, accepted: List<IdentityChange>, remaining: List<IdentityChange>) {
        runCatching {
            val editor = prefs.edit()
            if (remaining.isEmpty()) {
                editor.remove(scoped(scope, pending_key))
            } else {
                editor.putString(scoped(scope, pending_key), encode_pending(remaining))
            }
            accepted.forEach { change ->
                if (change.conversation_id.isNotBlank() && change.current_fingerprint.isNotBlank()) {
                    val pinned_keys = if (change.conversation_id.startsWith(owner_change_prefix)) {
                        val contact = PinIdentity.describe(change.conversation_id.removePrefix(owner_change_prefix))
                        (contact_names(scope, owner_prefix, contact) ?: listOf(contact.id)).map { owner_key(it) }
                    } else {
                        listOf(pin_key(change.conversation_id))
                    }
                    pinned_keys.forEach { editor.putString(scoped(scope, it), change.current_fingerprint) }
                }
            }
            editor.apply()
        }
    }

    suspend fun clear_account(account_id: String): Unit = mutex.withLock {
        if (account_id.isBlank()) return@withLock
        val keys = runCatching { prefs.all.keys.toList() }.getOrDefault(emptyList())
        val editor = prefs.edit()
        keys.filter { RatchetIdentityPinRules.belongs_to_account(it, account_id) }.forEach { editor.remove(it) }
        editor.commit()
        indexed_names = null
        synchronized(pending_guard) {
            if (loaded_scope == account_id.trim()) {
                pending.value = emptyList()
                loaded_scope = null
            }
        }
    }

    suspend fun clear(): Unit = mutex.withLock {
        prefs.edit().clear().commit()
        indexed_names = null
        synchronized(pending_guard) {
            pending.value = emptyList()
            loaded_scope = null
        }
    }

    private fun refresh_scope(): String {
        val scope = session_key_store.get_user_id()?.trim().orEmpty()
        if (scope != loaded_scope) {
            synchronized(pending_guard) {
                if (scope != loaded_scope) {
                    loaded_scope = scope
                    pending.value = load_pending(scope)
                }
            }
        }
        return scope
    }

    private fun scoped(scope: String, legacy_key: String): String =
        RatchetIdentityPinRules.scoped_key(scope, legacy_key)

    private fun read_string(scope: String, legacy_key: String): String? {
        val key = scoped(scope, legacy_key)
        prefs.getString(key, null)?.let { return it }
        if (scope.isEmpty()) return null
        val legacy = prefs.getString(legacy_key, null) ?: return null
        prefs.edit().putString(key, legacy).apply()
        return legacy
    }

    private fun read_int(scope: String, legacy_key: String): Int {
        val key = scoped(scope, legacy_key)
        if (prefs.contains(key)) return prefs.getInt(key, 0)
        if (scope.isEmpty() || !prefs.contains(legacy_key)) return 0
        val legacy = prefs.getInt(legacy_key, 0)
        prefs.edit().putInt(key, legacy).apply()
        return legacy
    }

    private fun read_boolean(scope: String, legacy_key: String): Boolean {
        val key = scoped(scope, legacy_key)
        if (prefs.contains(key)) return prefs.getBoolean(key, false)
        if (scope.isEmpty() || !prefs.contains(legacy_key)) return false
        val legacy = prefs.getBoolean(legacy_key, false)
        prefs.edit().putBoolean(key, legacy).apply()
        return legacy
    }

    private fun load_pending(scope: String): List<IdentityChange> = runCatching {
        val raw = read_string(scope, pending_key) ?: return@runCatching emptyList()
        val array = org.json.JSONArray(raw)
        val out = ArrayList<IdentityChange>(array.length())
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val conversation_id = row.optString("c")
            if (conversation_id.isBlank()) continue
            out.add(
                IdentityChange(
                    conversation_id = conversation_id,
                    sender_email = row.optString("s"),
                    previous_fingerprint = row.optString("p"),
                    current_fingerprint = row.optString("n"),
                    observed_at = row.optLong("t"),
                ),
            )
        }
        out.takeLast(max_pending_changes)
    }.getOrDefault(emptyList())

    private fun encode_pending(changes: List<IdentityChange>): String {
        val array = org.json.JSONArray()
        changes.takeLast(max_pending_changes).forEach { change ->
            array.put(
                org.json.JSONObject()
                    .put("c", change.conversation_id)
                    .put("s", change.sender_email)
                    .put("p", change.previous_fingerprint)
                    .put("n", change.current_fingerprint)
                    .put("t", change.observed_at),
            )
        }
        return array.toString()
    }

    private fun fingerprint(sender_identity_key_b64: String): String? = runCatching {
        val raw = RatchetCrypto.b64_decode(sender_identity_key_b64)
        RatchetCrypto.b64_encode(RatchetCrypto.sha256(raw))
    }.getOrNull()

    private fun pin_key(conversation_id: String): String = "pin_$conversation_id"

    private fun bootstrap_key(conversation_id: String): String = "boot_$conversation_id"

    private fun history_key(normalized_email: String): String = "$history_prefix$normalized_email"

    private fun post_quantum_key(conversation_id: String): String = "pq_$conversation_id"

    private fun x3dh_key(conversation_id: String): String = "x3dh_$conversation_id"

    private fun binding_key(recipient_email: String): String = "$binding_prefix$recipient_email"

    private fun pq_consumed_key(key_id: Int): String = "pqused_$key_id"

    private fun owner_key(recipient_email: String): String = "$owner_prefix$recipient_email"

    private fun owner_change_marker(recipient_email: String): String = "$owner_change_prefix$recipient_email"

    private fun normalize_email(email: String): String? =
        email.trim().lowercase(java.util.Locale.ROOT).takeIf { it.isNotBlank() }

    companion object {
        private const val prefs_name = "aster_ratchet_identity_pins"
        private const val separator = "|"
        private const val max_tracked_bootstraps = 64
        private const val max_remembered_identities = 16
        private const val pending_key = "pending_identity_changes"
        private const val max_pending_changes = 50
        private const val owner_change_prefix = "owner:"
        private const val owner_prefix = "owner_"
        private const val history_prefix = "hist_"
        private const val binding_prefix = "binding_"
        private val contact_prefixes = listOf(owner_prefix, history_prefix, binding_prefix)
    }
}
