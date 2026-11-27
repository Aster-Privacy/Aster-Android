//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.guard

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.Instant
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_ACTION_INVALID_CODE
import org.astermail.android.api.GUARD_ALREADY_ON_CODE
import org.astermail.android.api.GUARD_CODE_INVALID_CODE
import org.astermail.android.api.GUARD_DEVICE_REQUIRED_CODE
import org.astermail.android.api.GUARD_DEVICE_TYPE_INVALID_CODE
import org.astermail.android.api.GUARD_DEVICE_UNKNOWN_CODE
import org.astermail.android.api.GUARD_DISABLED_CODE
import org.astermail.android.api.GUARD_ENVELOPE_REQUIRED_CODE
import org.astermail.android.api.GUARD_ENVELOPE_UNSUPPORTED_CODE
import org.astermail.android.api.GUARD_INVALID_KEY_CODE
import org.astermail.android.api.GUARD_LAST_DEVICE_CODE
import org.astermail.android.api.GUARD_NOT_ALLOWED_CODE
import org.astermail.android.api.GUARD_PAUSED_CODE
import org.astermail.android.api.GUARD_REQUEST_CONFLICT_CODE
import org.astermail.android.api.GUARD_REQUEST_GONE_CODE
import org.astermail.android.api.GUARD_RESCUE_CODE_INVALID_CODE
import org.astermail.android.api.GUARD_TOO_MANY_REQUESTS_CODE
import org.astermail.android.api.GUARD_VETO_INVALID_CODE
import org.astermail.android.api.GUARD_WRONG_NUMBER_CODE
import org.astermail.android.api.api_error_code

const val GUARD_TIMED_RETRY_MIN_SECS = 60L

data class guard_error_message(
    @StringRes val res: Int,
    val at: Instant? = null,
    val attempts_left: Int? = null,
    val step_up_field: guard_step_up_field? = null,
)

enum class guard_step_up_field { password, code, key, all }

private const val STEP_UP_ATTEMPTS_WARNING_AT = 5

private fun step_up_details(t: Throwable): Map<String, String> = when (t) {
    is ApiError.InvalidCredentials -> t.details
    is ApiError.ValidationError -> t.details
    is ApiError.ForbiddenError -> t.details
    else -> emptyMap()
}

private fun attempts_left_of(details: Map<String, String>): Int? =
    details["attempts_remaining"]?.toIntOrNull()?.takeIf { it in 1..STEP_UP_ATTEMPTS_WARNING_AT }

fun guard_step_up_rejection(t: Throwable, now: Instant = Instant.now()): guard_error_message? {
    val details = step_up_details(t)
    val attempts = attempts_left_of(details)
    if (t is ApiError.InvalidCredentials) {
        if (details["failed_factor"] == "security_key") {
            return guard_error_message(R.string.guard_step_up_key_failed, attempts_left = attempts, step_up_field = guard_step_up_field.key)
        }
        val res = if (details["password_change_held"] == "true") {
            R.string.guard_step_up_password_wrong_held
        } else {
            R.string.guard_step_up_password_wrong
        }
        return guard_error_message(res, attempts_left = attempts, step_up_field = guard_step_up_field.password)
    }
    return when (api_error_code(t)) {
        STEP_UP_INVALID_CODE_CODE ->
            guard_error_message(R.string.guard_step_up_code_wrong, attempts_left = attempts, step_up_field = guard_step_up_field.code)
        STEP_UP_TOTP_REQUIRED_CODE ->
            guard_error_message(R.string.guard_step_up_code_required, step_up_field = guard_step_up_field.code)
        STEP_UP_ACCOUNT_LOCKED_CODE -> {
            val ends = details["retry_after_seconds"]?.toLongOrNull()?.let { guard_retry_ends_at(it, now) }
            ends?.let { guard_error_message(R.string.guard_step_up_locked, it, step_up_field = guard_step_up_field.all) }
                ?: guard_error_message(R.string.guard_step_up_locked_soon, step_up_field = guard_step_up_field.all)
        }
        else -> null
    }
}

private fun guard_step_up_reported(t: Throwable, now: Instant): guard_error_message? {
    val details = step_up_details(t)
    val reported = "failed_factor" in details ||
        (api_error_code(t) == STEP_UP_ACCOUNT_LOCKED_CODE && "retry_after_seconds" in details)
    return if (reported) guard_step_up_rejection(t, now) else null
}

private const val STEP_UP_INVALID_CODE_CODE = "INVALID_TWO_FACTOR_CODE"
private const val STEP_UP_TOTP_REQUIRED_CODE = "TOTP_REQUIRED"
private const val STEP_UP_ACCOUNT_LOCKED_CODE = "ACCOUNT_LOCKED"

