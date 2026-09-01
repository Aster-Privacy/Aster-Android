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

val PREMIUM_ALIAS_DOMAINS = listOf("astermail.me", "astermail.net")

private val PREMIUM_ALIAS_DOMAIN_PLANS = setOf(
    "star",
    "nova",
    "supernova",
    "duo",
    "family",
    "family_duo",
    "family_full",
    "pro",
    "business",
)

fun is_premium_alias_domain(domain: String): Boolean =
    PREMIUM_ALIAS_DOMAINS.any { it.equals(domain, ignoreCase = true) }

fun plan_allows_premium_alias_domains(plan_code: String?): Boolean =
    plan_code != null && PREMIUM_ALIAS_DOMAIN_PLANS.contains(plan_code.lowercase())
