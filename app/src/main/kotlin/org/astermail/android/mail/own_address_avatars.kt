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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OwnAddressAvatarSource { ALIAS, DOMAIN_ADDRESS, GHOST, ACCOUNT }

object OwnAddressAvatars {
    private val by_source = linkedMapOf<OwnAddressAvatarSource, Map<String, String>>()
    private val _entries = MutableStateFlow<Map<String, String>>(emptyMap())
    val entries: StateFlow<Map<String, String>> = _entries.asStateFlow()

    fun normalize(address: String): String {
        val lowered = address.trim().lowercase(Locale.ROOT)
        val at = lowered.lastIndexOf('@')
        if (at < 0) return lowered.replace(".", "")
        return lowered.substring(0, at).replace(".", "") + lowered.substring(at)
    }

    @Synchronized
    fun publish(source: OwnAddressAvatarSource, addresses: List<Pair<String, String?>>) {
        val next = linkedMapOf<String, String>()
        for ((address, picture) in addresses) {
            val trimmed = address.trim()
            val image = picture?.trim().orEmpty()
            if (trimmed.isEmpty() || !trimmed.contains('@') || image.isEmpty()) continue
            next.putIfAbsent(normalize(trimmed), image)
        }
        if (by_source[source] == next) return
        by_source[source] = next
        recompute()
    }

    @Synchronized
    fun clear() {
        if (by_source.isEmpty()) return
        by_source.clear()
        recompute()
    }

    fun get(address: String?): String? {
        val trimmed = address?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val current = _entries.value
        if (current.isEmpty()) return null
        return current[normalize(trimmed)]
    }

    private fun recompute() {
        val merged = linkedMapOf<String, String>()
        for (source in OwnAddressAvatarSource.values()) {
            val values = by_source[source] ?: continue
            for ((key, value) in values) merged.putIfAbsent(key, value)
        }
        if (_entries.value == merged) return
        _entries.value = merged
    }
}
