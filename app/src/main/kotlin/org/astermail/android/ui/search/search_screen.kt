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

package org.astermail.android.ui.search

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.mail.InboxItem
import org.astermail.android.mail.MailViewModel
import org.astermail.android.ui.mail.EmailRow
import org.astermail.android.ui.mail.inbox_item_to_email

private data class FilterChip(val key: String, val label_res: Int)

private val FILTER_CHIPS = listOf(
    FilterChip("Has attachment", R.string.filter_has_attachment),
    FilterChip("Unread", R.string.filter_unread),
    FilterChip("Starred", R.string.filter_starred),
    FilterChip("Encrypted", R.string.filter_encrypted),
)

private val OPERATOR_REGEX = Regex("""(-?)(\w+):("([^"]+)"|(\S+))""")

private data class ParsedQuery(
    val free_text: String,
    val operators: List<SearchOperator>,
)

private data class SearchOperator(
    val negated: Boolean,
    val key: String,
    val value: String,
)

private val operator_chips_saver = listSaver<List<SearchOperator>, String>(
    save = { chips ->
        chips.map { "${if (it.negated) "1" else "0"}|${it.key}|${it.value}" }
    },
    restore = { saved ->
        saved.map {
            val parts = it.split("|", limit = 3)
            SearchOperator(parts[0] == "1", parts[1], parts[2])
        }
    },
)

private fun parse_query(raw: String): ParsedQuery {
    val operators = mutableListOf<SearchOperator>()

    OPERATOR_REGEX.findAll(raw).forEach { match ->
        val negated = match.groupValues[1] == "-"
        val key = match.groupValues[2].lowercase()
        val value = (match.groupValues[4].ifEmpty { match.groupValues[5] }).lowercase()
        operators.add(SearchOperator(negated, key, value))
    }
    val remaining = OPERATOR_REGEX.replace(raw, " ")

    return ParsedQuery(
        free_text = remaining.trim().lowercase(),
        operators = operators,
    )
}

private fun matches_item(item: InboxItem, parsed: ParsedQuery, filter: String?): Boolean {
    val filter_ok = when (filter) {
        "Unread" -> !item.is_read
        "Starred" -> item.is_starred
        "Has attachment" -> item.has_attachments
        "Encrypted" -> item.is_encrypted
        else -> true
    }
    if (!filter_ok) return false

    for (op in parsed.operators) {
        val pass = evaluate_operator(item, op)
        if (!pass) return false
    }

    if (parsed.free_text.isNotEmpty()) {
        val q = parsed.free_text
        val in_sender = item.sender_name.lowercase().contains(q) ||
            item.sender_email.lowercase().contains(q)
        val in_subject = item.subject.lowercase().contains(q)
        val in_preview = item.preview.lowercase().contains(q)
        if (!in_sender && !in_subject && !in_preview) return false
    }

    return true
}

private fun evaluate_operator(item: InboxItem, op: SearchOperator): Boolean {
    val result = when (op.key) {
        "from" -> item.sender_name.lowercase().contains(op.value) ||
            item.sender_email.lowercase().contains(op.value)
        "to" -> item.sender_email.lowercase().contains(op.value)
        "subject" -> item.subject.lowercase().contains(op.value)
        "has" -> when (op.value) {
            "attachment", "attachments" -> item.has_attachments
            else -> item.has_attachments
        }
        "is" -> when (op.value) {
            "unread" -> !item.is_read
            "read" -> item.is_read
            "starred" -> item.is_starred
            "unstarred" -> !item.is_starred
            "encrypted" -> item.is_encrypted
            else -> true
        }
        "in" -> when (op.value) {
            "inbox" -> !item.is_trashed && !item.is_archived && !item.is_spam
            "trash" -> item.is_trashed
            "archive", "archived" -> item.is_archived
            "spam" -> item.is_spam
            "starred" -> item.is_starred
            "all" -> true
            else -> true
        }
        "before" -> {
            val target = parse_date_value(op.value)
            if (target != null) parse_item_timestamp(item.timestamp) < target else true
        }
        "after" -> {
            val target = parse_date_value(op.value)
            if (target != null) parse_item_timestamp(item.timestamp) > target else true
        }
        "date" -> {
            val now = System.currentTimeMillis()
            val ts = parse_item_timestamp(item.timestamp)
            when (op.value) {
                "today" -> now - ts < 86_400_000L
                "yesterday" -> (now - ts) in 86_400_000L..172_800_000L
                "this_week" -> now - ts < 7 * 86_400_000L
                "last_week" -> (now - ts) in (7 * 86_400_000L)..(14 * 86_400_000L)
                "this_month" -> now - ts < 30 * 86_400_000L
                else -> true
            }
        }
        "label" -> item.labels.any { it.lowercase().contains(op.value) }
        else -> true
    }
    return if (op.negated) !result else result
}

