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

enum class security_check {
    two_factor,
    passkey,
    recovery_email,
    login_alerts,
    tracking_pixels,
    remote_images,
    strip_exif,
}

enum class security_anchor {
    passkeys,
    login_alerts,
    tracking_protection,
    tracking_pixels,
    remote_images,
    strip_exif,
}

sealed interface security_check_target {
    data class screen(val route_id: String) : security_check_target
    data class section(val anchor: security_anchor) : security_check_target
}

const val security_route_two_factor = "two_factor"
const val security_route_recovery_email = "recovery_email"

fun security_check_target_for(check: security_check): security_check_target = when (check) {
    security_check.two_factor -> security_check_target.screen(security_route_two_factor)
    security_check.passkey -> security_check_target.section(security_anchor.passkeys)
    security_check.recovery_email -> security_check_target.screen(security_route_recovery_email)
    security_check.login_alerts -> security_check_target.section(security_anchor.login_alerts)
    security_check.tracking_pixels -> security_check_target.section(security_anchor.tracking_pixels)
    security_check.remote_images -> security_check_target.section(security_anchor.remote_images)
    security_check.strip_exif -> security_check_target.section(security_anchor.strip_exif)
}

fun security_anchor_fallback(anchor: security_anchor): security_anchor? = when (anchor) {
    security_anchor.tracking_pixels -> security_anchor.tracking_protection
    else -> null
}

fun resolve_security_anchor(
    anchor: security_anchor,
    is_placed: (security_anchor) -> Boolean,
): security_anchor? {
    var candidate: security_anchor? = anchor
    while (candidate != null) {
        if (is_placed(candidate)) return candidate
        candidate = security_anchor_fallback(candidate)
    }
    return null
}
