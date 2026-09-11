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

package org.astermail.android.ui.common

import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo

private const val GHOST_PREFIX = "ghost-"
private const val DOMAIN_PREFIX = "domain-"
private const val EXTERNAL_PREFIX = "external-"

fun resolve_primary_sender_email(
    default_sender_id: String?,
    user_email: String,
    aliases: List<AliasInfo>,
    ghost_aliases: List<GhostAlias>,
    custom_domain_addresses: List<CustomDomainAddressInfo> = emptyList(),
    external_senders: Map<String, String> = emptyMap(),
): String {
    if (default_sender_id.isNullOrBlank() || default_sender_id == "primary") return user_email
    aliases.firstOrNull { it.id == default_sender_id }?.let { return it.address }
    if (default_sender_id.startsWith(GHOST_PREFIX)) {
        val gid = default_sender_id.removePrefix(GHOST_PREFIX)
        ghost_aliases.firstOrNull { it.id == gid }?.let { return it.address }
    }
    custom_domain_addresses.firstOrNull { it.id == default_sender_id }?.let { return it.address }
    if (default_sender_id.startsWith(DOMAIN_PREFIX)) {
        val did = default_sender_id.removePrefix(DOMAIN_PREFIX)
        custom_domain_addresses.firstOrNull { it.id == did }?.let { return it.address }
    }
    if (default_sender_id.startsWith(EXTERNAL_PREFIX)) {
        external_senders[default_sender_id.removePrefix(EXTERNAL_PREFIX)]
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    return user_email
}

fun sender_id_for_email(
    email: String,
    user_email: String,
    aliases: List<AliasInfo>,
    ghost_aliases: List<GhostAlias>,
    custom_domain_addresses: List<CustomDomainAddressInfo> = emptyList(),
    external_senders: Map<String, String> = emptyMap(),
): String? {
    if (email == user_email) return "primary"
    aliases.firstOrNull { it.address == email }?.let { return it.id }
    ghost_aliases.firstOrNull { it.address == email }?.let { return GHOST_PREFIX + it.id }
    custom_domain_addresses.firstOrNull { it.address == email }?.let { return it.id }
    external_senders.entries
        .firstOrNull { it.value.equals(email, ignoreCase = true) }
        ?.let { return EXTERNAL_PREFIX + it.key }
    return null
}
