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

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity

private const val PROMPT_HANDOFF_STEP_MS = 50L
private const val PROMPT_HANDOFF_MAX_STEPS = 40

private fun FragmentActivity.prompt_still_attached(): Boolean =
    runCatching {
        supportFragmentManager.fragments.any { it.javaClass.name.startsWith("androidx.biometric.") }
    }.getOrDefault(false)

fun FragmentActivity.after_prompt_closed(action: () -> Unit) {
    if (!prompt_still_attached()) {
        action()
        return
    }
    val handler = Handler(Looper.getMainLooper())
    var steps = 0
    val check = object : Runnable {
        override fun run() {
            steps += 1
            if (prompt_still_attached() && steps < PROMPT_HANDOFF_MAX_STEPS && !isFinishing) {
                handler.postDelayed(this, PROMPT_HANDOFF_STEP_MS)
            } else {
                action()
            }
        }
    }
    handler.postDelayed(check, PROMPT_HANDOFF_STEP_MS)
}
