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

import org.astermail.android.crypto.storage_rotation.LocalStorageKeys
import org.astermail.android.crypto.storage_rotation.preserve_vault_fields
import org.astermail.android.crypto.storage_rotation.stamp_vault_generation
import org.json.JSONArray
import org.json.JSONObject

internal fun build_signup_vault(
    identity_private_b64: String,
    prekey_private_b64: String,
    recovery_codes: List<String>?,
    pgp_private_key: String?,
    data_kek_b64: String?,
    created_at: Long,
): JSONObject {
    val obj = JSONObject()
    obj.put("version", 1)
    stamp_vault_generation(obj)
    if (data_kek_b64 != null) install_signup_data_kek(obj, data_kek_b64)
    obj.put("identity_private_key", identity_private_b64)
    obj.put("signed_prekey_private", prekey_private_b64)
    obj.put("created_at", created_at)
    if (!recovery_codes.isNullOrEmpty()) {
        val arr = JSONArray()
        recovery_codes.forEach { arr.put(it) }
        obj.put("recovery_codes", arr)
    }
    if (pgp_private_key != null) {
        obj.put("identity_key", pgp_private_key)
    }
    return obj
}

internal fun prepare_password_change_vault(
    vault_obj: JSONObject,
    local: LocalStorageKeys,
    now: String,
): JSONObject {
    preserve_vault_fields(JSONObject(vault_obj.toString()), vault_obj, local, false, now)
    stamp_vault_generation(vault_obj)
    return vault_obj
}
