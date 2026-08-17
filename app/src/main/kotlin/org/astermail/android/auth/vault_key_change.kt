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

package org.astermail.android.auth

import org.astermail.android.storage.SessionKeyStore

data class VaultKeySnapshot(
    val identity_key: String?,
    val ratchet_identity_public: String?,
    val ratchet_previous_keys: String?,
    val ratchet_pq_secret: String?,
    val ratchet_pq_public: String?,
)

fun vault_key_snapshot(session_key_store: SessionKeyStore): VaultKeySnapshot = VaultKeySnapshot(
    identity_key = session_key_store.get_identity_key(),
    ratchet_identity_public = session_key_store.get_ratchet_identity_public_b64(),
    ratchet_previous_keys = session_key_store.get_ratchet_previous_keys_json(),
    ratchet_pq_secret = session_key_store.get_ratchet_pq_identity_secret(),
    ratchet_pq_public = session_key_store.get_ratchet_pq_identity_public(),
)

fun vault_keys_changed(before: VaultKeySnapshot, after: VaultKeySnapshot): Boolean {
    fun field_changed(old_value: String?, new_value: String?): Boolean =
        !new_value.isNullOrBlank() && new_value != old_value
    return field_changed(before.identity_key, after.identity_key) ||
        field_changed(before.ratchet_identity_public, after.ratchet_identity_public) ||
        field_changed(before.ratchet_previous_keys, after.ratchet_previous_keys) ||
        field_changed(before.ratchet_pq_secret, after.ratchet_pq_secret) ||
        field_changed(before.ratchet_pq_public, after.ratchet_pq_public)
}
