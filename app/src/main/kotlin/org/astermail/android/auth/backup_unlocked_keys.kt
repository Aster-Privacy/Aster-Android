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

import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PasswordKdf
import org.astermail.android.crypto.hkdf_sha256
import org.json.JSONArray
import org.json.JSONObject

const val MAX_BACKUP_UNLOCKED_KEYS = 16
const val BACKUP_UNLOCKED_KEYS_FIELD = "unlocked_keys"

private const val RECOVERY_CODE_PBKDF2_ITERATIONS = 310000
private const val RECOVERY_VAULT_HKDF_INFO = "Aster Mail_Recovery_Vault_v1"

data class CodeRestoreResult(
    val restored: Int = 0,
    val incomplete: Int = 0,
)

fun strip_backup_fields(vault: JSONObject): JSONObject {
    val stripped = JSONObject(vault.toString())
    stripped.remove(BACKUP_UNLOCKED_KEYS_FIELD)
    return stripped
}

fun read_unlocked_keys(vault: JSONObject): Map<String, String> {
    val unlocked = linkedMapOf<String, String>()
    val pairs = vault.optJSONArray(BACKUP_UNLOCKED_KEYS_FIELD) ?: return unlocked

    for (i in 0 until pairs.length()) {
        val pair = pairs.optJSONArray(i) ?: continue
        if (pair.length() != 2) continue
        val locked = pair.opt(0) as? String ?: continue
        val open = pair.opt(1) as? String ?: continue
        if (locked.isEmpty() || open.isEmpty()) continue
        if (unlocked.size >= MAX_BACKUP_UNLOCKED_KEYS) break
        unlocked[locked] = open
    }

    return unlocked
}

private fun backup_key_materials(vault: JSONObject): List<String> {
    val seen = mutableSetOf<String>()
    return (listOf(vault_identity_key(vault)) + json_strings(vault.optJSONArray("previous_keys")))
        .filter { it.isNotEmpty() && seen.add(it) }
}

private fun with_unlocked_keys(base: JSONObject, pairs: List<Pair<String, String>>): JSONObject {
    if (pairs.isEmpty()) return base
    val array = JSONArray()
    for ((locked, open) in pairs) array.put(JSONArray().put(locked).put(open))
    base.put(BACKUP_UNLOCKED_KEYS_FIELD, array)
    return base
}

fun build_backup_vault(
    vault: JSONObject,
    passphrase: CharArray,
    unlock: (String, CharArray) -> String = ::unlock_pgp_key,
): JSONObject {
    val base = strip_backup_fields(vault)
    val pairs = mutableListOf<Pair<String, String>>()

    for (armored in backup_key_materials(base)) {
        if (pairs.size >= MAX_BACKUP_UNLOCKED_KEYS) break
        val open = runCatching { unlock(armored, passphrase) }.getOrNull() ?: continue
        pairs.add(armored to open)
    }

    return with_unlocked_keys(base, pairs)
}

fun carry_backup_unlocked_keys(vault: JSONObject, unlocked: Map<String, String>): JSONObject {
    val base = strip_backup_fields(vault)
    val pairs = backup_key_materials(base)
        .mapNotNull { armored -> unlocked[armored]?.let { armored to it } }
        .take(MAX_BACKUP_UNLOCKED_KEYS)

    return with_unlocked_keys(base, pairs)
}

fun relock_with_unlocked_keys(
    unlocked: Map<String, String>,
    passphrase: CharArray,
    lock: (String, CharArray) -> String = ::lock_unlocked_pgp_key,
): (String) -> String = { armored ->
    val open = unlocked[armored] ?: error("no unlocked copy for this key")
    lock(open, passphrase)
}

fun open_recovery_vault_backup(
    code: String,
    encrypted_recovery_key: ByteArray,
    recovery_key_nonce: ByteArray,
    code_salt: ByteArray,
    encrypted_vault_backup: ByteArray,
    vault_backup_nonce: ByteArray,
    recovery_key_salt: ByteArray,
): JSONObject {
    val code_key = PasswordKdf.derive_aes_key(
        canonicalize_recovery_code(code),
        code_salt,
        RECOVERY_CODE_PBKDF2_ITERATIONS,
    )
    val recovery_key = try {
        AesGcm.decrypt(code_key, recovery_key_nonce, encrypted_recovery_key)
    } finally {
        code_key.fill(0)
    }

    val backup_key = try {
        hkdf_sha256(recovery_key, recovery_key_salt, RECOVERY_VAULT_HKDF_INFO.toByteArray(Charsets.UTF_8), 32)
    } finally {
        recovery_key.fill(0)
    }
    val plain = try {
        AesGcm.decrypt(backup_key, vault_backup_nonce, encrypted_vault_backup)
    } finally {
        backup_key.fill(0)
    }

    try {
        return JSONObject(String(plain, Charsets.UTF_8))
    } finally {
        plain.fill(0)
    }
}
