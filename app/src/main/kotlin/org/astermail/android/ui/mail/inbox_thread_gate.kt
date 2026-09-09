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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class InboxThreadGate {
    private var category by mutableStateOf<String?>(null)
    private var fingerprint by mutableIntStateOf(0)
    var category_only by mutableStateOf(false)
        private set

    fun observe(same_folder: Boolean, next_category: String, next_fingerprint: Int) {
        category_only = same_folder &&
            category != null &&
            fingerprint == next_fingerprint &&
            category != next_category
        category = next_category
        fingerprint = next_fingerprint
    }
}
