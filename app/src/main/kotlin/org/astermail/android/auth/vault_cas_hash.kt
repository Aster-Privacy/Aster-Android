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

import org.astermail.android.api.keys.CurrentVaultResult
import java.security.MessageDigest
import java.util.Base64

internal fun expected_vault_sha256(result: CurrentVaultResult): String? {
    if (result !is CurrentVaultResult.Available) return null
    result.vault_sha256?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return runCatching {
        val bytes = Base64.getDecoder().decode(result.encrypted_vault.trim())
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
    }.getOrNull()
}
