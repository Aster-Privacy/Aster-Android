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

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.mail.InboxItem
import org.astermail.android.mail.MailViewModel
import org.astermail.android.ui.mail.EmailRow
import org.astermail.android.ui.mail.ThreadInboxRow
import org.astermail.android.ui.mail.ThreadRow
import org.astermail.android.ui.mail.flat_thread_rows
import org.astermail.android.ui.mail.group_by_thread
import org.astermail.android.ui.mail.inbox_card_read_color
import org.astermail.android.ui.mail.inbox_item_to_email
import org.astermail.android.ui.mail.search_field_bg_color

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

internal data class SearchOperator(
    val negated: Boolean,
    val key: String,
    val value: String,
)

internal data class SearchOutcome(
    val results: List<InboxItem>,
    val hidden_spam_trash: Int,
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

private fun matches_item(
    item: InboxItem,
    parsed: ParsedQuery,
    filter: String?,
    apply_scope: Boolean = true,
): Boolean {
    if (apply_scope) {
        if (item.is_spam && !scope_includes_spam(parsed.operators)) return false
        if (item.is_trashed && !scope_includes_trash(parsed.operators)) return false
    }

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
        val hit = item.sender_name.contains(q, ignoreCase = true) ||
            item.sender_email.contains(q, ignoreCase = true) ||
            item.subject.contains(q, ignoreCase = true) ||
            item.preview.contains(q, ignoreCase = true) ||
            item.display_sender_name?.contains(q, ignoreCase = true) == true ||
            item.display_sender_email?.contains(q, ignoreCase = true) == true
        if (!hit) return false
    }

    return true
}

