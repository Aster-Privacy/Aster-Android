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

package org.astermail.android.ui.compose

import org.astermail.android.api.billing.PlanLimitsResponse
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.crypto.normalize_address_ignoring_dots

const val catch_all_feature = "has_catch_all"

private val catch_all_address_pattern = Regex("^[a-z0-9!#$%&'*+/=?^_`{|}~.-]{1,64}@[a-z0-9.-]+$")

fun is_catch_all_unlocked(limits: PlanLimitsResponse?): Boolean {
    val info = limits?.limits?.get(catch_all_feature) ?: return false
    return info.limit != 0
}

fun to_catch_all_address(candidate: String?): String? {
    val email = candidate?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    if (email.length > 254 || !catch_all_address_pattern.matches(email)) return null
    val local = email.substringBefore('@')
    if (local == "*" || local.startsWith('+') || local.startsWith('.') || local.endsWith('.') || local.contains("..")) return null
    return email
}

fun find_catch_all_domain(domains: List<CustomDomain>, email: String): CustomDomain? {
    val name = email.substringAfter('@')
    return domains.firstOrNull {
        it.status == "active" &&
            it.catch_all_enabled &&
            !it.is_shared &&
            it.domain_name.lowercase() == name
    }
}

fun catch_all_sender_options(
    unlocked: Boolean,
    domains: List<CustomDomain>,
    candidates: List<String?>,
    existing: List<String>,
    disabled: List<String>,
): List<String> {
    if (!unlocked) return emptyList()
    val known = (existing + disabled).map { normalize_address_ignoring_dots(it) }.toMutableSet()
    val options = mutableListOf<String>()
    for (candidate in candidates) {
        val email = to_catch_all_address(candidate) ?: continue
        if (find_catch_all_domain(domains, email) == null) continue
        if (!known.add(normalize_address_ignoring_dots(email))) continue
        options.add(email)
    }
    return options
}

fun catch_all_reply_candidates(
    message: compose_thread_message?,
    existing: List<String>,
): List<String> {
    if (message == null || message.is_sent) return emptyList()
    val delivered = message.delivered_to?.takeIf { it.isNotBlank() } ?: return emptyList()
    val known = existing.map { normalize_address_ignoring_dots(it) }.toSet()
    val registered = (listOf(delivered) + message.to_addresses + message.cc_addresses)
        .any { it.isNotBlank() && normalize_address_ignoring_dots(it) in known }
    return if (registered) emptyList() else listOf(delivered)
}
