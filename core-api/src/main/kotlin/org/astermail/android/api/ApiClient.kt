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

import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionSpec
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class ApiError(message: String) : Exception(message) {
    object NetworkError : ApiError("network error")
    object UnauthorizedError : ApiError("unauthorized")
    data class ForbiddenError(val detail: String = "forbidden") : ApiError(detail)
    data class PlanLimitExceeded(val detail: String, val resource: String?) : ApiError(detail)
    data class StorageQuotaExceeded(val detail: String) : ApiError(detail)
    object NotFoundError : ApiError("not found")
    data class ServerError(val code: Int) : ApiError("server error $code")
    data class ValidationError(val messages: List<String>) : ApiError(messages.joinToString("; "))
    data class UnknownError(val detail: String) : ApiError(detail)
}

interface TokenProvider {
    suspend fun load(): BearerTokens?
    suspend fun refresh(): BearerTokens?
    suspend fun clear()
}

fun build_user_agent(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL
    val device_name = if (model.startsWith(manufacturer, ignoreCase = true)) {
        model
    } else {
        "$manufacturer $model"
    }
    val android_version = Build.VERSION.RELEASE
    val sdk = Build.VERSION.SDK_INT
    return "AsterMail-Android/${BuildConfig.VERSION_NAME} (Android $android_version; SDK $sdk; $device_name)"
}

class ApiClient(
    val base_url: String,
    private val token_provider: TokenProvider,
    private val refresh_endpoint: String = "/api/core/v1/auth/refresh",
    private val csrf_endpoint: String = "/api/csrf/token",
    private val on_csrf_changed: (String?) -> Unit = {},
    initial_csrf: String? = null,
) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val csrf_mutex = Mutex()

    @Volatile
    private var csrf_token: String? = initial_csrf

    val http: HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            }
            addInterceptor(okhttp3.Interceptor { chain ->
                val original = chain.request()
                val method = original.method
                val is_safe = method == "GET" || method == "HEAD" || method == "OPTIONS"
                if (is_safe) {
                    chain.proceed(original)
                } else {
                    val token = csrf_token
                    if (token.isNullOrEmpty()) {
                        chain.proceed(original)
                    } else {
                        val existing_cookie = original.header("Cookie")
                        val pair = "csrf_token=$token"
                        val combined = if (existing_cookie.isNullOrBlank()) pair else "$existing_cookie; $pair"
                        val rebuilt = original.newBuilder()
                            .header("X-CSRF-Token", token)
                            .header("Cookie", combined)
                            .build()
                        chain.proceed(rebuilt)
                    }
                }
            })
        }
        expectSuccess = false

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 12_000
            connectTimeoutMillis = 6_000
            socketTimeoutMillis = 12_000
        }

        install(Auth) {
            bearer {
                loadTokens { token_provider.load() }
                refreshTokens { token_provider.refresh() }
                sendWithoutRequest { request ->
                    val path = request.url.buildString()
                    val is_public = path.endsWith("/auth/login") ||
                        path.endsWith("/auth/register") ||
                        path.endsWith("/auth/salt") ||
                        path.contains("/recovery/")
                    !is_public
                }
            }
        }

        defaultRequest {
            url.takeFrom(base_url)
            contentType(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, build_user_agent())
            header(HttpHeaders.Referrer, "${base_url.trimEnd('/')}/")
        }

        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                when (cause) {
                    is ClientRequestException -> throw map_http_status(cause.response.status.value, safe_read_body(cause.response))
                    is ServerResponseException -> throw ApiError.ServerError(cause.response.status.value)
                    else -> Unit
                }
            }
        }
    }

    suspend fun fetch_csrf_if_needed(): String? {
        csrf_token?.let { return it }
        return csrf_mutex.withLock {
            csrf_token?.let { return@withLock it }
            try {
                val response: HttpResponse = http.request {
                    method = HttpMethod.Get
                    url.takeFrom("$base_url$csrf_endpoint")
                }
                if (response.status.isSuccess()) {
                    val body_text = response.bodyAsStringSafe()
                    val token = extract_csrf_from_body(body_text)
                    if (token != null) {
                        csrf_token = token
                    }
                    token
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun set_csrf(token: String?) {
        csrf_token = token
        runCatching { on_csrf_changed(token) }
    }

    fun invalidate_bearer_cache() {
        runCatching {
            http.authProviders
                .filterIsInstance<BearerAuthProvider>()
                .firstOrNull()
                ?.clearToken()
        }
    }

    fun get_csrf(): String? = csrf_token

    fun clear_csrf() {
        csrf_token = null
        runCatching { on_csrf_changed(null) }
    }

    private fun extract_csrf_from_body(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val element = json.parseToJsonElement(body)
            val obj = element as? JsonObject ?: return null
            (obj["csrf_token"] ?: obj["token"])?.jsonPrimitive?.content
        } catch (_: SerializationException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun HttpResponse.bodyAsStringSafe(): String? = try {
        body<String>()
    } catch (_: Throwable) {
        null
    }

    private suspend fun safe_read_body(response: HttpResponse): String = try {
        response.body<String>()
    } catch (_: Throwable) {
        ""
    }

    fun map_http_status(code: Int, body: String): ApiError {
        val server_code = parse_error_code(body)
        val detail = parse_error_message(body) ?: ""
        if (server_code == "PLAN_LIMIT_EXCEEDED") {
            return ApiError.PlanLimitExceeded(
                detail = detail.ifBlank { "plan limit reached" },
                resource = parse_error_resource(body),
            ).also { emit_plan_limit(it) }
        }
        if (server_code == "STORAGE_QUOTA_EXCEEDED") {
            return ApiError.StorageQuotaExceeded(
                detail = detail.ifBlank { "storage full" },
            ).also { emit_storage_full(it) }
        }
        return when (code) {
            401 -> ApiError.UnauthorizedError.also { AuthEventBus.emit_unauthorized() }
            403 -> ApiError.ForbiddenError(detail.ifBlank { "forbidden" })
            404 -> ApiError.NotFoundError
            413 -> ApiError.StorageQuotaExceeded(detail.ifBlank { "storage full" })
                .also { emit_storage_full(it) }
            422 -> ApiError.ValidationError(parse_validation_messages(body))
            in 500..599 -> ApiError.ServerError(code)
            else -> ApiError.UnknownError(body.ifBlank { "http $code" })
        }
    }

    private fun emit_plan_limit(err: ApiError.PlanLimitExceeded) {
        UpgradeEventBus.emit_plan_limit(err.detail, err.resource)
    }

    private fun emit_storage_full(err: ApiError.StorageQuotaExceeded) {
        UpgradeEventBus.emit_storage_full(err.detail)
    }

    private fun parse_error_message(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
            obj["error"]?.jsonPrimitive?.content
        } catch (_: Throwable) {
            null
        }
    }

    private fun parse_error_code(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
            obj["code"]?.jsonPrimitive?.content
        } catch (_: Throwable) {
            null
        }
    }

    private fun parse_error_resource(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
            obj["resource"]?.jsonPrimitive?.content
        } catch (_: Throwable) {
            null
        }
    }

    private fun parse_validation_messages(body: String): List<String> {
        if (body.isBlank()) return emptyList()
        return try {
            val element = json.parseToJsonElement(body)
            val obj = element as? JsonObject ?: return listOf(body)
            val msg = obj["error"]?.jsonPrimitive?.content
            if (msg != null) listOf(msg) else emptyList()
        } catch (_: Throwable) {
            listOf(body)
        }
    }

    fun close() = http.close()
}

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
