//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class alias_inbox_request(
    val id: String,
    val address: String,
    val routing_token: String,
    val direction: String,
)

object alias_inbox_requests {
    private val _pending = MutableStateFlow<alias_inbox_request?>(null)
    val pending: StateFlow<alias_inbox_request?> = _pending.asStateFlow()

    fun submit(request: alias_inbox_request) {
        _pending.value = request
    }

    fun consume(request: alias_inbox_request) {
        _pending.compareAndSet(request, null)
    }
}
