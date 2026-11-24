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

import org.astermail.android.api.errors.ClientErrorReporter
import org.astermail.android.api.errors.feature_of_path
import org.astermail.android.api.errors.http_error_code as build_http_error_code

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
    object RefreshTokenRevoked : ApiError("refresh token revoked")
    object InvalidCredentials : ApiError("invalid credentials")
    data class PaymentRequired(val detail: String = "") : ApiError(detail)
    data class AttachmentTooLarge(val detail: String = "") : ApiError(detail)
    data class SendQuotaReached(val detail: String = "") : ApiError(detail)
    data class ForbiddenError(
        val detail: String = "forbidden",
        val code: String? = null,
        val details: Map<String, String> = emptyMap(),
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
    data class UpgradeRequired(
        val detail: String = "upgrade required",
        val code: String? = null,
    ) : ApiError(detail)
    data class GuardRequired(
        val request_id: String,
        val guard_token: String,
        val expires_at: String,
        val methods: List<String>,
        val user_id: String? = null,
    ) : ApiError("guard approval required")
    data class GuardHeld(
        val id: String,
        val kind: String,
        val apply_after: String,
    ) : ApiError("change held")
    data class GuardDevicePending(
        val device_id: String,
        val activates_at: String,
        val change_id: String,
    ) : ApiError("device pending")
    data class GuardRetry(
        val code: String,
        val retry_after_secs: Long,
    ) : ApiError("guard retry later")
    data class PreconditionRequired(
        val detail: String = "precondition required",
        val code: String? = null,
        val details: Map<String, String> = emptyMap(),
    ) : ApiError(detail)
}

const val GUARD_HEADER = "X-Aster-Guard"
const val GUARD_TOKEN_HEADER = "X-Aster-Guard-Token"
const val GUARD_REQUIRED_CODE = "GUARD_REQUIRED"
const val GUARD_HELD_CODE = "GUARD_HELD"
const val GUARD_DEVICE_PENDING_CODE = "GUARD_DEVICE_PENDING"
const val GUARD_DEVICE_REQUIRED_CODE = "GUARD_DEVICE_REQUIRED"
const val GUARD_ACTION_INVALID_CODE = "GUARD_ACTION_INVALID"
const val GUARD_REQUEST_GONE_CODE = "GUARD_REQUEST_GONE"
const val GUARD_WRONG_NUMBER_CODE = "GUARD_WRONG_NUMBER"
const val GUARD_DISABLED_CODE = "GUARD_DISABLED"
const val GUARD_PAUSED_CODE = "GUARD_PAUSED"
const val GUARD_TOO_MANY_REQUESTS_CODE = "GUARD_TOO_MANY_REQUESTS"
const val GUARD_ALREADY_ON_CODE = "GUARD_ALREADY_ON"
const val GUARD_ENVELOPE_REQUIRED_CODE = "GUARD_ENVELOPE_REQUIRED"
const val GUARD_VETO_INVALID_CODE = "GUARD_VETO_INVALID"
const val GUARD_NOT_ALLOWED_CODE = "GUARD_NOT_ALLOWED"
const val GUARD_DEVICE_UNKNOWN_CODE = "GUARD_DEVICE_UNKNOWN"
const val GUARD_LAST_DEVICE_CODE = "GUARD_LAST_DEVICE"
const val GUARD_REQUEST_CONFLICT_CODE = "GUARD_REQUEST_CONFLICT"
const val GUARD_CODE_INVALID_CODE = "GUARD_CODE_INVALID"
const val GUARD_RESCUE_CODE_INVALID_CODE = "GUARD_RESCUE_CODE_INVALID"
const val GUARD_ENVELOPE_UNSUPPORTED_CODE = "GUARD_ENVELOPE_UNSUPPORTED"
const val GUARD_DEVICE_TYPE_INVALID_CODE = "GUARD_DEVICE_TYPE_INVALID"
const val GUARD_INVALID_KEY_CODE = "GUARD_INVALID_KEY"

