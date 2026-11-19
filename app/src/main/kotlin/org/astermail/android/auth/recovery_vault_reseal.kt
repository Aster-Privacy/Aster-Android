//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.auth

import org.astermail.android.api.recovery.CompleteRecoveryRequest
import org.astermail.android.api.recovery.RecoveryShareData
import org.astermail.android.crypto.storage_rotation.server_write_count_floor
import org.astermail.android.crypto.storage_rotation.stamp_vault_generation
import org.astermail.android.crypto.storage_rotation.vault_generation
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

fun stamp_recovered_vault(
    reseal: RecoveryVaultReseal,
    binding: RecoveryBackupBinding,
    format_v2: Boolean,
): RecoveryVaultReseal {
    RecoveryBackupVaultFields.stamp(reseal.vault, binding, format_v2)
    RecoveryBackupVaultFields.stamp(reseal.backup, binding, format_v2)
    return reseal
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
    server_write_count: Long? = null,
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
    stamp_vault_generation(vault, backup, server_write_count_floor(server_write_count))

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

class RecoveryUploadVault(
    val plain: ByteArray,
    val backup_plain: ByteArray,
    val vault_format: Int,
    val identity_locked: Boolean,
    val vault_generation: Long?,
)

class RecoveryBackupFields(val encrypted_data: String, val nonce: String, val salt: String)

fun recovery_upload_vault(
    vault_bytes: ByteArray,
    codes: List<String>,
    password: CharArray,
    previous_password: CharArray?,
    server_write_count: Long?,
    binding: RecoveryBackupBinding? = null,
    format_v2: Boolean = false,
): RecoveryUploadVault {
    val reseal = reseal_recovery_vault(
        JSONObject(String(vault_bytes, Charsets.UTF_8)),
        codes,
        password,
        previous_password,
        server_write_count = server_write_count,
    )
    val resealed = if (binding == null) reseal else stamp_recovered_vault(reseal, binding, format_v2)
    return RecoveryUploadVault(
        plain = resealed.vault.toString().toByteArray(Charsets.UTF_8),
        backup_plain = resealed.backup.toString().toByteArray(Charsets.UTF_8),
        vault_format = stored_vault_format(resealed.vault),
        identity_locked = resealed.identity_locked,
        vault_generation = vault_generation(resealed.vault).takeIf { it > 0 },
    )
}

fun recovery_complete_request(
    recovery_token: String,
    password_hash: String,
    password_hash_v2: String,
    password_salt: String,
    encrypted_vault: String,
    vault_nonce: String,
    shares: List<RecoveryShareData>,
    backup: RecoveryBackupFields,
    vault: RecoveryUploadVault,
    vault_kdf_iterations: Int?,
): CompleteRecoveryRequest = CompleteRecoveryRequest(
    recovery_token = recovery_token,
    new_password_hash = password_hash,
    new_password_hash_v2 = password_hash_v2,
    new_password_salt = password_salt,
    new_encrypted_vault = encrypted_vault,
    new_vault_nonce = vault_nonce,
    new_recovery_shares = shares,
    new_encrypted_vault_backup = backup.encrypted_data,
    new_vault_backup_nonce = backup.nonce,
    new_recovery_key_salt = backup.salt,
    vault_format = vault.vault_format,
    vault_kdf_iterations = vault_kdf_iterations,
    vault_generation = vault.vault_generation,
)
