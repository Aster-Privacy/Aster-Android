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

import org.astermail.android.storage.SessionKeyStore

class AccountDataWriter(
    private val session_key_store: SessionKeyStore,
    private val capabilities: AccountKeyCapabilities,
) {

    suspend fun write_key(context: String): ByteArray? {
        if (!session_key_store.has_account_write_kek(context)) return null
        if (!capabilities.format_writes()) return null
        val key = session_key_store.get_account_write_kek(context) ?: return null
        if (key.size != AccountKey.LENGTH) {
            key.fill(0)
            return null
        }
        return key
    }

    suspend fun <T : Any> retry_after_key_load(attempt: suspend () -> T?): T? {
        attempt()?.let { return it }
        session_key_store.await_account_key_load(READ_WAIT_MS)
        return attempt()
    }

    companion object {
        const val READ_WAIT_MS = 5_000L
        const val PREFERENCES_CONTEXT = "astermail-preferences-v1"
        const val DRAFT_CONTEXT = "astermail-draft-v2"
    }
}
