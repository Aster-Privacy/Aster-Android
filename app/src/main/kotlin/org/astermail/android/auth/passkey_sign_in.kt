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
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.astermail.android.api.auth.PasskeyLoginAssertionData
import org.astermail.android.api.auth.PasskeyLoginOptions
import org.astermail.android.api.auth.PasskeyLoginVerifyRequest
import org.astermail.android.api.auth.WebAuthnAssertionData
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.api.auth.WebAuthnAssertionVerifyRequest
import org.astermail.android.api.security.PasskeyAttestationData
import org.astermail.android.api.security.PasskeyRegistrationCompleteRequest
import org.astermail.android.api.security.PasskeyRegistrationOptions
import org.astermail.android.api.security.PasskeyRegistrationParam
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.hkdf_sha256
import org.json.JSONArray
import org.json.JSONObject

class PasskeyCancelledException : Exception()

class PasskeyUnavailableException(cause: Throwable? = null) : Exception(cause)

class PasskeyFailedException(cause: Throwable? = null) : Exception(cause)

class PasskeyAlreadyRegisteredException(cause: Throwable? = null) : Exception(cause)

class PasskeyVaultNeedsPasswordException : Exception()

private const val prf_eval_label = "aster-vault-prf-eval-v1"
private const val prf_key_salt = "aster-vault-passphrase-key-v1"
private const val prf_nonce_length = 12

private val b64url_encoder = Base64.getUrlEncoder().withoutPadding()
private val b64url_decoder = Base64.getUrlDecoder()
private val b64_encoder = Base64.getEncoder()
private val b64_decoder = Base64.getDecoder()

private fun b64url_to_bytes(value: String): ByteArray =
    b64url_decoder.decode(value.trim().trimEnd('=').replace('+', '-').replace('/', '_'))

private fun b64url_to_standard(value: String): String = b64_encoder.encodeToString(b64url_to_bytes(value))

fun prf_eval_input(): String =
    b64url_encoder.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(prf_eval_label.toByteArray(Charsets.UTF_8)),
    )

private fun prf_extension(): JSONObject =
    JSONObject().put(
        "prf",
        JSONObject().put("eval", JSONObject().put("first", prf_eval_input())),
    )

fun prf_output_from(response_json: String): ByteArray? {
    val first = runCatching {
        JSONObject(response_json)
            .optJSONObject("clientExtensionResults")
            ?.optJSONObject("prf")
            ?.optJSONObject("results")
            ?.optString("first")
    }.getOrNull()
    if (first.isNullOrBlank()) return null
    return runCatching { b64url_to_bytes(first) }.getOrNull()?.takeIf { it.isNotEmpty() }
}

fun prf_enabled_from(response_json: String): Boolean =
    runCatching {
        JSONObject(response_json)
            .optJSONObject("clientExtensionResults")
            ?.optJSONObject("prf")
            ?.optBoolean("enabled", false) == true
    }.getOrDefault(false)

private fun prf_key(prf_output: ByteArray): ByteArray =
    hkdf_sha256(prf_output, prf_key_salt.toByteArray(Charsets.UTF_8), ByteArray(0), 32)

fun encrypt_passphrase_with_prf(prf_output: ByteArray, passphrase: ByteArray): Pair<String, String> {
    val key = prf_key(prf_output)
    val nonce = ByteArray(prf_nonce_length).also { SecureRandom().nextBytes(it) }
    try {
        val encrypted = AesGcm.encrypt(key, nonce, passphrase)
        return b64_encoder.encodeToString(encrypted) to b64_encoder.encodeToString(nonce)
    } finally {
        key.fill(0)
    }
}

fun decrypt_passphrase_with_prf(prf_output: ByteArray, encrypted_b64: String, nonce_b64: String): ByteArray? {
    val key = prf_key(prf_output)
    return try {
        AesGcm.decrypt(key, b64_decoder.decode(nonce_b64), b64_decoder.decode(encrypted_b64))
    } catch (_: Exception) {
        null
    } finally {
        key.fill(0)
    }
}

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

