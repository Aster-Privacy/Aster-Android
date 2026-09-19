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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Search
import compose.icons.tablericons.X
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDragHandle

@Immutable
private data class emoji_section(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val entries: List<emoji_entry>,
)

private val emoji_cell_size = 48.dp
private val emoji_grid_height = 360.dp

internal fun emoji_section_starts(section_sizes: List<Int>): List<Int> {
    var index = 0
    return section_sizes.map { size ->
        val start = index
        index += 1 + size
        start
    }
}

internal fun emoji_section_for_index(starts: List<Int>, item_index: Int, at_end: Boolean = false): Int {
    if (starts.isEmpty()) return 0
    if (at_end) return starts.lastIndex
    var result = 0
    starts.forEachIndexed { section, start -> if (item_index >= start) result = section }
    return result
}

internal fun emoji_search(entries: List<emoji_entry>, query: String): List<emoji_entry> {
    val terms = query.trim().lowercase().split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return emptyList()
    return entries
        .distinctBy { it.glyph }
        .filter { entry -> terms.all { term -> entry.keywords.contains(term) } }
}

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
    val recents = remember { RecentEmojiStore.load(context) }
    val recent_title = stringResource(R.string.emoji_group_recent)
    val catalog_titles = emoji_catalog.map { stringResource(it.title) }

    val sections = remember(recents, catalog_titles) {
        buildList {
            if (recents.isNotEmpty()) {
                add(emoji_section("recent", recent_title, TablerIcons.Clock, recents.map { emoji_entry(it, "") }))
            }
            emoji_catalog.forEachIndexed { index, group ->
                add(
                    emoji_section(
                        id = "group_$index",
                        title = catalog_titles[index],
                        icon = group.icon,
                        entries = group.entries.distinctBy { it.glyph },
                    ),
                )
            }
        }
    }
    val section_starts = remember(sections) { emoji_section_starts(sections.map { it.entries.size }) }
    val all_entries = remember { emoji_catalog.flatMap { it.entries } }
    val results = remember(query) { emoji_search(all_entries, query) }
    val searching = query.isNotBlank()

    val grid_state = rememberLazyGridState()
    val active_section by remember(section_starts) {
        derivedStateOf {
            emoji_section_for_index(
                starts = section_starts,
                item_index = grid_state.firstVisibleItemIndex,
                at_end = grid_state.firstVisibleItemIndex > 0 && !grid_state.canScrollForward,
            )
        }
    }
    val pick: (String) -> Unit = { glyph ->
        RecentEmojiStore.record(context, glyph)
        scope.launch {
            sheet_state.hide()
            on_pick(glyph)
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
            emoji_tab_row(
                sections = sections,
                active = if (searching) -1 else active_section,
                on_select = { index ->
                    query = ""
                    scope.launch { grid_state.animateScrollToItem(section_starts[index]) }
                },
            )
            emoji_search_field(
                query = query,
                on_query = { query = it },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(emoji_grid_height),
            ) {
                if (searching) {
                    emoji_search_results(results = results, on_pick = pick)
                } else {
                    emoji_section_grid(sections = sections, grid_state = grid_state, on_pick = pick)
                }
            }
        }
    }

    LaunchedEffect(Unit) { sheet_state.show() }
}

@Composable
private fun emoji_tab_row(
    sections: List<emoji_section>,
    active: Int,
    on_select: (Int) -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.xs)
                .testTag("emoji_tab_row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEachIndexed { index, section ->
                val selected = index == active
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { on_select(index) }
                        .testTag("emoji_tab_${section.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = section.title,
                        tint = if (selected) colors.text_primary else colors.text_muted,
                        modifier = Modifier.size(20.dp),
                    )
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .width(18.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(colors.accent_blue),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_primary),
        )
    }
}

@Composable
private fun emoji_search_field(query: String, on_query: (String) -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
            .height(44.dp)
            .clip(CircleShape)
            .background(reaction_chip_palette(is_dark = colors.bg_primary.luminance() < 0.5f).other_fill)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = TablerIcons.Search,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.emoji_search_placeholder),
                    color = colors.text_muted,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = on_query,
                singleLine = true,
                textStyle = TextStyle(color = colors.text_primary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.accent_blue),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("emoji_search_field"),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { on_query("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = stringResource(R.string.clear),
                    tint = colors.text_muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun emoji_section_grid(
    sections: List<emoji_section>,
    grid_state: LazyGridState,
    on_pick: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    LazyVerticalGrid(
        columns = GridCells.Adaptive(emoji_cell_size),
        state = grid_state,
        modifier = Modifier
            .fillMaxSize()
            .testTag("emoji_grid"),
        contentPadding = PaddingValues(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
    ) {
        sections.forEach { section ->
            item(
                key = "header_${section.id}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                Text(
                    text = section.title,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        start = AsterSpacing.sm,
                        top = AsterSpacing.md,
                        bottom = AsterSpacing.xs,
                    ),
                )
            }
            items(
                items = section.entries,
                key = { "${section.id}_${it.glyph}" },
                contentType = { "emoji" },
            ) { entry ->
                emoji_cell(glyph = entry.glyph, on_pick = on_pick)
            }
        }
    }
}

@Composable
private fun emoji_search_results(results: List<emoji_entry>, on_pick: (String) -> Unit) {
    val colors = AsterMaterial.colors
    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("emoji_search_empty"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.emoji_search_no_results),
                color = colors.text_muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(emoji_cell_size),
        modifier = Modifier
            .fillMaxSize()
            .testTag("emoji_search_results"),
        contentPadding = PaddingValues(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
    ) {
        items(
            items = results,
            key = { "result_${it.glyph}" },
            contentType = { "emoji" },
        ) { entry ->
            emoji_cell(glyph = entry.glyph, on_pick = on_pick)
        }
    }
}

@Composable
private fun emoji_cell(glyph: String, on_pick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .height(emoji_cell_size)
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable { on_pick(glyph) }
            .testTag("emoji_cell"),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, fontSize = 28.sp, maxLines = 1)
    }
}
