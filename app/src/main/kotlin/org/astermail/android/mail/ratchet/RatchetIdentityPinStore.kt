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
) {

    private val prefs = SecurePrefs.open(context, prefs_name)
    private val mutex = Mutex()

    private val pending = MutableStateFlow(load_pending())
    val unacknowledged_changes: StateFlow<List<IdentityChange>> = pending.asStateFlow()

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
        val key = pin_key(conversation_id)

        val outcome = mutex.withLock {
            val stored = prefs.getString(key, null)
            when {
                stored == null -> {
                    prefs.edit().putString(key, current).commit()
                    IdentityPinOutcome.FIRST_CONTACT
                }
                stored == current -> IdentityPinOutcome.UNCHANGED
                else -> {
                    val updated = pending.value
                        .filterNot { it.conversation_id == conversation_id } +
                        IdentityChange(
                            conversation_id = conversation_id,
                            sender_email = sender_email,
                            previous_fingerprint = stored,
                            current_fingerprint = current,
                            observed_at = observed_at,
                        )
                    prefs.edit()
                        .putString(key, current)
                        .putString(pending_key, encode_pending(updated))
                        .commit()
                    pending.value = updated
                    IdentityPinOutcome.CHANGED
                }
            }
        }
        return outcome
    }

    suspend fun is_replayed_bootstrap(conversation_id: String, ephemeral_key_b64: String): Boolean {
        if (conversation_id.isBlank() || ephemeral_key_b64.isBlank()) return false
        return mutex.withLock {
            seen_bootstraps(conversation_id).contains(ephemeral_key_b64)
        }
    }

    suspend fun record_bootstrap(conversation_id: String, ephemeral_key_b64: String): Unit = mutex.withLock {
        if (conversation_id.isBlank() || ephemeral_key_b64.isBlank()) return@withLock
        val existing = seen_bootstraps(conversation_id)
        if (existing.contains(ephemeral_key_b64)) return@withLock
        val updated = (existing + ephemeral_key_b64).takeLast(max_tracked_bootstraps)
        prefs.edit().putString(bootstrap_key(conversation_id), updated.joinToString(separator)).commit()
    }

    fun highest_x3dh_version(conversation_id: String): Int {
        if (conversation_id.isBlank()) return 0
        return prefs.getInt(x3dh_key(conversation_id), 0)
    }

    suspend fun record_x3dh_version(conversation_id: String, version: Int): Unit = mutex.withLock {
        if (conversation_id.isBlank() || version <= 0) return@withLock
        val key = x3dh_key(conversation_id)
        if (prefs.getInt(key, 0) >= version) return@withLock
        prefs.edit().putInt(key, version).commit()
    }

    fun is_post_quantum_established(conversation_id: String): Boolean {
        if (conversation_id.isBlank()) return false
        return prefs.getBoolean(post_quantum_key(conversation_id), false)
    }

    suspend fun record_post_quantum(conversation_id: String): Unit = mutex.withLock {
        if (conversation_id.isBlank()) return@withLock
        val key = post_quantum_key(conversation_id)
        if (prefs.getBoolean(key, false)) return@withLock
        prefs.edit().putBoolean(key, true).commit()
    }

    fun is_prekey_binding_verified(recipient_email: String): Boolean {
        val normalized = recipient_email.trim().lowercase()
        if (normalized.isBlank()) return false
        return prefs.getBoolean(binding_key(normalized), false)
    }

    suspend fun record_prekey_binding_verified(recipient_email: String): Unit = mutex.withLock {
        val normalized = recipient_email.trim().lowercase()
        if (normalized.isBlank()) return@withLock
        val key = binding_key(normalized)
        if (prefs.getBoolean(key, false)) return@withLock
        prefs.edit().putBoolean(key, true).commit()
    }

    private fun seen_bootstraps(conversation_id: String): List<String> =
        prefs.getString(bootstrap_key(conversation_id), null)
            ?.split(separator)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun acknowledge(conversation_id: String) {
        val updated = pending.value.filterNot { it.conversation_id == conversation_id }
        if (updated.size == pending.value.size) return
        pending.value = updated
        runCatching { prefs.edit().putString(pending_key, encode_pending(updated)).apply() }
    }

    fun acknowledge_sender(sender_email: String) {
        val normalized = sender_email.trim().lowercase()
        if (normalized.isBlank()) return
        val updated = pending.value.filterNot { it.sender_email.trim().lowercase() == normalized }
        if (updated.size == pending.value.size) return
        pending.value = updated
        runCatching { prefs.edit().putString(pending_key, encode_pending(updated)).apply() }
    }

    fun acknowledge_all() {
        if (pending.value.isEmpty()) return
        pending.value = emptyList()
        runCatching { prefs.edit().remove(pending_key).apply() }
    }

    suspend fun clear(): Unit = mutex.withLock {
        prefs.edit().clear().commit()
        pending.value = emptyList()
    }

    private fun load_pending(): List<IdentityChange> = runCatching {
        val raw = prefs.getString(pending_key, null) ?: return@runCatching emptyList()
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

    companion object {
        private const val prefs_name = "aster_ratchet_identity_pins"
        private const val separator = "|"
        private const val max_tracked_bootstraps = 64
        private const val pending_key = "pending_identity_changes"
        private const val max_pending_changes = 50
    }
}
