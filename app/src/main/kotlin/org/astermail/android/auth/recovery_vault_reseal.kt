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

import org.json.JSONArray
import org.json.JSONObject

class RecoveryPreviousPasswordIncorrect : IllegalStateException("previous password does not unlock the identity key")

class RecoveryVaultReseal(
    val vault: JSONObject,
    val backup: JSONObject,
    val identity_locked: Boolean,
)

fun recovery_locked_identity_key(backup: JSONObject): String? {
    val identity = vault_identity_key(strip_backup_fields(backup))
    if (!is_pgp_private_key(identity)) return null
    if (!pgp_key_needs_passphrase(identity)) return null
    if (read_unlocked_keys(backup).containsKey(identity)) return null
    return identity
}

fun recovery_needs_previous_password(backup: JSONObject): Boolean =
    recovery_locked_identity_key(backup) != null

fun reseal_recovery_vault(
    backup: JSONObject,
    recovery_codes: List<String>,
    new_password: CharArray,
    previous_password: CharArray?,
    unlocks: (String, CharArray) -> Boolean = ::pgp_key_unlocks,
    lock: (String, CharArray) -> String = ::lock_unlocked_pgp_key,
    reprotect: (String, CharArray, CharArray) -> String = ::reprotect_pgp_key,
    unlock: (String, CharArray) -> String = ::unlock_pgp_key,
): RecoveryVaultReseal {
    val locked_identity = recovery_locked_identity_key(backup)
    val usable_previous = previous_password?.takeIf { it.isNotEmpty() }
    if (locked_identity != null && usable_previous != null && !unlocks(locked_identity, usable_previous)) {
        throw RecoveryPreviousPasswordIncorrect()
    }

    val unlocked_keys = read_unlocked_keys(backup)
    val vault = strip_backup_fields(backup)
    val relocked = relock_vault_keys(vault, unlocked_keys, new_password, lock)

    if (usable_previous != null) {
        reprotect_vault_pgp_keys(
            vault,
            usable_previous,
            new_password,
            reprotect,
            { armored -> relocked.containsKey(armored) || unlocks(armored, new_password) },
        )
    }

    val codes = JSONArray()
    recovery_codes.forEach { codes.put(it) }
    vault.put("recovery_codes", codes)

    val carried = carry_backup_unlocked_keys(vault, relocked)
    val backup_out = if (usable_previous == null) {
        carried
    } else {
        build_backup_vault(vault, new_password, unlock)
    }

    val identity = vault_identity_key(vault)
    val identity_locked = is_pgp_private_key(identity) &&
        !relocked.containsKey(identity) &&
        !unlocks(identity, new_password)

    return RecoveryVaultReseal(vault = vault, backup = backup_out, identity_locked = identity_locked)
}
