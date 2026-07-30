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

package org.astermail.android.folders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.astermail.android.api.labels.LabelItem

private const val folder_unlock_timeout_ms = 30 * 60 * 1000L

object folder_lock_store {
    private val unlocked = mutableMapOf<String, Long>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Synchronized
    fun is_unlocked(folder_id: String): Boolean {
        val at = unlocked[folder_id] ?: return false
        if (System.currentTimeMillis() - at > folder_unlock_timeout_ms) {
            unlocked.remove(folder_id)
            return false
        }
        return true
    }

    @Synchronized
    fun mark_unlocked(folder_id: String) {
        unlocked[folder_id] = System.currentTimeMillis()
        _revision.value = _revision.value + 1
    }

    @Synchronized
    fun lock(folder_id: String) {
        if (unlocked.remove(folder_id) != null) _revision.value = _revision.value + 1
    }

    @Synchronized
    fun lock_all() {
        if (unlocked.isEmpty()) return
        unlocked.clear()
        _revision.value = _revision.value + 1
    }
}

fun is_folder_protected(label: LabelItem): Boolean =
    label.is_password_protected && label.password_set

fun requires_unlock(label: LabelItem): Boolean =
    is_folder_protected(label) && !folder_lock_store.is_unlocked(label.id)

fun protected_folder_tokens(labels: List<LabelItem>): Set<String> =
    labels.filter { requires_unlock(it) }.map { it.label_token }.toSet()

fun locked_active_folder(labels: List<LabelItem>, active_token: String): LabelItem? {
    if (active_token.isBlank()) return null
    return labels.firstOrNull { it.label_token == active_token }?.takeIf { requires_unlock(it) }
}
