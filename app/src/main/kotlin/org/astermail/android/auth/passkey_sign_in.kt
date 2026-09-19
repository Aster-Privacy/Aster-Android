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

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import org.astermail.android.api.auth.WebAuthnAssertionData
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.api.auth.WebAuthnAssertionVerifyRequest
import org.json.JSONArray
import org.json.JSONObject

class PasskeyCancelledException : Exception()

class PasskeyUnavailableException(cause: Throwable? = null) : Exception(cause)

class PasskeyFailedException(cause: Throwable? = null) : Exception(cause)

fun assertion_request_json(options: WebAuthnAssertionOptions): String {
    val allow = JSONArray()
    options.allowCredentials.forEach { credential ->
        allow.put(
            JSONObject()
                .put("type", credential.type)
                .put("id", credential.id),
        )
    }
    return JSONObject()
        .put("challenge", options.challenge)
        .put("timeout", options.timeout)
        .put("rpId", options.rpId)
        .put("allowCredentials", allow)
        .put("userVerification", options.userVerification)
        .toString()
}

fun assertion_verify_request(
    response_json: String,
    options: WebAuthnAssertionOptions,
    challenge: TotpChallenge,
    trust_device: Boolean,
    device_label: String?,
): WebAuthnAssertionVerifyRequest {
    val parsed = JSONObject(response_json)
    val inner = parsed.optJSONObject("response")
        ?: throw PasskeyFailedException()
    val raw_id = parsed.optString("rawId").ifBlank { parsed.optString("id") }
    val credential_id = parsed.optString("id").ifBlank { raw_id }
    val authenticator_data = inner.optString("authenticatorData")
    val client_data_json = inner.optString("clientDataJSON")
    val signature = inner.optString("signature")
    if (raw_id.isBlank() || authenticator_data.isBlank() || client_data_json.isBlank() || signature.isBlank()) {
        throw PasskeyFailedException()
    }
    return WebAuthnAssertionVerifyRequest(
        id = credential_id,
        raw_id = raw_id,
        response = WebAuthnAssertionData(
            authenticator_data = authenticator_data,
            client_data_json = client_data_json,
            signature = signature,
        ),
        challenge_token = options.challenge_token,
        pending_login_token = challenge.pending_login_token,
        trust_device = trust_device,
        device_label = device_label,
        remember_me = challenge.remember_me,
    )
}

suspend fun request_passkey_assertion(
    context: Context,
    options: WebAuthnAssertionOptions,
): String {
    val request = GetCredentialRequest(
        listOf(GetPublicKeyCredentialOption(assertion_request_json(options))),
    )
    val credential = try {
        CredentialManager.create(context).getCredential(context, request).credential
    } catch (cancelled: GetCredentialCancellationException) {
        throw PasskeyCancelledException()
    } catch (missing: NoCredentialException) {
        throw PasskeyUnavailableException(missing)
    } catch (failure: GetCredentialException) {
        throw PasskeyFailedException(failure)
    } catch (failure: NoClassDefFoundError) {
        throw PasskeyUnavailableException(failure)
    }
    val public_key = credential as? PublicKeyCredential
        ?: throw PasskeyFailedException()
    return public_key.authenticationResponseJson
}
