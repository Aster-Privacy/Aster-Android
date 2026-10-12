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

package org.astermail.android.subscriptions

import java.util.Locale

data class BlockedSenderIndex(
    val addresses: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
) {
    val is_empty: Boolean get() = addresses.isEmpty() && domains.isEmpty()
}

private fun normalize_blocked_value(value: String): String =
    value.trim().lowercase(Locale.ROOT)

fun build_blocked_sender_index(blocked: List<Pair<String, Boolean>>): BlockedSenderIndex {
    val addresses = mutableSetOf<String>()
    val domains = mutableSetOf<String>()
    for ((raw, is_domain) in blocked) {
        val value = normalize_blocked_value(raw)
        if (value.isEmpty()) continue
        if (is_domain || !value.contains('@') || value.startsWith("@")) {
            val domain = value.removePrefix("@")
            if (domain.isNotEmpty()) domains.add(domain)
        } else {
            addresses.add(value)
        }
    }
    return BlockedSenderIndex(addresses, domains)
}

fun is_sender_in_blocked_index(sender_email: String, index: BlockedSenderIndex): Boolean {
    val address = normalize_blocked_value(sender_email)
    if (address.isEmpty()) return false
    if (address in index.addresses) return true
    val at_index = address.lastIndexOf('@')
    if (at_index == -1) return false
    val domain = address.substring(at_index + 1)
    return domain.isNotEmpty() && domain in index.domains
}

fun <T> without_blocked_senders(
    subscriptions: List<T>,
    index: BlockedSenderIndex,
    sender_email: (T) -> String,
): List<T> {
    if (index.is_empty) return subscriptions
    return subscriptions.filterNot { is_sender_in_blocked_index(sender_email(it), index) }
}
