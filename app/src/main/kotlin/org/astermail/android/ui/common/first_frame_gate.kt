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

package org.astermail.android.ui.common

import java.util.concurrent.atomic.AtomicBoolean

const val first_frame_hold_cap_ms = 600L
const val first_frame_inset_cap_ms = 1000L

enum class launch_start { inbox, sign_in, onboarding, welcome }

fun launch_start_for(
    is_signed_in: Boolean,
    pending_guard_sign_in: Boolean,
    onboarding_seen: Boolean,
): launch_start = when {
    is_signed_in -> launch_start.inbox
    pending_guard_sign_in -> launch_start.sign_in
    !onboarding_seen -> launch_start.onboarding
    else -> launch_start.welcome
}

fun first_frame_ready_before_inbox(start: launch_start, is_locked: Boolean): Boolean =
    start != launch_start.inbox || is_locked

fun inbox_first_frame_ready(
    state_matches_folder: Boolean,
    has_rows: Boolean,
    cache_pending: Boolean,
    rows_deriving: Boolean,
): Boolean = state_matches_folder && !rows_deriving && (has_rows || !cache_pending)

fun first_frame_may_draw(insets_applied: Boolean, content_ready: Boolean, elapsed_ms: Long): Boolean =
    (insets_applied || elapsed_ms >= first_frame_inset_cap_ms) &&
        (content_ready || elapsed_ms >= first_frame_hold_cap_ms)

object first_frame_gate {
    private val ready = AtomicBoolean(true)

    val is_ready: Boolean
        get() = ready.get()

    fun arm() {
        ready.set(false)
    }

    fun mark_ready() {
        ready.set(true)
    }
}
