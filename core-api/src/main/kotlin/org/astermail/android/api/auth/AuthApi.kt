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

package org.astermail.android.api.auth

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.ContentType
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LoginRequest(
    val user_hash: String,
    val password_hash: String,
    val remember_me: Boolean? = null,
    val captcha_token: String? = null,
    @EncodeDefault val client_platform: String = "android",
)

@Serializable
data class LoginResponse(
    val user_id: String,
    val username: String,
    val email: String,
    val csrf_token: String,
    val encrypted_vault: String,
    val vault_nonce: String,
    val access_token: String? = null,
    val needs_prekey_replenishment: Boolean = false,
    val switch_token: String? = null,
    val switch_token_expires_at: String? = null,
    val is_suspended: Boolean? = null,
)

@Serializable
data class TotpChallengeResponse(
    val totp_required: Boolean = false,
    val pending_login_token: String,
    val available_methods: List<String> = emptyList(),
)

@Serializable
data class TotpLoginVerifyRequest(
    val code: String,
    val pending_login_token: String,
    val trust_device: Boolean = false,
)

data class TotpVerifyOutcome(
    val response: LoginResponse,
    val trusted_device_token: String?,
)

sealed interface LoginResult {
    data class Success(val response: LoginResponse) : LoginResult
    data class TotpRequired(val challenge: TotpChallengeResponse) : LoginResult
}

@Serializable
data class GetSaltRequest(
    val user_hash: String,
)

@Serializable
data class SaltResponse(
    val salt: String,
)

@Serializable
data class Argon2Params(
    val memory: Int = 65536,
    val iterations: Int = 3,
    val parallelism: Int = 4,
)

