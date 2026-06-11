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
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun save(state: RatchetState): Unit = mutex.withLock {
        val encoded = json.encodeToString(state)
        prefs.edit().putString(key_for(state.conversation_id), encoded).apply()
    }

    suspend fun delete(conversation_id: String): Unit = mutex.withLock {
        prefs.edit().remove(key_for(conversation_id)).apply()
    }

    fun derive_state_encryption_key(): ByteArray? {
        val derived_master = derive_storage_master_key() ?: return null
        return try {
            RatchetCrypto.hkdf_sha256(
                ikm = derived_master,
                salt = "Aster Mail_Ratchet_State_Encryption".toByteArray(Charsets.UTF_8),
                info = "ratchet_state_key".toByteArray(Charsets.UTF_8),
                length = 32,
            )
        } finally {
            derived_master.fill(0)
        }
    }

    private fun derive_storage_master_key(): ByteArray? {
        val passphrase = session_key_store.get_passphrase() ?: return null
        return try {
            val prefix = "aster-hkdf-salt-v1:".toByteArray(Charsets.UTF_8)
            val combined = ByteArray(prefix.size + passphrase.size)
            System.arraycopy(prefix, 0, combined, 0, prefix.size)
            System.arraycopy(passphrase, 0, combined, prefix.size, passphrase.size)
            val salt = RatchetCrypto.sha256(combined)
            combined.fill(0)
            RatchetCrypto.hkdf_sha256(
                ikm = passphrase,
                salt = salt,
                info = "aster-storage-encryption-key-v1".toByteArray(Charsets.UTF_8),
                length = 32,
            )
        } finally {
            passphrase.fill(0)
        }
    }

    private fun key_for(conversation_id: String): String = "ratchet_state_$conversation_id"

    companion object {
        private const val prefs_name = "aster_ratchet_state_v1"
    }
}
