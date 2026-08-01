//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

const val filter_kind_label = "label"
const val filter_kind_tag = "tag"
const val filter_kind_alias = "alias"
const val filter_kind_folder = "folder"

fun mail_folder_for_filter(filter_kind: String?, filter_value: String): String = when (filter_kind) {
    filter_kind_label -> "label:$filter_value"
    filter_kind_tag -> "tag:$filter_value"
    filter_kind_alias -> "routing:$filter_value"
    filter_kind_folder -> filter_value
    else -> "inbox"
}
