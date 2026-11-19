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

import java.util.Base64
import org.astermail.android.crypto.AesGcm

const val sealed_passkey_name_prefix = "an1:"

private const val passkey_name_tag = "aster-passkey-name-v1\u0000"
private const val passkey_name_nonce_length = 12
private const val passkey_name_min_sealed_length = passkey_name_nonce_length + 16

fun is_sealed_passkey_name(stored: String?): Boolean =
    stored != null && stored.startsWith(sealed_passkey_name_prefix)

fun open_passkey_name(stored: String?, keys: List<ByteArray>): String? {
    if (stored.isNullOrEmpty()) return null
    if (!is_sealed_passkey_name(stored)) return stored
    val packed = runCatching {
        Base64.getDecoder().decode(stored.substring(sealed_passkey_name_prefix.length))
    }.getOrNull() ?: return null
    if (packed.size < passkey_name_min_sealed_length) return null
    val nonce = packed.copyOfRange(0, passkey_name_nonce_length)
    val sealed = packed.copyOfRange(passkey_name_nonce_length, packed.size)
    for (key in keys) {
        if (key.size != 32) continue
        val opened = runCatching { AesGcm.decrypt(key, nonce, sealed) }.getOrNull() ?: continue
        val text = runCatching {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(opened)).toString()
        }.getOrNull()
        opened.fill(0)
        if (text == null || !text.startsWith(passkey_name_tag)) continue
        return text.substring(passkey_name_tag.length)
    }
    return null
}

fun passkey_name_keys(data_kek: ByteArray?, decrypt_keks: List<String>): List<ByteArray> {
    val keys = mutableListOf<ByteArray>()
    if (data_kek != null && data_kek.size == 32) keys.add(data_kek)
    for (encoded in decrypt_keks) {
        val raw = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: continue
        if (raw.size == 32 && keys.none { it.contentEquals(raw) }) keys.add(raw) else raw.fill(0)
    }
    return keys
}
