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

object MutedFolderSync {

    private const val PREFS_NAME = "aster_muted_folder_sync"
    private const val KEY_SYNCED_TOKENS = "synced_tokens"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable
    private data class UpdateMutedFoldersRequest(
        val folder_tokens: List<String>,
    )

    fun push(context: Context, tokens: Collection<String>) {
        val sorted = tokens.filter { it.isNotBlank() }.distinct().sorted()
        val marker = sorted.joinToString("\n")
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (prefs.getString(KEY_SYNCED_TOKENS, null) == marker) return

        val app_context = context.applicationContext

        scope.launch {
            val ok = runCatching {
                val client = EntryPointAccessors.fromApplication(
                    app_context,
                    UnifiedPushState.ApiClientEntryPoint::class.java,
                ).api_client()
                client.http.put(
                    "${client.base_url}/api/sync/v1/notification-muted-folders",
                ) {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateMutedFoldersRequest(folder_tokens = sorted))
                }.status.isSuccess()
            }.getOrDefault(false)

            if (ok) {
                prefs.edit().putString(KEY_SYNCED_TOKENS, marker).apply()
            }
        }
    }

    fun reset(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SYNCED_TOKENS)
            .apply()
    }
}
