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

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import org.astermail.android.R

data class ToolbarAction(
    val id: String,
    val label_res: Int,
    val icon: ImageVector,
)

val toolbar_action_catalog: List<ToolbarAction> = listOf(
    ToolbarAction("read", R.string.mark_as_read, Icons.Outlined.MarkEmailRead),
    ToolbarAction("trash", R.string.move_to_trash, Icons.Outlined.Delete),
    ToolbarAction("archive", R.string.swipe_archive, Icons.Outlined.Archive),
    ToolbarAction("folder", R.string.move_to_folder, Icons.Outlined.FolderOpen),
    ToolbarAction("label", R.string.label, Icons.AutoMirrored.Outlined.Label),
    ToolbarAction("star", R.string.star, Icons.Outlined.Star),
    ToolbarAction("snooze", R.string.snooze, Icons.Outlined.Schedule),
    ToolbarAction("spam", R.string.report_spam, Icons.Outlined.Report),
    ToolbarAction("reply", R.string.reply, Icons.AutoMirrored.Filled.Reply),
    ToolbarAction("forward", R.string.forward, Icons.AutoMirrored.Outlined.Forward),
)

private const val prefs_name = "aster_toolbar"
private const val key_actions = "actions"
private const val default_actions = "reply,archive,trash,folder"
const val toolbar_slot_count = 4

fun parse_toolbar_actions(raw: String?): List<String> {
    val source = if (raw.isNullOrBlank()) default_actions else raw
    val parsed = source.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val valid_ids = toolbar_action_catalog.map { it.id }.toSet()
    val cleaned = parsed.filter { it in valid_ids }.distinct()
    val fallback = default_actions.split(",")
    return (cleaned + fallback.filter { it !in cleaned }).take(toolbar_slot_count)
}

fun load_toolbar_actions(context: Context): List<String> {
    val prefs = context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
    return parse_toolbar_actions(prefs.getString(key_actions, null))
}

fun cache_toolbar_actions(context: Context, ids: List<String>) {
    val prefs = context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)
    prefs.edit().putString(key_actions, ids.joinToString(",")).apply()
}

@Deprecated("Use SettingsViewModel.save_preferences to persist e2e; this only writes the local cache.")
fun save_toolbar_actions(context: Context, ids: List<String>) = cache_toolbar_actions(context, ids)

fun toolbar_action_by_id(id: String): ToolbarAction? = toolbar_action_catalog.find { it.id == id }
