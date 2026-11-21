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

@file:OptIn(ExperimentalSerializationApi::class)

package org.astermail.android.api.guard

import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_TOKEN_HEADER
import org.astermail.android.api.auth.LoginResponse
import org.astermail.android.api.auth.WebAuthnAssertionData
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.api.auth.read_login_response

const val GUARD_BASE_PATH = "/api/core/v1/guard"
const val GUARD_DEVICE_TYPE_MOBILE = "mobile"
const val GUARD_ENVELOPE_VERSION = 2

object GuardSwitch {
    const val NEW_DEVICE_APPROVAL = 1
    const val HOLD_CHANGES = 2
    const val APPROVE_MAIL_APPS = 4
    const val RECOVERY_WAIT = 8
}

object GuardHoldScope {
    const val FORWARDING_RULES = 1
    const val RECOVERY_PASSWORD = 2
    const val SECURITY_EXPORT = 4
}

@Serializable
data class WebAuthnAssertion(
    val raw_id: String,
    val response: WebAuthnAssertionData,
    val challenge_token: String,
)

@Serializable
@JsonClassDiscriminator("type")
sealed class StepUpProof {
    @Serializable
    @SerialName("password")
    data class Password(val password_hash: String, val totp_code: String?) : StepUpProof()

    @Serializable
    @SerialName("hardware_key")
    data class HardwareKey(val assertion: WebAuthnAssertion) : StepUpProof()

    @Serializable
    @SerialName("passkey")
    data class Passkey(val assertion: WebAuthnAssertion) : StepUpProof()
}

@Serializable
data class SignedAction(
    val nonce: String,
    val signature: String,
    val proof: StepUpProof? = null,
)

@Serializable
data class GuardChallenge(
    val nonce: String,
    val expires_at: String = "",
)

@Serializable
data class GuardThisDevice(
    val id: String,
    val long_trusted: Boolean = false,
    val approved_at: String? = null,
)

@Serializable
data class GuardDeviceInfo(
    val id: String,
    val name: String = "",
    val device_type: String = "",
    val approved_at: String? = null,
    val long_trusted: Boolean = false,
)

@Serializable
data class GuardPendingChange(
    val id: String,
    val kind: String = "",
    val apply_after: String = "",
    val created_at: String = "",
    val skippable: Boolean? = null,
    val cancellable: Boolean? = null,
)

@Serializable
data class GuardStatus(
    val switches: Int = 0,
    val hold_scopes: Int = 0,
    val version: Long = 0,
    val enrolled_at: String? = null,
    val recovery_wait_effective: Boolean = false,
    val paused_until: String? = null,
    val has_rescue_code: Boolean = false,
    val this_device: GuardThisDevice? = null,
    val devices: List<GuardDeviceInfo> = emptyList(),
    val pending_changes: List<GuardPendingChange> = emptyList(),
)

@Serializable
data class GuardNewDevice(
    val device_type: String = "",
    val name: String = "",
    val ed25519_pk: String = "",
    val mlkem_pk: String = "",
    val x25519_pk: String = "",
    val action_pk: String? = null,
    val keys_digest: String = "",
)

@Serializable
data class GuardRequestItem(
    val id: String,
    val kind: String = "sign_in",
    val created_at: String = "",
    val expires_at: String = "",
    val context: String = "",
    val new_device: GuardNewDevice = GuardNewDevice(),
    val envelope_versions: List<Int> = emptyList(),
)

@Serializable
data class GuardRequestList(
    val requests: List<GuardRequestItem> = emptyList(),
)

@Serializable
data class GuardDeviceKeys(
    val ed25519_pk: String,
    val mlkem_pk: String,
    val x25519_pk: String,
    val action_pk: String,
    val device_type: String = GUARD_DEVICE_TYPE_MOBILE,
    val name: String,
)

@Serializable
data class GuardSetupNonce(
    val nonce: String,
    val expires_at: String = "",
)

@Serializable
data class GuardSetupRequest(
    val device: GuardDeviceKeys,
    val nonce: String,
    val possession_signature: String,
    val rescue_code: String,
    val switches: Int,
    val hold_scopes: Int,
    val proof: StepUpProof,
)

