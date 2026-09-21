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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val inbox_filter_all = "all"
const val inbox_filter_unread = "unread"
const val inbox_filter_read = "read"
const val inbox_filter_attachments = "attachments"

const val inbox_quick_action_mark_all_read = "mark_all_read"
const val inbox_quick_action_archive_read = "archive_all_read"
const val inbox_quick_action_delete_old = "delete_old"

const val inbox_quick_action_age_days = 30

data class quick_delete_target(val ids: List<String>, val thread_ids: Set<String>)

fun thread_matches_inbox_filter(thread: ThreadRow, filter: String): Boolean = when (filter) {
    inbox_filter_unread -> thread.has_unread
    inbox_filter_read -> !thread.has_unread
    inbox_filter_attachments -> thread.has_attachment
    else -> true
}

object inbox_tools_pref {
    private const val prefs_name = "aster_inbox_tools"
    private const val key_visible = "tools_visible"

    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(prefs_name, Context.MODE_PRIVATE)

    fun load(context: Context) {
        _visible.value = prefs(context).getBoolean(key_visible, true)
    }

    fun set(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(key_visible, value).apply()
        _visible.value = value
    }
}

@Composable
fun show_inbox_tools(): Boolean {
    val context = LocalContext.current
    LaunchedEffect(context) { inbox_tools_pref.load(context) }
    val visible by inbox_tools_pref.visible.collectAsState()
    return visible
}
