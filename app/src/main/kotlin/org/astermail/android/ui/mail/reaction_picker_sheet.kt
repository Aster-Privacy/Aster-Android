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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.History
import compose.icons.tablericons.Search
import compose.icons.tablericons.X
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.design.components.AsterTextField

private data class emoji_section(val title: String, val entries: List<emoji_entry>)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun reaction_picker_sheet(
    on_close: () -> Unit,
    on_pick: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var recents by remember { mutableStateOf(RecentEmojiStore.load(context)) }
    val recent_title = stringResource(R.string.emoji_group_recent)
    val catalog_titles = emoji_catalog.map { stringResource(it.title) }

    val sections = remember(query, recents, catalog_titles) {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            buildList {
                if (recents.isNotEmpty()) {
                    add(emoji_section(recent_title, recents.map { emoji_entry(it, "") }))
                }
                emoji_catalog.forEachIndexed { index, group ->
                    add(emoji_section(catalog_titles[index], group.entries))
                }
            }
        } else {
            val terms = trimmed.split(' ').filter { it.isNotBlank() }
            val hits = emoji_catalog
                .flatMap { it.entries }
                .distinctBy { it.glyph }
                .filter { entry -> terms.all { term -> entry.keywords.contains(term) } }
            if (hits.isEmpty()) emptyList() else listOf(emoji_section("", hits))
        }
    }

    val grid_state = rememberLazyGridState()
    val tab_state = rememberLazyListState()
    val section_starts = remember(sections) {
        var index = 0
        sections.map { section ->
            val start = index
            index += (if (section.title.isBlank()) 0 else 1) + section.entries.size
            start
        }
    }

    ModalBottomSheet(
        onDismissRequest = on_close,
        sheetState = sheet_state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .testTag("reaction_picker_sheet"),
        ) {
            Text(
                text = stringResource(R.string.add_reaction),
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = AsterSpacing.md),
            )
            Box(modifier = Modifier.padding(AsterSpacing.md)) {
                AsterTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.emoji_search_placeholder),
                    leading_icon = {
                        Icon(
                            imageVector = TablerIcons.Search,
                            contentDescription = null,
                            tint = colors.text_tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailing_icon = if (query.isEmpty()) {
                        null
                    } else {
                        {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(SquircleShape(999.dp))
                                    .clickable { query = "" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = TablerIcons.X,
                                    contentDescription = stringResource(R.string.clear),
                                    tint = colors.text_tertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    min_height = 46.dp,
                )
            }

            if (query.isBlank()) {
                LazyRow(
                    state = tab_state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AsterSpacing.xs),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = AsterSpacing.md,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
                ) {
                    items(sections.size) { index ->
                        val section = sections[index]
                        val is_recent = index == 0 && section.title == recent_title && recents.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 34.dp)
                                .clip(SquircleShape(10.dp))
                                .background(colors.bg_tertiary)
                                .clickable {
                                    scope.launch {
                                        grid_state.animateScrollToItem(section_starts[index])
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (is_recent) {
                                Icon(
                                    imageVector = TablerIcons.History,
                                    contentDescription = section.title,
                                    tint = colors.text_secondary,
                                    modifier = Modifier.size(17.dp),
                                )
                            } else {
                                Text(text = section.entries.firstOrNull()?.glyph ?: "", fontSize = 17.sp)
                            }
                        }
                    }
                }
            }

            if (sections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.emoji_search_no_results),
                        color = colors.text_tertiary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    state = grid_state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 420.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = AsterSpacing.sm,
                        vertical = AsterSpacing.xs,
                    ),
                ) {
                    sections.forEach { section ->
                        if (section.title.isNotBlank()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = section.title,
                                    color = colors.text_tertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(
                                        start = AsterSpacing.xs,
                                        top = AsterSpacing.sm,
                                        bottom = AsterSpacing.xs,
                                    ),
                                )
                            }
                        }
                        items(section.entries, key = { section.title + it.glyph }) { entry ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(SquircleShape(10.dp))
                                    .clickable {
                                        recents = RecentEmojiStore.record(context, entry.glyph)
                                        scope.launch {
                                            sheet_state.hide()
                                            on_pick(entry.glyph)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = entry.glyph, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { sheet_state.show() }
}
