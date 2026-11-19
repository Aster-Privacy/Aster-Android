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

import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.ui.drawer.drawer_alias_item

fun sidebar_alias_items(
    aliases: List<AliasInfo>,
    domain_addresses: List<CustomDomainAddressInfo>,
): List<drawer_alias_item> =
    order_sidebar_addresses(
        aliases
            .filter { it.is_enabled && !it.decryption_failed && it.encrypted_local_part.isNotBlank() }
            .map { alias ->
                sidebar_address_entry(
                    item = drawer_alias_item(
                        id = alias.id,
                        address = alias.address,
                        routing_token = alias.alias_address_hash.ifBlank { null },
                    ),
                    sidebar_order = alias.sidebar_order,
                    sidebar_hidden = alias.sidebar_hidden,
                )
            } + domain_addresses
            .filter { it.is_enabled && !it.decryption_failed && it.encrypted_local_part.isNotBlank() }
            .map { addr ->
                sidebar_address_entry(
                    item = drawer_alias_item(
                        id = addr.id,
                        address = addr.address,
                    ),
                    sidebar_order = addr.sidebar_order,
                    sidebar_hidden = addr.sidebar_hidden,
                )
            },
    )
