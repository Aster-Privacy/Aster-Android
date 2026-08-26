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

package org.astermail.android.mail

private val builtin_mail_folders = setOf(
    "inbox",
    "sent",
    "drafts",
    "starred",
    "scheduled",
    "snoozed",
    "outbox",
    "contacts",
    "subscriptions",
)

fun folder_label_token(folder: String): String? = when {
    folder.isBlank() -> null
    folder in builtin_mail_folders -> null
    folder == "archive" || folder == "trash" || folder == "spam" -> null
    is_all_mail_folder(folder) -> null
    folder.startsWith("tag:") || folder.startsWith("routing:") -> null
    folder.startsWith("label:") -> folder.removePrefix("label:").takeIf { it.isNotBlank() }
    else -> folder
}

fun can_move_to_inbox(folder: String): Boolean =
    folder == "archive" ||
        folder == "trash" ||
        folder == "spam" ||
        folder_label_token(folder) != null
