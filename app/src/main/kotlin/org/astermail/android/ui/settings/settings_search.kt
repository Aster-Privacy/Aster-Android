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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.ui.mail.search_field_bg_color

val local_settings_navigator = staticCompositionLocalOf<(String) -> Unit> { {} }

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
    "recovery_key" to TablerIcons.Key,
    "recovery_key_view" to TablerIcons.Key,
    "identity_key" to TablerIcons.Fingerprint,
    "contact_keys" to TablerIcons.Fingerprint,
    "delete_account" to TablerIcons.Trash,
    "privacy" to TablerIcons.ShieldLock,
    "language" to TablerIcons.Language,
    "blocked" to TablerIcons.Ban,
    "allowlist" to TablerIcons.CircleCheck,
    "auto_forward" to TablerIcons.Send,
    "vacation_reply" to TablerIcons.Plane,
    "labels" to TablerIcons.Tag,
    "export" to TablerIcons.Download,
    "api_keys" to TablerIcons.Code,
    "subscriptions" to TablerIcons.Mailbox,
)

@Composable
fun settings_search_action() {
    var open by remember { mutableStateOf(false) }
    AsterIconButton(
        icon = TablerIcons.Search,
        content_description = stringResource(R.string.settings_search_placeholder),
        onClick = { open = true },
    )
    if (open) {
        settings_search_overlay(on_dismiss = { open = false })
    }
}

@Composable
private fun settings_search_overlay(on_dismiss: () -> Unit) {
    val colors = AsterMaterial.colors
    val navigate = local_settings_navigator.current
    var query by remember { mutableStateOf("") }
    val focus_requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus_requester.requestFocus() }
    Dialog(
        onDismissRequest = on_dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg_primary)
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsterIconButton(
                    icon = TablerIcons.ArrowLeft,
                    content_description = stringResource(R.string.back),
                    onClick = on_dismiss,
                )
                settings_search_field(
                    query = query,
                    on_query_change = { query = it },
                    focus_requester = focus_requester,
                    modifier = Modifier.weight(1f),
                )
            }
            AsterDivider()
            settings_search_results(
                query = query.trim(),
                on_open = { id ->
                    query = ""
                    on_dismiss()
                    navigate(id)
                },
            )
        }
    }
}

@Composable
private fun settings_search_field(
    query: String,
    on_query_change: (String) -> Unit,
    focus_requester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .padding(end = AsterSpacing.sm)
            .height(48.dp)
            .clip(SquircleShape(24.dp))
            .background(search_field_bg_color(colors))
            .padding(horizontal = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Search,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_search_placeholder),
                    color = colors.text_tertiary,
                    fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = on_query_change,
                singleLine = true,
                textStyle = TextStyle(color = colors.text_primary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.accent_blue),
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus_requester),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = TablerIcons.X,
                contentDescription = stringResource(R.string.clear),
                tint = colors.text_secondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { on_query_change("") },
            )
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.size(AsterSpacing.md))
        Column(
            modifier = Modifier
                .padding(horizontal = AsterSpacing.md)
                .fillMaxWidth()
                .background(colors.bg_card, SquircleShape(18.dp))
                .border(1.dp, colors.border_secondary, SquircleShape(18.dp)),
        ) {
            matched.forEachIndexed { idx, hit ->
                settings_search_result_row(hit) { on_open(hit.screen_id) }
                if (idx < matched.lastIndex) {
                    AsterDivider(modifier = Modifier.padding(start = 50.dp))
                }
            }
        }
        Spacer(Modifier.size(AsterSpacing.xxl))
    }
}

@Composable
private fun settings_search_message(text: String) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AsterSpacing.xl),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(text = text, color = colors.text_tertiary, fontSize = 14.sp)
    }
}

@Composable
private fun settings_search_result_row(hit: settings_search_match, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = hit.icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.label,
                color = colors.text_primary,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.parent != hit.label) {
                Spacer(Modifier.size(2.dp))
                Text(
                    text = hit.parent,
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
        val haystack = label_lower + " " + parent_lower
        if (!tokens.all { haystack.contains(it) }) continue
        var score = 0
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
