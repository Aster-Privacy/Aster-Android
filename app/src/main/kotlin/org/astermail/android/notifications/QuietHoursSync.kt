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
import dagger.hilt.android.EntryPointAccessors
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.TimeZone

object QuietHoursSync {

    private const val PREFS_NAME = "aster_quiet_hours_sync"
    private const val KEY_SYNCED_MARKER = "synced_marker"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable
    private data class SyncQuietHoursRequest(
        val enabled: Boolean,
        val start_time: String,
        val end_time: String,
        val timezone: String,
    )

    fun push(context: Context, enabled: Boolean, start: String, end: String) {
        val timezone = TimeZone.getDefault().id
        val marker = "$enabled|$start|$end|$timezone"
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (prefs.getString(KEY_SYNCED_MARKER, null) == marker) return

        val app_context = context.applicationContext

        scope.launch {
            val ok = runCatching {
                val client = EntryPointAccessors.fromApplication(
                    app_context,
                    UnifiedPushState.ApiClientEntryPoint::class.java,
                ).api_client()
                client.http.put("${client.base_url}/api/sync/v1/quiet-hours") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        SyncQuietHoursRequest(
                            enabled = enabled,
                            start_time = start,
                            end_time = end,
                            timezone = timezone,
                        ),
                    )
                }.status.isSuccess()
            }.getOrDefault(false)

            if (ok) {
                prefs.edit().putString(KEY_SYNCED_MARKER, marker).apply()
            }
        }
    }

    fun reset(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SYNCED_MARKER)
            .apply()
    }
}
