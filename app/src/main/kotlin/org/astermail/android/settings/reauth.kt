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

package org.astermail.android.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.api.security.ReauthProofRequest
import org.astermail.android.api.security.StepUpKeyAssertion
import org.astermail.android.auth.assertion_request_json
import org.astermail.android.auth.PasskeyCancelledException
import org.astermail.android.auth.PasskeyFailedException
import org.astermail.android.auth.PasskeyUnavailableException
import org.astermail.android.auth.parse_assertion_response

enum class ReauthDemand {
    password,
    totp_code,
    security_key,
}

data class ReauthPrompt(
    val needs_code: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

fun reauth_demand_of(t: Throwable): ReauthDemand? {
    val code = when (t) {
        is ApiError.StepUpRequired -> return ReauthDemand.password
        is ApiError.ForbiddenError -> t.code
        is ApiError.ValidationError -> t.code
        is ApiError.Conflict -> t.code
        else -> null
    }
    return when (code) {
        "STEP_UP_REQUIRED" -> ReauthDemand.password
        "TOTP_REQUIRED" -> ReauthDemand.totp_code
        "SECURITY_KEY_REQUIRED" -> ReauthDemand.security_key
        else -> null
    }
}

fun step_up_assertion_from(response_json: String, options: WebAuthnAssertionOptions): StepUpKeyAssertion {
    val parsed = parse_assertion_response(response_json)
    return StepUpKeyAssertion(
        raw_id = parsed.raw_id,
        response = parsed.response,
        challenge_token = options.challenge_token,
    )
}

suspend fun collect_step_up_assertion(
    load_options: suspend () -> WebAuthnAssertionOptions,
    get_credential: suspend (String) -> String,
): StepUpKeyAssertion {
    val options = load_options()
    return step_up_assertion_from(get_credential(assertion_request_json(options)), options)
}

suspend fun <T> run_with_reauth(
    password_hash: String,
    totp_code: String?,
    key_assertion: suspend () -> StepUpKeyAssertion,
    call: suspend (ReauthProofRequest) -> T,
): T {
    val proof = ReauthProofRequest(
        password_hash = password_hash,
        totp_code = totp_code?.trim()?.takeIf { it.isNotEmpty() },
    )
    return try {
        call(proof)
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        if (proof.totp_code != null || reauth_demand_of(t) != ReauthDemand.security_key) throw t
        call(proof.copy(hardware_key_assertion = key_assertion()))
    }
}

sealed interface Reauthorized<out T> {
    data class Done<T>(val value: T) : Reauthorized<T>
    data class Retry(val prompt: ReauthPrompt) : Reauthorized<Nothing>
}

fun reauth_retry_prompt(context: Context, prompt: ReauthPrompt, t: Throwable): ReauthPrompt = when {
    reauth_demand_of(t) == ReauthDemand.totp_code -> prompt.copy(
        busy = false,
        needs_code = true,
        error = context.getString(R.string.error_2fa_code_required),
    )
    t is PasskeyCancelledException || t is PasskeyUnavailableException || t is PasskeyFailedException ->
        prompt.copy(busy = false, error = key_check_error_text(context, t))
    else -> prompt.copy(
        busy = false,
        error = org.astermail.android.localized_api_error(
            context,
            t,
            context.getString(R.string.something_went_wrong),
        ),
    )
}

suspend fun <T> reauthorize(
    context: Context,
    prompt: ReauthPrompt,
    password: String,
    totp_code: String?,
    derive_password_hash: suspend (String) -> String?,
    load_key_options: suspend () -> WebAuthnAssertionOptions,
    get_credential: suspend (String) -> String,
    call: suspend (ReauthProofRequest) -> T,
): Reauthorized<T> {
    if (password.isBlank()) {
        return Reauthorized.Retry(
            prompt.copy(busy = false, error = context.getString(R.string.enter_account_password)),
        )
    }
    return try {
        val password_hash = withContext(Dispatchers.Default) { derive_password_hash(password) }
            ?: return Reauthorized.Retry(
                prompt.copy(
                    busy = false,
                    error = context.getString(R.string.session_unavailable_sign_in_again),
                ),
            )
        Reauthorized.Done(
            run_with_reauth(
                password_hash = password_hash,
                totp_code = totp_code,
                key_assertion = { collect_step_up_assertion(load_key_options, get_credential) },
                call = call,
            ),
        )
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        Reauthorized.Retry(reauth_retry_prompt(context, prompt, t))
    }
}
