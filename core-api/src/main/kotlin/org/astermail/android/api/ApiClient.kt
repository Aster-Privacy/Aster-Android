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
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionSpec
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.timeout
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
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.astermail.android.api.mail_rules.Action as MailRuleAction
import org.astermail.android.api.mail_rules.Condition as MailRuleCondition

sealed class ApiError(message: String) : Exception(message) {
    object NetworkError : ApiError("network error")
    object UnauthorizedError : ApiError("unauthorized")
    object InvalidCredentials : ApiError("invalid credentials")
    data class PaymentRequired(val detail: String = "") : ApiError(detail)
    data class AttachmentTooLarge(val detail: String = "") : ApiError(detail)
    data class SendQuotaReached(val detail: String = "") : ApiError(detail)
    data class ForbiddenError(
        val detail: String = "forbidden",
        val code: String? = null,
    ) : ApiError(detail)
    data class PlanLimitExceeded(val detail: String, val resource: String?) : ApiError(detail)
    data class StorageQuotaExceeded(val detail: String) : ApiError(detail)
    object NotFoundError : ApiError("not found")
    data class ServerError(val code: Int) : ApiError("server error $code")
    data class ValidationError(
        val messages: List<String>,
        val code: String? = null,
        val details: Map<String, String> = emptyMap(),
    ) : ApiError(messages.joinToString("; "))
    data class Conflict(
        val detail: String,
        val code: String? = null,
    ) : ApiError(detail)
    data class RateLimited(
        val detail: String = "rate limited",
        val resets_at: String? = null,
        val code: String? = null,
        val details: Map<String, String> = emptyMap(),
    ) : ApiError(detail)
    data class UnknownError(val detail: String) : ApiError(detail)
    data class StepUpRequired(val detail: String = "step up required") : ApiError(detail)
}

const val FINGERPRINT_MISMATCH_CODE = "FINGERPRINT_MISMATCH"
const val INVALID_CREDENTIALS_CODE = "INVALID_CREDENTIALS"
const val STORAGE_QUOTA_CODE = "STORAGE_QUOTA_EXCEEDED"
const val ACCOUNT_SUSPENDED_CODE = "ACCOUNT_SUSPENDED"

fun map_unauthorized(server_code: String?, detail: String): ApiError =
    when (server_code) {
        FINGERPRINT_MISMATCH_CODE -> ApiError.StepUpRequired(detail.ifBlank { "step up required" })
        INVALID_CREDENTIALS_CODE -> ApiError.InvalidCredentials
        else -> ApiError.UnauthorizedError
    }

fun should_emit_unauthorized(server_code: String?): Boolean =
    server_code != INVALID_CREDENTIALS_CODE && server_code != FINGERPRINT_MISMATCH_CODE

object DualStackDns : okhttp3.Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> {
        val resolved = okhttp3.Dns.SYSTEM.lookup(hostname)
        if (resolved.size < 2) return resolved
        val v4 = resolved.filterIsInstance<java.net.Inet4Address>()
        if (v4.isEmpty() || v4.size == resolved.size) return resolved
        val v6 = resolved.filter { it !is java.net.Inet4Address }
        val ordered = ArrayList<java.net.InetAddress>(resolved.size)
        var i = 0
        while (i < v4.size || i < v6.size) {
            if (i < v4.size) ordered.add(v4[i])
            if (i < v6.size) ordered.add(v6[i])
            i++
        }
        return ordered
    }
}

interface TokenProvider {
    suspend fun load(): BearerTokens?
    suspend fun refresh(): BearerTokens?
    suspend fun clear()
}

fun build_user_agent(): String {
    val manufacturer = (Build.MANUFACTURER ?: "unknown").replaceFirstChar { it.uppercase() }
    val model = Build.MODEL ?: "device"
    val device_name = if (model.startsWith(manufacturer, ignoreCase = true)) {
        model
    } else {
        "$manufacturer $model"
    }
    val android_version = Build.VERSION.RELEASE ?: "0"
    val sdk = Build.VERSION.SDK_INT
    return "AsterMail-Android/${BuildConfig.VERSION_NAME} (Android $android_version; SDK $sdk; $device_name)"
}

