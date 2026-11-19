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
import org.astermail.android.R
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.api.recovery.VerifyCodesStepUpRequest
import org.astermail.android.api.recovery.VerifyCodesStepUpResponse
import org.astermail.android.api.recovery_email.RecoveryEmailApi
import org.astermail.android.api.recovery_email.RecoveryEmailSuccessResponse
import org.astermail.android.api.recovery_email.RemoveRecoveryEmailRequest
import org.astermail.android.api.recovery_email.SaveRecoveryEmailRequest
import org.astermail.android.api.security.StepUpKeyAssertion
import org.astermail.android.auth.PasskeyCancelledException
import org.astermail.android.auth.PasskeyFailedException
import org.astermail.android.auth.PasskeyUnavailableException

suspend fun verify_codes_with_reauth(
    recovery_api: RecoveryApi,
    password_hash: String,
    totp_code: String?,
    key_assertion: suspend () -> StepUpKeyAssertion,
): VerifyCodesStepUpResponse = run_with_reauth(password_hash, totp_code, key_assertion) { proof ->
    recovery_api.verify_step_up(
        VerifyCodesStepUpRequest(
            password_hash = proof.password_hash,
            totp_code = proof.totp_code,
            hardware_key_assertion = proof.hardware_key_assertion,
        ),
    )
}

suspend fun save_recovery_email_with_reauth(
    recovery_email_api: RecoveryEmailApi,
    request: SaveRecoveryEmailRequest,
    key_assertion: suspend () -> StepUpKeyAssertion,
): RecoveryEmailSuccessResponse {
    val password_hash = request.password_hash ?: return recovery_email_api.save(request)
    return run_with_reauth(password_hash, request.totp_code, key_assertion) { proof ->
        recovery_email_api.save(
            request.copy(
                totp_code = proof.totp_code,
                hardware_key_assertion = proof.hardware_key_assertion,
            ),
        )
    }
}

suspend fun remove_recovery_email_with_reauth(
    recovery_email_api: RecoveryEmailApi,
    password_hash: String,
    totp_code: String?,
    key_assertion: suspend () -> StepUpKeyAssertion,
): RecoveryEmailSuccessResponse = run_with_reauth(password_hash, totp_code, key_assertion) { proof ->
    recovery_email_api.remove(
        RemoveRecoveryEmailRequest(
            password_hash = proof.password_hash,
            totp_code = proof.totp_code,
            hardware_key_assertion = proof.hardware_key_assertion,
        ),
    )
}

fun is_key_check_cancelled(t: Throwable): Boolean = t is PasskeyCancelledException

fun key_check_error_text(context: Context, t: Throwable): String? = when (t) {
    is PasskeyUnavailableException, is PasskeyFailedException ->
        context.getString(R.string.reauth_key_check_failed)
    else -> null
}
