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

package org.astermail.android.ui.mail

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import dagger.hilt.android.EntryPointAccessors
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.astermail.android.AsterApplication

private val side_view_image_lock = Any()

@Volatile
private var side_view_image_client_instance: OkHttpClient? = null

private fun side_view_image_client(): OkHttpClient {
    side_view_image_client_instance?.let { return it }
    return synchronized(side_view_image_lock) {
        side_view_image_client_instance ?: OkHttpClient.Builder()
            .dns(org.astermail.android.api.DualStackDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
            .also { side_view_image_client_instance = it }
    }
}

internal fun proxied_image_request(url: String, access_token: String?): Request? {
    val token = access_token?.trim()
    if (token.isNullOrEmpty()) return null
    return Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .build()
}

private fun empty_image_response(code: Int, reason: String): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        null,
        code,
        reason,
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )

internal fun <T> fetch_with_one_token_refresh(
    token: String?,
    fetch: (String?) -> T?,
    status_of: (T) -> Int,
    close: (T) -> Unit,
    refresh: (String) -> String?,
): T? {
    val first = fetch(token) ?: return null
    if (status_of(first) != 401 || token.isNullOrBlank()) return first
    val refreshed = refresh(token)?.trim()?.takeIf { it.isNotEmpty() && it != token } ?: return first
    close(first)
    return fetch(refreshed)
}

private fun refreshed_access_token(context: Context, failed_token: String): String? =
    runCatching {
        val entry_point = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AsterApplication.ImageLoaderEntryPoint::class.java,
        )
        val token_store = entry_point.token_store()
        token_store.access_token?.takeIf { it.isNotBlank() && it != failed_token }
            ?: kotlinx.coroutines.runBlocking {
                when (entry_point.lazy_auth_repository().get().refresh_session()) {
                    org.astermail.android.api.auth.RefreshOutcome.Success ->
                        token_store.access_token?.takeIf { it != failed_token }
                    else -> null
                }
            }
    }.getOrNull()

private fun current_access_token(context: Context): String? =
    runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AsterApplication.ImageLoaderEntryPoint::class.java,
        ).token_store().access_token
    }.getOrNull()

internal fun intercept_side_view_image(
    context: Context?,
    request: WebResourceRequest?,
    allow_external: Boolean,
): WebResourceResponse? {
    val uri = request?.url ?: return null
    if (request.isForMainFrame) return null
    val action = mail_body_request_action(
        scheme = uri.scheme,
        host = uri.host,
        path = uri.path,
        proxied_url = runCatching { uri.getQueryParameter("url") }.getOrNull(),
        allow_external = allow_external,
        port = uri.port,
        user_info = uri.encodedUserInfo,
    )
    if (action == MailBodyRequestAction.LOCAL) return null
    if (action == MailBodyRequestAction.BLOCK || context == null) return empty_image_response(403, "Forbidden")
    val token = current_access_token(context)
    val client = side_view_image_client()
    return try {
        fun fetch_once(target: String, bearer: String?): okhttp3.Response? =
            proxied_image_request(target, bearer)?.let { client.newCall(it).execute() }
        fun fetch(bearer: String?): okhttp3.Response? {
            var target = uri.toString()
            var response = fetch_once(target, bearer) ?: return null
            if (response.isRedirect) {
                val next = proxy_redirect_target(target, response.header("Location"))
                if (next != null) {
                    response.close()
                    target = next
                    response = fetch_once(target, bearer) ?: return null
                }
            }
            if (proxy_status_is_retryable(response.code)) {
                response.close()
                response = fetch_once(target, bearer) ?: return null
            }
            return response
        }
        val response = fetch_with_one_token_refresh(
            token = token,
            fetch = ::fetch,
            status_of = { it.code },
            close = { it.close() },
            refresh = { failed -> refreshed_access_token(context, failed) },
        ) ?: return empty_image_response(403, "Forbidden")
        val body = response.body
        if (!response.isSuccessful || body == null) {
            response.close()
            return empty_image_response(404, "Not Found")
        }
        val content_type = response.header("Content-Type")?.substringBefore(';')?.trim()
            ?.takeIf { it.isNotBlank() } ?: "image/jpeg"
        WebResourceResponse(
            content_type,
            null,
            200,
            "OK",
            mapOf("Cache-Control" to "no-store"),
            body.byteStream(),
        )
    } catch (_: Throwable) {
        empty_image_response(404, "Not Found")
    }
}
