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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.astermail.android.api.auth.RegistrationExtras

interface RegistrationExtension {
    suspend fun <T> around_register(
        on_extended_wait: (Boolean) -> Unit,
        submit: suspend (RegistrationExtras) -> T,
    ): T
}

object NoRegistrationExtension : RegistrationExtension {
    override suspend fun <T> around_register(
        on_extended_wait: (Boolean) -> Unit,
        submit: suspend (RegistrationExtras) -> T,
    ): T = submit(RegistrationExtras.none)
}

object RegistrationWaitState {
    private val _extended_wait = MutableStateFlow(false)
    val extended_wait: StateFlow<Boolean> = _extended_wait.asStateFlow()

    fun set(value: Boolean) {
        _extended_wait.value = value
    }
}
