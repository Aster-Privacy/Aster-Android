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

private fun server_code_of(t: Throwable): String? = when (t) {
    is ApiError.ForbiddenError -> t.server_code
    is ApiError.Conflict -> t.server_code
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

private fun localized_server_code(context: Context, code: String): String? =
    server_code_string_res(code)?.let { context.getString(it) }

fun localized_api_error(context: Context, t: Throwable, fallback: String): String {
    if (t is CancellationException) throw t
    server_code_of(t)?.let { code -> localized_server_code(context, code)?.let { return it } }
    server_supplied_detail(t)?.let { return it }
    return when (t) {
        is ApiError.NetworkError -> context.getString(R.string.error_no_connection)
        is ApiError.UnauthorizedError -> context.getString(R.string.session_expired_sign_in)
        else -> fallback
    }
}
