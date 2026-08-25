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

fun localized_api_error(context: Context, t: Throwable, fallback: String): String {
    if (t is CancellationException) throw t
    localized_server_code(context, t)?.let { return it }
    server_supplied_detail(t)?.let { return it }
    return when (t) {
        is ApiError.NetworkError -> context.getString(R.string.error_no_connection)
        is ApiError.UnauthorizedError -> context.getString(R.string.session_expired_sign_in)
        else -> fallback
    }
}
