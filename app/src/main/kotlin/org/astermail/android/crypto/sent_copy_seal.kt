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

package org.astermail.android.crypto

import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SentCopySeal {

    fun seal(json: String, identity_key: String, passphrase: CharArray): Pair<String, String>? {
        val public_key = AccountKey.public_key_armored(identity_key) ?: return null
        val armored = runCatching {
            PgpEncryptor.encrypt_and_sign(json, listOf(public_key), identity_key, passphrase)
        }.getOrNull() ?: return null
        val reopened = runCatching {
            PgpDecryptor.decrypt_with_own_keys_status(armored, listOf(identity_key), passphrase)
        }.getOrNull() ?: return null
        if (reopened.plaintext != json || reopened.signature != PgpSignatureStatus.VALID) return null
        return Pair(Base64.getEncoder().encodeToString(armored.toByteArray(Charsets.UTF_8)), "")
    }
}

class AccountKeyCapabilities(
    private val fetch_format_writes: suspend () -> Boolean,
    private val now_ms: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private var cached: Boolean? = null
    private var cached_at_ms = 0L

    suspend fun format_writes(): Boolean = lock.withLock {
        val value = cached
        if (value != null && now_ms() - cached_at_ms < CACHE_MS) return@withLock value
        val fresh = try {
            fetch_format_writes()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
        cached = fresh
        cached_at_ms = now_ms()
        fresh
    }

    companion object {
        const val CACHE_MS = 5 * 60 * 1000L
    }
}
