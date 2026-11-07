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

package org.astermail.android.auth

import com.yubico.yubikit.core.YubiKeyConnection
import com.yubico.yubikit.core.application.ApplicationNotAvailableException
import com.yubico.yubikit.core.application.CommandState
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.fido.client.AuthInvalidClientError
import com.yubico.yubikit.fido.client.ClientError
import com.yubico.yubikit.fido.client.MultipleAssertionsAvailable
import com.yubico.yubikit.fido.client.PinRequiredClientError
import com.yubico.yubikit.fido.client.WebAuthnClient
import com.yubico.yubikit.fido.client.clientdata.ClientDataProvider
import com.yubico.yubikit.fido.webauthn.AuthenticatorAssertionResponse
import com.yubico.yubikit.fido.webauthn.PublicKeyCredential
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialDescriptor
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialRequestOptions
import com.yubico.yubikit.fido.webauthn.PublicKeyCredentialType
import com.yubico.yubikit.fido.webauthn.UserVerificationRequirement
import java.io.IOException
import java.util.Base64
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.json.JSONObject

const val security_key_min_pin_length = 4
const val security_key_options_max_age_ms = 60_000L

fun interface SecurityKeyTarget {
    fun open(): YubiKeyConnection
}

data class SecurityKeyRequest(
    val options: WebAuthnAssertionOptions,
    val origin: String,
)

data class SecurityKeyAssertion(
    val response_json: String,
    val options: WebAuthnAssertionOptions,
)

sealed class SecurityKeyException(cause: Throwable? = null) : Exception(cause) {
    class PinRequired(cause: Throwable? = null) : SecurityKeyException(cause)
    class PinInvalid(val retries: Int?, cause: Throwable? = null) : SecurityKeyException(cause)
    class PinBlocked(cause: Throwable? = null) : SecurityKeyException(cause)
    class PinAuthBlocked(cause: Throwable? = null) : SecurityKeyException(cause)
    class PinNotSet(cause: Throwable? = null) : SecurityKeyException(cause)
    class UvInvalid(cause: Throwable? = null) : SecurityKeyException(cause)
    class NotRegistered(cause: Throwable? = null) : SecurityKeyException(cause)
    class Unsupported(cause: Throwable? = null) : SecurityKeyException(cause)
    class Timeout(cause: Throwable? = null) : SecurityKeyException(cause)
    class Removed(cause: Throwable? = null) : SecurityKeyException(cause)
    class Failed(cause: Throwable? = null) : SecurityKeyException(cause)
}

private val security_key_b64url = Base64.getUrlEncoder().withoutPadding()

fun security_key_b64url_bytes(value: String): ByteArray =
    Base64.getUrlDecoder().decode(value.trim().trimEnd('=').replace('+', '-').replace('/', '_'))

private fun json_string(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char < ' ' -> append(String.format(Locale.ROOT, "\\u%04x", char.code))
            else -> append(char)
        }
    }
    append('"')
}

fun security_key_client_data_json(challenge: String, origin: String): ByteArray {
    val challenge_b64 = security_key_b64url.encodeToString(security_key_b64url_bytes(challenge))
    return buildString {
        append("{\"type\":\"webauthn.get\",\"challenge\":")
        append(json_string(challenge_b64))
        append(",\"origin\":")
        append(json_string(origin))
        append(",\"crossOrigin\":false}")
    }.toByteArray(Charsets.UTF_8)
}

fun security_key_uv_required(options: WebAuthnAssertionOptions): Boolean =
    options.userVerification.trim().equals(UserVerificationRequirement.REQUIRED, ignoreCase = true)