@Serializable
data class GuardSetupResponse(
    val device_id: String,
    val code_secret_sealed: String,
    val status: GuardStatus = GuardStatus(),
)

@Serializable
data class GuardSwitchesRequest(
    val switches: Int,
    val hold_scopes: Int,
    val action: SignedAction,
)

@Serializable
data class GuardApproveRequest(
    val number: Int,
    val approval_envelope: String? = null,
    val action: SignedAction,
)

@Serializable
data class GuardActionRequest(
    val action: SignedAction,
)

@Serializable
data class GuardResume(
    val device_id: String,
    val nonce: String,
    val signature: String,
)

@Serializable
data class GuardSignInBegin(
    val device: GuardDeviceKeys,
    val envelope_versions: List<Int> = listOf(GUARD_ENVELOPE_VERSION),
    val number_hash: String,
    val resume: GuardResume? = null,
)

@Serializable
data class GuardSignInBeginResponse(
    val status: String,
    val expires_at: String = "",
)

@Serializable
data class GuardSignInStatus(
    val status: String,
    val expires_at: String = "",
    val apply_after: String? = null,
)

@Serializable
data class GuardResumeNonce(
    val nonce: String,
    val expires_at: String = "",
)

@Serializable
data class GuardRescueStarted(
    val rescue_id: String,
    val apply_after: String = "",
)

@Serializable
data class GuardRescueStatus(
    val status: String,
    val apply_after: String = "",
)

@Serializable
data class GuardJoinRequest(
    val device: GuardDeviceKeys,
    val envelope_versions: List<Int> = listOf(GUARD_ENVELOPE_VERSION),
    val number_hash: String,
    val action: SignedAction,
    val request_id: String? = null,
)

@Serializable
data class GuardJoinStarted(
    val request_id: String,
    val expires_at: String = "",
)

@Serializable
data class GuardJoinStatus(
    val status: String,
    val expires_at: String = "",
)

@Serializable
data class GuardJoinComplete(
    val device_id: String,
    val code_secret_sealed: String,
    val approval_envelope: String? = null,
)

@Serializable
data class GuardSignInGuard(
    val device_id: String,
    val code_secret_sealed: String,
    val approval_envelope: String? = null,
)

data class GuardSignInComplete(
    val login: LoginResponse,
    val guard: GuardSignInGuard?,
    val trusted_device_token: String? = null,
)

@Serializable
data class GuardPanicResult(
    val signed_out_sessions: Int = 0,
    val removed_devices: Int = 0,
    val cancelled_changes: Int = 0,
    val can_reset_password_now: Boolean = false,
)

@Serializable
data class GuardChangeResult(
    val id: String = "",
    val status: String = "",
    val kind: String = "",
    val apply_after: String? = null,
    val result: JsonObject? = null,
)

@Serializable
data class GuardChangeList(
    val changes: List<GuardPendingChange> = emptyList(),
)

@Serializable
data class GuardVetoResult(
    val status: String,
)

@Serializable
data class GuardCancelResult(
    val cancelled: Boolean = true,
)

@Serializable
data class GuardSkipResult(
    val status: String = GUARD_SKIP_APPLIED,
) {
    val stale: Boolean get() = status == GUARD_SKIP_FAILED_STALE
}

const val GUARD_SKIP_APPLIED = "applied"
const val GUARD_SKIP_FAILED_STALE = "failed_stale"

@Serializable
private data class GuardDeviceIdBody(val device_id: String)

@Serializable
private data class GuardCodeBody(val code: String)

@Serializable
private data class GuardRescueBody(val rescue_code: String)

@Serializable
private data class GuardJoinCompleteBody(val request_id: String)

@Serializable
private data class GuardVetoTokenBody(val id: String, val t: String)

@Serializable
private data class GuardVetoSignedBody(val id: String, val action: SignedAction)

