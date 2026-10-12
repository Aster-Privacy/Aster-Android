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

const val tutorial_list_collapsed_key = "tutorial_list_collapsed"

val onboarding_checklist_order = listOf("import_mail", "first_email", "recovery_method", "install_app")

data class tutorial_list_row(val key: String, val done: Boolean)

data class tutorial_list_progress(val done: Int, val total: Int) {
    val remaining: Int get() = total - done
    val all_done: Boolean get() = total > 0 && remaining == 0
    val fraction: Float get() = if (total > 0) done.toFloat() / total.toFloat() else 0f
}

fun tutorial_list_rows(tasks: Map<String, Boolean>): List<tutorial_list_row> =
    onboarding_checklist_order.mapNotNull { key ->
        val done = tasks[key] ?: return@mapNotNull null
        tutorial_list_row(key, done)
    }

fun tutorial_list_progress_of(tasks: Map<String, Boolean>): tutorial_list_progress {
    val rows = tutorial_list_rows(tasks)
    return tutorial_list_progress(done = rows.count { it.done }, total = rows.size)
}

fun tutorial_list_is_active(is_loaded: Boolean, is_dismissed: Boolean, progress: tutorial_list_progress): Boolean =
    is_loaded && !is_dismissed && progress.total > 0

fun tutorial_list_visible(is_active: Boolean, progress: tutorial_list_progress): Boolean =
    is_active && !progress.all_done
