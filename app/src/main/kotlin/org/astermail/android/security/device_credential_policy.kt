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

package org.astermail.android.security

import android.os.Build

enum class DeviceCredentialRoute { BIOMETRIC_PROMPT, KEYGUARD_CONFIRM }

enum class DeviceCredentialResult { SUCCESS, CANCELED, UNAVAILABLE, FAILED }

object DeviceCredentialPolicy {
    fun route(sdk_int: Int): DeviceCredentialRoute =
        if (sdk_int >= Build.VERSION_CODES.R) DeviceCredentialRoute.BIOMETRIC_PROMPT else DeviceCredentialRoute.KEYGUARD_CONFIRM

    fun prepares_before_prompt(route: DeviceCredentialRoute): Boolean =
        route == DeviceCredentialRoute.BIOMETRIC_PROMPT
}