interface GuardApi {
    suspend fun challenge(): GuardChallenge
    suspend fun status(): GuardStatus
    suspend fun step_up_passkey_options(): WebAuthnAssertionOptions
    suspend fun setup_nonce(): GuardSetupNonce
    suspend fun setup(req: GuardSetupRequest): GuardSetupResponse
    suspend fun put_switches(switches: Int, hold_scopes: Int, action: SignedAction): GuardStatus
    suspend fun requests(): GuardRequestList
    suspend fun approve(id: String, number: Int, approval_envelope: String?, action: SignedAction)
    suspend fun deny(id: String, action: SignedAction)
    suspend fun changes(): GuardChangeList
    suspend fun cancel_change(id: String, action: SignedAction): GuardCancelResult
    suspend fun skip_change(id: String, action: SignedAction): GuardSkipResult
    suspend fun change_result(id: String): GuardChangeResult
    suspend fun remove_device(id: String, action: SignedAction)
    suspend fun panic(action: SignedAction): GuardPanicResult
    suspend fun join(req: GuardJoinRequest): GuardJoinStarted
    suspend fun join_status(id: String): GuardJoinStatus
    suspend fun join_complete(request_id: String): GuardJoinComplete
    suspend fun sign_in_begin(token: String, req: GuardSignInBegin): GuardSignInBeginResponse
    suspend fun sign_in_resume_nonce(token: String, device_id: String): GuardResumeNonce
    suspend fun sign_in_status(token: String): GuardSignInStatus
    suspend fun sign_in_code(token: String, code: String): GuardSignInStatus
    suspend fun sign_in_complete(token: String): GuardSignInComplete
    suspend fun sign_in_rescue(token: String, rescue_code: String): GuardRescueStarted
    suspend fun rescue_status(rescue_id: String): GuardRescueStatus
    suspend fun veto_with_token(id: String, t: String): GuardVetoResult
    suspend fun veto_signed(id: String, action: SignedAction): GuardVetoResult
}

class GuardApiImpl(private val client: ApiClient) : GuardApi {

    private val base = GUARD_BASE_PATH

    private fun url(path: String): String = "${client.base_url}$base$path"

    private fun HttpRequestBuilder.json_body(value: Any) {
        contentType(ContentType.Application.Json)
        client.get_csrf()?.let { header("X-CSRF-Token", it) }
        setBody(value)
    }

    private fun HttpRequestBuilder.guard_token(token: String) {
        header(GUARD_TOKEN_HEADER, token)
    }

    override suspend fun challenge(): GuardChallenge =
        decode_or_throw(client.http.post(url("/challenge")) { json_body(JsonObject(emptyMap())) })

    override suspend fun status(): GuardStatus =
        decode_or_throw(client.http.get(url("")))

    override suspend fun step_up_passkey_options(): WebAuthnAssertionOptions =
        decode_or_throw(client.http.post(url("/step-up/passkey/options")) { json_body(JsonObject(emptyMap())) })

    override suspend fun setup_nonce(): GuardSetupNonce =
        decode_or_throw(client.http.post(url("/setup/nonce")) { json_body(JsonObject(emptyMap())) })

    override suspend fun setup(req: GuardSetupRequest): GuardSetupResponse =
        decode_or_throw(client.http.post(url("/setup")) { json_body(req) })

    override suspend fun put_switches(switches: Int, hold_scopes: Int, action: SignedAction): GuardStatus =
        decode_or_throw(
            client.http.put(url("/switches")) {
                json_body(GuardSwitchesRequest(switches, hold_scopes, action))
            },
        )

    override suspend fun requests(): GuardRequestList =
        decode_or_throw(client.http.get(url("/requests")))

    override suspend fun approve(id: String, number: Int, approval_envelope: String?, action: SignedAction) {
        expect_ok(
            client.http.post(url("/requests/${safe_id(id)}/approve")) {
                json_body(GuardApproveRequest(number, approval_envelope, action))
            },
        )
    }

    override suspend fun deny(id: String, action: SignedAction) {
        expect_ok(client.http.post(url("/requests/${safe_id(id)}/deny")) { json_body(GuardActionRequest(action)) })
    }

    override suspend fun changes(): GuardChangeList =
        decode_or_throw(client.http.get(url("/changes")))

    override suspend fun cancel_change(id: String, action: SignedAction): GuardCancelResult =
        decode_or_throw(client.http.post(url("/changes/${safe_id(id)}/cancel")) { json_body(GuardActionRequest(action)) })

    override suspend fun skip_change(id: String, action: SignedAction): GuardSkipResult =
        decode_or_throw(client.http.post(url("/changes/${safe_id(id)}/skip")) { json_body(GuardActionRequest(action)) })