suspend fun run_security_key_flow(
    prepare: suspend () -> WebAuthnAssertionOptions,
    origin: String,
    next_target: suspend (tap_again: Boolean) -> SecurityKeyTarget,
    request_pin: suspend (retries: Int?, wrong: Boolean) -> CharArray?,
    on_working: () -> Unit,
    now_ms: () -> Long,
    run_on_key: suspend ((CommandState) -> String) -> String,
): SecurityKeyAssertion {
    var options = prepare()
    var options_at = now_ms()
    var pin: CharArray? = null
    var tap_again = false
    var retry_target: SecurityKeyTarget? = null
    try {
        while (true) {
            val target = retry_target ?: next_target(tap_again)
            retry_target = null
            on_working()
            if (now_ms() - options_at >= security_key_options_max_age_ms) {
                options = prepare()
                options_at = now_ms()
            }
            val current = options
            val attempt_pin = pin?.copyOf()
            try {
                val response_json = run_on_key { state ->
                    val connection = try {
                        target.open()
                    } catch (failure: Throwable) {
                        throw map_security_key_failure(failure, uv_required = false, pin_supplied = false)
                    }
                    assert_with_security_key(connection, SecurityKeyRequest(current, origin), attempt_pin, state)
                }
                return SecurityKeyAssertion(response_json, current)
            } catch (failure: SecurityKeyException.PinRequired) {
                pin?.fill(0.toChar())
                pin = null
                pin = request_pin(null, false) ?: throw PasskeyCancelledException()
                retry_target = target
                tap_again = true
            } catch (failure: SecurityKeyException.PinInvalid) {
                pin?.fill(0.toChar())
                pin = null
                pin = request_pin(failure.retries, true) ?: throw PasskeyCancelledException()
                retry_target = target
                tap_again = true
            } catch (failure: SecurityKeyException.Removed) {
                tap_again = true
            } finally {
                attempt_pin?.fill(0.toChar())
            }
        }
    } finally {
        pin?.fill(0.toChar())
    }
}

fun assert_with_security_key(
    connection: YubiKeyConnection,
    request: SecurityKeyRequest,
    pin: CharArray?,
    state: CommandState? = null,
): String {
    val options = request.options
    val uv_required = security_key_uv_required(options)
    val allow = options.allowCredentials.mapNotNull { descriptor ->
        runCatching { security_key_b64url_bytes(descriptor.id) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY, it) }
    }
    if (allow.isEmpty()) {
        runCatching { connection.close() }
        throw SecurityKeyException.NotRegistered()
    }
    val challenge_bytes = runCatching { security_key_b64url_bytes(options.challenge) }.getOrNull()
    if (challenge_bytes == null || challenge_bytes.isEmpty()) {
        runCatching { connection.close() }
        throw SecurityKeyException.Failed()
    }
    val client_data = security_key_client_data_json(options.challenge, request.origin)
    val client = try {
        WebAuthnClient.create(connection, emptyList(), null)
    } catch (failure: Throwable) {
        runCatching { connection.close() }
        throw map_security_key_failure(failure, uv_required, pin != null)
    }
    val credential = try {
        request_security_key_assertion(client, client_data, challenge_bytes, options, allow, pin, uv_required, state)
    } catch (failure: Throwable) {
        throw map_security_key_failure(failure, uv_required, pin != null)
    } finally {
        runCatching { client.close() }
    }
    val response = credential.response as? AuthenticatorAssertionResponse
        ?: throw SecurityKeyException.Failed()
    return security_key_response_json(credential.rawId, client_data, response)
}

private fun request_security_key_assertion(
    client: WebAuthnClient,
    client_data: ByteArray,
    challenge_bytes: ByteArray,
    options: WebAuthnAssertionOptions,
    allow: List<PublicKeyCredentialDescriptor>,
    pin: CharArray?,
    uv_required: Boolean,
    state: CommandState?,
): PublicKeyCredential {
    fun attempt(user_verification: String): PublicKeyCredential {
        val request_options = PublicKeyCredentialRequestOptions(
            challenge_bytes,
            options.timeout,
            options.rpId,
            allow,
            user_verification,
            null,
        )
        return try {
            client.getAssertion(
                ClientDataProvider.fromClientDataJson(client_data),
                request_options,
                options.rpId,
                pin,
                state,
            )
        } catch (multiple: MultipleAssertionsAvailable) {
            multiple.select(0)
        }
    }
    val requested = if (uv_required) {
        UserVerificationRequirement.REQUIRED
    } else {
        options.userVerification.trim().lowercase(Locale.ROOT).ifBlank { UserVerificationRequirement.PREFERRED }
    }
    return try {
        attempt(requested)
    } catch (failure: ClientError) {
        val verification_unavailable = !uv_required &&
            requested != UserVerificationRequirement.DISCOURAGED &&
            failure !is PinRequiredClientError &&
            failure !is AuthInvalidClientError &&
            failure.errorCode == ClientError.Code.CONFIGURATION_UNSUPPORTED &&
            failure.cause !is CtapException
        if (!verification_unavailable) throw failure
        attempt(UserVerificationRequirement.DISCOURAGED)
    }
}

