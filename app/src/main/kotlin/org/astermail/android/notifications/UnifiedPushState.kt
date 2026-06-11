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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.unifiedpush.android.connector.UnifiedPush

object UnifiedPushState {

    private const val PREFS_NAME = "aster_unifiedpush"
    private const val KEY_ENDPOINT = "endpoint_url"
    private const val KEY_REGISTERED_ENDPOINT = "registered_endpoint_url"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable
    private data class SubscribeRequest(
        val endpoint: String,
        val p256dh: String,
        val auth: String,
        val user_agent: String?,
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ApiClientEntryPoint {
        fun api_client(): ApiClient
    }

    fun endpoint(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENDPOINT, null)

    fun save_endpoint(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENDPOINT, url)
            .apply()
    }

    fun clear_endpoint(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ENDPOINT)
            .remove(KEY_REGISTERED_ENDPOINT)
            .apply()
    }

    fun try_register(context: Context) {
        runCatching {
            val distributors = UnifiedPush.getDistributors(context)
            if (distributors.isEmpty()) return@runCatching
            val current = UnifiedPush.getAckDistributor(context)
            if (current == null) {
                UnifiedPush.saveDistributor(context, distributors.first())
            }
            UnifiedPush.register(context)
        }
    }

    fun unregister(context: Context) {
        runCatching { UnifiedPush.unregister(context) }
        clear_endpoint(context)
    }

    fun register_with_backend(
        context: Context,
        endpoint_url: String,
        p256dh: String,
        auth: String,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_REGISTERED_ENDPOINT, null) == endpoint_url) return
        scope.launch {
            runCatching {
                val client = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ApiClientEntryPoint::class.java,
                ).api_client()
                val request = SubscribeRequest(
                    endpoint = endpoint_url,
                    p256dh = p256dh,
                    auth = auth,
                    user_agent = "Aster-Android",
                )
                client.http.post("${client.base_url}/api/sync/v1/web-push/subscribe") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body<Map<String, Any?>>()
                prefs.edit().putString(KEY_REGISTERED_ENDPOINT, endpoint_url).apply()
            }
        }
    }
}
