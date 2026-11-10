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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.json.JSONArray
import org.json.JSONObject

const val MAX_PREVIOUS_IDENTITY_KEYS = 10
const val MAX_LEGACY_IDENTITY_KEYS = 32

data class RecoveredIdentityKeys(
    val previous_keys: List<String>,
    val legacy_identity_keys: List<String>,
    val absorbed: List<Boolean>,
)

fun vault_identity_key(vault: JSONObject): String =
    vault.optString("identity_key", "").ifBlank { vault.optString("identity_private_key", "") }

fun json_strings(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it, "").takeIf { value -> value.isNotEmpty() } }
}

private fun unique_non_empty(values: List<String?>): List<String> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (value in values) {
        if (value.isNullOrEmpty() || !seen.add(value)) continue
        result.add(value)
    }
    return result
}

fun vault_identity_key_materials(vault: JSONObject): List<String> =
    unique_non_empty(
        listOf(vault_identity_key(vault)) +
            json_strings(vault.optJSONArray("previous_keys")) +
            json_strings(vault.optJSONArray("legacy_identity_keys")),
    )

fun vault_older_identity_keys(vault: JSONObject): List<String> {
    val current = vault_identity_key(vault)
    return vault_identity_key_materials(vault).filter { it != current }
}

fun replace_vault_identity_key(vault: JSONObject, identity_key: String) {
    val old_identity_key = vault_identity_key(vault)
    if (old_identity_key.isNotEmpty() && old_identity_key != identity_key) {
        val retained = listOf(old_identity_key) +
            json_strings(vault.optJSONArray("legacy_identity_keys")).filter { it != old_identity_key }
        vault.put("legacy_identity_keys", JSONArray(unique_non_empty(retained).take(MAX_LEGACY_IDENTITY_KEYS)))
    }
    vault.put("identity_key", identity_key)
}

private fun read_secret_ring(armored: String): PGPSecretKeyRing =
    PGPSecretKeyRing(
        PGPUtil.getDecoderStream(ByteArrayInputStream(armored.toByteArray(Charsets.UTF_8))),
        BcKeyFingerprintCalculator(),
    )

fun pgp_key_identity(armored: String): String =
    runCatching {
        val fingerprint = read_secret_ring(armored).publicKey.fingerprint
        "fp:" + fingerprint.joinToString("") { "%02X".format(Locale.ROOT, it) }
    }.getOrElse { "raw:$armored" }

fun reprotect_pgp_key(armored: String, old_passphrase: CharArray, new_passphrase: CharArray): String {
    val ring = read_secret_ring(armored)
    val digests = BcPGPDigestCalculatorProvider()
    val decryptor = BcPBESecretKeyDecryptorBuilder(digests).build(old_passphrase)
    requireNotNull(ring.secretKey.extractPrivateKey(decryptor))
    val encryptor = BcPBESecretKeyEncryptorBuilder(
        SymmetricKeyAlgorithmTags.AES_256,
        digests.get(HashAlgorithmTags.SHA256),
    ).build(new_passphrase)
    val reprotected = PGPSecretKeyRing.copyWithNewPassword(ring, decryptor, encryptor)
    val out = ByteArrayOutputStream()
    ArmoredOutputStream(out).use { reprotected.encode(it) }
    return out.toString(Charsets.UTF_8.name())
}

fun pgp_key_needs_passphrase(armored: String): Boolean =
    runCatching { read_secret_ring(armored).secretKey.keyEncryptionAlgorithm != SymmetricKeyAlgorithmTags.NULL }
        .getOrDefault(false)

fun pgp_key_unlocks(armored: String, passphrase: CharArray): Boolean {
    if (!pgp_key_needs_passphrase(armored)) return true
    val decryptor = BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(passphrase)
    return runCatching { read_secret_ring(armored).secretKey.extractPrivateKey(decryptor) != null }.getOrDefault(false)
}

fun pgp_keys_locked(
    identity_key: String?,
    vault: JSONObject?,
    unlocks: (String) -> Boolean,
): Boolean {
    val candidates = listOfNotNull(
        identity_key,
        vault?.optString("identity_key", ""),
        vault?.optString("signed_prekey_private", ""),
    ).filter { is_pgp_private_key(it) }.distinct()
    return candidates.any { !unlocks(it) }
}