@StringRes
fun guard_error_string_res(code: String?): Int? = when (code) {
    GUARD_INVALID_KEY_CODE -> R.string.guard_error_invalid_key
    GUARD_ACTION_INVALID_CODE -> R.string.guard_error_action_invalid
    GUARD_DEVICE_REQUIRED_CODE -> R.string.guard_error_device_required
    GUARD_NOT_ALLOWED_CODE -> R.string.guard_error_not_allowed
    GUARD_REQUEST_CONFLICT_CODE -> R.string.guard_error_request_conflict
    GUARD_LAST_DEVICE_CODE -> R.string.guard_error_last_device
    GUARD_ALREADY_ON_CODE -> R.string.guard_error_already_on
    GUARD_ENVELOPE_REQUIRED_CODE -> R.string.guard_error_envelope
    GUARD_ENVELOPE_UNSUPPORTED_CODE -> R.string.guard_error_envelope
    GUARD_DEVICE_TYPE_INVALID_CODE -> R.string.guard_error_device_type_invalid
    GUARD_DEVICE_UNKNOWN_CODE -> R.string.guard_error_device_unknown
    GUARD_REQUEST_GONE_CODE -> R.string.guard_error_request_gone
    GUARD_WRONG_NUMBER_CODE -> R.string.guard_approve_wrong_number
    GUARD_DISABLED_CODE -> R.string.guard_error_disabled
    GUARD_RESCUE_CODE_INVALID_CODE -> R.string.guard_rescue_invalid
    GUARD_CODE_INVALID_CODE -> R.string.guard_wait_wrong_code
    GUARD_VETO_INVALID_CODE -> R.string.guard_veto_invalid
    GUARD_PAUSED_CODE -> R.string.guard_error_paused
    GUARD_TOO_MANY_REQUESTS_CODE -> R.string.guard_error_too_many
    else -> null
}

fun guard_retry_ends_at(retry_after_secs: Long, now: Instant): Instant? =
    if (retry_after_secs >= GUARD_TIMED_RETRY_MIN_SECS) now.plusSeconds(retry_after_secs) else null

fun guard_error_message_for(t: Throwable, now: Instant = Instant.now()): guard_error_message? =
    guard_step_up_reported(t, now) ?: guard_base_error_message(t, now)

private fun guard_base_error_message(t: Throwable, now: Instant): guard_error_message? = when (t) {
    is ApiError.GuardRetry -> {
        val ends = guard_retry_ends_at(t.retry_after_secs, now)
        when (t.code) {
            GUARD_PAUSED_CODE -> ends?.let { guard_error_message(R.string.guard_error_paused_until, it) }
                ?: guard_error_message(R.string.guard_error_paused)
            else -> ends?.let { guard_error_message(R.string.guard_error_too_many_until, it) }
                ?: guard_error_message(R.string.guard_error_too_many)
        }
    }
    is ApiError.GuardHeld -> guard_parse_instant(t.apply_after)
        ?.let { guard_error_message(R.string.guard_held_banner_body, it) }
        ?: guard_error_message(R.string.guard_held_banner_body_no_time)
    is ApiError.GuardDevicePending -> guard_parse_instant(t.activates_at)
        ?.let { guard_error_message(R.string.guard_error_device_pending, it) }
        ?: guard_error_message(R.string.guard_error_device_pending_no_time)
    is ApiError.GuardRequired -> guard_error_message(R.string.guard_error_required)
    is ApiError.NetworkError, is java.io.IOException -> guard_error_message(R.string.error_no_connection)
    is ApiError.ServerError -> guard_error_message(R.string.guard_error_server)
    else -> guard_error_string_res(api_error_code(t))?.let { guard_error_message(it) }
}

fun guard_error_message.text(context: Context): String {
    val base = at?.let { context.getString(res, guard_format_instant(it)) } ?: context.getString(res)
    val left = attempts_left ?: return base
    return "$base ${context.resources.getQuantityString(R.plurals.guard_step_up_attempts_left, left, left)}"
}

@Composable
fun guard_error_message.resolve(): String {
    val base = at?.let { stringResource(res, guard_format_instant(it)) } ?: stringResource(res)
    val left = attempts_left ?: return base
    return "$base ${pluralStringResource(R.plurals.guard_step_up_attempts_left, left, left)}"
}

fun guard_error_text(context: Context, t: Throwable): String? = guard_error_message_for(t)?.text(context)

fun guard_specific_error_text(context: Context, t: Throwable): String? = when (t) {
    is ApiError.NetworkError, is java.io.IOException, is ApiError.ServerError -> null
    else -> guard_error_text(context, t)
}

fun guard_error_text_or(context: Context, t: Throwable, @StringRes fallback: Int): String =
    guard_error_text(context, t) ?: context.getString(fallback)
