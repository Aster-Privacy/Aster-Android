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

package org.astermail.android.mail

private val unmovable_mail_folders = setOf("drafts", "scheduled", "outbox")

fun is_unmovable_mail_folder(folder: String): Boolean = folder in unmovable_mail_folders

fun system_move_targets(folder: String, all_starred: Boolean): List<String> {
    if (folder.isBlank() || is_unmovable_mail_folder(folder)) return emptyList()
    val moves = when (folder) {
        "trash" -> emptyList()
        "spam" -> listOf("trash")
        "archive" -> listOf("spam", "trash")
        "sent" -> listOf("archive", "trash")
        else -> listOf("archive", "spam", "trash")
    }
    return if (all_starred || folder == "trash" || folder == "spam") moves else moves + "star"
}
