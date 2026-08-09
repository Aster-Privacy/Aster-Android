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

package org.astermail.android.mail.ratchet

import android.content.Context
import java.util.UUID
import org.astermail.android.api.ratchet.EnvelopeCapabilityResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.ReportEnvelopeCapabilityRequest

interface EnvelopeCapabilityStore {
    fun get_client_id(): String?
    fun put_client_id(client_id: String)
    fun get_last_reported_at_ms(user_id: String): Long
    fun put_last_reported_at_ms(user_id: String, at_ms: Long)
}

class SharedPrefsEnvelopeCapabilityStore(private val context: Context) : EnvelopeCapabilityStore {

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get_client_id(): String? = prefs().getString(CLIENT_ID_KEY, null)

    override fun put_client_id(client_id: String) {
        prefs().edit().putString(CLIENT_ID_KEY, client_id).apply()
    }

    override fun get_last_reported_at_ms(user_id: String): Long =
        prefs().getLong(LAST_REPORTED_PREFIX + user_id, 0L)

    override fun put_last_reported_at_ms(user_id: String, at_ms: Long) {
        prefs().edit().putLong(LAST_REPORTED_PREFIX + user_id, at_ms).apply()
    }

    private companion object {
        const val PREFS_NAME = "envelope_capability"
        const val CLIENT_ID_KEY = "client_id"
        const val LAST_REPORTED_PREFIX = "last_reported_"
    }
}

class EnvelopeCapabilityReporter(
    private val store: EnvelopeCapabilityStore,
    private val ratchet_api: RatchetApi,
    private val new_client_id: () -> String = { UUID.randomUUID().toString() },
    private val now_ms: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun report_if_due(user_id: String, force: Boolean = false): EnvelopeCapabilityResponse? {
        if (user_id.isBlank()) return null

        val now = now_ms()
        val last = store.get_last_reported_at_ms(user_id)
        if (!force && last > 0L && now - last in 0 until REPORT_INTERVAL_MS) return null

        val client_id = store.get_client_id() ?: new_client_id().also { store.put_client_id(it) }

        val response = runCatching {
            ratchet_api.report_envelope_capability(
                ReportEnvelopeCapabilityRequest(
                    client_id = client_id,
                    max_envelope_marker = MAX_ENVELOPE_MARKER,
                    platform = PLATFORM,
                ),
            )
        }.getOrNull()

        if (response != null && response.success) {
            store.put_last_reported_at_ms(user_id, now)
        }
        return response
    }

    companion object {
        const val MAX_ENVELOPE_MARKER: Int = 4
        const val PLATFORM: String = "android"
        const val REPORT_INTERVAL_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
