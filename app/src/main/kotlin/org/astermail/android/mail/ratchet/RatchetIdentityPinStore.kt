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
        confirmed: Boolean = false,
    ): IdentityPinOutcome {
        if (conversation_id.isBlank() || sender_identity_key_b64.isBlank()) {
            return IdentityPinOutcome.UNCHANGED
        }
        val current = fingerprint(sender_identity_key_b64) ?: return IdentityPinOutcome.UNCHANGED
        val scope = refresh_scope()
        val key = scoped(scope, pin_key(conversation_id))

        return mutex.withLock {
            val stored = read_string(scope, pin_key(conversation_id))
            when (RatchetIdentityPinRules.decide(stored, current, confirmed)) {
                IdentityPinDecision.PIN_FIRST -> {
                    prefs.edit().putString(key, current).commit()
                    IdentityPinOutcome.FIRST_CONTACT
                }
                IdentityPinDecision.KEEP -> IdentityPinOutcome.UNCHANGED
                IdentityPinDecision.REPLACE -> {
                    synchronized(pending_guard) {
                        val updated = pending.value.filterNot { it.conversation_id == conversation_id }
                        prefs.edit()
                            .putString(key, current)
                            .putString(scoped(scope, pending_key), encode_pending(updated))
                            .commit()
                        pending.value = updated
                    }
                    IdentityPinOutcome.CHANGED
                }
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
        val normalized = recipient_email.trim().lowercase(java.util.Locale.ROOT)
        if (normalized.isBlank()) return false
        return read_boolean(refresh_scope(), binding_key(normalized))
    }

    suspend fun record_prekey_binding_verified(recipient_email: String): Unit = mutex.withLock {
        val normalized = recipient_email.trim().lowercase(java.util.Locale.ROOT)
        if (normalized.isBlank()) return@withLock
        val scope = refresh_scope()
        if (read_boolean(scope, binding_key(normalized))) return@withLock
        prefs.edit().putBoolean(scoped(scope, binding_key(normalized)), true).commit()
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
        val normalized = sender_email.trim().lowercase(java.util.Locale.ROOT)
        if (normalized.isBlank()) return
        val scope = refresh_scope()
        synchronized(pending_guard) {
            val accepted = pending.value.filter {
                it.sender_email.trim().lowercase(java.util.Locale.ROOT) == normalized
            }
            if (accepted.isEmpty()) return
            val updated = pending.value.filterNot {
                it.sender_email.trim().lowercase(java.util.Locale.ROOT) == normalized
            }
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
                    editor.putString(scoped(scope, pin_key(change.conversation_id)), change.current_fingerprint)
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
        synchronized(pending_guard) {
            if (loaded_scope == account_id.trim()) {
                pending.value = emptyList()
                loaded_scope = null
            }
        }
    }

    suspend fun clear(): Unit = mutex.withLock {
        prefs.edit().clear().commit()
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

    private fun post_quantum_key(conversation_id: String): String = "pq_$conversation_id"

    private fun x3dh_key(conversation_id: String): String = "x3dh_$conversation_id"

    private fun binding_key(recipient_email: String): String = "binding_$recipient_email"

    private fun pq_consumed_key(key_id: Int): String = "pqused_$key_id"

    companion object {
        private const val prefs_name = "aster_ratchet_identity_pins"
        private const val separator = "|"
        private const val max_tracked_bootstraps = 64
        private const val pending_key = "pending_identity_changes"
        private const val max_pending_changes = 50
    }
}