@Serializable
data class ClientPgpKeyData(
    val fingerprint: String,
    val key_id: String,
    val public_key_armored: String,
    val algorithm: String = "RSA-4096",
    val key_size: Int = 4096,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RegisterRequest(
    val username: String,
    val user_hash: String,
    val password_hash: String,
    val password_salt: String,
    val argon2_params: Argon2Params,
    val identity_key: String,
    val signed_prekey: String,
    val signed_prekey_signature: String,
    val encrypted_vault: String,
    val vault_nonce: String,
    val display_name: String? = null,
    val profile_color: String? = null,
    val email_domain: String? = null,
    val remember_me: Boolean? = null,
    val captcha_token: String? = null,
    val referral_code: String? = null,
    val pgp_key: ClientPgpKeyData? = null,
    @EncodeDefault val client_platform: String = "android",
)

@Serializable
data class RegisterResponse(
    val user_id: String,
    val username: String,
    val email: String,
    val csrf_token: String,
    val access_token: String? = null,
    val recovery_email_required: Boolean = false,
)

@Serializable
data class RefreshResponse(
    val csrf_token: String,
    val access_token: String,
)

@Serializable
data class BatchProfilesRequest(
    val emails: List<String>,
)

@Serializable
data class PublicProfile(
    val display_name: String? = null,
    val profile_picture: String? = null,
    val profile_color: String? = null,
)

@Serializable
data class BatchProfilesResponse(
    val profiles: Map<String, PublicProfile> = emptyMap(),
)

@Serializable
data class UserInfo(
    val user_id: String,
    val username: String? = null,
    val email: String? = null,
    val display_name: String? = null,
    val profile_color: String? = null,
    val profile_picture: String? = null,
    val created_at: String? = null,
    val identity_key: String? = null,
)

@Serializable
data class DeleteAccountRequest(
    val password_hash: String,
    val totp_code: String? = null,
)

interface AuthApi {
    suspend fun get_user_salt(user_hash: String): SaltResponse
    suspend fun login(request: LoginRequest, trusted_device_token: String? = null): LoginResult
    suspend fun verify_totp_login(request: TotpLoginVerifyRequest): TotpVerifyOutcome
    suspend fun register(request: RegisterRequest): RegisterResponse
    suspend fun refresh(): RefreshResponse
    suspend fun logout()
    suspend fun me(): UserInfo
    suspend fun batch_profiles(emails: List<String>): BatchProfilesResponse
    suspend fun delete_account(request: DeleteAccountRequest)
}

class AuthApiImpl(private val client: ApiClient) : AuthApi {
    private val base = "/api/core/v1/auth"

    override suspend fun get_user_salt(user_hash: String): SaltResponse {
        val response = client.http.post("${client.base_url}$base/salt") {
            contentType(ContentType.Application.Json)
            setBody(GetSaltRequest(user_hash))
        }
        return decode_or_throw(response)
    }

    override suspend fun login(request: LoginRequest, trusted_device_token: String?): LoginResult {
        val response = client.http.post("${client.base_url}$base/login") {
            contentType(ContentType.Application.Json)
            if (!trusted_device_token.isNullOrBlank()) {
                header(HttpHeaders.Cookie, "aster_td=$trusted_device_token")
            }
            setBody(request)
        }
        if (!response_is_success(response.status.value)) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        val body_str: String = response.body()
        val json_element = client.json.parseToJsonElement(body_str)
        val json_obj = json_element as? JsonObject
        if (json_obj != null && json_obj.containsKey("totp_required")) {
            val challenge = client.json.decodeFromString<TotpChallengeResponse>(body_str)
            return LoginResult.TotpRequired(challenge)
        }
        val login_resp = client.json.decodeFromString<LoginResponse>(body_str)
        client.set_csrf(login_resp.csrf_token)
        return LoginResult.Success(login_resp)
    }

    override suspend fun verify_totp_login(request: TotpLoginVerifyRequest): TotpVerifyOutcome {
        val response = client.http.post("${client.base_url}$base/totp/verify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val body: LoginResponse = decode_or_throw(response)
        client.set_csrf(body.csrf_token)
        val td_token = parse_trusted_device_cookie(response.headers.getAll(HttpHeaders.SetCookie))
        return TotpVerifyOutcome(response = body, trusted_device_token = td_token)
    }

    private fun parse_trusted_device_cookie(set_cookies: List<String>?): String? {
        if (set_cookies.isNullOrEmpty()) return null
        val needle = "aster_td="
        for (raw in set_cookies) {
            var idx = raw.indexOf(needle)
            while (idx >= 0) {
                val before = if (idx == 0) ' ' else raw[idx - 1]
                if (before == ' ' || before == ',' || before == ';') {
                    val start = idx + needle.length
                    var end = start
                    while (end < raw.length && raw[end] != ';' && raw[end] != ',' && !raw[end].isWhitespace()) {
                        end++
                    }
                    val value = raw.substring(start, end).trim()
                    if (value.isNotEmpty()) {
                        android.util.Log.i("AuthApi", "trusted_device cookie parsed (len=${value.length})")
                        return value
                    }
                }
                idx = raw.indexOf(needle, idx + 1)
            }
        }
        android.util.Log.w("AuthApi", "no aster_td cookie in Set-Cookie headers (count=${set_cookies.size})")
        for ((i, c) in set_cookies.withIndex()) {
            val name = c.substringBefore('=', missingDelimiterValue = "?")
            android.util.Log.w("AuthApi", "set-cookie[$i] name=$name len=${c.length}")
        }
        return null
    }

    override suspend fun register(request: RegisterRequest): RegisterResponse {
        val response = client.http.post("${client.base_url}$base/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val body: RegisterResponse = decode_or_throw(response)
        client.set_csrf(body.csrf_token)
        return body
    }

    override suspend fun refresh(): RefreshResponse {
        val response = client.http.post("${client.base_url}$base/refresh") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody("{}")
        }
        val body: RefreshResponse = decode_or_throw(response)
        client.set_csrf(body.csrf_token)
        return body
    }

    override suspend fun logout() {
        val response = client.http.post("${client.base_url}$base/logout") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody("{}")
        }
        if (!response_is_success(response.status.value)) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun me(): UserInfo {
        val response = client.http.get("${client.base_url}$base/me")
        return decode_or_throw(response)
    }

    override suspend fun delete_account(request: DeleteAccountRequest) {
        val response = client.http.delete("${client.base_url}$base/me") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (!response_is_success(response.status.value)) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    override suspend fun batch_profiles(emails: List<String>): BatchProfilesResponse {
        val response = client.http.post("${client.base_url}$base/profiles") {
            contentType(ContentType.Application.Json)
            setBody(BatchProfilesRequest(emails))
        }
        return decode_or_throw(response)
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (!response_is_success(response.status.value)) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        return try {
            response.body()
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }

    private fun response_is_success(code: Int) = code in 200..299
}
