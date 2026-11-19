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

import androidx.annotation.StringRes
import org.astermail.android.R

internal enum class subscription_bar_action(@StringRes val label_res: Int) {
    deselect(R.string.deselect_all),
    select_all(R.string.select_all),
    unsubscribe(R.string.unsubscribe_selected),
    reactivate(R.string.reactivate_selected),
    delete(R.string.delete_selected_subscriptions),
}

internal fun subscription_bar_actions(is_unsubscribed_tab: Boolean): List<subscription_bar_action> = listOf(
    subscription_bar_action.deselect,
    subscription_bar_action.select_all,
    if (is_unsubscribed_tab) subscription_bar_action.reactivate else subscription_bar_action.unsubscribe,
    subscription_bar_action.delete,
)

internal data class subscription_selection_result(
    val selected: Set<String>,
    val delete_request: List<String> = emptyList(),
    val unsubscribe_request: List<String> = emptyList(),
    val reactivate_request: List<String> = emptyList(),
)

internal fun reduce_subscription_selection(
    selected: Set<String>,
    visible_ids: Set<String>,
    action: subscription_bar_action,
): subscription_selection_result {
    val current = selected intersect visible_ids
    return when (action) {
        subscription_bar_action.deselect -> subscription_selection_result(selected = emptySet())
        subscription_bar_action.select_all -> subscription_selection_result(selected = visible_ids)
        subscription_bar_action.unsubscribe -> subscription_selection_result(
            selected = current,
            unsubscribe_request = current.toList(),
        )
        subscription_bar_action.reactivate -> subscription_selection_result(
            selected = emptySet(),
            reactivate_request = current.toList(),
        )
        subscription_bar_action.delete -> subscription_selection_result(
            selected = current,
            delete_request = current.toList(),
        )
    }
}

internal fun toggle_subscription_selection(selected: Set<String>, id: String): Set<String> =
    if (id in selected) selected - id else selected + id

internal fun is_subscription_selection_active(selected: Set<String>): Boolean = selected.isNotEmpty()