@OptIn(ExperimentalSerializationApi::class)
private val mail_rules_fallback_module = SerializersModule {
    polymorphicDefaultDeserializer(MailRuleCondition::class) { MailRuleCondition.Unsupported.serializer() }
    polymorphicDefaultDeserializer(MailRuleAction::class) { MailRuleAction.Unsupported.serializer() }
}

class ApiClient(
    val base_url: String,
    private val token_provider: TokenProvider,
    private val refresh_endpoint: String = "/api/core/v1/auth/refresh",
    private val on_csrf_changed: (String?) -> Unit = {},
    initial_csrf: String? = null,
    private val csrf_refresher: suspend () -> String? = { null },
    private val allow_cleartext_for_test: Boolean = false,
) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
        serializersModule = mail_rules_fallback_module
    }

    private val csrf_mutex = Mutex()

    private val csrf_recovery_mutex = Mutex()

    @Volatile
    private var csrf_token: String? = initial_csrf

    private val api_host: String? = runCatching { io.ktor.http.Url(base_url).host }.getOrNull()

    val http: HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                val specs = if (allow_cleartext_for_test) {
                    listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)
                } else {
                    listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)
                }
                connectionSpecs(specs)
                dns(DualStackDns)
                retryOnConnectionFailure(true)
                connectTimeout(java.time.Duration.ofMillis(4_000))
                pingInterval(java.time.Duration.ofSeconds(20))
                connectionPool(okhttp3.ConnectionPool(5, 90, java.util.concurrent.TimeUnit.SECONDS))
            }
            addInterceptor(okhttp3.Interceptor { chain ->
                if (!org.astermail.android.api.network.low_network_state.active()) {
                    chain.proceed(chain.request())
                } else {
                    val extended = org.astermail.android.api.network.effective_request_timeout_ms(
                        chain.readTimeoutMillis().toLong(),
                        true,
                    ).toInt()
                    chain
                        .withReadTimeout(extended, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .withWriteTimeout(extended, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .proceed(chain.request())
                }
            })
            addNetworkInterceptor(okhttp3.Interceptor { chain ->
                val original = chain.request()
                val method = original.method
                val is_safe = method == "GET" || method == "HEAD" || method == "OPTIONS"
                if (is_safe) {
                    chain.proceed(original)
                } else {
                    val token = csrf_token
                    val same_host = api_host == null || original.url.host == api_host
                    if (token.isNullOrEmpty() || !same_host) {
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
            requestTimeoutMillis = 18_000
            connectTimeoutMillis = 4_000
            socketTimeoutMillis = 15_000
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
                        path.endsWith("/auth/refresh") ||
                        path.contains("/recovery/")
                    val same_host = api_host == null || request.url.host == api_host
                    !is_public && same_host
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

    init {
        http.plugin(HttpSend).intercept { request ->
            apply_folder_unlock_header(request)
            apply_low_network_timeout(request)
            val original_call: HttpClientCall = execute(request)
            if (original_call.response.status != HttpStatusCode.Forbidden) {
                return@intercept original_call
            }
            val method = request.method
            val is_unsafe = method != HttpMethod.Get &&
                method != HttpMethod.Head &&
                method != HttpMethod.Options
            if (!is_unsafe) {
                return@intercept original_call
            }
            val same_host = api_host == null || request.url.host == api_host
            if (!same_host) {
                return@intercept original_call
            }
            val saved_call = original_call.save()
            val body_text = try {
                saved_call.response.bodyAsText()
            } catch (_: Throwable) {
                ""
            }
            if (!body_text.contains("CSRF_INVALID")) {
                return@intercept saved_call
            }
            val token_used = csrf_token
            val fresh = csrf_recovery_mutex.withLock {
                val current = csrf_token
                if (!current.isNullOrEmpty() && current != token_used) {
                    current
                } else {
                    try {
                        csrf_refresher()
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
            if (fresh.isNullOrEmpty()) {
                saved_call
            } else {
                reattach_fresh_bearer(request)
                execute(request)
            }
        }
    }

    private fun apply_low_network_timeout(request: HttpRequestBuilder) {
        runCatching {
            if (!org.astermail.android.api.network.low_network_state.active()) return
            val existing = request.getCapabilityOrNull(HttpTimeoutCapability)
            if (existing?.requestTimeoutMillis != null) return
            val extended = org.astermail.android.api.network.LOW_NETWORK_MIN_REQUEST_TIMEOUT_MS
            request.timeout {
                requestTimeoutMillis = extended
                socketTimeoutMillis = extended
            }
        }
    }

    private fun apply_folder_unlock_header(request: HttpRequestBuilder) {
        runCatching {
            val same_host = api_host == null || request.url.host == api_host
            if (!same_host) return
            if (request.headers.contains(folder_unlock_header)) return
            val built = io.ktor.http.Url(request.url.buildString())
            val parameters = built.parameters.entries()
                .associate { entry -> entry.key to entry.value.toList() }
            val resolved = folder_unlock_resolver.resolve(
                folder_unlock_request(
                    method = request.method.value,
                    path = built.encodedPath,
                    parameters = parameters,
                ),
            )
            if (!resolved.isNullOrBlank()) {
                request.headers.append(folder_unlock_header, resolved)
            }
        }
    }

    private suspend fun reattach_fresh_bearer(request: HttpRequestBuilder) {
        runCatching {
            val tokens = token_provider.load() ?: return
            request.headers.remove(HttpHeaders.Authorization)
            request.headers.append(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }
    }

    suspend fun fetch_csrf_if_needed(): String? {
        csrf_token?.let { return it }
        return csrf_mutex.withLock {
            csrf_token?.let { return@withLock it }
            try {
                csrf_refresher()
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun set_csrf(token: String?) {
        val safe = sanitize_csrf(token)
        csrf_token = safe
        runCatching { on_csrf_changed(safe) }
    }

    private fun sanitize_csrf(token: String?): String? {
        if (token.isNullOrEmpty()) return null
        val ok = token.all { it.code in 0x21..0x7e && it != ';' && it != ',' }
        return if (ok) token else null
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
                detail = detail,
                resource = parse_error_resource(body),
            ).also { emit_plan_limit(it) }
        }
        if (server_code == STORAGE_QUOTA_CODE) {
            return ApiError.StorageQuotaExceeded(detail).also { emit_storage_full(it) }
        }
        if (server_code == "EXTERNAL_SEND_QUOTA_REACHED") {
            return ApiError.SendQuotaReached(detail.ifBlank { "You've reached this account's daily limit for messages to addresses outside Aster. Messages to other Aster addresses aren't affected." })
        }
        return when (code) {
            400 -> ApiError.ValidationError(
                parse_validation_messages(body).ifEmpty { listOf(detail.ifBlank { "bad request" }) },
                server_code,
                parse_error_details(body),
            )
            401 -> map_unauthorized(server_code, detail).also {
                if (should_emit_unauthorized(server_code)) AuthEventBus.emit_unauthorized()
            }
            402 -> ApiError.PaymentRequired(detail)
            403 -> ApiError.ForbiddenError(detail.ifBlank { "forbidden" }, server_code)
            404 -> ApiError.NotFoundError
            413 -> ApiError.AttachmentTooLarge(detail.ifBlank { "attachment too large" })
            422 -> ApiError.ValidationError(
                parse_validation_messages(body).ifEmpty { listOf(detail.ifBlank { "unprocessable request" }) },
                server_code,
                parse_error_details(body),
            )
            409 -> ApiError.Conflict(detail.ifBlank { "conflict" }, server_code)
            429 -> ApiError.RateLimited(
                detail = detail,
                resets_at = parse_error_resets_at(body),
                code = server_code,
                details = parse_error_details(body),
            )
            in 500..599 -> ApiError.ServerError(code)
            else -> ApiError.UnknownError(detail)
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

    private fun parse_error_details(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        return try {
            val obj = json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
            val details = obj["details"] as? JsonObject ?: return emptyMap()
            details.mapNotNull { (key, value) ->
                val primitive = value as? kotlinx.serialization.json.JsonPrimitive ?: return@mapNotNull null
                key to primitive.content
            }.toMap()
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun parse_error_resets_at(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
            obj["resets_at"]?.jsonPrimitive?.content
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
