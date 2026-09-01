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

package org.astermail.android.billing

import android.content.Context

object ReviewPrompt {
    const val REQUIRED_SENDS = 5
    const val DELAY_MS = 3L * 24L * 60L * 60L * 1000L

    private const val PREFS_NAME = "aster_review_prompt"
    private const val KEY_SENDS = "sends"
    private const val KEY_ELIGIBLE_AT = "eligible_at"
    private const val KEY_DONE = "done"

    fun counts_send(done: Boolean, eligible_at: Long): Boolean = !done && eligible_at <= 0L

    fun becomes_eligible(sends: Int): Boolean = sends >= REQUIRED_SENDS

    fun is_eligible(done: Boolean, eligible_at: Long, now_ms: Long): Boolean =
        !done && eligible_at > 0L && now_ms - eligible_at >= DELAY_MS

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record_send(context: Context) {
        runCatching {
            val store = prefs(context)
            val done = store.getBoolean(KEY_DONE, false)
            val eligible_at = store.getLong(KEY_ELIGIBLE_AT, 0L)
            if (!counts_send(done, eligible_at)) return

            val sends = store.getInt(KEY_SENDS, 0) + 1
            val edit = store.edit().putInt(KEY_SENDS, sends)
            if (becomes_eligible(sends)) edit.putLong(KEY_ELIGIBLE_AT, System.currentTimeMillis())
            edit.apply()
        }
    }

    fun should_request(context: Context): Boolean = runCatching {
        val store = prefs(context)
        is_eligible(
            done = store.getBoolean(KEY_DONE, false),
            eligible_at = store.getLong(KEY_ELIGIBLE_AT, 0L),
            now_ms = System.currentTimeMillis(),
        )
    }.getOrDefault(false)

    fun mark_done(context: Context) {
        runCatching { prefs(context).edit().putBoolean(KEY_DONE, true).apply() }
    }
}
