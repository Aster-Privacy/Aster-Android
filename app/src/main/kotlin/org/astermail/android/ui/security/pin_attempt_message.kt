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

package org.astermail.android.ui.security

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.security.PinAttemptNotice

fun pin_failure_message(resources: Resources, notice: PinAttemptNotice): String = when (notice) {
    is PinAttemptNotice.AttemptsLeft ->
        resources.getQuantityString(R.plurals.app_lock_wrong_pin_attempts_left, notice.count, notice.count)
    PinAttemptNotice.NextFailureLocks -> resources.getString(R.string.app_lock_wrong_pin_next_locks)
    PinAttemptNotice.None -> resources.getString(R.string.app_lock_wrong_pin)
}

@Composable
fun pin_attempt_hint(notice: PinAttemptNotice): String? = when (notice) {
    is PinAttemptNotice.AttemptsLeft ->
        pluralStringResource(R.plurals.app_lock_attempts_left, notice.count, notice.count)
    PinAttemptNotice.NextFailureLocks -> stringResource(R.string.app_lock_next_wrong_pin_locks)
    PinAttemptNotice.None -> null
}