private fun parse_date_value(value: String): Long? {
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .parse(value)?.time
    } catch (_: Throwable) {
        val days = value.removeSuffix("d").toIntOrNull()
        if (days != null) System.currentTimeMillis() - days * 86_400_000L else null
    }
}

private fun parse_item_timestamp(ts: String): Long {
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        fmt.parse(ts.take(19))?.time ?: 0L
    } catch (_: Throwable) {
        0L
    }
}

@Composable
fun SearchScreen(
    on_back: () -> Unit,
    on_open_email: (String) -> Unit,
    initial_query: String = "",
) {
    val colors = AsterMaterial.colors
    val mail_vm: MailViewModel = hiltViewModel()
    val search_state by mail_vm.search_state.collectAsStateWithLifecycle()
    val initial_parsed = remember(initial_query) { parse_query(initial_query.trim()) }
    var operator_chips by rememberSaveable(initial_query, stateSaver = operator_chips_saver) {
        mutableStateOf(initial_parsed.operators)
    }
    var free_text by rememberSaveable(initial_query) { mutableStateOf(initial_parsed.free_text) }
    val query = remember(operator_chips, free_text) {
        val ops = operator_chips.joinToString(" ") {
            val prefix = if (it.negated) "-" else ""
            val v = if (it.value.contains(' ')) "\"${it.value}\"" else it.value
            "$prefix${it.key}:$v"
        }
        listOf(ops, free_text).filter { it.isNotBlank() }.joinToString(" ")
    }
    var active_filter by rememberSaveable { mutableStateOf<String?>(null) }
    val focus_requester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (free_text.isEmpty() && operator_chips.isEmpty() && active_filter == null) {
            focus_requester.requestFocus()
        }
        mail_vm.build_search_index()
    }

    val parsed = remember(query) { parse_query(query.trim()) }

    val has_query = query.isNotBlank() || active_filter != null

    val filtered by androidx.compose.runtime.produceState(
        initialValue = emptyList<org.astermail.android.mail.InboxItem>(),
        parsed, active_filter, search_state.all_items, has_query,
    ) {
        if (!has_query) {
            value = emptyList()
            return@produceState
        }
        kotlinx.coroutines.delay(120)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            search_state.all_items
                .filter { matches_item(it, parsed, active_filter) }
                .sortedByDescending { parse_item_timestamp(it.timestamp) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = AsterSpacing.xs, end = AsterSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = on_back)
                    .testTag("back"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.text_primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(AsterSpacing.xs))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(colors.bg_secondary, SquircleShape(18.dp))
                    .padding(horizontal = AsterSpacing.md),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TablerIcons.Search,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Box(modifier = Modifier.weight(1f)) {
                        if (free_text.isEmpty() && operator_chips.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_mail),
                                color = colors.text_muted,
                                fontSize = 15.sp,
                            )
                        }
                        BasicTextField(
                            value = free_text,
                            onValueChange = { free_text = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colors.text_primary,
                                fontSize = 15.sp,
                            ),
                            cursorBrush = SolidColor(colors.accent_blue),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus_requester),
                        )
                    }
                    if (free_text.isNotEmpty() || operator_chips.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { free_text = ""; operator_chips = emptyList() }
                                .testTag("search_clear"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TablerIcons.X,
                                contentDescription = stringResource(R.string.clear),
                                tint = colors.text_muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        if (operator_chips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                operator_chips.forEach { op ->
                    operator_chip(op) {
                        operator_chips = operator_chips.filterNot { it === op }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            FILTER_CHIPS.forEach { f ->
                val selected = active_filter == f.key
                search_chip(stringResource(f.label_res), selected) {
                    active_filter = if (selected) null else f.key
                }
            }
        }

        AsterDivider()

        if (has_query && search_state.is_indexing) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.accent_blue,
                trackColor = Color.Transparent,
            )
        }

        if (search_state.is_indexing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AsterSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
                ) {
                    CircularProgressIndicator(
                        color = colors.accent_blue,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.decrypting_indexing),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        if (!has_query) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.search_operators),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.md))
                search_tip(stringResource(R.string.op_from), stringResource(R.string.op_from_desc)) { free_text = "from:"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_to), stringResource(R.string.op_to_desc)) { free_text ="to:"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_subject), stringResource(R.string.op_subject_desc)) { free_text ="subject:"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_has_attachment), stringResource(R.string.op_has_attachment_desc)) { free_text ="has:attachment"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_is_unread), stringResource(R.string.op_is_unread_desc)) { free_text ="is:unread"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_is_starred), stringResource(R.string.op_is_starred_desc)) { free_text ="is:starred"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_in_inbox), stringResource(R.string.op_in_inbox_desc)) { free_text ="in:"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_before), stringResource(R.string.op_before_desc)) { free_text ="before:"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_after), stringResource(R.string.op_after_desc)) { free_text ="after:"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_date_today), stringResource(R.string.op_date_today_desc)) { free_text ="date:"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_label), stringResource(R.string.op_label_desc)) { free_text ="label:"; focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_negate), stringResource(R.string.op_negate_desc)) { free_text ="-from:"; focus_requester.requestFocus() }
                Spacer(Modifier.height(AsterSpacing.lg))
                Text(
                    text = stringResource(R.string.search_privacy_note),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        } else if (filtered.isEmpty() && has_query) {
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
                        text = stringResource(R.string.no_results_found),
                        color = colors.text_muted,
                        fontSize = 15.sp,
                    )
                }
            }
        } else if (filtered.isNotEmpty()) {
            Text(
                text = stringResource(R.string.results_count, filtered.size),
                color = colors.text_muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { item ->
                    val email = remember(item) { inbox_item_to_email(item) }
                    EmailRow(
                        email = email,
                        on_click = { on_open_email(item.id) },
                        on_long_click = {},
                        on_toggle_star = {
                            mail_vm.toggle_star(item.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun search_tip(syntax: String, description: String, on_click: () -> Unit = {}) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(18.dp))
            .clickable(onClick = on_click)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = syntax,
            color = colors.accent_blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(140.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = description,
            color = colors.text_muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun operator_chip(op: SearchOperator, on_remove: () -> Unit) {
    val colors = AsterMaterial.colors
    val label_key = when (op.key) {
        "from" -> "From"
        "to" -> "To"
        "subject" -> "Subject"
        "has" -> "Has"
        "is" -> "Is"
        "in" -> "In"
        "before" -> "Before"
        "after" -> "After"
        "date" -> "Date"
        "label" -> "Label"
        else -> op.key.replaceFirstChar { it.uppercase() }
    }
    val prefix = if (op.negated) "Not " else ""
    Row(
        modifier = Modifier
            .clip(SquircleShape(999.dp))
            .background(colors.accent_blue.copy(alpha = 0.14f))
            .border(1.dp, colors.accent_blue.copy(alpha = 0.35f), SquircleShape(999.dp))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$prefix$label_key: ${op.value}",
            color = colors.accent_blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = on_remove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TablerIcons.X,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun search_chip(text: String, selected: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val bg = if (selected) colors.accent_blue else Color.Transparent
    val border_color = if (selected) colors.accent_blue else colors.border_secondary
    val text_color = if (selected) Color.White else colors.text_secondary
    val animated_bg by animateColorAsState(
        targetValue = bg,
        animationSpec = tween(150),
        label = "chip_bg",
    )
    val animated_text by animateColorAsState(
        targetValue = text_color,
        animationSpec = tween(150),
        label = "chip_text",
    )
    Box(
        modifier = Modifier
            .clip(SquircleShape(999.dp))
            .background(animated_bg)
            .border(1.dp, border_color, SquircleShape(999.dp))
            .clickable(onClick = on_click)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = animated_text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
