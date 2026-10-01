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

package org.astermail.android.mail_rules

import org.astermail.android.api.labels.LabelItem

enum class RuleSystemFolder { INBOX, ARCHIVE, SPAM, TRASH }

data class RuleSystemFolderEntry(
    val folder: LabelItem,
    val system_type: RuleSystemFolder,
)

fun rule_system_folder_type(folder_type: String?): RuleSystemFolder? = when (folder_type) {
    "inbox", "default_open" -> RuleSystemFolder.INBOX
    "archive" -> RuleSystemFolder.ARCHIVE
    "spam" -> RuleSystemFolder.SPAM
    "trash" -> RuleSystemFolder.TRASH
    else -> null
}

fun rule_system_folders(labels: List<LabelItem>): List<RuleSystemFolderEntry> {
    val usable = labels.filter { it.label_token.isNotBlank() }
    val inbox = usable.firstOrNull { it.folder_type == "inbox" }
        ?: usable.firstOrNull { it.folder_type == "default_open" }
    val ordered = listOf(
        inbox,
        usable.firstOrNull { it.folder_type == "archive" },
        usable.firstOrNull { it.folder_type == "spam" },
        usable.firstOrNull { it.folder_type == "trash" },
    )
    return ordered.mapNotNull { folder ->
        val type = folder?.let { rule_system_folder_type(it.folder_type) } ?: return@mapNotNull null
        RuleSystemFolderEntry(folder = folder, system_type = type)
    }
}

fun rule_folder_type_of(labels: List<LabelItem>, token: String?): String? =
    token?.let { t -> labels.firstOrNull { it.label_token == t }?.folder_type }

fun rule_target_matches_alias_delivery(
    rule_folder_token: String,
    rule_folder_type: String?,
    alias_delivery_folder_token: String?,
    alias_never_inbox: Boolean,
): Boolean {
    if (alias_delivery_folder_token != null) return alias_delivery_folder_token == rule_folder_token
    return when (rule_system_folder_type(rule_folder_type)) {
        RuleSystemFolder.INBOX -> !alias_never_inbox
        RuleSystemFolder.ARCHIVE -> alias_never_inbox
        else -> false
    }
}