    override suspend fun change_result(id: String): GuardChangeResult =
        decode_or_throw(client.http.get(url("/changes/${safe_id(id)}/result")))

    override suspend fun remove_device(id: String, action: SignedAction) {
        expect_ok(client.http.post(url("/devices/${safe_id(id)}/remove")) { json_body(GuardActionRequest(action)) })
    }

    override suspend fun panic(action: SignedAction): GuardPanicResult =
        decode_or_throw(client.http.post(url("/panic")) { json_body(GuardActionRequest(action)) })

    override suspend fun join(req: GuardJoinRequest): GuardJoinStarted =
        decode_or_throw(client.http.post(url("/join")) { json_body(req) })

    override suspend fun join_status(id: String): GuardJoinStatus =
        decode_or_throw(client.http.get(url("/join/${safe_id(id)}")))

    override suspend fun join_complete(request_id: String): GuardJoinComplete =
        decode_or_throw(client.http.post(url("/join/complete")) { json_body(GuardJoinCompleteBody(request_id)) })

    override suspend fun sign_in_begin(token: String, req: GuardSignInBegin): GuardSignInBeginResponse =
        decode_or_throw(
            client.http.post(url("/sign-in/begin")) {
                guard_token(token)
                json_body(req)
            },
        )

    override suspend fun sign_in_resume_nonce(token: String, device_id: String): GuardResumeNonce =
        decode_or_throw(
            client.http.post(url("/sign-in/resume-nonce")) {
                guard_token(token)
                json_body(GuardDeviceIdBody(device_id))
            },
        )

    override suspend fun sign_in_status(token: String): GuardSignInStatus =
        decode_or_throw(client.http.get(url("/sign-in/status")) { guard_token(token) })

    override suspend fun sign_in_code(token: String, code: String): GuardSignInStatus =
        decode_or_throw(
            client.http.post(url("/sign-in/code")) {
                guard_token(token)
                json_body(GuardCodeBody(code))
            },
        )

    override suspend fun sign_in_complete(token: String): GuardSignInComplete {
        val response = client.http.post(url("/sign-in/complete")) {
            guard_token(token)
            json_body(JsonObject(emptyMap()))
        }
        if (response.status.value !in 200..299) throw error_of(response)
        val text = response.bodyAsText()
        val login = read_login_response(client, response, text)
        val guard = runCatching {
            (client.json.parseToJsonElement(text) as? JsonObject)
                ?.get("guard")
                ?.takeIf { it is JsonObject }
                ?.let { client.json.decodeFromJsonElement(GuardSignInGuard.serializer(), it) }
        }.getOrNull()
        return GuardSignInComplete(login.response, guard, login.trusted_device_token)
    }

    override suspend fun sign_in_rescue(token: String, rescue_code: String): GuardRescueStarted =
        decode_or_throw(
            client.http.post(url("/sign-in/rescue")) {
                guard_token(token)
                json_body(GuardRescueBody(rescue_code))
            },
        )

    override suspend fun rescue_status(rescue_id: String): GuardRescueStatus =
        decode_or_throw(client.http.get(url("/sign-in/rescue/status")) { parameter("id", rescue_id) })

    override suspend fun veto_with_token(id: String, t: String): GuardVetoResult =
        decode_or_throw(client.http.post(url("/veto")) { json_body(GuardVetoTokenBody(id, t)) })

    override suspend fun veto_signed(id: String, action: SignedAction): GuardVetoResult =
        decode_or_throw(client.http.post(url("/veto")) { json_body(GuardVetoSignedBody(id, action)) })

    private fun safe_id(id: String): String {
        if (id.isEmpty() || !id.all { it.isLetterOrDigit() || it == '-' }) throw ApiError.NotFoundError
        return id
    }

    private suspend fun error_of(response: HttpResponse): ApiError {
        val body = try { response.bodyAsText() } catch (_: Throwable) { "" }
        return client.map_http_status(response.status.value, body, response.headers[HttpHeaders.RetryAfter])
    }

    private suspend fun expect_ok(response: HttpResponse) {
        if (response.status.value !in 200..299) throw error_of(response)
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) throw error_of(response)
        return try {
            response.body()
        } catch (t: kotlin.coroutines.cancellation.CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
