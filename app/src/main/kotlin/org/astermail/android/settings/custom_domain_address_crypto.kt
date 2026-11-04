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

package org.astermail.android.settings

import java.security.MessageDigest
import java.util.Base64
import org.astermail.android.api.settings.CustomDomainAddressInfo

internal fun decrypt_custom_domain_address_local_part(
    address: CustomDomainAddressInfo,
    decrypt_field: (String, String) -> String,
): String {
    if (address.local_part_nonce.isNotEmpty()) {
        return decrypt_field(address.encrypted_local_part, address.local_part_nonce)
    }

    // Nonce-less directory records carry Base64 text and its SHA-256 hash.
    require(address.encrypted_local_part.length <= 88) { "Invalid directory address" }
    val decoder = Base64.getDecoder()
    val local_part_bytes = decoder.decode(address.encrypted_local_part)
    val local_part = local_part_bytes.toString(Charsets.UTF_8)
    require(is_valid_import_local_part(local_part, is_system_domain = false)) { "Invalid directory address" }
    val expected_hash = decoder.decode(address.local_part_hash)
    val actual_hash = MessageDigest.getInstance("SHA-256").digest(local_part_bytes)
    require(MessageDigest.isEqual(expected_hash, actual_hash)) { "Directory address hash mismatch" }
    return local_part
}
