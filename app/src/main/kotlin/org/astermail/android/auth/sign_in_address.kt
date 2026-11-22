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

package org.astermail.android.auth

import java.util.Locale

object SignInAddress {
    val domains = listOf("astermail.org", "aster.cx")

    private const val max_local_length = 40
    private const val max_password_length = 128

    fun local_part(typed: String): String =
        typed.substringBefore('@')
            .lowercase(Locale.ROOT)
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
            .take(max_local_length)

    fun candidates(typed: String, picked_domain: String, domain_explicit: Boolean): List<String> {
        val trimmed = typed.trim()
        val local = local_part(trimmed)
        if (local.isEmpty()) return listOf(trimmed)
        val typed_domain = trimmed.substringAfter('@', "").trim().lowercase(Locale.ROOT).trimEnd('.')
        if (typed_domain.isNotEmpty()) return listOf("$local@$typed_domain")
        if (domain_explicit) return listOf("$local@$picked_domain")
        return (listOf(picked_domain) + domains.filter { it != picked_domain }).map { "$local@$it" }
    }

    fun clamp_password(password: String): String {
        if (password.length <= max_password_length) return password
        val cut = password.take(max_password_length)
        return if (cut.last().isHighSurrogate()) cut.dropLast(1) + "\uFFFD" else cut
    }
}
