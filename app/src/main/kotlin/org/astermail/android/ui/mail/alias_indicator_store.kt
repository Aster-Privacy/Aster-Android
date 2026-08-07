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

package org.astermail.android.ui.mail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object alias_indicator_store {
    var enabled by mutableStateOf(true)
        private set

    var labels by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var token_labels by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    fun set_enabled(value: Boolean) {
        if (enabled != value) enabled = value
    }

    fun set_labels(value: Map<String, String>) {
        if (labels != value) labels = value
    }

    fun set_token_labels(value: Map<String, String>) {
        if (token_labels != value) token_labels = value
    }

    fun label_for(address: String?): String? {
        if (!enabled) return null
        val key = normalize_alias_key(address)
        if (key.isEmpty()) return null
        return labels[key]
    }

    fun label_for_delivery(routing_token: String?, address: String?): String? {
        if (!enabled) return null
        return resolve_alias_delivery_label(token_labels, labels, routing_token, address)
    }
}
