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

package org.astermail.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Ban
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Code
import compose.icons.tablericons.DeviceLaptop
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.Download
import compose.icons.tablericons.Fingerprint
import compose.icons.tablericons.Key
import compose.icons.tablericons.Language
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Mail
import compose.icons.tablericons.Mailbox
import compose.icons.tablericons.Plane
import compose.icons.tablericons.Search
import compose.icons.tablericons.Send
import compose.icons.tablericons.Settings
import compose.icons.tablericons.ShieldLock
import compose.icons.tablericons.Tag
import compose.icons.tablericons.Trash
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.auto_mirrored
import org.astermail.android.design.acrylic
import org.astermail.android.design.acrylic_backdrop
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.settings.optional_shared_settings_view_model
import org.astermail.android.ui.common.page_surface
import org.astermail.android.ui.mail.inbox_card_content_padding
import org.astermail.android.ui.mail.inbox_card_horizontal_margin
import org.astermail.android.ui.mail.inbox_card_read_color
import org.astermail.android.ui.mail.inbox_group_shape
import org.astermail.android.ui.mail.inbox_group_split
import org.astermail.android.ui.mail.inbox_preview_color
import org.astermail.android.ui.mail.inbox_preview_text_style
import org.astermail.android.ui.mail.inbox_row_metrics
import org.astermail.android.ui.mail.inbox_sender_color
import org.astermail.android.ui.mail.inbox_sender_text_style
import org.astermail.android.ui.mail.search_field_bg_color

val local_settings_search_opener = staticCompositionLocalOf<() -> Unit> { {} }

private data class settings_search_match(
    val screen_id: String,
    val label: String,
    val parent: String,
    val icon: ImageVector,
    val score: Int,
)

private data class settings_search_row_text(
    val entry: settings_index_entry,
    val label: String,
    val parent: String,
)

private val extra_screen_icons = mapOf(
    "change_password" to TablerIcons.Lock,
    "password" to TablerIcons.Lock,
    "two_factor" to TablerIcons.DeviceMobile,
    "sessions" to TablerIcons.DeviceLaptop,
    "recovery_email" to TablerIcons.Mail,
    "recovery_codes" to TablerIcons.Key,
    "identity_key" to TablerIcons.Fingerprint,
    "contact_keys" to TablerIcons.Fingerprint,
    "delete_account" to TablerIcons.Trash,
    "privacy" to TablerIcons.ShieldLock,
    "language" to TablerIcons.Language,
    "blocked" to TablerIcons.Ban,
    "allowlist" to TablerIcons.CircleCheck,
    "auto_forward" to TablerIcons.Send.auto_mirrored(),
    "vacation_reply" to TablerIcons.Plane,
    "labels" to TablerIcons.Tag,
    "export" to TablerIcons.Download,
    "api_keys" to TablerIcons.Code,
    "subscriptions" to TablerIcons.Mailbox,
)

@Composable
fun settings_search_action() {
    val open_search = local_settings_search_opener.current
    AsterIconButton(
        icon = TablerIcons.Search,
        content_description = stringResource(R.string.settings_search_placeholder),
        onClick = open_search,
    )
}

@Composable
fun settings_search_screen(
    on_back: () -> Unit,
    on_open: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var query by rememberSaveable { mutableStateOf("") }
    val focus_requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus_manager = LocalFocusManager.current
    val dismiss_and_back = remember(on_back) {
        {
            focus_manager.clearFocus(force = true)
            keyboard?.hide()
            on_back()
        }
    }
    BackHandler { dismiss_and_back() }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos {}
        if (query.isEmpty()) focus_requester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors)
            .systemBarsPadding()
            .imePadding(),
    ) {
        settings_search_input_bar(
            query = query,
            on_query_change = { query = it },
            on_back = dismiss_and_back,
            focus_requester = focus_requester,
        )
        AsterDivider()
        settings_search_results(
            query = query.trim(),
            on_open = { id ->
                focus_manager.clearFocus(force = true)
                keyboard?.hide()
                on_open(id)
            },
        )
    }
}

