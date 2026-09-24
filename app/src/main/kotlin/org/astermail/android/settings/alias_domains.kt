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

import org.astermail.android.api.settings.TwinAddressResponse
import org.astermail.android.api.settings.TwinSibling

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

const val TWIN_OFFER_DOMAIN = "aster.cx"

private val TWIN_SOURCE_DOMAINS = setOf("astermail.org", "astermail.me", "astermail.net")

private val CLAIMABLE_LOCAL_PART = Regex("^[a-z0-9](?:[a-z0-9._-]{0,62}[a-z0-9])?$")

fun twin_domain_offerable(domain: String, state: String): Boolean =
    domain.trim().equals(TWIN_OFFER_DOMAIN, ignoreCase = true) &&
        (state == "reserved" || state == "available")

fun offerable_twin_siblings(twin: TwinAddressResponse?): List<TwinSibling> {
    if (twin == null) return emptyList()
    val all = twin.siblings.ifEmpty {
        listOf(
            TwinSibling(
                address = twin.address,
                domain = twin.domain,
                local_part = twin.local_part,
                state = twin.state,
            ),
        )
    }
    return all.filter { twin_domain_offerable(it.domain, it.state) }
}

fun twin_offer_siblings(
    twin: TwinAddressResponse?,
    owned_addresses: Collection<String>,
    verified: Boolean,
    owned_loaded: Boolean,
): List<TwinSibling> {
    if (!verified && !owned_loaded) return emptyList()
    return offerable_twin_siblings(twin).filter { !twin_address_owned(it.address, owned_addresses) }
}

private fun twin_address_owned(address: String, owned_addresses: Collection<String>): Boolean {
    val normalized = address.trim().lowercase()
    val at = normalized.lastIndexOf('@')
    val dotless = if (at > 0) normalized.substring(0, at).replace(".", "") + normalized.substring(at) else normalized
    return owned_addresses.any {
        val owned = it.trim().lowercase()
        owned == normalized || owned == dotless
    }
}

fun seed_twin_address(primary_email: String?, owned_addresses: Collection<String>): TwinAddressResponse? {
    val email = primary_email?.trim()?.lowercase().orEmpty()
    val at = email.lastIndexOf('@')
    if (at <= 0) return null
    val local_part = email.substring(0, at)
    val domain = email.substring(at + 1)
    if (domain !in TWIN_SOURCE_DOMAINS) return null
    if (local_part.length < 3 || local_part.length > 64) return null
    if (local_part.contains("..") || local_part.all { it.isDigit() }) return null
    if (!CLAIMABLE_LOCAL_PART.matches(local_part)) return null
    val address = "$local_part@$TWIN_OFFER_DOMAIN"
    val claimed = twin_address_owned(address, owned_addresses)
    val sibling = TwinSibling(
        address = address,
        domain = TWIN_OFFER_DOMAIN,
        local_part = local_part,
        state = if (claimed) "claimed" else "reserved",
    )
    return TwinAddressResponse(
        address = sibling.address,
        domain = sibling.domain,
        local_part = sibling.local_part,
        state = sibling.state,
        siblings = listOf(sibling),
    )
}