fun api_error_code(t: Throwable): String? = when (t) {
    is ApiError.ForbiddenError -> t.code
    is ApiError.Conflict -> t.code
    is ApiError.ValidationError -> t.code
    is ApiError.RateLimited -> t.code
    is ApiError.GuardRetry -> t.code
    else -> null
}

const val VAULT_LEVEL_HEADER = "X-Aster-Vault-Level"
const val VAULT_LEVEL = "2"

const val FINGERPRINT_MISMATCH_CODE = "FINGERPRINT_MISMATCH"
const val INVALID_CREDENTIALS_CODE = "INVALID_CREDENTIALS"
const val REFRESH_TOKEN_REVOKED_CODE = "REFRESH_TOKEN_REVOKED"
const val STORAGE_QUOTA_CODE = "STORAGE_QUOTA_EXCEEDED"
const val ACCOUNT_SUSPENDED_CODE = "ACCOUNT_SUSPENDED"
const val CAPTCHA_FAILED_CODE = "CAPTCHA_FAILED"

fun map_unauthorized(server_code: String?, detail: String): ApiError =
    when (server_code) {
        FINGERPRINT_MISMATCH_CODE -> ApiError.StepUpRequired(detail.ifBlank { "step up required" })
        INVALID_CREDENTIALS_CODE -> ApiError.InvalidCredentials
        REFRESH_TOKEN_REVOKED_CODE -> ApiError.RefreshTokenRevoked
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
    suspend fun refresh(failed_access_token: String?): BearerTokens? = refresh()
    suspend fun clear()
}

private val sign_in_path_suffixes = listOf(
    "/auth/login",
    "/auth/register",
    "/auth/salt",
    "/auth/refresh",
    "/auth/totp/verify",
    "/auth/totp/backup-code",
    "/auth/hardware-keys/assert/initiate",
    "/auth/hardware-keys/assert/verify",
)

fun is_sign_in_path(path: String): Boolean =
    path.contains("/recovery/") ||
        path.contains("/guard/sign-in/") ||
        path.endsWith("/guard/veto") ||
        sign_in_path_suffixes.any { path.endsWith(it) }

