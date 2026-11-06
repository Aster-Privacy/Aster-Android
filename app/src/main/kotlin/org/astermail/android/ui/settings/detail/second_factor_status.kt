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
package org.astermail.android.ui.settings.detail

enum class second_factor_method {
    none,
    authenticator,
    security_key,
    both,
}

fun second_factor_method_for(totp_enabled: Boolean, security_key_count: Int): second_factor_method {
    val has_key = security_key_count > 0
    return when {
        totp_enabled && has_key -> second_factor_method.both
        totp_enabled -> second_factor_method.authenticator
        has_key -> second_factor_method.security_key
        else -> second_factor_method.none
    }
}

fun has_second_factor(totp_enabled: Boolean, security_key_count: Int): Boolean =
    second_factor_method_for(totp_enabled, security_key_count) != second_factor_method.none

const val security_score_max = 7

data class security_score_input(
    val totp_enabled: Boolean = false,
    val security_key_count: Int = 0,
    val recovery_email_verified: Boolean = false,
    val login_alerts_enabled: Boolean = false,
    val block_tracking_pixels: Boolean = false,
    val block_remote_images: Boolean = false,
    val strip_exif_on_compose: Boolean = false,
)

fun security_score(input: security_score_input): Int = listOf(
    has_second_factor(input.totp_enabled, input.security_key_count),
    input.security_key_count > 0,
    input.recovery_email_verified,
    input.login_alerts_enabled,
    input.block_tracking_pixels,
    input.block_remote_images,
    input.strip_exif_on_compose,
).count { it }
