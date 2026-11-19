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

import org.astermail.android.crypto.StorageWriteKey
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore

@Singleton
class RatchetStateStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val session_key_store: SessionKeyStore,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val prefs = SecurePrefs.open(context, prefs_name)
    private val mutex = Mutex()

    suspend fun load(conversation_id: String): RatchetState? = mutex.withLock {
        val raw = prefs.getString(key_for(conversation_id), null) ?: return null
        try {
            json.decodeFromString<RatchetState>(raw)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun save(state: RatchetState): Unit = mutex.withLock {
        val encoded = json.encodeToString(state)
        prefs.edit().putString(key_for(state.conversation_id), encoded).commit()
    }

    suspend fun delete(conversation_id: String): Unit = mutex.withLock {
        prefs.edit().remove(key_for(conversation_id)).commit()
    }

    suspend fun sync_floor(conversation_id: String): Long = mutex.withLock {
        prefs.getLong(floor_key_for(conversation_id), 0L)
    }

    suspend fun raise_sync_floor(conversation_id: String, sync_version: Long): Unit = mutex.withLock {
        val key = floor_key_for(conversation_id)
        if (sync_version > prefs.getLong(key, 0L)) {
            prefs.edit().putLong(key, sync_version).commit()
        }
    }

    fun derive_state_encryption_key(): ByteArray? {
        val master = write_master_key() ?: return null
        return try {
            derive_state_key(master)
        } finally {
            master.fill(0)
        }
    }

    fun write_master_key(): ByteArray? =
        StorageWriteKey.write_key(session_key_store)

    fun state_encryption_key_candidates(): List<ByteArray> =
        master_key_candidates().map { master ->
            try {
                derive_state_key(master)
            } finally {
                master.fill(0)
            }
        }

    fun derive_sync_keys(): RatchetSyncKeys? {
        val user_id = session_key_store.get_user_id()?.takeIf { it.isNotEmpty() } ?: return null
        val master = write_master_key() ?: return null
        return try {
            RatchetSyncKeys(derive_sync_state_key(master), derive_state_key(master), user_id)
        } finally {
            master.fill(0)
        }
    }

    fun retired_state_key_candidates(): List<ByteArray> {
        val current = write_master_key()
        return try {
            master_key_candidates().flatMap { master ->
                try {
                    if (current != null && master.contentEquals(current)) {
                        emptyList()
                    } else if (StorageWriteKey.is_public_mail_kek(master)) {
                        emptyList()
                    } else {
                        listOf(derive_sync_state_key(master), derive_state_key(master))
                    }
                } finally {
                    master.fill(0)
                }
            }
        } finally {
            current?.fill(0)
        }
    }

    private fun derive_sync_state_key(master: ByteArray): ByteArray = sync_state_key_from_master(master)

    private fun derive_state_key(master: ByteArray): ByteArray = state_key_from_master(master)

    fun master_key_candidates(): List<ByteArray> {
        val candidates = mutableListOf<ByteArray>()
        val seen = mutableSetOf<String>()
        fun offer(key: ByteArray?) {
            if (key == null || key.size != 32) return
            if (!seen.add(RatchetCrypto.b64_encode(key))) {
                key.fill(0)
                return
            }
            candidates.add(key)
        }
        offer(StorageWriteKey.write_key(session_key_store))
        StorageWriteKey.read_keys(session_key_store).forEach { offer(it) }
        return candidates
    }

    fun note_master_key_use(master: ByteArray) {
        StorageWriteKey.note_if_public(session_key_store, master)
    }

    private fun key_for(conversation_id: String): String = "ratchet_state_$conversation_id"

    private fun floor_key_for(conversation_id: String): String = "ratchet_sync_floor_$conversation_id"

    companion object {
        private const val prefs_name = "aster_ratchet_state_v1"

        fun sync_state_key_from_master(master: ByteArray): ByteArray =
            RatchetCrypto.hkdf_sha256(
                ikm = master,
                salt = "Aster Mail_Ratchet_State_Sync".toByteArray(Charsets.UTF_8),
                info = "ratchet_state_sync_key_v3".toByteArray(Charsets.UTF_8),
                length = 32,
            )

        fun state_key_from_master(master: ByteArray): ByteArray =
            RatchetCrypto.hkdf_sha256(
                ikm = master,
                salt = "Aster Mail_Ratchet_State_Encryption".toByteArray(Charsets.UTF_8),
                info = "ratchet_state_key".toByteArray(Charsets.UTF_8),
                length = 32,
            )

        fun is_public_state_key(key: ByteArray): Boolean =
            StorageWriteKey.public_mail_kek_raws().any { raw ->
                val derived = listOf(state_key_from_master(raw), sync_state_key_from_master(raw))
                try {
                    derived.any { java.security.MessageDigest.isEqual(it, key) }
                } finally {
                    derived.forEach { it.fill(0) }
                    raw.fill(0)
                }
            }
    }
}

class RatchetSyncKeys(
    val state_key: ByteArray,
    val compat_key: ByteArray,
    val user_id: String,
) {
    fun wipe() {
        state_key.fill(0)
        compat_key.fill(0)
    }
}
