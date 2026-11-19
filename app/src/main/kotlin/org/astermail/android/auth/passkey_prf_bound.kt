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

import android.content.Context
import java.security.MessageDigest
import java.util.Base64

private const val prf_bound_marker_prefix = "aster_prf_bound:"
private const val prf_bound_prefs_name = "aster_prf_bound_wraps"

interface PrfBoundMarkers {
    fun is_bound(credential_id: String): Boolean
    fun remember(credential_id: String)
}

fun prf_bound_marker_key(credential_id: String): String? {
    val id = runCatching { b64url_to_bytes(credential_id) }.getOrNull() ?: return null
    if (id.isEmpty()) return null
    val digest = MessageDigest.getInstance("SHA-256").digest(id)
    return prf_bound_marker_prefix + Base64.getEncoder().encodeToString(digest.copyOfRange(0, 16))
}

class SharedPrefsPrfBoundMarkers(context: Context) : PrfBoundMarkers {
    private val prefs = runCatching {
        context.applicationContext.getSharedPreferences(prf_bound_prefs_name, Context.MODE_PRIVATE)
    }.getOrNull()

    override fun is_bound(credential_id: String): Boolean {
        val key = prf_bound_marker_key(credential_id) ?: return false
        return prefs?.getString(key, null) == "1"
    }

    override fun remember(credential_id: String) {
        val key = prf_bound_marker_key(credential_id) ?: return
        prefs?.edit()?.putString(key, "1")?.apply()
    }
}
