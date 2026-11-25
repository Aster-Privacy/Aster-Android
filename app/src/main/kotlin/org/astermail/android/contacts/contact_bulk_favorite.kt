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

package org.astermail.android.contacts

import org.astermail.android.ui.contacts.Contact

fun favorite_target(selected: List<Contact>): Boolean? =
    if (selected.isEmpty()) null else !selected.all { it.is_favorite }

fun contacts_needing_favorite(selected: List<Contact>, target: Boolean): List<Contact> =
    selected.filter { it.is_favorite != target }

fun apply_favorite(contacts: List<Contact>, ids: Set<String>, value: Boolean): List<Contact> =
    contacts.map { if (it.id in ids && it.is_favorite != value) it.copy(is_favorite = value) else it }
