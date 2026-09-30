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

package org.astermail.android.ui.settings.mail_rules

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Archive
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.Trash
import org.astermail.android.R
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.mail_rules.RuleSystemFolder
import org.astermail.android.mail_rules.rule_system_folder_type

fun rule_system_folder_name_res(type: RuleSystemFolder): Int = when (type) {
    RuleSystemFolder.INBOX -> R.string.folder_inbox
    RuleSystemFolder.ARCHIVE -> R.string.folder_archive
    RuleSystemFolder.SPAM -> R.string.folder_spam
    RuleSystemFolder.TRASH -> R.string.folder_trash
}

fun rule_system_folder_icon(type: RuleSystemFolder): ImageVector = when (type) {
    RuleSystemFolder.INBOX -> TablerIcons.Inbox
    RuleSystemFolder.ARCHIVE -> TablerIcons.Archive
    RuleSystemFolder.SPAM -> TablerIcons.AlertTriangle
    RuleSystemFolder.TRASH -> TablerIcons.Trash
}

@Composable
fun rule_folder_name(labels: List<LabelItem>, token: String?): String? {
    val folder = token?.let { t -> labels.firstOrNull { it.label_token == t } } ?: return null
    val system_type = rule_system_folder_type(folder.folder_type)
    if (system_type != null) return stringResource(rule_system_folder_name_res(system_type))
    return folder.encrypted_name?.takeIf { it.isNotBlank() }
}
