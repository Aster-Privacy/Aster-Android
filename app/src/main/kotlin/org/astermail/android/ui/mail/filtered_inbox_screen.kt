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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.mail.MailViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton

enum class FilterType { folder, label, alias }

@Composable
fun FilteredInboxScreen(
    filter_type: FilterType,
    filter_value: String,
    filter_display_name: String,
    on_open_drawer: () -> Unit,
    on_open_email: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val list_state = rememberLazyListState()
    val mail_vm: MailViewModel = hiltViewModel()
    val inbox_state by mail_vm.inbox_state.collectAsStateWithLifecycle()

    LaunchedEffect(filter_type, filter_value) {
        val folder = when (filter_type) {
            FilterType.folder -> filter_value
            FilterType.label -> "label:$filter_value"
            FilterType.alias -> "inbox"
        }
        mail_vm.load_inbox(folder, force = true)
    }

    LaunchedEffect(list_state) {
        snapshotFlow {
            val layout_info = list_state.layoutInfo
            val total = layout_info.totalItemsCount
            val last_visible = layout_info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && (total - last_visible) <= 3
        }.distinctUntilChanged().collect { near_end ->
            if (near_end) mail_vm.load_more()
        }
    }

    val filtered_emails = remember(inbox_state.items, filter_type, filter_value) {
        inbox_state.items.map { inbox_item_to_email(it) }
    }
    val threads = remember(filtered_emails) {
        group_by_thread(filtered_emails).sortedByDescending { it.newest.received_at }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            filtered_top_bar(
                title = filter_display_name,
                subtitle = subtitle_for(filter_type, threads.size),
                on_open_drawer = on_open_drawer,
            )
            AsterDivider(modifier = Modifier.fillMaxWidth())
            if (inbox_state.is_loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    inbox_skeleton()
                }
            } else if (threads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_messages_in, filter_display_name),
                        color = colors.text_muted,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = list_state,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = threads, key = { it.thread_id }, contentType = { "thread_row" }) { thread ->
                        Box(modifier = Modifier.background(colors.bg_primary)) {
                            ThreadInboxRow(
                                thread = thread,
                                on_click = { on_open_email(thread.thread_id) },
                                on_long_click = { on_open_email(thread.thread_id) },
                                on_toggle_star = { mail_vm.toggle_star(thread.newest.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun subtitle_for(filter_type: FilterType, count: Int): String {
    val type_label = when (filter_type) {
        FilterType.folder -> stringResource(R.string.type_folder)
        FilterType.label -> stringResource(R.string.type_label)
        FilterType.alias -> stringResource(R.string.type_alias)
    }
    return stringResource(R.string.messages_in_type, count, type_label)
}


@Composable
private fun filtered_top_bar(
    title: String,
    subtitle: String,
    on_open_drawer: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxWidth().background(colors.bg_primary)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsterIconButton(
                icon = Icons.Filled.Menu,
                content_description = stringResource(R.string.open_drawer),
                onClick = on_open_drawer,
            )
            Spacer(Modifier.width(AsterSpacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.text_primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    text = subtitle,
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
