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

import android.content.Context
import android.content.Intent

const val launch_folder_inbox = "inbox"
const val launch_folder_category_prefix = "category:"

val launch_folder_system_ids: List<String> = listOf(
    launch_folder_inbox,
    all_mail_folder,
    "starred",
    "sent",
    "drafts",
    "archive",
)

data class launch_destination(
    val folder: String,
    val category: String,
) {
    companion object {
        val inbox = launch_destination(launch_folder_inbox, "primary")
    }
}

fun launch_folder_for_category(category: String): String =
    if (category == "primary") launch_folder_inbox else launch_folder_category_prefix + category

fun launch_folder_category(value: String): String? =
    value.takeIf { it.startsWith(launch_folder_category_prefix) }
        ?.removePrefix(launch_folder_category_prefix)
        ?.takeIf { it != "primary" && it in BUILTIN_CATEGORY_IDS }

fun sanitize_launch_folder(raw: String?): String = when {
    raw == null -> launch_folder_inbox
    raw in launch_folder_system_ids -> raw
    launch_folder_category(raw) != null -> raw
    else -> launch_folder_inbox
}

fun is_plain_launch(
    action: String?,
    has_data: Boolean,
    extra_keys: Set<String>,
    target_extra_keys: Set<String>,
): Boolean =
    action == Intent.ACTION_MAIN &&
        !has_data &&
        extra_keys.none { it in target_extra_keys }

fun resolve_launch_destination(
    stored: String?,
    plain_launch: Boolean,
    has_pending_target: Boolean,
    categories_enabled: Boolean,
    active_category_tabs: List<String>,
): launch_destination {
    if (!plain_launch || has_pending_target) return launch_destination.inbox
    val value = sanitize_launch_folder(stored)
    val category = launch_folder_category(value)
    if (category != null) {
        return if (categories_enabled && category in active_category_tabs) {
            launch_destination(launch_folder_inbox, category)
        } else {
            launch_destination.inbox
        }
    }
    return launch_destination(value, "primary")
}

object launch_folder_store {
    private const val prefs_name = "aster_launch"
    private const val key_folder = "launch_folder"

    fun load(context: Context): String =
        sanitize_launch_folder(
            context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE).getString(key_folder, null),
        )

    fun save(context: Context, value: String) {
        val clean = sanitize_launch_folder(value)
        val prefs = context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE).edit()
        if (clean == launch_folder_inbox) prefs.remove(key_folder) else prefs.putString(key_folder, clean)
        prefs.apply()
    }
}