@Composable
private fun settings_search_input_bar(
    query: String,
    on_query_change: (String) -> Unit,
    on_back: () -> Unit,
    focus_requester: FocusRequester,
) {
    val colors = AsterMaterial.colors
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.sm)
            .padding(top = AsterSpacing.sm, bottom = AsterSpacing.xs)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsterIconButton(
            icon = TablerIcons.ArrowLeft,
            auto_mirror = true,
            content_description = stringResource(R.string.back),
            onClick = on_back,
            modifier = Modifier.testTag("back"),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .padding(horizontal = AsterSpacing.sm)
                .acrylic(colors, SquircleShape(26.dp), search_field_bg_color(colors))
                .padding(horizontal = AsterSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(28.dp + AsterSpacing.sm))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_search_placeholder),
                        color = colors.text_muted,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = on_query_change,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.text_primary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(colors.accent_blue),
                    keyboardOptions = KeyboardOptions(
                        autoCorrect = false,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus_requester),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(AsterSpacing.sm))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(SquircleShape(999.dp))
                        .clickable { on_query_change("") }
                        .testTag("settings_search_clear"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.X,
                        contentDescription = stringResource(R.string.clear),
                        tint = colors.text_secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun settings_search_results(query: String, on_open: (String) -> Unit) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val icons = remember {
        build_settings_sections(is_family = true).flatMap { it.rows }.associate { it.id to it.icon }
    }
    val table = remember(configuration) {
        settings_search_index.map { entry ->
            settings_search_row_text(
                entry = entry,
                label = context.getString(entry.label_res),
                parent = context.getString(entry.screen_title_res),
            )
        }
    }

    if (query.isEmpty()) {
        settings_search_message(stringResource(R.string.settings_search_hint))
        return
    }

    val matched = remember(query, table) { rank_settings_matches(query, table, icons) }

    if (matched.isEmpty()) {
        settings_search_message(stringResource(R.string.no_results_found))
        return
    }

    val settings_vm = optional_shared_settings_view_model()
    val settings_state = settings_vm?.state?.collectAsStateWithLifecycle()?.value
    val row_density = settings_state?.preferences?.mail_list_density
    val metrics = remember(row_density) { inbox_row_metrics(row_density) }

    Text(
        text = pluralStringResource(R.plurals.search_results_count, matched.size, matched.size),
        color = colors.text_muted,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
    )
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_search_results"),
        contentPadding = PaddingValues(top = AsterSpacing.sm, bottom = AsterSpacing.lg),
    ) {
        itemsIndexed(
            items = matched,
            key = { _, hit -> hit.screen_id + "|" + hit.label },
            contentType = { _, _ -> "settings_search_row" },
        ) { index, hit ->
            settings_search_result_row(
                hit = hit,
                is_first = index == 0,
                is_last = index == matched.lastIndex,
                min_height = metrics.min_height,
                vertical_padding = metrics.vertical_padding,
                on_click = { on_open(hit.screen_id) },
            )
        }
    }
}

@Composable
private fun settings_search_message(text: String) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AsterSpacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = TablerIcons.Search,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(AsterSpacing.md))
            Text(
                text = text,
                color = colors.text_muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun settings_search_result_row(
    hit: settings_search_match,
    is_first: Boolean,
    is_last: Boolean,
    min_height: androidx.compose.ui.unit.Dp,
    vertical_padding: androidx.compose.ui.unit.Dp,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val group_shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val card_color = inbox_card_read_color(colors)
    val interaction_source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(group_shape)
            .acrylic_backdrop(colors)
            .drawBehind { drawRect(card_color) }
            .testTag("settings_search_row_${hit.screen_id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction_source,
                    indication = ripple(),
                    onClick = on_click,
                )
                .defaultMinSize(minHeight = min_height)
                .padding(
                    start = inbox_card_content_padding,
                    end = inbox_card_content_padding,
                    top = vertical_padding,
                    bottom = vertical_padding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = hit.icon,
                contentDescription = null,
                tint = colors.text_secondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hit.label,
                    style = inbox_sender_text_style(),
                    color = inbox_sender_color(colors, unread = true),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hit.parent != hit.label) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = hit.parent,
                        style = inbox_preview_text_style(),
                        color = inbox_preview_color(colors, unread = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun settings_keyword_score(keywords: List<String>, needle: String, last_token: String): Int {
    if (keywords.isEmpty()) return 0
    if (keywords.any { it == needle }) return 130
    val prefix_hit = keywords.any { keyword ->
        keyword.startsWith(needle) && keyword.substringAfterLast(' ').startsWith(last_token)
    }
    return if (prefix_hit) 95 else 0
}

private fun rank_settings_matches(
    query: String,
    table: List<settings_search_row_text>,
    icons: Map<String, ImageVector>,
): List<settings_search_match> {
    val needle = query.lowercase()
    val tokens = needle.split(' ', '\t').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    val results = mutableListOf<settings_search_match>()
    for (row in table) {
        val label_lower = row.label.lowercase()
        val parent_lower = row.parent.lowercase()
        val keywords = row.entry.keywords
        val haystack = (listOf(label_lower, parent_lower) + keywords).joinToString(" ")
        if (!tokens.all { haystack.contains(it) }) continue
        var score = settings_keyword_score(keywords, needle, tokens.last())
        if (label_lower == needle) score += 120
        if (label_lower.startsWith(needle)) score += 60
        if (label_lower.contains(needle)) score += 30
        if (label_lower.split(' ').any { it.startsWith(tokens.first()) }) score += 25
        if (row.entry.is_screen_title) score += 20
        if (parent_lower.contains(needle)) score += 8
        score -= (row.label.length / 12).coerceAtMost(6)
        results += settings_search_match(
            screen_id = row.entry.screen_id,
            label = row.label,
            parent = row.parent,
            icon = icons[row.entry.screen_id]
                ?: extra_screen_icons[row.entry.screen_id]
                ?: TablerIcons.Settings,
            score = score,
        )
    }
    val per_screen = mutableMapOf<String, Int>()
    return results
        .sortedWith(compareByDescending<settings_search_match> { it.score }.thenBy { it.label.length })
        .distinctBy { it.screen_id + "|" + it.label.lowercase() }
        .filter { hit ->
            val used = per_screen[hit.screen_id] ?: 0
            if (used >= 3) {
                false
            } else {
                per_screen[hit.screen_id] = used + 1
                true
            }
        }
        .take(40)
}
