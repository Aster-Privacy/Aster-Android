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

import org.astermail.android.api.settings.SubscriptionInfo

private val vanguard_plan_codes = setOf(
    "nova",
    "supernova",
    "duo",
    "family",
    "family_duo",
    "family_full",
)

fun subscription_plan_code(subscription: SubscriptionInfo?): String {
    val code = subscription?.plan?.code?.trim()?.lowercase().orEmpty()
    if (code.isNotBlank()) return code
    val name = subscription?.effective_plan_name?.trim()?.lowercase().orEmpty()
    return when {
        name.contains("supernova") -> "supernova"
        name.contains("nova") -> "nova"
        name.contains("family") -> "family"
        name.contains("duo") -> "duo"
        name.contains("star") -> "star"
        name.isBlank() || name.contains("free") -> "free"
        else -> name
    }
}

fun is_vanguard_plan(subscription: SubscriptionInfo?): Boolean =
    subscription_plan_code(subscription) in vanguard_plan_codes
