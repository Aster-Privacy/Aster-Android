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

import org.json.JSONObject

const val VAULT_GENERATION_FIELD = "vault_generation"

private const val MAX_SAFE_GENERATION = 9_007_199_254_740_991L

fun vault_generation(vault: JSONObject?): Long {
    val value = vault?.opt(VAULT_GENERATION_FIELD) as? Number ?: return 0L
    val whole = value.toDouble()
    if (whole.isNaN() || whole != Math.floor(whole) || whole <= 0.0 || whole > MAX_SAFE_GENERATION) return 0L
    return whole.toLong()
}

fun stamp_vault_generation(next: JSONObject, vararg sources: JSONObject?): JSONObject {
    val highest = sources.fold(vault_generation(next)) { acc, source -> maxOf(acc, vault_generation(source)) }
    next.put(VAULT_GENERATION_FIELD, highest + 1)
    return next
}

private const val MAX_SERVER_WRITE_COUNT = 1L shl 40

fun server_write_count_floor(count: Long?): JSONObject? =
    count?.takeIf { it in 1..MAX_SERVER_WRITE_COUNT }?.let { JSONObject().put(VAULT_GENERATION_FIELD, it) }