private fun evaluate_operator(item: InboxItem, op: SearchOperator): Boolean {
    val result = when (op.key) {
        "from" -> item.sender_name.contains(op.value, ignoreCase = true) ||
            item.sender_email.contains(op.value, ignoreCase = true) ||
            item.display_sender_name?.contains(op.value, ignoreCase = true) == true ||
            item.display_sender_email?.contains(op.value, ignoreCase = true) == true
        "to" -> item.to_addresses.any { it.contains(op.value, ignoreCase = true) } ||
            item.received_on?.contains(op.value, ignoreCase = true) == true
        "subject" -> item.subject.contains(op.value, ignoreCase = true)
        "has" -> when (op.value) {
            "attachment", "attachments" -> item.has_attachments
            else -> matches_attachment_type(item, op.value)
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
            "all" -> !item.is_trashed && !item.is_spam
            "anywhere" -> true
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

private fun parse_digits(text: String, start: Int, end: Int): Int {
    var acc = 0
    for (i in start until end) {
        val c = text[i]
        if (c < '0' || c > '9') return -1
        acc = acc * 10 + (c - '0')
    }
    return acc
}

private fun days_from_civil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = (y - era * 400).toLong()
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468L
}

private fun parse_date_value(value: String): Long? {
    if (value.length >= 10 && value[4] == '-' && value[7] == '-') {
        val year = parse_digits(value, 0, 4)
        val month = parse_digits(value, 5, 7)
        val day = parse_digits(value, 8, 10)
        if (year >= 0 && month in 1..12 && day in 1..31) {
            return days_from_civil(year, month, day) * 86_400_000L
        }
    }
    val days = value.removeSuffix("d").toIntOrNull()
    return if (days != null) System.currentTimeMillis() - days * 86_400_000L else null
}

private fun parse_item_timestamp(ts: String): Long {
    if (ts.length < 19) return 0L
    val year = parse_digits(ts, 0, 4)
    val month = parse_digits(ts, 5, 7)
    val day = parse_digits(ts, 8, 10)
    val hour = parse_digits(ts, 11, 13)
    val minute = parse_digits(ts, 14, 16)
    val second = parse_digits(ts, 17, 19)
    if (year < 0 || month !in 1..12 || day !in 1..31) return 0L
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return 0L
    return (days_from_civil(year, month, day) * 86_400L + hour * 3600L + minute * 60L + second) * 1000L
}

@Composable
fun SearchScreen(
    on_back: () -> Unit,
    on_open_email: (String) -> Unit,
    initial_query: String = "",
) {
    val colors = AsterMaterial.colors
    val mail_vm: MailViewModel = hiltViewModel()
    val settings_vm: org.astermail.android.settings.SettingsViewModel = org.astermail.android.settings.shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val context_for_prefs = androidx.compose.ui.platform.LocalContext.current
    val search_state by mail_vm.search_state.collectAsStateWithLifecycle()
    val initial_parsed = remember(initial_query) { parse_query(initial_query.trim()) }
    var operator_chips by rememberSaveable(initial_query, stateSaver = operator_chips_saver) {
        mutableStateOf(initial_parsed.operators)
    }
    var field_value by rememberSaveable(initial_query, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = initial_parsed.free_text,
                selection = TextRange(initial_parsed.free_text.length),
            ),
        )
    }
    val free_text = field_value.text
    fun set_free_text(next: String) {
        field_value = TextFieldValue(text = next, selection = TextRange(next.length))
    }
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
    var select_mode by remember { mutableStateOf(false) }
    val selected_ids = remember { mutableStateListOf<String>() }
    var show_selection_overflow by remember { mutableStateOf(false) }
    var show_folder_sheet by remember { mutableStateOf(false) }
    var show_label_sheet by remember { mutableStateOf(false) }
    var show_snooze_sheet by remember { mutableStateOf(false) }
    var advanced_open by remember { mutableStateOf(false) }

    LaunchedEffect(settings_state.preferences?.selection_toolbar_actions) {
        val raw = settings_state.preferences?.selection_toolbar_actions
        if (raw != null) {
            org.astermail.android.ui.mail.cache_selection_toolbar_actions(
                context_for_prefs,
                org.astermail.android.ui.mail.parse_selection_toolbar_actions(raw),
            )
        }
    }
    val selection_toolbar_slots = remember(select_mode, settings_state.preferences?.selection_toolbar_actions) {
        org.astermail.android.ui.mail.load_selection_toolbar_actions(context_for_prefs)
    }

    fun exit_select_mode() {
        select_mode = false
        selected_ids.clear()
    }

    fun toggle_selection(id: String) {
        if (selected_ids.contains(id)) selected_ids.remove(id) else selected_ids.add(id)
        if (selected_ids.isEmpty()) select_mode = false
    }

    BackHandler(enabled = select_mode) { exit_select_mode() }

    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus_manager = androidx.compose.ui.platform.LocalFocusManager.current
    val dismiss_and_back = remember(on_back) {
        {
            focus_manager.clearFocus(force = true)
            keyboard?.hide()
            on_back()
        }
    }

    BackHandler(enabled = !select_mode) { dismiss_and_back() }

    LaunchedEffect(Unit) {
        mail_vm.build_search_index()
    }

    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos {}
        if (free_text.isEmpty() && operator_chips.isEmpty() && active_filter == null) {
            focus_requester.requestFocus()
        }
    }

    val parsed = remember(query) { parse_query(query.trim()) }

    val has_query = query.isNotBlank() || active_filter != null

    val lock_revision by org.astermail.android.folders.folder_lock_store.revision.collectAsState()
    val visible_corpus = remember(search_state.all_items, lock_revision) {
        org.astermail.android.folders.filter_locked_items(search_state.all_items)
    }

    val sorted_corpus by androidx.compose.runtime.produceState<List<org.astermail.android.mail.InboxItem>?>(
        initialValue = null,
        visible_corpus,
    ) {
        value = null
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            visible_corpus
                .map { it to parse_item_timestamp(it.timestamp) }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }

    val computed by androidx.compose.runtime.produceState<SearchOutcome?>(
        initialValue = null,
        parsed, active_filter, sorted_corpus, has_query,
    ) {
        if (!has_query) {
            value = SearchOutcome(emptyList(), 0)
            return@produceState
        }
        val corpus = sorted_corpus
        if (corpus == null) {
            value = null
            return@produceState
        }
        value = null
        if (parsed.free_text.isNotEmpty()) kotlinx.coroutines.delay(120)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val results = mutableListOf<org.astermail.android.mail.InboxItem>()
            var hidden = 0
            for (item in corpus) {
                if (matches_item(item, parsed, active_filter)) {
                    results.add(item)
                    continue
                }
                if ((item.is_spam || item.is_trashed) &&
                    matches_item(item, parsed, active_filter, apply_scope = false)
                ) {
                    hidden++
                }
            }
            SearchOutcome(results, hidden)
        }
    }

    var last_results by remember { mutableStateOf<List<org.astermail.android.mail.InboxItem>>(emptyList()) }
    LaunchedEffect(computed, has_query) {
        val settled = computed
        last_results = if (!has_query) emptyList() else settled?.results ?: last_results
    }
    val corpus_loading = !search_state.is_indexed && search_state.error == null
    val results_pending = has_query && (computed == null || corpus_loading)
    val hidden_spam_trash = computed?.hidden_spam_trash ?: 0
    val chip_people = remember(visible_corpus) {
        collect_chip_people(visible_corpus)
    }
    val chip_recipients = remember(visible_corpus) {
        collect_recipient_people(visible_corpus)
    }
    val custom_chips = remember(operator_chips) {
        operator_chips.filterNot { is_quick_operator(it) }
    }
    val visible_results = computed?.results ?: last_results
    val filtered = remember(visible_results, lock_revision) {
        org.astermail.android.folders.filter_locked_items(visible_results)
    }
    val grouping_enabled = settings_state.preferences?.conversation_grouping != false
    val result_threads = remember(filtered, grouping_enabled) {
        val emails = filtered.map { inbox_item_to_email(it) }
        val rows = if (grouping_enabled) group_by_thread(emails) else flat_thread_rows(emails)
        rows.sortedWith(
            compareByDescending<ThreadRow> { it.newest.received_at }.thenByDescending { it.thread_id },
        )
    }
    val thread_member_ids = remember(result_threads, visible_corpus, grouping_enabled) {
        if (!grouping_enabled) {
            emptyMap()
        } else {
            val by_thread = visible_corpus.groupBy { item ->
                item.thread_token?.takeIf { it.isNotBlank() } ?: item.id
            }
            result_threads.associate { row ->
                row.newest.id to (by_thread[row.thread_id]?.map { it.id } ?: listOf(row.newest.id))
            }
        }
    }
    fun expand_selection(ids: List<String>): List<String> =
        if (!grouping_enabled) ids else ids.flatMap { thread_member_ids[it] ?: listOf(it) }.distinct()

    LaunchedEffect(result_threads, results_pending) {
        if (select_mode && !results_pending) {
            if (result_threads.isEmpty()) {
                exit_select_mode()
            } else {
                val visible = result_threads.map { it.newest.id }.toHashSet()
                selected_ids.removeAll { it !in visible }
            }
        }
    }

    fun mark_read_selected() {
        val target = expand_selection(selected_ids.toList()).toSet()
        val ids = visible_corpus.filter { it.id in target && !it.is_read }.map { it.id }
        if (ids.isNotEmpty()) mail_vm.mark_read_bulk(ids)
        exit_select_mode()
    }

    fun archive_selected() {
        val ids = expand_selection(selected_ids.toList())
        mail_vm.archive(ids, selected_ids.size)
        exit_select_mode()
    }

    fun delete_selected() {
        val ids = expand_selection(selected_ids.toList())
        mail_vm.trash(ids, selected_ids.size)
        exit_select_mode()
    }

    fun mark_unread_selected() {
        val target = expand_selection(selected_ids.toList()).toSet()
        val ids = visible_corpus.filter { it.id in target && it.is_read }.map { it.id }
        if (ids.isNotEmpty()) mail_vm.mark_unread_bulk(ids)
        exit_select_mode()
    }

    fun star_selected() {
        mail_vm.star_bulk(expand_selection(selected_ids.toList()))
        exit_select_mode()
    }

    fun mark_spam_selected() {
        val ids = expand_selection(selected_ids.toList())
        mail_vm.mark_spam(ids, selected_ids.size)
        exit_select_mode()
    }

    fun run_selection_action(action_id: String) {
        when (action_id) {
            "read" -> mark_read_selected()
            "unread" -> mark_unread_selected()
            "trash" -> delete_selected()
            "archive" -> archive_selected()
            "star" -> star_selected()
            "spam" -> mark_spam_selected()
            "folder" -> {
                if (settings_state.labels.isEmpty()) settings_vm.load_labels()
                show_folder_sheet = true
            }
            "label" -> {
                if (settings_state.tags.isEmpty()) settings_vm.load_tags()
                show_label_sheet = true
            }
            "snooze" -> show_snooze_sheet = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .imePadding(),
    ) {
        if (select_mode) {
            search_select_top_bar(
                selected_count = selected_ids.size,
                on_close = ::exit_select_mode,
                on_select_all = {
                    val all_selected = result_threads.isNotEmpty() &&
                        selected_ids.size >= result_threads.size
                    selected_ids.clear()
                    if (all_selected) {
                        exit_select_mode()
                    } else {
                        selected_ids.addAll(result_threads.map { it.newest.id })
                    }
                },
                all_selected = result_threads.isNotEmpty() &&
                    selected_ids.size >= result_threads.size,
            )
        } else {
            search_input_bar(
                field_value = field_value,
                on_field_change = { field_value = it },
                show_placeholder = free_text.isEmpty() && operator_chips.isEmpty(),
                show_clear = free_text.isNotEmpty() || operator_chips.isNotEmpty(),
                on_clear = { set_free_text(""); operator_chips = emptyList() },
                on_back = dismiss_and_back,
                focus_requester = focus_requester,
                active_filter = active_filter,
                on_filter_change = { active_filter = it },
            )
        }

        if (!select_mode) {
            search_chip_row(
                operators = operator_chips,
                people = chip_people,
                recipient_people = chip_recipients,
                on_operators_change = { operator_chips = it },
                on_advanced_click = { advanced_open = true },
            )
        }

        if (custom_chips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                custom_chips.forEach { op ->
                    operator_chip(op) {
                        operator_chips = operator_chips.filterNot { it === op }
                    }
                }
            }
        }

        AsterDivider()

        if (has_query && !results_pending && hidden_spam_trash > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.spam_trash_hidden_notice),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.view_spam_trash_messages),
                    color = colors.accent_blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(SquircleShape(8.dp))
                        .clickable {
                            operator_chips = without_key(operator_chips, "in") +
                                SearchOperator(false, "in", "anywhere")
                        }
                        .padding(horizontal = AsterSpacing.sm, vertical = 4.dp),
                )
            }
        }

        if (has_query && search_state.is_indexing) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.accent_blue,
                trackColor = Color.Transparent,
            )
        }

        val index_progress by mail_vm.search_index_progress.collectAsStateWithLifecycle()
        val index_paused by mail_vm.search_index_paused.collectAsStateWithLifecycle()
        if (has_query && (search_state.is_indexing || index_paused)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AsterSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
                ) {
                    if (!index_paused) {
                        CircularProgressIndicator(
                            color = colors.accent_blue,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        text = stringResource(
                            if (index_paused) R.string.index_download_paused else R.string.decrypting_indexing,
                        ),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
                val progress = index_progress
                if (progress != null && progress.total > 0) {
                    Text(
                        text = stringResource(
                            R.string.message_download_status,
                            progress.indexed,
                            progress.total,
                        ),
                        color = colors.text_muted,
                        fontSize = 12.sp,
                    )
                    if (!index_paused && progress.indexed in 1 until progress.total) {
                        val elapsed = System.currentTimeMillis() - progress.started_at_ms
                        if (elapsed > 2_000L) {
                            val remaining_ms = elapsed.toDouble() / progress.indexed *
                                (progress.total - progress.indexed)
                            val remaining_min = (remaining_ms / 60_000.0).toInt()
                            Text(
                                text = if (remaining_min >= 1) {
                                    stringResource(R.string.index_time_remaining_min, remaining_min)
                                } else {
                                    stringResource(R.string.index_time_remaining_less_min)
                                },
                                color = colors.text_muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(if (index_paused) R.string.resume else R.string.pause),
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(SquircleShape(8.dp))
                        .clickable {
                            if (index_paused) mail_vm.resume_search_indexing()
                            else mail_vm.pause_search_indexing()
                        }
                        .padding(horizontal = AsterSpacing.sm, vertical = 4.dp),
                )
            }
        }

        if (!has_query) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.search_operators),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.md))
                search_tip(stringResource(R.string.op_from), stringResource(R.string.op_from_desc)) { set_free_text("from:"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_to), stringResource(R.string.op_to_desc)) { set_free_text("to:"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_subject), stringResource(R.string.op_subject_desc)) { set_free_text("subject:"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_has_attachment), stringResource(R.string.op_has_attachment_desc)) { set_free_text("has:attachment"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_is_unread), stringResource(R.string.op_is_unread_desc)) { set_free_text("is:unread"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_is_starred), stringResource(R.string.op_is_starred_desc)) { set_free_text("is:starred"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_in_inbox), stringResource(R.string.op_in_inbox_desc)) { set_free_text("in:"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_before), stringResource(R.string.op_before_desc)) { set_free_text("before:"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_after), stringResource(R.string.op_after_desc)) { set_free_text("after:"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_date_today), stringResource(R.string.op_date_today_desc)) { set_free_text("date:"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_label), stringResource(R.string.op_label_desc)) { set_free_text("label:"); focus_requester.requestFocus() }
                search_tip(stringResource(R.string.op_negate), stringResource(R.string.op_negate_desc)) { set_free_text("-from:"); focus_requester.requestFocus() }
                Spacer(Modifier.height(AsterSpacing.lg))
                Text(
                    text = stringResource(R.string.search_privacy_note),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        } else if (result_threads.isEmpty() && results_pending) {
            org.astermail.android.ui.mail.inbox_skeleton(Modifier.weight(1f))
        } else if (result_threads.isEmpty()) {
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
        } else if (result_threads.isNotEmpty()) {
            Text(
                text = pluralStringResource(R.plurals.results_count, result_threads.size, result_threads.size),
                color = colors.text_muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
            )
            search_results_list(
                threads = result_threads,
                select_mode = select_mode,
                selected_ids = selected_ids,
                on_open_email = on_open_email,
                on_toggle_selection = ::toggle_selection,
                on_enter_select_mode = { id ->
                    select_mode = true
                    selected_ids.clear()
                    selected_ids.add(id)
                },
                on_toggle_star = { mail_vm.toggle_star(it) },
                on_set_selection = { ids ->
                    select_mode = true
                    selected_ids.clear()
                    selected_ids.addAll(ids)
                },
                modifier = Modifier.weight(1f),
            )
            if (select_mode) {
                search_select_bottom_bar(
                    selected_count = selected_ids.size,
                    custom_actions = selection_toolbar_slots,
                    on_action = ::run_selection_action,
                    on_more = { show_selection_overflow = true },
                )
            }
        }
    }

    if (show_selection_overflow) {
        org.astermail.android.ui.mail.selection_overflow_sheet(
            on_close = { show_selection_overflow = false },
            on_action = { id ->
                show_selection_overflow = false
                run_selection_action(id)
            },
        )
    }

    if (show_folder_sheet) {
        val unnamed_folder_label = stringResource(R.string.unnamed_folder)
        val folder_decrypt_failed_label = stringResource(R.string.folder_decrypt_failed)
        val folder_items = org.astermail.android.folders.flatten_folder_tree(settings_state.labels)
            .map { node ->
                val label = node.label
                val readable = label.encrypted_name?.takeIf {
                    it.isNotBlank() && !org.astermail.android.looks_encrypted(it)
                }
                label.copy(encrypted_name = readable ?: folder_decrypt_failed_label)
            }
        org.astermail.android.ui.mail.label_picker_sheet(
            title = stringResource(R.string.move_to_folder),
            empty_message = stringResource(R.string.no_folders_yet_create),
            items = folder_items,
            on_close = { show_folder_sheet = false },
            on_pick = { picked ->
                val display = picked.encrypted_name?.takeIf { it.isNotBlank() } ?: unnamed_folder_label
                show_folder_sheet = false
                mail_vm.apply_label_bulk(expand_selection(selected_ids.toList()), picked.label_token, display)
                exit_select_mode()
            },
        )
    }

    if (show_label_sheet) {
        val tag_items = org.astermail.android.labels.tag_rows(settings_state.tags)
        val expanded_selection = expand_selection(selected_ids.toList()).toSet()
        val selected_items = search_state.all_items.filter { it.id in expanded_selection }
        val applied_tags = if (selected_items.isEmpty()) {
            emptySet()
        } else {
            selected_items.map { it.tag_tokens.toSet() }.reduce { acc, tokens -> acc intersect tokens }
        }
        org.astermail.android.ui.mail.tag_picker_sheet(
            title = stringResource(R.string.edit_labels),
            empty_message = stringResource(R.string.no_labels_yet_create),
            items = tag_items,
            on_close = { show_label_sheet = false },
            on_pick = { picked ->
                val display = picked.encrypted_name.takeIf { it.isNotBlank() } ?: picked.tag_token
                show_label_sheet = false
                if (picked.tag_token in applied_tags) {
                    mail_vm.remove_tag_bulk(expanded_selection.toList(), picked.tag_token, display)
                } else {
                    mail_vm.apply_tag_bulk(expanded_selection.toList(), picked.tag_token, display)
                }
                exit_select_mode()
            },
            applied_tokens = applied_tags,
        )
    }

    if (show_snooze_sheet) {
        org.astermail.android.ui.mail.snooze_sheet(
            on_close = { show_snooze_sheet = false },
            on_pick = { iso, label ->
                show_snooze_sheet = false
                mail_vm.snooze_bulk(expand_selection(selected_ids.toList()), iso, label)
                exit_select_mode()
            },
        )
    }

    if (advanced_open) {
        advanced_search_sheet(
            operators = operator_chips,
            free_text = free_text,
            on_apply = { next_operators, next_text ->
                advanced_open = false
                operator_chips = next_operators
                set_free_text(next_text)
            },
            on_dismiss = { advanced_open = false },
        )
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
    val bg = if (selected) {
        colors.accent_blue
    } else if (colors.is_dark) {
        colors.input_bg
    } else {
        colors.bg_secondary
    }
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

@Composable
private fun search_input_bar(
    field_value: TextFieldValue,
    on_field_change: (TextFieldValue) -> Unit,
    show_placeholder: Boolean,
    show_clear: Boolean,
    on_clear: () -> Unit,
    on_back: () -> Unit,
    focus_requester: FocusRequester,
    active_filter: String?,
    on_filter_change: (String?) -> Unit,
) {
    val colors = AsterMaterial.colors
    var filter_menu_open by remember { mutableStateOf(false) }
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
            content_description = stringResource(R.string.back),
            onClick = on_back,
            modifier = Modifier.testTag("back"),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .padding(horizontal = AsterSpacing.sm)
                .clip(SquircleShape(26.dp))
                .background(search_field_bg_color(colors))
                .padding(horizontal = AsterSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (show_clear) {
                Spacer(Modifier.width(28.dp + AsterSpacing.sm))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (show_placeholder) {
                    Text(
                        text = stringResource(R.string.search_mail),
                        color = colors.text_muted,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = field_value,
                    onValueChange = on_field_change,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.text_primary,
                        fontSize = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(colors.accent_blue),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus_requester),
                )
            }
            if (show_clear) {
                Spacer(Modifier.width(AsterSpacing.sm))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(SquircleShape(999.dp))
                        .clickable(onClick = on_clear)
                        .testTag("search_clear"),
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
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(SquircleShape(999.dp))
                    .clickable { filter_menu_open = true }
                    .testTag("search_filter_menu"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Filter,
                    contentDescription = stringResource(R.string.filters),
                    tint = if (active_filter != null) colors.accent_blue else colors.text_secondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            aster_dropdown_menu(
                expanded = filter_menu_open,
                on_dismiss = { filter_menu_open = false },
            ) {
                FILTER_CHIPS.forEach { chip ->
                    val selected = active_filter == chip.key
                    aster_dropdown_item(
                        label = stringResource(chip.label_res),
                        selected = selected,
                        on_click = {
                            filter_menu_open = false
                            on_filter_change(if (selected) null else chip.key)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun search_results_list(
    threads: List<ThreadRow>,
    select_mode: Boolean,
    selected_ids: List<String>,
    on_open_email: (String) -> Unit,
    on_toggle_selection: (String) -> Unit,
    on_enter_select_mode: (String) -> Unit,
    on_toggle_star: (String) -> Unit,
    on_set_selection: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val selected_set = selected_ids.toSet()
    val list_state = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val settings_vm = org.astermail.android.settings.optional_shared_settings_view_model()
    val settings_state = settings_vm?.state?.collectAsStateWithLifecycle()?.value
    val live_select_mode by rememberUpdatedState(select_mode)
    val live_selected_set by rememberUpdatedState(selected_set)
    val live_threads by rememberUpdatedState(threads)
    val live_enter_select_mode by rememberUpdatedState(on_enter_select_mode)
    val live_set_selection by rememberUpdatedState(on_set_selection)
    val id_at_offset: (Float) -> String? = remember(list_state) {
        { y ->
            list_state.layoutInfo.visibleItemsInfo
                .firstOrNull { y >= it.offset && y < it.offset + it.size }
                ?.key as? String
        }
    }
    val drag_anchor_index = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val drag_last_index = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val drag_pre_selected = remember { mutableStateListOf<String>() }
    val drag_pointer_y = remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    val drag_viewport_height = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var drag_selecting by remember { mutableStateOf(false) }

    fun index_at_offset(y: Float): Int? {
        val id = id_at_offset(y) ?: return null
        val idx = live_threads.indexOfFirst { it.newest.id == id }
        return if (idx >= 0) idx else null
    }

    fun apply_drag_selection(y: Float) {
        val anchor = drag_anchor_index.intValue
        if (anchor < 0) return
        val idx = index_at_offset(y) ?: return
        if (idx == drag_last_index.intValue) return
        drag_last_index.intValue = idx
        val target = LinkedHashSet(drag_pre_selected)
        for (i in minOf(anchor, idx)..maxOf(anchor, idx)) {
            live_threads.getOrNull(i)?.newest?.id?.let { target.add(it) }
        }
        if (target != live_selected_set) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            live_set_selection(target.toList())
        }
    }

    val drag_edge_px = with(LocalDensity.current) { 96.dp.toPx() }
    LaunchedEffect(drag_selecting) {
        if (!drag_selecting) return@LaunchedEffect
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val y = drag_pointer_y.floatValue
            val viewport = drag_viewport_height.floatValue
            if (y < 0f || viewport <= 0f) continue
            val ratio = when {
                y < drag_edge_px -> -(1f - (y / drag_edge_px))
                y > viewport - drag_edge_px -> 1f - ((viewport - y) / drag_edge_px)
                else -> 0f
            }.coerceIn(-1f, 1f)
            if (ratio != 0f) {
                list_state.scrollBy(ratio * 26f)
                apply_drag_selection(y)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        state = list_state,
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_results")
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val idx = index_at_offset(offset.y)
                        val id = idx?.let { live_threads.getOrNull(it)?.newest?.id }
                        if (id != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val base = LinkedHashSet(live_selected_set)
                            if (!live_select_mode) {
                                base.clear()
                                live_enter_select_mode(id)
                            }
                            base.remove(id)
                            drag_pre_selected.clear()
                            drag_pre_selected.addAll(base)
                            drag_anchor_index.intValue = idx
                            drag_last_index.intValue = idx
                            drag_viewport_height.floatValue = size.height.toFloat()
                            drag_pointer_y.floatValue = offset.y
                            drag_selecting = true
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        drag_pointer_y.floatValue = change.position.y
                        apply_drag_selection(change.position.y)
                    },
                    onDragEnd = {
                        drag_selecting = false
                        drag_pointer_y.floatValue = -1f
                        drag_anchor_index.intValue = -1
                        drag_last_index.intValue = -1
                        drag_pre_selected.clear()
                    },
                    onDragCancel = {
                        drag_selecting = false
                        drag_pointer_y.floatValue = -1f
                        drag_anchor_index.intValue = -1
                        drag_last_index.intValue = -1
                        drag_pre_selected.clear()
                    },
                )
            },
        contentPadding = PaddingValues(bottom = AsterSpacing.lg),
    ) {
        itemsIndexed(
            items = threads,
            key = { _, thread -> thread.newest.id },
            contentType = { _, _ -> "search_row" },
        ) { index, thread ->
            val row_id = thread.newest.id
            val is_selected = select_mode && selected_set.contains(row_id)
            ThreadInboxRow(
                thread = thread,
                on_click = {
                    if (select_mode) on_toggle_selection(row_id) else on_open_email(row_id)
                },
                on_long_click = {
                    if (select_mode) on_toggle_selection(row_id) else on_enter_select_mode(row_id)
                },
                on_toggle_star = { on_toggle_star(row_id) },
                modifier = Modifier.testTag("search_row_$row_id"),
                is_first = index == 0,
                is_last = index == threads.lastIndex,
                is_selected = is_selected,
                select_mode = select_mode,
                is_pinned = thread.is_pinned,
                user_prefs = settings_state?.preferences,
            )
        }
    }
        org.astermail.android.ui.common.fast_scroll_bar(
            state = list_state,
            modifier = Modifier.align(Alignment.TopEnd),
            bottom_padding = AsterSpacing.lg,
        )
    }
}

@Composable
internal fun search_select_top_bar(
    selected_count: Int,
    on_close: () -> Unit,
    on_select_all: () -> Unit,
    all_selected: Boolean = false,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = AsterSpacing.xs)
            .testTag("search_select_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsterIconButton(
            icon = TablerIcons.X,
            content_description = stringResource(R.string.exit_selection),
            onClick = on_close,
            modifier = Modifier.testTag("search_exit_select"),
        )
        Spacer(Modifier.width(AsterSpacing.xs))
        Text(
            text = if (selected_count == 0) {
                stringResource(R.string.select)
            } else {
                stringResource(R.string.inbox_selected_count, selected_count)
            },
            style = MaterialTheme.typography.titleMedium,
            color = colors.text_primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("search_selected_count"),
        )
        org.astermail.android.ui.common.select_all_button(
            on_click = on_select_all,
            modifier = Modifier.testTag("search_select_all"),
            all_selected = all_selected,
        )
    }
}

@Composable
internal fun search_select_bottom_bar(
    selected_count: Int,
    custom_actions: List<String>,
    on_action: (String) -> Unit,
    on_more: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val enabled = selected_count > 0
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.bg_primary,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column {
            AsterDivider(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                custom_actions.forEach { action_id ->
                    val action = org.astermail.android.ui.mail.selection_toolbar_action_by_id(action_id)
                        ?: return@forEach
                    search_select_action(
                        icon = action.icon,
                        label = stringResource(action.label_res),
                        enabled = enabled,
                        on_click = { on_action(action_id) },
                        tint = if (action_id == "trash" || action_id == "spam") colors.danger else colors.text_primary,
                        test_tag = "search_sel_action_$action_id",
                    )
                }
                search_select_action(
                    icon = TablerIcons.Dots,
                    label = stringResource(R.string.more_actions),
                    enabled = enabled,
                    on_click = on_more,
                    test_tag = "search_sel_action_more",
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.search_select_action(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    on_click: () -> Unit,
    tint: Color = AsterMaterial.colors.text_primary,
    test_tag: String? = null,
) {
    val colors = AsterMaterial.colors
    val resolved_tint = if (enabled) tint else colors.text_muted
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(SquircleShape(18.dp))
            .clickable(enabled = enabled, onClick = on_click)
            .padding(horizontal = 2.dp, vertical = 8.dp)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = resolved_tint,
            modifier = Modifier.size(22.dp),
        )
        var label_size by remember(label) { mutableStateOf(11.sp) }
        Text(
            text = label,
            color = resolved_tint,
            fontSize = label_size,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (result.hasVisualOverflow && label_size.value > 8f) {
                    label_size = (label_size.value - 0.5f).sp
                }
            },
        )
    }
}
