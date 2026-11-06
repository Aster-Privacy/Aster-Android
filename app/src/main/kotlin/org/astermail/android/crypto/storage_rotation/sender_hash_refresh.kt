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

package org.astermail.android.crypto.storage_rotation

private fun sender_hash_inputs(sender_email: String): List<Pair<String, String>> {
    val address = sender_email.trim()
    val at = address.lastIndexOf('@')
    if (at <= 0 || at == address.lastIndex) return emptyList()
    val local = address.substring(0, at)
    val domain = address.substring(at + 1)
    val lower_domain = domain.lowercase()
    return listOf(
        ALIAS_HMAC_INFO to "${normalize_address(local)}@$domain",
        ALIAS_HMAC_INFO to "${normalize_address(local)}@$lower_domain",
        DOMAIN_ADDRESS_HMAC_INFO to "${local.lowercase()}@$lower_domain",
        DOMAIN_ADDRESS_HMAC_INFO to "${normalize_address(local)}@$lower_domain",
    ).distinct()
}

private fun sender_hash_under(key: ByteArray, info: String, value: String): String {
    val hmac_key = derive_hmac_key(key, info)
    try {
        return encode_b64(hmac_sha256(hmac_key, value.toByteArray(Charsets.UTF_8)))
    } finally {
        hmac_key.fill(0)
    }
}

fun current_sender_alias_hash(
    stored_hash: String?,
    sender_email: String?,
    write_key: ByteArray?,
    read_keys: List<ByteArray>,
): String? {
    if (stored_hash.isNullOrBlank() || sender_email.isNullOrBlank() || write_key == null) return stored_hash
    val inputs = sender_hash_inputs(sender_email)
    if (inputs.any { (info, value) -> sender_hash_under(write_key, info, value) == stored_hash }) return stored_hash
    for (old_key in read_keys) {
        if (old_key.contentEquals(write_key)) continue
        val match = inputs.firstOrNull { (info, value) -> sender_hash_under(old_key, info, value) == stored_hash }
        if (match != null) return sender_hash_under(write_key, match.first, match.second)
    }
    return stored_hash
}
