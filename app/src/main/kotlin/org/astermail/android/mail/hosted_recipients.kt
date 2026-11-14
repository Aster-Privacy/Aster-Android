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

package org.astermail.android.mail

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object HostedRecipientDirectory {
    private const val MAX_ADDRESS_LENGTH = 254
    private val address_shape = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
    private val lookup_username_shape = Regex("^[A-Za-z0-9_.-]{1,64}$")
    private val entries = ConcurrentHashMap<String, Boolean>()

    fun normalize(email: String): String =
        email.trim().lowercase(Locale.ROOT).trimEnd('.')

    fun lookup_username(address: String): String? {
        val at = address.lastIndexOf('@')
        if (at <= 0) return null
        val local = address.substring(0, at).substringBefore('+')
        return local.takeIf { lookup_username_shape.matches(it) }
    }

    fun needs_lookup(email: String): Boolean {
        val address = normalize(email)
        if (address.isEmpty() || address.length > MAX_ADDRESS_LENGTH) return false
        if (!address_shape.matches(address) || lookup_username(address) == null) return false
        if (is_aster_domain_recipient(address)) return false
        return !entries.containsKey(address)
    }

    fun record(email: String, internal: Boolean) {
        val address = normalize(email)
        if (address.isEmpty() || is_aster_domain_recipient(address)) return
        entries[address] = internal
    }

    fun is_hosted(email: String): Boolean {
        val address = normalize(email)
        if (address.isEmpty() || is_aster_domain_recipient(address)) return false
        return entries[address] == true
    }

    fun clear() {
        entries.clear()
    }
}

fun is_aster_domain_recipient(email: String): Boolean {
    val normalized = email.trim().lowercase(Locale.ROOT)
    return ASTER_INTERNAL_DOMAINS.any { normalized.endsWith("@$it") }
}

fun hosted_recipients_of(recipients: List<String>): List<String> =
    recipients
        .filter { it.isNotBlank() && HostedRecipientDirectory.is_hosted(it) }
        .map { it.trim() }
        .distinctBy { HostedRecipientDirectory.normalize(it) }
