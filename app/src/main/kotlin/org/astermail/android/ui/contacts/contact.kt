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

package org.astermail.android.ui.contacts

data class Contact(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val company: String = "",
    val title: String = "",
    val work_email: String = "",
    val work_phone: String = "",
    val birthday: String = "",
    val address: String = "",
    val city: String = "",
    val region: String = "",
    val postal_code: String = "",
    val country: String = "",
    val website: String = "",
    val twitter: String = "",
    val linkedin: String = "",
    val notes: String = "",
    val is_favorite: Boolean = false,
    val groups: List<String> = emptyList(),
    val raw_json: String = "",
)
