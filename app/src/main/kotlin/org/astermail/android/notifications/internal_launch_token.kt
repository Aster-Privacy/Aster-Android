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

package org.astermail.android.notifications

import android.content.Context
import android.content.Intent
import java.security.MessageDigest
import java.security.SecureRandom

object internal_launch_token {
    const val EXTRA_TOKEN = "internal_launch_token"
    private const val PREFS_NAME = "internal_launch"
    private const val KEY_TOKEN = "token"
    private const val TOKEN_BYTES = 32

    @Volatile
    private var cached: String? = null

    @Synchronized
    fun get(context: Context): String {
        cached?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: new_token().also { prefs.edit().putString(KEY_TOKEN, it).commit() }
        cached = token
        return token
    }

    fun is_trusted(context: Context, intent: Intent?): Boolean {
        val presented = runCatching { intent?.getStringExtra(EXTRA_TOKEN) }.getOrNull()
        if (presented.isNullOrBlank()) return false
        return matches(get(context), presented)
    }

    fun matches(expected: String, presented: String?): Boolean {
        if (expected.isBlank() || presented.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            presented.toByteArray(Charsets.UTF_8),
        )
    }

    fun new_token(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