fun reprotect_vault_pgp_keys(
    vault: JSONObject,
    old_passphrase: CharArray,
    new_passphrase: CharArray,
    reprotect: (String, CharArray, CharArray) -> String = ::reprotect_pgp_key,
    unlocks: (String) -> Boolean = { pgp_key_unlocks(it, new_passphrase) },
): Int {
    val replaced = mutableListOf<String>()
    val usable_ids = mutableSetOf<String>()
    val usable_materials = mutableSetOf<String>()

    val results = mutableMapOf<String, String>()
    val repair_once = { armored: String ->
        if (armored.isEmpty()) {
            armored
        } else if (unlocks(armored)) {
            usable_ids.add(pgp_key_identity(armored))
            usable_materials.add(armored)
            armored
        } else {
            val relocked = runCatching { reprotect(armored, old_passphrase, new_passphrase) }.getOrNull()
            if (relocked == null || relocked == armored) {
                armored
            } else {
                usable_ids.add(pgp_key_identity(armored))
                usable_materials.add(relocked)
                replaced.add(armored)
                relocked
            }
        }
    }
    val repair = { armored: String -> results.getOrPut(armored) { repair_once(armored) } }

    val single_fields = listOf("identity_key", "identity_private_key", "signed_prekey_private")
    val repaired_fields = single_fields.associateWith { field -> repair(vault.optString(field, "")) }
    val repaired_previous = json_strings(vault.optJSONArray("previous_keys")).map(repair)
    val legacy = json_strings(vault.optJSONArray("legacy_identity_keys"))
    val recovered_legacy = mutableListOf<String>()
    for (armored in unique_non_empty(legacy)) {
        if (pgp_key_identity(armored) in usable_ids) continue
        val relocked = repair(armored)
        if (relocked != armored) recovered_legacy.add(relocked)
    }

    if (replaced.isEmpty()) return 0

    for ((field, value) in repaired_fields) {
        if (value.isNotEmpty()) vault.put(field, value)
    }
    val previous_keys = unique_by_fingerprint(
        repaired_previous.filter { it in usable_materials } +
            recovered_legacy +
            repaired_previous.filter { it !in usable_materials },
    ).take(MAX_PREVIOUS_IDENTITY_KEYS)
    vault.put("previous_keys", JSONArray(previous_keys))
    vault.put(
        "legacy_identity_keys",
        JSONArray(unique_non_empty(replaced + legacy).take(MAX_LEGACY_IDENTITY_KEYS)),
    )
    return replaced.size
}

private fun unique_by_fingerprint(keys: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (armored in keys) {
        if (armored.isEmpty()) continue
        if (!seen.add(pgp_key_identity(armored))) continue
        result.add(armored)
    }
    return result
}

fun unlock_pgp_key(armored: String, passphrase: CharArray): String {
    val ring = read_secret_ring(armored)
    val decryptor = BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(passphrase)
    requireNotNull(ring.secretKey.extractPrivateKey(decryptor))
    val unlocked = PGPSecretKeyRing.copyWithNewPassword(ring, decryptor, null)
    val out = ByteArrayOutputStream()
    ArmoredOutputStream(out).use { unlocked.encode(it) }
    return out.toString(Charsets.UTF_8.name())
}

fun lock_unlocked_pgp_key(armored: String, passphrase: CharArray): String {
    val ring = read_secret_ring(armored)
    val digests = BcPGPDigestCalculatorProvider()
    requireNotNull(ring.secretKey.extractPrivateKey(null))
    val encryptor = BcPBESecretKeyEncryptorBuilder(
        SymmetricKeyAlgorithmTags.AES_256,
        digests.get(HashAlgorithmTags.SHA256),
    ).build(passphrase)
    val locked = PGPSecretKeyRing.copyWithNewPassword(ring, null, encryptor)
    val out = ByteArrayOutputStream()
    ArmoredOutputStream(out).use { locked.encode(it) }
    return out.toString(Charsets.UTF_8.name())
}

fun merge_recovered_identity_keys(
    vault: JSONObject,
    old_vaults: List<JSONObject>,
    old_passphrase: CharArray,
    current_passphrase: CharArray,
    reprotect: (String, CharArray, CharArray) -> String = ::reprotect_pgp_key,
): RecoveredIdentityKeys =
    merge_identity_keys_with(vault, old_vaults) { armored ->
        reprotect(armored, old_passphrase, current_passphrase)
    }

fun merge_identity_keys_with(
    vault: JSONObject,
    old_vaults: List<JSONObject>,
    relock: (String) -> String,
): RecoveredIdentityKeys {
    val recovered_per_vault = mutableListOf<List<String>>()
    val identity_recovered = mutableListOf<Boolean>()
    val old_materials = mutableListOf<String>()

    for (old_vault in old_vaults) {
        val identity = vault_identity_key(old_vault)
        val reprotected = mutableListOf<String>()
        var identity_ok = identity.isEmpty()

        old_materials.addAll(vault_identity_key_materials(old_vault))

        for (armored in unique_non_empty(listOf(identity) + json_strings(old_vault.optJSONArray("previous_keys")))) {
            val next = runCatching { relock(armored) }.getOrNull() ?: continue
            reprotected.add(next)
            if (armored == identity) identity_ok = true
        }

        recovered_per_vault.add(reprotected)
        identity_recovered.add(identity_ok)
    }

    val previous_keys = unique_by_fingerprint(
        json_strings(vault.optJSONArray("previous_keys")) + recovered_per_vault.flatten(),
    ).take(MAX_PREVIOUS_IDENTITY_KEYS)

    val kept = (listOf(vault_identity_key(vault)) + previous_keys)
        .filter { it.isNotEmpty() }
        .map(::pgp_key_identity)
        .toSet()

    val absorbed = old_vaults.indices.map { i ->
        identity_recovered[i] && recovered_per_vault[i].all { pgp_key_identity(it) in kept }
    }

    val legacy_identity_keys = unique_non_empty(
        json_strings(vault.optJSONArray("legacy_identity_keys")) + old_materials,
    ).take(MAX_LEGACY_IDENTITY_KEYS)

    return RecoveredIdentityKeys(previous_keys, legacy_identity_keys, absorbed)
}
