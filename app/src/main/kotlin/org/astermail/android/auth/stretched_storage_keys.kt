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

import org.astermail.android.crypto.CryptoNative
import org.json.JSONObject

private const val storage_key_length = 32

fun read_storage_kdf_version(vault_obj: JSONObject): Int {
    val raw = vault_obj.opt("kdf_version")
    val stretched = CryptoNative.stretched_storage_kdf_version
    return if (raw is Number && raw.toDouble() == stretched.toDouble()) {
        stretched
    } else {
        CryptoNative.legacy_storage_kdf_version
    }
}

fun storage_key_candidates(
    passphrase: ByteArray,
    kdf_version: Int,
    derive: (ByteArray, Int) -> ByteArray = CryptoNative::derive_storage_key_for_version,
): List<ByteArray> {
    val legacy = CryptoNative.legacy_storage_kdf_version
    if (kdf_version != CryptoNative.stretched_storage_kdf_version) {
        return listOf(derive(passphrase, legacy))
    }
    val stretched_key = derive(passphrase, CryptoNative.stretched_storage_kdf_version)
    val legacy_key = try {
        derive(passphrase, legacy)
    } catch (t: Throwable) {
        stretched_key.fill(0)
        throw t
    }
    return listOf(stretched_key, legacy_key)
}

fun stretched_storage_keks_to_absorb(
    vault_obj: JSONObject,
    passphrase: ByteArray,
    encode: (ByteArray) -> String,
    derive: (ByteArray, Int) -> ByteArray = CryptoNative::derive_storage_key_for_version,
): List<String> {
    val kdf_version = read_storage_kdf_version(vault_obj)
    if (kdf_version != CryptoNative.stretched_storage_kdf_version) return emptyList()
    val keys = storage_key_candidates(passphrase, kdf_version, derive)
    return try {
        keys.filter { it.size == storage_key_length }.map(encode).distinct()
    } finally {
        keys.forEach { it.fill(0) }
    }
}

fun append_storage_keks(current: List<String>, extra: List<String>): List<String> {
    if (extra.isEmpty()) return current
    val merged = current.toMutableList()
    for (kek in extra) {
        if (kek.isNotBlank() && kek !in merged) merged.add(kek)
    }
    return merged
}

fun <T : Any> open_with_storage_key_candidates(
    candidates: List<ByteArray>,
    open: (ByteArray) -> T?,
): T? {
    for (key in candidates) {
        if (key.size != storage_key_length) continue
        val opened = try {
            open(key)
        } catch (_: Exception) {
            null
        }
        if (opened != null) return opened
    }
    return null
}
