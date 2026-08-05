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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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
    private const val KEY_REGISTERED_P256DH = "registered_p256dh"
    private const val KEY_REGISTERED_AUTH = "registered_auth"
    private const val KEY_PENDING_P256DH = "pending_p256dh"
    private const val KEY_PENDING_AUTH = "pending_auth"
    private const val KEY_LAST_SUBSCRIBED_AT = "last_subscribed_at"
    private const val KEY_LAST_REGISTER_ATTEMPT_AT = "last_register_attempt_at"
    private const val KEY_LAST_SUBSCRIBE_ATTEMPT_AT = "last_subscribe_attempt_at"
    private const val KEY_VAPID_PUBLIC_KEY = "vapid_public_key"
    private const val KEY_VAPID_FETCHED_AT = "vapid_fetched_at"
    private const val KEY_DEVICE_ID = "push_device_id"
    private const val RESUBSCRIBE_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val REGISTER_COOLDOWN_MS = 15L * 60L * 1000L
    private const val SUBSCRIBE_COOLDOWN_MS = 5L * 60L * 1000L
    private const val VAPID_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable
    private data class SubscribeRequest(
        val endpoint: String,
        val p256dh: String,
        val auth: String,
        val user_agent: String?,
        val device_id: String?,
    )

    @Serializable
    private data class UnsubscribeRequest(
        val endpoint: String,
    )

    @Serializable
    private data class VapidKeyResponse(
        val public_key: String? = null,
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ApiClientEntryPoint {
        fun api_client(): ApiClient
    }

    fun endpoint(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENDPOINT, null)

    fun has_pending_registration(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endpoint = prefs.getString(KEY_ENDPOINT, null)
        val registered = prefs.getString(KEY_REGISTERED_ENDPOINT, null)
        return endpoint == null || endpoint != registered
    }

    fun save_endpoint(context: Context, url: String, p256dh: String?, auth: String?) {
        val edit = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENDPOINT, url)
        if (!p256dh.isNullOrBlank() && !auth.isNullOrBlank()) {
            edit.putString(KEY_PENDING_P256DH, p256dh).putString(KEY_PENDING_AUTH, auth)
        }
        edit.apply()
    }

    fun clear_endpoint(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ENDPOINT)
            .remove(KEY_PENDING_P256DH)
            .remove(KEY_PENDING_AUTH)
            .remove(KEY_REGISTERED_ENDPOINT)
            .remove(KEY_REGISTERED_P256DH)
            .remove(KEY_REGISTERED_AUTH)
            .remove(KEY_LAST_SUBSCRIBED_AT)
            .remove(KEY_LAST_REGISTER_ATTEMPT_AT)
            .remove(KEY_LAST_SUBSCRIBE_ATTEMPT_AT)
            .apply()
    }

    fun clear_backend_registration(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_REGISTERED_ENDPOINT)
            .remove(KEY_REGISTERED_P256DH)
            .remove(KEY_REGISTERED_AUTH)
            .remove(KEY_LAST_SUBSCRIBED_AT)
            .remove(KEY_LAST_REGISTER_ATTEMPT_AT)
            .remove(KEY_LAST_SUBSCRIBE_ATTEMPT_AT)
            .apply()
    }

    fun subscription_stale(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endpoint = prefs.getString(KEY_REGISTERED_ENDPOINT, null) ?: return false
        val p256dh = prefs.getString(KEY_REGISTERED_P256DH, null) ?: return false
        if (endpoint.isBlank() || p256dh.isBlank()) return false
        val last = prefs.getLong(KEY_LAST_SUBSCRIBED_AT, 0L)
        return System.currentTimeMillis() - last > RESUBSCRIBE_INTERVAL_MS
    }

    fun sync_registration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endpoint = prefs.getString(KEY_ENDPOINT, null)
        val p256dh = prefs.getString(KEY_PENDING_P256DH, null)
        val auth = prefs.getString(KEY_PENDING_AUTH, null)
        if (endpoint.isNullOrBlank() || p256dh.isNullOrBlank() || auth.isNullOrBlank()) {
            try_register(context)
            return
        }
        register_with_backend(context, endpoint, p256dh, auth)
    }

    fun refresh_backend_subscription(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endpoint = prefs.getString(KEY_REGISTERED_ENDPOINT, null) ?: return
        val p256dh = prefs.getString(KEY_REGISTERED_P256DH, null) ?: return
        val auth = prefs.getString(KEY_REGISTERED_AUTH, null) ?: return
        post_subscription(context, endpoint, p256dh, auth)
    }

    private fun cooldown_active(context: Context, key: String, window_ms: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        if (last != 0L && now - last < window_ms) return true
        prefs.edit().putLong(key, now).apply()
        return false
    }

    fun try_register(context: Context) {
        scope.launch {
            runCatching {
                val distributors = UnifiedPush.getDistributors(context)
                if (distributors.isEmpty()) return@launch
                if (cooldown_active(context, KEY_LAST_REGISTER_ATTEMPT_AT, REGISTER_COOLDOWN_MS)) {
                    return@launch
                }
                if (UnifiedPush.getAckDistributor(context) == null) {
                    UnifiedPush.saveDistributor(context, distributors.first())
                }
                UnifiedPush.register(context, vapid = get_vapid_public_key(context))
            }
        }
    }

    fun reregister_with_vapid(context: Context) {
        scope.launch {
            runCatching {
                val vapid = get_vapid_public_key(context) ?: return@launch
                UnifiedPush.register(context, vapid = vapid)
            }
        }
    }

    private suspend fun get_vapid_public_key(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_VAPID_PUBLIC_KEY, null)
        val fetched_at = prefs.getLong(KEY_VAPID_FETCHED_AT, 0L)
        if (!cached.isNullOrBlank() && System.currentTimeMillis() - fetched_at < VAPID_CACHE_TTL_MS) {
            return cached
        }
        val fetched = runCatching {
            val client = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ApiClientEntryPoint::class.java,
            ).api_client()
            client.http.get("${client.base_url}/api/sync/v1/web-push/vapid-key")
                .body<VapidKeyResponse>()
                .public_key
        }.getOrNull()
        if (fetched.isNullOrBlank()) return cached
        prefs.edit()
            .putString(KEY_VAPID_PUBLIC_KEY, fetched)
            .putLong(KEY_VAPID_FETCHED_AT, System.currentTimeMillis())
            .apply()
        return fetched
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
        val unchanged = prefs.getString(KEY_REGISTERED_ENDPOINT, null) == endpoint_url &&
            prefs.getString(KEY_REGISTERED_P256DH, null) == p256dh &&
            prefs.getString(KEY_REGISTERED_AUTH, null) == auth
        if (unchanged && !subscription_stale(context)) return
        post_subscription(context, endpoint_url, p256dh, auth)
    }

    private fun device_id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    private fun post_subscription(
        context: Context,
        endpoint_url: String,
        p256dh: String,
        auth: String,
    ) {
        if (cooldown_active(context, KEY_LAST_SUBSCRIBE_ATTEMPT_AT, SUBSCRIBE_COOLDOWN_MS)) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previous_endpoint = prefs.getString(KEY_REGISTERED_ENDPOINT, null)
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
                    device_id = device_id(context),
                )
                val response = client.http.post("${client.base_url}/api/sync/v1/web-push/subscribe") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                if (response.status.isSuccess()) {
                    prefs.edit()
                        .putString(KEY_REGISTERED_ENDPOINT, endpoint_url)
                        .putString(KEY_REGISTERED_P256DH, p256dh)
                        .putString(KEY_REGISTERED_AUTH, auth)
                        .putLong(KEY_LAST_SUBSCRIBED_AT, System.currentTimeMillis())
                        .apply()
                    if (!previous_endpoint.isNullOrBlank() && previous_endpoint != endpoint_url) {
                        runCatching {
                            client.http.delete("${client.base_url}/api/sync/v1/web-push/subscribe") {
                                contentType(ContentType.Application.Json)
                                setBody(UnsubscribeRequest(endpoint = previous_endpoint))
                            }
                        }
                    }
                }
            }
        }
    }
}