fun passkey_login_request_json(options: PasskeyLoginOptions): String =
    JSONObject()
        .put("challenge", options.challenge)
        .put("timeout", options.timeout)
        .put("rpId", options.rpId)
        .put("allowCredentials", JSONArray())
        .put("userVerification", options.userVerification)
        .put("extensions", prf_extension())
        .toString()

fun passkey_login_verify_request(
    response_json: String,
    options: PasskeyLoginOptions,
    device_label: String?,
): PasskeyLoginVerifyRequest {
    val parsed = JSONObject(response_json)
    val inner = parsed.optJSONObject("response")
        ?: throw PasskeyFailedException()
    val raw_id = parsed.optString("rawId").ifBlank { parsed.optString("id") }
    val credential_id = parsed.optString("id").ifBlank { raw_id }
    val authenticator_data = inner.optString("authenticatorData")
    val client_data_json = inner.optString("clientDataJSON")
    val signature = inner.optString("signature")
    val user_handle = inner.optString("userHandle").takeIf { it.isNotBlank() && it != "null" }
    if (raw_id.isBlank() || authenticator_data.isBlank() || client_data_json.isBlank() || signature.isBlank()) {
        throw PasskeyFailedException()
    }
    return PasskeyLoginVerifyRequest(
        id = credential_id,
        raw_id = raw_id,
        response = PasskeyLoginAssertionData(
            authenticator_data = authenticator_data,
            client_data_json = client_data_json,
            signature = signature,
            user_handle = user_handle,
        ),
        challenge_token = options.challenge_token,
        remember_me = true,
        device_label = device_label,
    )
}

fun prf_assertion_request_json(rp_id: String, credential_id: String): String =
    JSONObject()
        .put("challenge", b64url_encoder.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }))
        .put("timeout", 60000)
        .put("rpId", rp_id)
        .put(
            "allowCredentials",
            JSONArray().put(JSONObject().put("type", "public-key").put("id", credential_id)),
        )
        .put("userVerification", "required")
        .put("extensions", prf_extension())
        .toString()

fun registration_request_json(options: PasskeyRegistrationOptions): String {
    val params = JSONArray()
    options.pubKeyCredParams
        .ifEmpty { listOf(PasskeyRegistrationParam(alg = -7), PasskeyRegistrationParam(alg = -257)) }
        .forEach { param -> params.put(JSONObject().put("type", param.type).put("alg", param.alg)) }
    val user_handle = b64url_encoder.encodeToString(options.user.id.toByteArray(Charsets.US_ASCII))
    return JSONObject()
        .put("challenge", options.challenge)
        .put("rp", JSONObject().put("name", options.rp.name.ifBlank { "AsterMail" }).put("id", options.rp.id))
        .put(
            "user",
            JSONObject()
                .put("id", user_handle)
                .put("name", options.user.name)
                .put("displayName", options.user.displayName.ifBlank { options.user.name }),
        )
        .put("pubKeyCredParams", params)
        .put("timeout", options.timeout)
        .put("attestation", "none")
        .put("excludeCredentials", exclude_credentials_json(options))
        .put(
            "authenticatorSelection",
            JSONObject()
                .put("authenticatorAttachment", "platform")
                .put("residentKey", "required")
                .put("requireResidentKey", true)
                .put("userVerification", "required"),
        )
        .put("extensions", prf_extension())
        .toString()
}

fun exclude_credentials_json(options: PasskeyRegistrationOptions): JSONArray {
    val out = JSONArray()
    options.excludeCredentials
        .filter { it.id.isNotBlank() }
        .forEach { out.put(JSONObject().put("type", it.type).put("id", it.id)) }
    return out
}

class PasskeyRegistration(
    val request: PasskeyRegistrationCompleteRequest,
    val credential_id: String,
    val prf_output: ByteArray?,
    val prf_enabled: Boolean,
)

