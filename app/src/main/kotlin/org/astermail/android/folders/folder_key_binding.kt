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

package org.astermail.android.folders

import java.util.Locale
import org.astermail.android.crypto.AesGcm
import org.json.JSONObject

const val FOLDER_KEY_PINS_FIELD = "folder_key_pins"
const val FOLDER_KEY_VERSION_LEGACY = 1
const val FOLDER_KEY_VERSION_BOUND = 2
const val FOLDER_PIN_CURRENT_SALT = 1
const val FOLDER_PIN_BOUND = 2

private const val folder_key_aad_prefix = "astermail-folder-key-v2:"

class folder_key_version_exception :
    IllegalStateException("unsupported folder key version")

data class folder_key_format(val allow_legacy_salt: Boolean, val bound: Boolean)

fun parse_folder_key_version(version: Int?): Int = when (version) {
    null -> FOLDER_KEY_VERSION_LEGACY
    FOLDER_KEY_VERSION_LEGACY, FOLDER_KEY_VERSION_BOUND -> version
    else -> throw folder_key_version_exception()
}

fun folder_pin_allows_legacy_salt(pin: Int): Boolean = pin < FOLDER_PIN_CURRENT_SALT

fun folder_pin_level_for_write(bound_writes_ready: Boolean, pin: Int = 0): Int =
    if (folder_writes_bound(pin, bound_writes_ready)) FOLDER_PIN_BOUND else FOLDER_PIN_CURRENT_SALT

fun folder_writes_bound(pin: Int, bound_writes_ready: Boolean): Boolean =
    bound_writes_ready || pin >= FOLDER_PIN_BOUND

fun folder_wrap_below_pin(pin: Int, version: Int?): Boolean {
    if (pin < FOLDER_PIN_BOUND) return false
    val parsed = runCatching { parse_folder_key_version(version) }.getOrNull() ?: return false
    return parsed < FOLDER_KEY_VERSION_BOUND
}

fun required_folder_key_format(pin: Int, version: Int, salt_current: Boolean): folder_key_format {
    if (pin >= FOLDER_PIN_BOUND && version < FOLDER_KEY_VERSION_BOUND) throw folder_kdf_downgrade_exception()
    if (pin >= FOLDER_PIN_CURRENT_SALT && !salt_current) throw folder_kdf_downgrade_exception()
    val bound = version >= FOLDER_KEY_VERSION_BOUND
    if (bound && !salt_current) throw folder_kdf_downgrade_exception()
    return folder_key_format(allow_legacy_salt = folder_pin_allows_legacy_salt(pin) && !bound, bound = bound)
}

fun folder_pin_level_after_unlock(format: folder_key_format, salt_current: Boolean, bound_writes_ready: Boolean): Int = when {
    format.bound && bound_writes_ready -> FOLDER_PIN_BOUND
    salt_current -> FOLDER_PIN_CURRENT_SALT
    else -> 0
}

fun folder_wrap_matches(sent_key: String, sent_nonce: String, returned_key: String?, returned_nonce: String?): Boolean {
    if (returned_key.isNullOrBlank() || returned_nonce.isNullOrBlank()) return false
    val decoder = java.util.Base64.getMimeDecoder()
    return runCatching {
        java.security.MessageDigest.isEqual(decoder.decode(sent_key), decoder.decode(returned_key)) &&
            java.security.MessageDigest.isEqual(decoder.decode(sent_nonce), decoder.decode(returned_nonce))
    }.getOrDefault(false)
}

fun folder_key_aad(folder_id: String): ByteArray =
    (folder_key_aad_prefix + folder_id.lowercase(Locale.ROOT)).toByteArray(Charsets.UTF_8)

fun wrap_folder_key(folder_key: ByteArray, encryption_key: ByteArray, nonce: ByteArray, bound_folder_id: String?): ByteArray =
    AesGcm.encrypt(encryption_key, nonce, folder_key, bound_folder_id?.let(::folder_key_aad))

fun unwrap_folder_key(
    encrypted_folder_key: ByteArray,
    encryption_key: ByteArray,
    nonce: ByteArray,
    bound_folder_id: String?,
): ByteArray = AesGcm.decrypt(encryption_key, nonce, encrypted_folder_key, bound_folder_id?.let(::folder_key_aad))

fun folder_pin_level(value: Any?): Int {
    val level = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
        is Number -> value.toDouble().takeIf { it % 1.0 == 0.0 && it in 0.0..Int.MAX_VALUE.toDouble() }?.toInt()
        else -> null
    } ?: return 0
    return if (level in FOLDER_PIN_CURRENT_SALT..FOLDER_PIN_BOUND) level else 0
}

fun merge_folder_key_pins(vararg sources: Any?): JSONObject? {
    val merged = JSONObject()
    for (source in sources) {
        if (source !is JSONObject) continue
        for (raw_id in source.keys()) {
            val level = folder_pin_level(source.opt(raw_id))
            if (level == 0) continue
            val id = raw_id.lowercase(Locale.ROOT)
            if (level > merged.optInt(id, 0)) merged.put(id, level)
        }
    }
    return if (merged.length() > 0) merged else null
}

fun vault_folder_key_pin(vault: JSONObject?, folder_id: String): Int {
    val pins = vault?.optJSONObject(FOLDER_KEY_PINS_FIELD) ?: return 0
    return merge_folder_key_pins(pins)?.optInt(folder_id.lowercase(Locale.ROOT), 0) ?: 0
}

fun with_folder_key_pin(pins: JSONObject?, folder_id: String, level: Int): JSONObject? {
    if (folder_id.isBlank() || level !in FOLDER_PIN_CURRENT_SALT..FOLDER_PIN_BOUND) return null
    val merged = merge_folder_key_pins(pins) ?: JSONObject()
    val id = folder_id.lowercase(Locale.ROOT)
    if (merged.optInt(id, 0) >= level) return null
    return merged.put(id, level)
}
