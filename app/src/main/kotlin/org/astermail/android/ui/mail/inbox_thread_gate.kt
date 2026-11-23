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

internal class InboxThreadGate {
    private var folder: String? = null
    private var category: String? = null
    private var fingerprint = 0
    var category_only = false
        private set

    fun observe(next_folder: String, next_category: String, next_fingerprint: Int): Boolean {
        if (next_folder == folder && next_category == category && next_fingerprint == fingerprint) {
            return category_only
        }
        category_only = next_folder == folder &&
            category != null &&
            fingerprint == next_fingerprint &&
            category != next_category
        folder = next_folder
        category = next_category
        fingerprint = next_fingerprint
        return category_only
    }
}