fun registration_complete_request(
    response_json: String,
    options: PasskeyRegistrationOptions,
    name: String,
): PasskeyRegistration {
    val parsed = JSONObject(response_json)
    val inner = parsed.optJSONObject("response")
        ?: throw PasskeyFailedException()
    val raw_id = parsed.optString("rawId").ifBlank { parsed.optString("id") }
    val attestation_object = inner.optString("attestationObject")
    val client_data_json = inner.optString("clientDataJSON")
    if (raw_id.isBlank() || attestation_object.isBlank() || client_data_json.isBlank()) {
        throw PasskeyFailedException()
    }
    val request = try {
        PasskeyRegistrationCompleteRequest(
            id = raw_id,
            raw_id = raw_id,
            response = PasskeyAttestationData(
                attestation_object = b64url_to_standard(attestation_object),
                client_data_json = b64url_to_standard(client_data_json),
            ),
            name_encrypted = name,
            challenge_token = options.challenge_token,
            is_passkey = true,
        )
    } catch (failure: IllegalArgumentException) {
        throw PasskeyFailedException(failure)
    }
    return PasskeyRegistration(
        request = request,
        credential_id = raw_id,
        prf_output = prf_output_from(response_json),
        prf_enabled = prf_enabled_from(response_json),
    )
}

fun map_get_credential_failure(failure: Throwable): Exception = when (failure) {
    is GetCredentialCancellationException -> PasskeyCancelledException()
    is NoCredentialException -> PasskeyUnavailableException(failure)
    is GetCredentialProviderConfigurationException -> PasskeyUnavailableException(failure)
    is GetCredentialUnsupportedException -> PasskeyUnavailableException(failure)
    is GetPublicKeyCredentialDomException -> when (failure.domError) {
        is NotAllowedError -> PasskeyCancelledException()
        else -> PasskeyFailedException(failure)
    }
    is NoClassDefFoundError -> PasskeyUnavailableException(failure)
    else -> PasskeyFailedException(failure)
}

suspend fun request_passkey_json(context: Context, request_json: String): String {
    val request = GetCredentialRequest(
        listOf(GetPublicKeyCredentialOption(request_json)),
    )
    val credential = try {
        CredentialManager.create(context).getCredential(context, request).credential
    } catch (failure: GetCredentialException) {
        throw map_get_credential_failure(failure)
    } catch (failure: NoClassDefFoundError) {
        throw map_get_credential_failure(failure)
    }
    val public_key = credential as? PublicKeyCredential
        ?: throw PasskeyFailedException()
    return public_key.authenticationResponseJson
}

fun map_create_credential_failure(failure: Throwable): Exception = when (failure) {
    is CreateCredentialCancellationException -> PasskeyCancelledException()
    is CreateCredentialNoCreateOptionException -> PasskeyUnavailableException(failure)
    is CreateCredentialProviderConfigurationException -> PasskeyUnavailableException(failure)
    is CreateCredentialUnsupportedException -> PasskeyUnavailableException(failure)
    is CreatePublicKeyCredentialDomException -> when (failure.domError) {
        is InvalidStateError -> PasskeyAlreadyRegisteredException(failure)
        is NotAllowedError -> PasskeyCancelledException()
        else -> PasskeyFailedException(failure)
    }
    is NoClassDefFoundError -> PasskeyUnavailableException(failure)
    else -> PasskeyFailedException(failure)
}

suspend fun create_passkey_json(context: Context, request_json: String): String {
    val response = try {
        CredentialManager.create(context).createCredential(
            context,
            CreatePublicKeyCredentialRequest(request_json),
        )
    } catch (failure: CreateCredentialException) {
        throw map_create_credential_failure(failure)
    } catch (failure: NoClassDefFoundError) {
        throw map_create_credential_failure(failure)
    }
    val public_key = response as? CreatePublicKeyCredentialResponse
        ?: throw PasskeyFailedException()
    return public_key.registrationResponseJson
}

suspend fun request_passkey_assertion(
    context: Context,
    options: WebAuthnAssertionOptions,
): String = request_passkey_json(context, assertion_request_json(options))

const val PASSKEY_CHALLENGE_EXPIRED_MESSAGE = "Challenge expired"

fun is_passkey_challenge_expired(cause: Throwable): Boolean =
    cause is org.astermail.android.api.ApiError.ValidationError &&
        cause.messages.any { it.equals(PASSKEY_CHALLENGE_EXPIRED_MESSAGE, ignoreCase = true) }
