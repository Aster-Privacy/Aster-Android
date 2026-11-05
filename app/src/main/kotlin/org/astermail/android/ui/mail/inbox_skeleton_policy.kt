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

internal fun email_ids_signature(emails: List<Email>): Int {
    val ids = emails.mapTo(HashSet(emails.size)) { it.id }
    return 31 * ids.size + ids.hashCode()
}

internal fun inbox_rows_deriving(
    has_rows: Boolean,
    folder_items_loaded: Boolean,
    derivation_pending: Boolean,
    derived_from_loaded_items: Boolean,
): Boolean = !has_rows && folder_items_loaded && (derivation_pending || !derived_from_loaded_items)

internal fun inbox_hidden_by_category(
    has_rows: Boolean,
    folder_items_loaded: Boolean,
    rows_deriving: Boolean,
): Boolean = !has_rows && folder_items_loaded && !rows_deriving

internal fun inbox_skeleton_wanted(
    rows_deriving: Boolean,
    skeleton_showing: Boolean,
    cache_pending: Boolean,
    loading_without_rows: Boolean,
    waiting_for_rows: Boolean,
    showing_error: Boolean,
    refreshing_known_empty: Boolean,
    emptied_by_action: Boolean,
): Boolean {
    if (rows_deriving) return skeleton_showing
    if (refreshing_known_empty || emptied_by_action) return false
    return cache_pending || loading_without_rows || (!showing_error && waiting_for_rows)
}
