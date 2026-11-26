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

package org.astermail.android.notifications

internal data class DistributorResolution(
    val target: String?,
    val missing_choice: String?,
)

internal fun resolve_push_distributor(
    own_package: String,
    choice: String?,
    acked: String?,
    installed: List<String>,
): DistributorResolution {
    val missing = choice?.takeIf { it != own_package && it !in installed }
    val fallback = own_package.takeIf { it in installed } ?: installed.firstOrNull()
    val target = when {
        choice != null && choice in installed -> choice
        missing != null -> fallback
        acked != null && acked in installed -> acked
        else -> fallback
    }
    return DistributorResolution(target = target, missing_choice = missing)
}

internal fun external_push_distributors(own_package: String, installed: List<String>): List<String> =
    installed.filter { it.isNotBlank() && it != own_package }.distinct()

internal fun selected_push_distributor(
    own_package: String,
    choice: String?,
    acked: String?,
    installed: List<String>,
): String = resolve_push_distributor(own_package, choice, acked, installed).target ?: own_package
