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

package org.astermail.android.guard

import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.StepUpProof
import org.astermail.android.api.guard.WebAuthnAssertion
import org.astermail.android.auth.PasskeyCancelledException
import org.astermail.android.auth.PasskeyFailedException
import org.astermail.android.auth.PasskeyUnavailableException
import org.astermail.android.auth.parse_assertion_response
import org.json.JSONArray
import org.json.JSONObject

fun interface guard_passkey_sheet {
    suspend fun assertion_json(request_json: String): String
}

enum class guard_passkey_failure {
    canceled,
    interrupted,
    no_passkey,
    unavailable,
    wrong_account,
    account_changed,
    expired,
    failed,
}

class GuardPasskeyException(val failure: guard_passkey_failure, cause: Throwable? = null) : Exception(cause)

private const val GUARD_PASSKEY_USER_VERIFICATION = "required"
private const val GUARD_PASSKEY_HINT = "client-device"
private const val UNKNOWN_CREDENTIAL_MESSAGE = "unknown credential"
private const val CHALLENGE_USER_MISMATCH_MESSAGE = "challenge user mismatch"
private const val CHALLENGE_USED_MESSAGE = "challenge already used or expired"
private const val CHALLENGE_EXPIRED_MESSAGE = "challenge expired"

fun guard_passkey_request_json(options: WebAuthnAssertionOptions): String =
    JSONObject()
        .put("challenge", options.challenge)
        .put("timeout", options.timeout)
        .put("rpId", options.rpId)
        .put("allowCredentials", JSONArray())
        .put("userVerification", GUARD_PASSKEY_USER_VERIFICATION)
        .put("hints", JSONArray().put(GUARD_PASSKEY_HINT))
        .toString()

suspend fun guard_passkey_proof(
    api: GuardApi,
    expected_user: UUID,
    current_user: () -> UUID?,
    sheet: guard_passkey_sheet,
): StepUpProof.Passkey {
    if (current_user() != expected_user) throw GuardPasskeyException(guard_passkey_failure.account_changed)
    val options = api.step_up_passkey_options()
    val response_json = try {
        sheet.assertion_json(guard_passkey_request_json(options))
    } catch (t: CancellationException) {
        throw t
    } catch (t: Throwable) {
        throw GuardPasskeyException(guard_passkey_sheet_failure(t), t)
    }
    if (current_user() != expected_user) throw GuardPasskeyException(guard_passkey_failure.account_changed)
    val parsed = try {
        parse_assertion_response(response_json)
    } catch (t: Throwable) {
        throw GuardPasskeyException(guard_passkey_failure.failed, t)
    }
    return StepUpProof.Passkey(
        WebAuthnAssertion(raw_id = parsed.raw_id, response = parsed.response, challenge_token = options.challenge_token),
    )
}

fun guard_passkey_sheet_failure(t: Throwable): guard_passkey_failure = when (t) {
    is PasskeyCancelledException -> if (t.interrupted) guard_passkey_failure.interrupted else guard_passkey_failure.canceled
    is PasskeyUnavailableException -> if (t.no_provider) guard_passkey_failure.unavailable else guard_passkey_failure.no_passkey
    is PasskeyFailedException -> guard_passkey_failure.failed
    else -> guard_passkey_failure.failed
}

fun guard_passkey_failure_for(t: Throwable): guard_passkey_failure? {
    if (t is GuardPasskeyException) return t.failure
    if (t !is ApiError.ValidationError) return null
    val messages = t.messages.map { it.trim().lowercase() }
    return when {
        messages.any { it.contains(UNKNOWN_CREDENTIAL_MESSAGE) } -> guard_passkey_failure.wrong_account
        messages.any { it.contains(CHALLENGE_USER_MISMATCH_MESSAGE) } -> guard_passkey_failure.account_changed
        messages.any { it.contains(CHALLENGE_USED_MESSAGE) || it.contains(CHALLENGE_EXPIRED_MESSAGE) } ->
            guard_passkey_failure.expired
        else -> null
    }
}