fun build_user_agent(app_version: String = BuildConfig.VERSION_NAME): String {
    val android_version = Build.VERSION.RELEASE ?: "0"
    return "AsterMail-Android/$app_version (Android $android_version)"
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
    release_name: String = BuildConfig.VERSION_NAME,
    private val device_id: String? = null,
    val webauthn_origin: String = BuildConfig.WEBAUTHN_ORIGIN,
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
                connectionSpecs(api_connection_specs(allow_cleartext_for_test))
                dns(DualStackDns)
                retryOnConnectionFailure(true)
                connectTimeout(java.time.Duration.ofMillis(4_000))
                pingInterval(java.time.Duration.ofSeconds(20))
                connectionPool(okhttp3.ConnectionPool(5, 90, java.util.concurrent.TimeUnit.SECONDS))
            }
            addInterceptor(okhttp3.Interceptor { chain ->
                val sent = System.currentTimeMillis()
                val response = chain.proceed(chain.request())
                if (api_host == null || chain.request().url.host == api_host) {
                    org.astermail.android.api.network.server_clock.note(response.header("Date"), sent, System.currentTimeMillis())
                }
                response
            })
            addInterceptor(okhttp3.Interceptor { chain ->
                try {
                    chain.proceed(chain.request())
                } catch (error: java.net.SocketTimeoutException) {
                    org.astermail.android.api.network.low_network_state.note_timeout()
                    throw error
                }
            })
            addInterceptor(okhttp3.Interceptor { chain ->
                if (!org.astermail.android.api.network.low_network_state.extend_timeouts()) {
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
                refreshTokens {
                    val failed = response.call.request.headers[HttpHeaders.Authorization]
                        ?.removePrefix("Bearer ")
                        ?.trim()
                    token_provider.refresh(failed)
                }
                sendWithoutRequest { request ->
                    val same_host = api_host == null || request.url.host == api_host
                    !is_sign_in_path(request.url.build().encodedPath) && same_host
                }
            }
        }

        defaultRequest {
            url.takeFrom(base_url)
            contentType(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, build_user_agent(release_name))
            header(HttpHeaders.Referrer, "${base_url.trimEnd('/')}/")
            device_id?.let { header(DEVICE_ID_HEADER, it) }
            header(VAULT_LEVEL_HEADER, VAULT_LEVEL)
            header(GUARD_HEADER, "1")
        }

        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                note_slow_link(cause)
                when (cause) {
                    is ClientRequestException -> throw map_http_status(cause.response.status.value, safe_read_body(cause.response))
                    is ServerResponseException -> throw ApiError.ServerError(cause.response.status.value)
                    else -> Unit
                }
            }
        }
    }

    init {
        ClientErrorReporter.configure(base_url, release_name, build_user_agent(release_name))
        http.requestPipeline.intercept(io.ktor.client.request.HttpRequestPipeline.Before) {
            apply_low_network_timeout(context)
        }
        http.plugin(HttpSend).intercept { request ->
            apply_folder_unlock_header(request)
            val original_call: HttpClientCall = try {
                execute(request)
            } catch (error: Throwable) {
                note_slow_link(error)
                report_transport_failure(
                    request.url.encodedPathSegments.joinToString("/"),
                    error,
                )
                throw error
            }
            report_failed_call(request.url.encodedPathSegments.joinToString("/"), original_call.response.status.value)
            if (original_call.response.status == HttpStatusCode.Accepted) {
                val saved_accepted = original_call.save()
                val accepted_body = try {
                    saved_accepted.response.bodyAsText()
                } catch (_: Throwable) {
                    ""
                }
                parse_guard_held(accepted_body)?.let { throw it }
                return@intercept saved_accepted
            }
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

    private fun report_failed_call(path: String, status: Int) {
        runCatching {
            if (status < 400) return
            if (ClientErrorReporter.is_report_path(path)) return
            val feature = feature_of_path(path)
            ClientErrorReporter.report(
                feature = feature,
                error_code = build_http_error_code(feature, status),
                severity = if (status >= 500) "error" else "warn",
                http_status = status,
            )
        }
    }

    private fun report_transport_failure(path: String, error: Throwable) {
        runCatching {
            if (error is kotlinx.coroutines.CancellationException) return
            if (ClientErrorReporter.is_report_path(path)) return
            val feature = feature_of_path(path)
            val suffix = if (error is io.ktor.client.plugins.HttpRequestTimeoutException) {
                "timeout"
            } else {
                "unreachable"
            }
            ClientErrorReporter.report(
                feature = feature,
                error_code = "${feature}_$suffix".take(64),
                severity = "error",
            )
        }
    }

    private fun is_timeout_chain(error: Throwable): Boolean {
        var current: Throwable? = error
        repeat(6) {
            val step = current ?: return false
            if (step is io.ktor.client.plugins.HttpRequestTimeoutException ||
                step is java.net.SocketTimeoutException
            ) {
                return true
            }
            current = step.cause?.takeIf { it !== step }
        }
        return false
    }

    private fun note_slow_link(error: Throwable) {
        if (is_timeout_chain(error)) org.astermail.android.api.network.low_network_state.note_timeout()
    }

    private fun apply_low_network_timeout(request: HttpRequestBuilder) {
        runCatching {
            if (!org.astermail.android.api.network.low_network_state.extend_timeouts()) return
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
        if (is_sign_in_path(request.url.build().encodedPath)) return
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

    fun map_http_status(code: Int, body: String, retry_after: String? = null): ApiError {
        val server_code = parse_error_code(body)
        val detail = parse_error_message(body) ?: ""
        map_guard_error(code, server_code, body, retry_after)?.let { return it }
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
            403 -> ApiError.ForbiddenError(
                detail.ifBlank { "forbidden" },
                server_code,
                parse_error_details(body),
            ).also {
                if (server_code == ACCOUNT_SUSPENDED_CODE) AuthEventBus.emit_suspended()
            }
            404 -> ApiError.NotFoundError
            413 -> ApiError.AttachmentTooLarge(detail.ifBlank { "attachment too large" })
            422 -> ApiError.ValidationError(
                parse_validation_messages(body).ifEmpty { listOf(detail.ifBlank { "unprocessable request" }) },
                server_code,
                parse_error_details(body),
            )
            409 -> ApiError.Conflict(detail.ifBlank { "conflict" }, server_code)
            426 -> ApiError.UpgradeRequired(detail.ifBlank { "upgrade required" }, server_code).also { ClientUpdateNotice.note(it) }
            428 -> ApiError.PreconditionRequired(
                detail.ifBlank { "precondition required" },
                server_code,
                parse_error_details(body),
            )
            429 -> ApiError.RateLimited(
                detail = detail,
                resets_at = parse_error_resets_at(body),
                code = server_code,
                details = parse_error_details(body),
            )
            405, 406, 415 -> ApiError.ServerError(code)
            in 500..599 -> ApiError.ServerError(code)
            else -> ApiError.UnknownError(detail)
        }
    }

    private fun map_guard_error(
        code: Int,
        server_code: String?,
        body: String,
        retry_after: String?,
    ): ApiError? {
        if (server_code == null || !server_code.startsWith("GUARD_")) return null
        val details = parse_guard_details(body)
        fun text(key: String): String? =
            (details?.get(key) as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
        return when {
            code == 403 && server_code == GUARD_REQUIRED_CODE -> {
                val request_id = text("request_id") ?: return null
                val guard_token = text("guard_token") ?: return null
                val methods = (details?.get("methods") as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    .orEmpty()
                ApiError.GuardRequired(
                    request_id,
                    guard_token,
                    text("expires_at").orEmpty(),
                    methods,
                    text("user_id")?.takeIf { runCatching { java.util.UUID.fromString(it) }.isSuccess },
                )
            }
            code == 403 && server_code == GUARD_DEVICE_PENDING_CODE -> ApiError.GuardDevicePending(
                device_id = text("device_id").orEmpty(),
                activates_at = text("activates_at").orEmpty(),
                change_id = text("change_id").orEmpty(),
            )
            (code == 409 || code == 202) && server_code == GUARD_HELD_CODE -> {
                val id = text("id") ?: return null
                ApiError.GuardHeld(id, text("kind").orEmpty(), text("apply_after").orEmpty())
            }
            code == 429 && (server_code == GUARD_PAUSED_CODE || server_code == GUARD_TOO_MANY_REQUESTS_CODE) -> {
                val from_body = (details?.get("retry_after_secs") as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content
                    ?.toLongOrNull()
                val seconds = from_body ?: retry_after?.trim()?.toLongOrNull() ?: 0L
                ApiError.GuardRetry(server_code, seconds.coerceAtLeast(0L))
            }
            code == 410 -> ApiError.Conflict("gone", server_code)
            else -> null
        }
    }

    internal fun parse_guard_held(body: String): ApiError.GuardHeld? {
        if (body.isBlank()) return null
        if (parse_error_code(body) != GUARD_HELD_CODE) return null
        return map_guard_error(202, GUARD_HELD_CODE, body, null) as? ApiError.GuardHeld
    }

    private fun parse_guard_details(body: String): JsonObject? {
        if (body.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
            obj["details"] as? JsonObject
        } catch (_: Throwable) {
            null
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

internal fun api_connection_specs(allow_cleartext: Boolean): List<ConnectionSpec> =
    if (allow_cleartext) {
        listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS)
    } else {
        listOf(ConnectionSpec.MODERN_TLS)
    }

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