fun security_key_response_json(
    raw_id: ByteArray,
    client_data: ByteArray,
    response: AuthenticatorAssertionResponse,
): String {
    val credential_id = security_key_b64url.encodeToString(raw_id)
    val response_json = JSONObject()
        .put("clientDataJSON", security_key_b64url.encodeToString(client_data))
        .put("authenticatorData", security_key_b64url.encodeToString(response.authenticatorData))
        .put("signature", security_key_b64url.encodeToString(response.signature))
    response.userHandle?.takeIf { it.isNotEmpty() }?.let {
        response_json.put("userHandle", security_key_b64url.encodeToString(it))
    }
    return JSONObject()
        .put("id", credential_id)
        .put("rawId", credential_id)
        .put("type", PublicKeyCredentialType.PUBLIC_KEY)
        .put("authenticatorAttachment", "cross-platform")
        .put("response", response_json)
        .put("clientExtensionResults", JSONObject())
        .toString()
}

fun map_security_key_failure(
    failure: Throwable,
    uv_required: Boolean,
    pin_supplied: Boolean,
): Throwable {
    if (failure is CancellationException || failure is SecurityKeyException) return failure
    return when (failure) {
        is PinRequiredClientError -> SecurityKeyException.PinRequired(failure)
        is AuthInvalidClientError -> when (failure.authType) {
            AuthInvalidClientError.AuthType.PIN -> when {
                failure.retries == 0 -> SecurityKeyException.PinBlocked(failure)
                failure.retries < 0 -> SecurityKeyException.PinInvalid(null, failure)
                else -> SecurityKeyException.PinInvalid(failure.retries, failure)
            }
            else -> SecurityKeyException.UvInvalid(failure)
        }
        is ClientError -> {
            val ctap = failure.cause as? CtapException
            ctap?.let { map_ctap_failure(it.ctapError, failure, pin_supplied) } ?: when (failure.errorCode) {
                ClientError.Code.DEVICE_INELIGIBLE -> SecurityKeyException.NotRegistered(failure)
                ClientError.Code.TIMEOUT -> SecurityKeyException.Timeout(failure)
                ClientError.Code.CONFIGURATION_UNSUPPORTED ->
                    if (uv_required) SecurityKeyException.PinNotSet(failure) else SecurityKeyException.Failed(failure)
                else -> SecurityKeyException.Failed(failure)
            }
        }
        is CtapException -> map_ctap_failure(failure.ctapError, failure, pin_supplied)
            ?: SecurityKeyException.Failed(failure)
        is ApplicationNotAvailableException -> SecurityKeyException.Unsupported(failure)
        is IOException -> SecurityKeyException.Removed(failure)
        else -> SecurityKeyException.Failed(failure)
    }
}

private fun map_ctap_failure(code: Byte, failure: Throwable, pin_supplied: Boolean): SecurityKeyException? =
    when (code) {
        CtapException.ERR_PIN_BLOCKED -> SecurityKeyException.PinBlocked(failure)
        CtapException.ERR_PIN_AUTH_BLOCKED -> SecurityKeyException.PinAuthBlocked(failure)
        CtapException.ERR_PIN_NOT_SET -> SecurityKeyException.PinNotSet(failure)
        CtapException.ERR_PUAT_REQUIRED, CtapException.ERR_UV_BLOCKED ->
            if (pin_supplied) SecurityKeyException.UvInvalid(failure) else SecurityKeyException.PinRequired(failure)
        CtapException.ERR_NO_CREDENTIALS -> SecurityKeyException.NotRegistered(failure)
        CtapException.ERR_KEEPALIVE_CANCEL -> SecurityKeyException.Removed(failure)
        CtapException.ERR_TIMEOUT,
        CtapException.ERR_ACTION_TIMEOUT,
        CtapException.ERR_USER_ACTION_TIMEOUT -> SecurityKeyException.Timeout(failure)
        CtapException.ERR_OPERATION_DENIED -> SecurityKeyException.Failed(failure)
        else -> null
    }
