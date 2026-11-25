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

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class SignOutTokens(val access: String, val refresh: String?, val csrf: String?)

internal suspend fun <T : Any> run_sign_out(
    wipe: suspend () -> T?,
    notify_server: suspend (T) -> Unit,
): Result<Unit> = withContext(NonCancellable) {
    runCatching {
        val captured = wipe() ?: return@runCatching
        runCatching { notify_server(captured) }
        Unit
    }
}
