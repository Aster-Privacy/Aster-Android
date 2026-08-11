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
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import org.astermail.android.api.ratchet.EnvelopeCapabilityResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.ReportEnvelopeCapabilityRequest

data class EnvelopeCapabilityReport(val at_ms: Long, val identity_fingerprint: String)

interface EnvelopeCapabilityStore {
    fun get_client_id(): String?
    fun put_client_id(client_id: String)
    fun get_last_report(user_id: String): EnvelopeCapabilityReport?
    fun put_last_report(user_id: String, report: EnvelopeCapabilityReport)
}

class SharedPrefsEnvelopeCapabilityStore(private val context: Context) : EnvelopeCapabilityStore {

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get_client_id(): String? = prefs().getString(CLIENT_ID_KEY, null)

    override fun put_client_id(client_id: String) {
        prefs().edit().putString(CLIENT_ID_KEY, client_id).apply()
    }

    override fun get_last_report(user_id: String): EnvelopeCapabilityReport? {
        val stored = runCatching { prefs().getString(LAST_REPORTED_PREFIX + user_id, null) }
            .getOrNull() ?: return null
        val at_ms = stored.substringBefore('|').toLongOrNull() ?: return null
        return EnvelopeCapabilityReport(at_ms, stored.substringAfter('|', ""))
    }

    override fun put_last_report(user_id: String, report: EnvelopeCapabilityReport) {
        prefs().edit()
            .putString(LAST_REPORTED_PREFIX + user_id, "${report.at_ms}|${report.identity_fingerprint}")
            .apply()
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

    suspend fun report_if_due(
        user_id: String,
        identity_public_b64: String?,
        force: Boolean = false,
    ): EnvelopeCapabilityResponse? {
        if (user_id.isBlank()) return null

        val now = now_ms()
        val fingerprint = identity_fingerprint(identity_public_b64)
        val last = store.get_last_report(user_id)

        if (!force &&
            last != null &&
            last.at_ms > 0L &&
            last.identity_fingerprint == fingerprint &&
            now - last.at_ms in 0 until REPORT_INTERVAL_MS
        ) {
            return null
        }

        val client_id = store.get_client_id() ?: new_client_id().also { store.put_client_id(it) }

        val response = runCatching {
            ratchet_api.report_envelope_capability(
                ReportEnvelopeCapabilityRequest(
                    client_id = client_id,
                    max_envelope_marker = MAX_ENVELOPE_MARKER,
                    platform = PLATFORM,
                    identity_fingerprint = fingerprint.ifEmpty { null },
                ),
            )
        }.getOrNull()

        if (response != null && response.success) {
            store.put_last_report(user_id, EnvelopeCapabilityReport(now, fingerprint))
        }
        return response
    }

    private fun identity_fingerprint(identity_public_b64: String?): String {
        if (identity_public_b64.isNullOrBlank()) return ""

        return runCatching {
            val point = Base64.getDecoder().decode(identity_public_b64)
            if (point.size != IDENTITY_POINT_LEN || point[0] != UNCOMPRESSED_POINT_TAG) return ""
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(point))
        }.getOrDefault("")
    }

    companion object {
        const val MAX_ENVELOPE_MARKER: Int = 4
        const val PLATFORM: String = "android"
        const val REPORT_INTERVAL_MS: Long = 7L * 24 * 60 * 60 * 1000
        private const val IDENTITY_POINT_LEN: Int = 65
        private const val UNCOMPRESSED_POINT_TAG: Byte = 0x04
    }
}
