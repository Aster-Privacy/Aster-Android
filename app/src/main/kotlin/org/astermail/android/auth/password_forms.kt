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

import java.text.Normalizer
import org.astermail.android.api.ApiError

object PasswordForms {
    fun of(typed: String): List<String> =
        listOf(
            typed,
            Normalizer.normalize(typed, Normalizer.Form.NFC),
            Normalizer.normalize(typed, Normalizer.Form.NFD),
        ).distinct()

    suspend fun <T> first_accepted(typed: String, attempt: suspend (String) -> Result<T>): Result<T> {
        val forms = of(typed)
        val first = attempt(forms.first())
        if (first.exceptionOrNull() !is ApiError.InvalidCredentials) return first
        for (form in forms.drop(1)) {
            val next = attempt(form)
            if (next.isSuccess) return next
        }
        return first
    }
}
