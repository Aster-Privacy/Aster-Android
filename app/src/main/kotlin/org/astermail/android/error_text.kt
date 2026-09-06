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

package org.astermail.android

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.astermail.android.api.ApiError
import org.astermail.android.api.server_supplied_detail

const val FAMILY_2FA_REQUIRED_CODE = "FAMILY_2FA_REQUIRED"

fun localized_server_code(context: Context, t: Throwable): String? {
    val forbidden = t as? ApiError.ForbiddenError ?: return null
    return when (forbidden.code) {
        FAMILY_2FA_REQUIRED_CODE -> context.getString(R.string.error_family_2fa_required)
        else -> null
    }
}

private val validation_code_strings = mapOf(
    "INVALID_TWO_FACTOR_CODE" to R.string.error_invalid_2fa_code,
    "TWO_FACTOR_CODE_REQUIRED" to R.string.error_2fa_code_required,
    "ACCOUNT_PASSWORD_REQUIRED" to R.string.error_account_password_required,
    "INVALID_BACKUP_CODE" to R.string.error_invalid_backup_code,
    "INVALID_RECOVERY_CODE" to R.string.error_invalid_code,
    "INVALID_RECOVERY_PHRASE" to R.string.error_invalid_recovery_phrase,
    "INVALID_OR_EXPIRED_CODE" to R.string.error_sign_in_code_invalid,
    "FOLDER_PASSWORD_ALREADY_SET" to R.string.error_folder_password_already_set,
    "FOLDER_PASSWORD_NOT_SET" to R.string.error_folder_password_not_set,
    "IMAGE_TOO_LARGE" to R.string.error_image_too_large,
    "PAYLOAD_TOO_LARGE" to R.string.error_upload_too_large,
)

private fun send_refusal_message(
    context: Context,
    code: String?,
    details: Map<String, String>,
): String? {
    return when (code) {
        "TOO_MANY_RECIPIENTS" -> details["max_allowed"]?.toIntOrNull()?.let {
            context.getString(R.string.send_refusal_too_many_recipients, it)
        }
        "TOO_MANY_ATTACHMENTS" -> details["max_allowed"]?.toIntOrNull()?.let {
            context.getString(R.string.send_refusal_too_many_attachments, it)
        }
        "ATTACHMENTS_TOO_LARGE" -> details["max_bytes"]?.toLongOrNull()?.let {
            context.getString(
                R.string.send_refusal_attachments_too_large,
                android.text.format.Formatter.formatShortFileSize(context, it),
            )
        }
        "RECIPIENT_CONCENTRATION" -> context.getString(
            R.string.send_refusal_recipient_concentration,
            details["domain"]?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.send_refusal_that_provider),
        )
        "FORWARDING_ENCRYPTION_KEY_MISSING" -> context.getString(
            R.string.forwarding_failed_encryption,
            details["address"].orEmpty(),
        )
        else -> null
    }
}

private fun server_code_of(t: Throwable): String? = when (t) {
    is ApiError.ForbiddenError -> t.code
    is ApiError.Conflict -> t.code
    else -> null
}

fun server_code_string_res(code: String): Int? = when (code) {
    "ACCOUNT_SUSPENDED" -> R.string.error_account_suspended
    "ACCOUNT_LOCKED" -> R.string.error_account_locked
    "ACCOUNT_PROBATION" -> R.string.error_account_probation
    "VERIFICATION_REQUIRED" -> R.string.error_verification_required
    "CLIENT_UPGRADE_REQUIRED" -> R.string.error_client_upgrade_required
    "ALIAS_REENCRYPTION_INCOMPLETE" -> R.string.error_alias_reencryption_incomplete
    else -> null
}

private fun localized_server_code_string(context: Context, code: String): String? =
    server_code_string_res(code)?.let { context.getString(it) }

fun localized_api_error(context: Context, t: Throwable, fallback: String): String {
    if (t is CancellationException) throw t
    localized_server_code(context, t)?.let { return it }
    server_code_of(t)?.let { code ->
        localized_server_code_string(context, code)?.let { return it }
    }
    if (t is ApiError.ValidationError) {
        send_refusal_message(context, t.code, t.details)?.let { return it }
        validation_code_strings[t.code]?.let { return context.getString(it) }
    }
    if (t is ApiError.RateLimited) {
        send_refusal_message(context, t.code, t.details)?.let { return it }
    }
    if (t is ApiError.PlanLimitExceeded) return context.getString(R.string.error_plan_limit_reached)
    if (t is ApiError.StorageQuotaExceeded) return context.getString(R.string.error_storage_full)
    server_supplied_detail(t)?.let { return it }
    return when (t) {
        is ApiError.NetworkError -> context.getString(R.string.error_no_connection)
        is ApiError.UnauthorizedError -> context.getString(R.string.session_expired_sign_in)
        else -> fallback
    }
}
