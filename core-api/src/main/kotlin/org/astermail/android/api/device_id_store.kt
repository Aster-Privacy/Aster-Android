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

package org.astermail.android.api

import android.content.Context
import java.security.SecureRandom

const val DEVICE_ID_HEADER = "X-Aster-Device-Id"

object DeviceIdStore {
    private const val prefs_name = "aster_device_id_v1"
    private const val key_device_id = "device_id"
    private val valid_device_id = Regex("^[A-Za-z0-9_-]{16,128}$")

    @Volatile
    private var cached: String? = null

    fun get(context: Context): String? {
        cached?.let { return it }
        return runCatching {
            val prefs = context.applicationContext.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
            val existing = prefs.getString(key_device_id, null)
            val id = if (existing != null && valid_device_id.matches(existing)) {
                existing
            } else {
                generate().also { prefs.edit().putString(key_device_id, it).apply() }
            }
            cached = id
            id
        }.getOrNull()
    }

    private fun generate(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
