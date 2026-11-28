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

import org.astermail.android.ui.common.vertical_scroll_with_indicator
import org.astermail.android.ui.common.lazy_top_fade
import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu
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
import org.astermail.android.ui.common.glass_bar
import org.astermail.android.ui.common.page_surface
import org.astermail.android.design.acrylic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

private data class FilterToggle(val key: String, val value: String, val label_res: Int)

private val FILTER_TOGGLES = listOf(
    FilterToggle("has", "attachment", R.string.filter_has_attachment),
    FilterToggle("is", "unread", R.string.filter_unread),
    FilterToggle("is", "starred", R.string.filter_starred),
    FilterToggle("is", "encrypted", R.string.filter_encrypted),
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
        saved.mapNotNull {
            val parts = it.split("|", limit = 3)
            if (parts.size < 3) null else SearchOperator(parts[0] == "1", parts[1], parts[2])
        }
    },
)

private const val BODY_HITS_TIMEOUT_MS = 2_000L

private const val BODY_INDEX_STEP_SIZE = 100
private const val CORPUS_SETTLE_MS = 400L

private const val SEARCH_READ_FADE_SCALE = 0.5f

private enum class SearchBodyKind { tips, loading, empty, results }

private const val SAME_QUERY_REFRESH_SETTLE_MS = 180L

internal fun clear_search_screen_cache() {
    search_screen_cache.clear()
}

internal fun same_result_items(
    a: List<org.astermail.android.mail.InboxItem>,
    b: List<org.astermail.android.mail.InboxItem>,
): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (a[i] !== b[i]) return false
    }
    return true
}

internal fun body_index_step(indexed: Int?): Int =
    if (indexed == null) -1 else indexed.coerceAtLeast(0) / BODY_INDEX_STEP_SIZE

internal fun body_index_fraction(indexed: Int, total: Int): Float =
    if (total <= 0) 0f else (indexed.toFloat() / total).coerceIn(0f, 1f)

private class search_filtered_results(
    val items: List<org.astermail.android.mail.InboxItem>,
    val ids: Set<String>,
)

private class search_corpus_lookup(
    val by_id: Map<String, org.astermail.android.mail.InboxItem>,
    val thread_index: Map<String, List<String>>,
    val trashed_ids: Set<String>,
)

private class search_selection_flags(
    val all_starred: Boolean,
    val all_read: Boolean,
    val delete_plan: org.astermail.android.mail.SelectionDeletePlan,
)

private val EMPTY_FILTERED = search_filtered_results(emptyList(), emptySet())

private fun search_selection_flags_of(
    selection: List<String>,
    expand: (List<String>) -> List<String>,
    lookup: search_corpus_lookup?,
    shown_ids: Set<String>,
): search_selection_flags {
    if (selection.isEmpty() || lookup == null) {
        return search_selection_flags(
            all_starred = false,
            all_read = false,
            delete_plan = org.astermail.android.mail.plan_selection_delete(emptyMap(), emptySet(), emptySet()),
        )
    }
    val picked = expand(selection).mapNotNull { lookup.by_id[it] }
    return search_selection_flags(
        all_starred = picked.isNotEmpty() && picked.all { it.is_starred },
        all_read = picked.isNotEmpty() && picked.all { it.is_read },
        delete_plan = org.astermail.android.mail.plan_selection_delete(
            rows = selection.associateWith { expand(listOf(it)) },
            shown_ids = shown_ids,
            trashed_ids = lookup.trashed_ids,
        ),
    )
}

private object search_screen_cache {
    var source: List<org.astermail.android.mail.InboxItem>? = null
    var lock_revision: Long = Long.MIN_VALUE
    var visible: List<org.astermail.android.mail.InboxItem>? = null
    var corpus: List<org.astermail.android.mail.InboxItem>? = null
    var sorted: List<org.astermail.android.mail.InboxItem>? = null
    var parsed: ParsedQuery? = null
    var env_labels: SearchLabelIndex? = null
    var outcome: SearchOutcome? = null
    var outcome_corpus: List<org.astermail.android.mail.InboxItem>? = null
    var outcome_day: Long = Long.MIN_VALUE
    var outcome_body_step: Int = Int.MIN_VALUE
    var people_corpus: List<org.astermail.android.mail.InboxItem>? = null
    var threads_source: List<org.astermail.android.mail.InboxItem>? = null
    var threads_grouping: Boolean = true
    var threads_tags: List<*>? = null
    var threads: List<ThreadRow> = emptyList()
    var people: ChipPeopleLists? = null
    var filtered_source: List<org.astermail.android.mail.InboxItem>? = null
    var filtered_revision: Long = Long.MIN_VALUE
    var filtered: search_filtered_results? = null
    var lookup_corpus: List<org.astermail.android.mail.InboxItem>? = null
    var lookup: search_corpus_lookup? = null

    fun clear() {
        source = null
        lock_revision = Long.MIN_VALUE
        visible = null
        corpus = null
        sorted = null
        parsed = null
        env_labels = null
        outcome = null
        outcome_corpus = null
        outcome_day = Long.MIN_VALUE
        outcome_body_step = Int.MIN_VALUE
        people_corpus = null
        threads_source = null
        threads_grouping = true
        threads_tags = null
        threads = emptyList()
        people = null
        filtered_source = null
        filtered_revision = Long.MIN_VALUE
        filtered = null
        lookup_corpus = null
        lookup = null
    }

    fun filtered_for(source: List<org.astermail.android.mail.InboxItem>, revision: Long) =
        filtered.takeIf { filtered_source === source && filtered_revision == revision }

    fun lookup_for(corpus: List<org.astermail.android.mail.InboxItem>) =
        lookup.takeIf { lookup_corpus === corpus }

    fun sorted_for(corpus: List<org.astermail.android.mail.InboxItem>) =
        sorted.takeIf { this.corpus === corpus }

    fun people_for(corpus: List<org.astermail.android.mail.InboxItem>) =
        people.takeIf { people_corpus === corpus }

    fun threads_for(source: List<org.astermail.android.mail.InboxItem>, grouping: Boolean, tags: List<*>) =
        threads.takeIf { threads_source === source && threads_grouping == grouping && threads_tags == tags }

    fun outcome_for(
        parsed: ParsedQuery,
        labels: SearchLabelIndex,
        sorted: List<org.astermail.android.mail.InboxItem>?,
        day: Long = search_local_day(),
    ) = outcome.takeIf {
        sorted != null && outcome_corpus === sorted && this.parsed == parsed && env_labels == labels &&
            outcome_day == day
    }
}

@Composable
fun SearchScreen(
    on_back: () -> Unit,
    on_open_email: (String) -> Unit,
    on_open_draft: (String) -> Unit = on_open_email,
    initial_query: String = "",
    origin_folder: String? = null,
    mail_vm: MailViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
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
    fun apply_field_change(next: TextFieldValue) {
        val lifted = lift_operators(next.text, submit = false)
        if (lifted.operators.isEmpty()) {
            field_value = next
            return
        }
        operator_chips = merge_lifted(operator_chips, lifted.operators)
        set_free_text(lifted.text)
    }
    fun submit_typed_operators() {
        val lifted = lift_operators(free_text, submit = true)
        if (lifted.operators.isEmpty()) return
        operator_chips = merge_lifted(operator_chips, lifted.operators)
        set_free_text(lifted.text.trim())
    }
    fun apply_chip_operators(next: List<SearchOperator>) {
        val typed = parse_query(free_text)
        val pending = pending_typed_operators(free_text)
        operator_chips = next
        set_free_text(pending_operator_text(pending, typed.free_text))
    }
    val focus_requester = remember { FocusRequester() }
    var select_mode by remember { mutableStateOf(false) }
    val selected_ids = remember { mutableStateListOf<String>() }
    var show_selection_overflow by remember { mutableStateOf(false) }
    var show_folder_sheet by remember { mutableStateOf(false) }
    var show_label_sheet by remember { mutableStateOf(false) }
    var show_snooze_sheet by remember { mutableStateOf(false) }
    var advanced_open by remember { mutableStateOf(false) }
    var pending_permanent_ids by remember { mutableStateOf<List<String>>(emptyList()) }

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

    val sheet_open = show_folder_sheet ||
        show_label_sheet ||
        show_snooze_sheet ||
        advanced_open ||
        show_selection_overflow

    fun close_open_sheets() {
        show_folder_sheet = false
        show_label_sheet = false
        show_snooze_sheet = false
        advanced_open = false
        show_selection_overflow = false
    }

    BackHandler(enabled = sheet_open) { close_open_sheets() }

    key(select_mode) {
        if (select_mode) {
            BackHandler(enabled = !sheet_open) { exit_select_mode() }
        }
    }

    val reduce_motion = org.astermail.android.design.aster_reduce_motion()

    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus_manager = androidx.compose.ui.platform.LocalFocusManager.current
    fun dismiss_keyboard() {
        focus_manager.clearFocus(force = true)
        keyboard?.hide()
    }

    val dismiss_and_back = remember(on_back) {
        {
            focus_manager.clearFocus(force = true)
            keyboard?.hide()
            on_back()
        }
    }

    LaunchedEffect(Unit) {
        mail_vm.build_search_index()
    }

    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos {}
        if (free_text.isEmpty() && operator_chips.isEmpty()) {
            runCatching { focus_requester.requestFocus() }
        }
    }

    val parsed = remember(operator_chips, free_text) { combined_query(operator_chips, free_text) }

    val has_query = has_search_query(parsed)

    val label_index = remember(settings_state.labels, settings_state.tags) {
        SearchLabelIndex(
            settings_state.labels.mapNotNull { label ->
                org.astermail.android.labels.readable_label_name(label)
                    ?.let { SearchLabelEntry(it, label.label_token, is_folder = true) }
            } + settings_state.tags
                .filter { org.astermail.android.labels.tag_name_readable(it) }
                .map { SearchLabelEntry(it.encrypted_name, it.tag_token) },
        )
    }
    val uses_tags = parsed.operators.any { it.key == "label" }
    val uses_labels = uses_tags || parsed.operators.any { it.key == "folder" }
    LaunchedEffect(uses_labels, uses_tags) {
        if (uses_labels && settings_state.labels.isEmpty()) settings_vm.load_labels()
        if (uses_tags && settings_state.tags.isEmpty()) settings_vm.load_tags()
    }
    val search_labels = if (uses_labels) label_index else NO_SEARCH_LABELS

    var corpus_folders_requested by remember { mutableStateOf(false) }
    LaunchedEffect(has_query) {
        if (has_query && !corpus_folders_requested) {
            corpus_folders_requested = true
            org.astermail.android.mail.search_corpus_folders.forEach { mail_vm.load_search_folder(it) }
        }
    }

    val shown_operators = remember(operator_chips, free_text) {
        chip_visible_operators(operator_chips, free_text)
    }

    val lock_revision by org.astermail.android.folders.folder_lock_store.revision.collectAsState()
    val visible_corpus = remember(search_state.all_items, lock_revision) {
        val source = search_state.all_items
        val revision = lock_revision.toLong()
        search_screen_cache.visible
            ?.takeIf { search_screen_cache.source === source && search_screen_cache.lock_revision == revision }
            ?: org.astermail.android.folders.filter_locked_items(source).also {
                search_screen_cache.source = source
                search_screen_cache.lock_revision = revision
                search_screen_cache.visible = it
            }
    }

    val sorted_corpus by androidx.compose.runtime.produceState<List<org.astermail.android.mail.InboxItem>?>(
        initialValue = search_screen_cache.sorted_for(visible_corpus),
        visible_corpus,
    ) {
        search_screen_cache.sorted_for(visible_corpus)?.let {
            value = it
            return@produceState
        }
        if (value != null) kotlinx.coroutines.delay(CORPUS_SETTLE_MS)
        val sorted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            visible_corpus
                .map { it to parse_item_timestamp(it.timestamp) }
                .sortedByDescending { it.second }
                .map { it.first }
        }
        search_screen_cache.corpus = visible_corpus
        search_screen_cache.sorted = sorted
        value = sorted
    }

    val body_step_flow = remember(mail_vm) {
        mail_vm.body_index_progress
            .map { body_index_step(it?.indexed) }
            .distinctUntilChanged()
    }
    val body_step by body_step_flow.collectAsStateWithLifecycle(
        initialValue = body_index_step(mail_vm.body_index_progress.value?.indexed),
    )

    val query_body_step = if (body_search_terms(parsed).isEmpty()) -1 else body_step
    var search_running by remember { mutableStateOf(false) }
    val results_scrolling = remember { mutableStateOf(false) }
    val computed by androidx.compose.runtime.produceState<SearchOutcome?>(
        initialValue = search_screen_cache.outcome_for(parsed, search_labels, sorted_corpus),
        parsed, search_labels, sorted_corpus, has_query, query_body_step,
    ) {
        if (!has_query) {
            search_running = false
            value = null
            return@produceState
        }
        val corpus = sorted_corpus
        if (corpus == null) {
            search_running = false
            value = null
            return@produceState
        }
        val body_terms = body_search_terms(parsed)
        val body_current = body_terms.isEmpty() || search_screen_cache.outcome_body_step == query_body_step
        search_screen_cache.outcome_for(parsed, search_labels, corpus)?.takeIf { body_current }?.let {
            search_running = false
            value = it
            return@produceState
        }
        val same_query = search_screen_cache.parsed == parsed && search_screen_cache.env_labels == search_labels
        search_running = !same_query
        if (same_query && search_screen_cache.outcome != null) {
            androidx.compose.runtime.snapshotFlow { results_scrolling.value }.first { !it }
            kotlinx.coroutines.delay(SAME_QUERY_REFRESH_SETTLE_MS)
        } else if (parsed.terms.isNotEmpty()) {
            kotlinx.coroutines.delay(120)
        }
        val base_env = current_search_env(search_labels).let { env ->
            if (body_terms.isEmpty()) {
                env
            } else {
                val hits = kotlinx.coroutines.withTimeoutOrNull(BODY_HITS_TIMEOUT_MS) {
                    mail_vm.search_body_hits(body_terms)
                }
                env.copy(body_hits = hits ?: emptyMap())
            }
        }
        val env = if (parsed.operators.any { needs_attachment_details(it) }) {
            val untyped = parsed.copy(operators = parsed.operators.filterNot { needs_attachment_details(it) })
            val candidate_ids = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                corpus.asSequence()
                    .filter { it.has_attachments && matches_item(it, untyped, apply_scope = false, env = base_env) }
                    .take(ATTACHMENT_TYPE_SCAN_LIMIT)
                    .map { it.id }
                    .toList()
            }
            base_env.copy(attachments = mail_vm.search_attachment_types(candidate_ids))
        } else {
            base_env
        }
        val env_day = search_local_day(env.now, env.zone)
        val outcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val results = mutableListOf<org.astermail.android.mail.InboxItem>()
            var hidden = 0
            for (item in corpus) {
                if (matches_item(item, parsed, env = env)) {
                    results.add(item)
                    continue
                }
                if ((item.is_spam || item.is_trashed) &&
                    matches_item(item, parsed, apply_scope = false, env = env)
                ) {
                    hidden++
                }
            }
            val previous = search_screen_cache.outcome
            if (same_query && previous != null && previous.hidden_spam_trash == hidden &&
                same_result_items(previous.results, results)
            ) {
                previous
            } else {
                SearchOutcome(results, hidden)
            }
        }
        search_screen_cache.parsed = parsed
        search_screen_cache.env_labels = search_labels
        search_screen_cache.outcome = outcome
        search_screen_cache.outcome_corpus = corpus
        search_screen_cache.outcome_day = env_day
        search_screen_cache.outcome_body_step = query_body_step
        value = outcome
        search_running = false
    }

    var last_results by remember {
        mutableStateOf(
            search_screen_cache.outcome
                ?.takeIf {
                    search_screen_cache.parsed == parsed && search_screen_cache.env_labels == search_labels &&
                        search_screen_cache.outcome_day == search_local_day()
                }
                ?.results
                ?: emptyList(),
        )
    }
    LaunchedEffect(computed, has_query) {
        val settled = computed
        last_results = if (!has_query) emptyList() else settled?.results ?: last_results
    }
    val corpus_loading = !search_state.is_indexed && search_state.error == null
    val results_pending = has_query && (computed == null || corpus_loading)
    val hidden_spam_trash = computed?.hidden_spam_trash ?: 0
    val produced_people by androidx.compose.runtime.produceState<Pair<List<org.astermail.android.mail.InboxItem>, ChipPeopleLists>?>(
        initialValue = search_screen_cache.people_for(visible_corpus)?.let { visible_corpus to it },
        visible_corpus,
    ) {
        search_screen_cache.people_for(visible_corpus)?.let {
            value = visible_corpus to it
            return@produceState
        }
        val people = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            collect_chip_people(visible_corpus) to collect_recipient_people(visible_corpus)
        }
        search_screen_cache.people_corpus = visible_corpus
        search_screen_cache.people = people
        value = visible_corpus to people
    }
    val people_lists = chip_people_for(visible_corpus, produced_people)
    val chip_people = people_lists.first
    val chip_recipients = people_lists.second
    val visible_results = computed?.results ?: last_results
    val filtered_state by androidx.compose.runtime.produceState(
        initialValue = search_screen_cache.filtered_for(visible_results, lock_revision.toLong())
            ?.let { visible_results to it }
            ?: (null to EMPTY_FILTERED),
        visible_results, lock_revision,
    ) {
        val revision = lock_revision.toLong()
        search_screen_cache.filtered_for(visible_results, revision)?.let {
            value = visible_results to it
            return@produceState
        }
        val source = visible_results
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val items = org.astermail.android.folders.filter_locked_items(source)
            search_filtered_results(items, items.mapTo(HashSet(items.size)) { it.id })
        }
        search_screen_cache.filtered_source = source
        search_screen_cache.filtered_revision = revision
        search_screen_cache.filtered = result
        value = source to result
    }
    val filtered = filtered_state.second.items
    val filtered_ids = filtered_state.second.ids
    val filtered_settled = filtered_state.first === visible_results
    val grouping_enabled = settings_state.preferences?.conversation_grouping != false
    val result_tags = settings_state.tags
    val threads_state by androidx.compose.runtime.produceState(
        initialValue = search_screen_cache.threads_for(filtered, grouping_enabled, result_tags)
            ?.let { filtered to it }
            ?: (null to emptyList()),
        filtered, grouping_enabled, result_tags,
    ) {
        search_screen_cache.threads_for(filtered, grouping_enabled, result_tags)?.let {
            value = filtered to it
            return@produceState
        }
        val source = filtered
        val threads = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            search_result_threads(source, grouping_enabled, context_for_prefs, result_tags)
        }
        search_screen_cache.threads_source = source
        search_screen_cache.threads_grouping = grouping_enabled
        search_screen_cache.threads_tags = result_tags
        search_screen_cache.threads = threads
        value = source to threads
    }
    val result_threads = threads_state.second
    val threads_settled = threads_state.first === filtered
    val threads_catching_up = !threads_settled && filtered.isNotEmpty()
    val corpus_lookup by androidx.compose.runtime.produceState<search_corpus_lookup?>(
        initialValue = search_screen_cache.lookup_for(visible_corpus),
        visible_corpus,
    ) {
        search_screen_cache.lookup_for(visible_corpus)?.let {
            value = it
            return@produceState
        }
        val corpus = visible_corpus
        val lookup = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            search_corpus_lookup(
                by_id = corpus.associateBy { it.id },
                thread_index = search_thread_index(corpus),
                trashed_ids = corpus.asSequence().filter { it.is_trashed }.mapTo(HashSet()) { it.id },
            )
        }
        search_screen_cache.lookup_corpus = corpus
        search_screen_cache.lookup = lookup
        value = lookup
    }
    val member_ids_state by androidx.compose.runtime.produceState<Pair<List<ThreadRow>?, Map<String, List<String>>>>(
        initialValue = null to emptyMap(),
        result_threads, corpus_lookup, grouping_enabled,
    ) {
        val index = corpus_lookup?.thread_index
        val threads = result_threads
        if (!grouping_enabled) {
            value = threads to emptyMap()
            return@produceState
        }
        if (index == null) return@produceState
        val members = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            thread_member_ids_from_index(threads, index)
        }
        value = threads to members
    }
    val thread_member_ids = member_ids_state.second
    val members_settled = member_ids_state.first === result_threads
    var shown_query by remember { mutableStateOf<ParsedQuery?>(null) }
    LaunchedEffect(parsed, filtered_settled, threads_settled, search_running) {
        if (!search_running && filtered_settled && threads_settled) shown_query = parsed
    }
    val query_catching_up = shown_query != parsed && (!filtered_settled || !threads_settled)
    val search_refreshing = has_query &&
        (search_running || query_catching_up || (select_mode && !members_settled))
    fun expand_selection(ids: List<String>): List<String> {
        val members = when {
            !grouping_enabled || members_settled -> thread_member_ids
            else -> thread_member_ids_from_index(
                result_threads,
                corpus_lookup?.thread_index ?: search_thread_index(visible_corpus),
            )
        }
        return expand_thread_selection(ids, members, grouping_enabled)
    }

    val selection_snapshot by remember { androidx.compose.runtime.derivedStateOf { selected_ids.toList() } }
    val selection_flags = remember(selection_snapshot, thread_member_ids, grouping_enabled, corpus_lookup, filtered_ids) {
        search_selection_flags_of(
            selection = selection_snapshot,
            expand = { expand_thread_selection(it, thread_member_ids, grouping_enabled) },
            lookup = corpus_lookup,
            shown_ids = filtered_ids,
        )
    }
    val selection_all_starred = selection_flags.all_starred
    val selection_all_read = selection_flags.all_read

    LaunchedEffect(result_threads, results_pending, search_refreshing) {
        if (select_mode && !results_pending && !search_refreshing) {
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

    val selection_all_trashed = selection_flags.delete_plan.all_trashed

    fun delete_selected() {
        val plan = selection_flags.delete_plan
        if (plan.trash_ids.isNotEmpty()) mail_vm.trash(plan.trash_ids, plan.trash_row_count)
        if (plan.permanent_ids.isEmpty()) {
            exit_select_mode()
        } else {
            selected_ids.retainAll(plan.permanent_rows.toSet())
            pending_permanent_ids = plan.permanent_ids
        }
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

    fun report_phishing_selected() {
        val ids = expand_selection(selected_ids.toList())
        val in_spam = visible_corpus.filter { it.id in ids }.let { it.isNotEmpty() && it.all { item -> item.is_spam } }
        mail_vm.report_phishing(ids, selected_ids.size, in_spam = in_spam)
        exit_select_mode()
    }

    fun run_selection_action(action_id: String) {
        when (action_id) {
            "read" -> mark_read_selected()
            "unread" -> mark_unread_selected()
            "trash", "delete", "delete_permanent" -> delete_selected()
            "archive" -> archive_selected()
            "star" -> star_selected()
            "spam" -> mark_spam_selected()
            "phishing" -> report_phishing_selected()
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
            .page_surface(colors)
            .systemBarsPadding()
            .imePadding(),
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = select_mode,
            transitionSpec = { org.astermail.android.design.aster_crossfade(reduce_motion) },
            label = "search_top_bar",
        ) { top_select_mode ->
        if (top_select_mode) {
            search_select_top_bar(
                selected_count = selected_ids.size,
                on_close = { exit_select_mode() },
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
                on_field_change = { apply_field_change(it) },
                show_placeholder = free_text.isEmpty(),
                placeholder = stringResource(
                    if (operator_chips.isNotEmpty()) R.string.search_refine_placeholder else R.string.search_mail,
                ),
                show_clear = free_text.isNotEmpty() || operator_chips.isNotEmpty(),
                on_clear = { set_free_text(""); operator_chips = emptyList() },
                on_back = dismiss_and_back,
                focus_requester = focus_requester,
                operators = shown_operators,
                on_operators_change = { apply_chip_operators(it) },
                on_submit = {
                    submit_typed_operators()
                    dismiss_keyboard()
                },
            )
        }
        }

        org.astermail.android.design.AsterExpandVisibility(visible = !select_mode) {
            search_chip_row(
                operators = shown_operators,
                people = chip_people,
                recipient_people = chip_recipients,
                folder = origin_folder,
                free_text = free_text,
                on_operators_change = { apply_chip_operators(it) },
                on_advanced_click = {
                    submit_typed_operators()
                    advanced_open = true
                },
            )
        }

        search_status_section(
            mail_vm = mail_vm,
            has_query = has_query,
            metadata_indexing = search_state.is_indexing,
            partial = search_state.is_partial,
            search_refreshing = search_refreshing,
            reduce_motion = reduce_motion,
        )

        if (has_query && !select_mode && !results_pending && hidden_spam_trash > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.sm, vertical = 4.dp)
                    .clip(SquircleShape(8.dp))
                    .clickable {
                        apply_chip_operators(
                            set_search_scope(shown_operators, origin_folder, "anywhere", free_text.isNotBlank()),
                        )
                    }
                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.spam_trash_hidden_notice),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Text(
                    text = stringResource(R.string.view_spam_trash_messages),
                    color = colors.accent_blue,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        val search_body = when {
            !has_query -> SearchBodyKind.tips
            result_threads.isNotEmpty() -> SearchBodyKind.results
            results_pending || threads_catching_up -> SearchBodyKind.loading
            else -> SearchBodyKind.empty
        }
        androidx.compose.animation.AnimatedContent(
            targetState = search_body,
            transitionSpec = { org.astermail.android.design.aster_crossfade(reduce_motion) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "search_body",
        ) { body_kind ->
            when (body_kind) {
                SearchBodyKind.tips -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .vertical_scroll_with_indicator()
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
                    search_tip(stringResource(R.string.op_filename), stringResource(R.string.op_filename_desc)) { set_free_text("filename:"); focus_requester.requestFocus() }
                    search_tip(stringResource(R.string.op_negate), stringResource(R.string.op_negate_desc)) { set_free_text("-from:"); focus_requester.requestFocus() }
                    Spacer(Modifier.height(AsterSpacing.lg))
                    Text(
                        text = stringResource(R.string.search_privacy_note),
                        color = colors.text_muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                SearchBodyKind.loading -> {
                    val search_skeleton_geometry = org.astermail.android.ui.mail.remember_skeleton_geometry(
                        org.astermail.android.ui.mail.SkeletonPhase.skeleton,
                        org.astermail.android.ui.mail.skeleton_geometry_of(settings_state.preferences),
                    )
                    org.astermail.android.ui.mail.inbox_skeleton(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = AsterSpacing.sm),
                        list_density = search_skeleton_geometry.list_density,
                        show_avatar = search_skeleton_geometry.show_avatar,
                        show_preview = search_skeleton_geometry.show_preview,
                    )
                }
                SearchBodyKind.empty -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AsterSpacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    val corpus_error = search_state.error
                    val filters_active = parsed.operators.isNotEmpty()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (corpus_error != null) TablerIcons.AlertCircle else TablerIcons.Search,
                            contentDescription = null,
                            tint = colors.text_muted,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(AsterSpacing.md))
                        Text(
                            text = corpus_error ?: stringResource(
                                if (filters_active) R.string.search_no_results_filtered else R.string.no_results_found,
                            ),
                            color = colors.text_muted,
                            fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        if (corpus_error != null) {
                            Spacer(Modifier.height(AsterSpacing.md))
                            org.astermail.android.design.components.AsterSecondaryButton(
                                label = stringResource(R.string.retry),
                                onClick = { mail_vm.build_search_index(force = true) },
                            )
                        } else if (filters_active) {
                            Spacer(Modifier.height(AsterSpacing.md))
                            org.astermail.android.design.components.AsterSecondaryButton(
                                label = stringResource(R.string.search_clear_filters),
                                onClick = {
                                    set_free_text(parse_query(free_text).free_text)
                                    operator_chips = emptyList()
                                },
                            )
                        }
                    }
                }
                SearchBodyKind.results -> Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = pluralStringResource(R.plurals.search_results_count, result_threads.size, result_threads.size),
                        color = colors.text_muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                    )
                    val row_highlight_terms = remember(parsed) { search_highlight_terms_for(parsed) }
                    androidx.compose.runtime.CompositionLocalProvider(
                        org.astermail.android.ui.mail.local_read_fade_scale provides SEARCH_READ_FADE_SCALE,
                        local_search_row_terms provides row_highlight_terms,
                    ) {
                        search_results_list(
                            threads = result_threads,
                            select_mode = select_mode,
                            selected_ids = selected_ids,
                            on_open_email = { id ->
                                if (visible_corpus.firstOrNull { it.id == id }?.raw_item?.item_type == "draft") {
                                    on_open_draft(id)
                                } else {
                                    search_highlight_handoff.offer(id, row_highlight_terms)
                                    on_open_email(id)
                                }
                            },
                            on_toggle_selection = { toggle_selection(it) },
                            on_enter_select_mode = { id ->
                                dismiss_keyboard()
                                select_mode = true
                                selected_ids.clear()
                                selected_ids.add(id)
                            },
                            on_toggle_star = { mail_vm.toggle_star(it) },
                            on_set_selection = { ids ->
                                dismiss_keyboard()
                                select_mode = true
                                selected_ids.clear()
                                selected_ids.addAll(ids)
                            },
                            on_scroll_busy = { results_scrolling.value = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    org.astermail.android.design.AsterExpandVisibility(visible = select_mode) {
                        search_select_bottom_bar(
                            selected_count = selected_ids.size,
                            custom_actions = selection_toolbar_slots,
                            on_action = { run_selection_action(it) },
                            on_more = { show_selection_overflow = true },
                            selection_all_starred = selection_all_starred,
                            selection_all_read = selection_all_read,
                            selection_all_trashed = selection_all_trashed,
                        )
                    }
                }
            }
        }
    }

    if (pending_permanent_ids.isNotEmpty()) {
        val permanent_ids = pending_permanent_ids
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { pending_permanent_ids = emptyList() },
            title = stringResource(R.string.confirm_delete_permanent_title),
            message = stringResource(
                R.string.confirm_delete_permanent_message_count,
                androidx.compose.ui.res.pluralStringResource(
                    R.plurals.common_messages_count,
                    permanent_ids.size,
                    permanent_ids.size,
                ),
            ),
            confirm_label = stringResource(R.string.swipe_delete_forever),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                pending_permanent_ids = emptyList()
                mail_vm.delete_permanent_bulk(permanent_ids)
                exit_select_mode()
            },
        )
    }

    if (show_selection_overflow) {
        org.astermail.android.ui.mail.selection_overflow_sheet(
            on_close = { show_selection_overflow = false },
            on_action = { id ->
                show_selection_overflow = false
                run_selection_action(id)
            },
            selection_all_starred = selection_all_starred,
            delete_is_permanent = selection_all_trashed,
        )
    }

    if (show_folder_sheet) {
        val unnamed_folder_label = stringResource(R.string.unnamed_folder)
        val folder_decrypt_failed_label = stringResource(R.string.folder_decrypt_failed)
        val folder_nodes = org.astermail.android.folders.flatten_folder_tree(settings_state.labels)
        val folder_items = folder_nodes
            .map { node ->
                val label = node.label
                val readable = org.astermail.android.labels.readable_label_name(label)
                label.copy(encrypted_name = readable ?: folder_decrypt_failed_label)
            }
        org.astermail.android.ui.mail.label_picker_sheet(
            title = stringResource(R.string.move_to_folder),
            empty_message = stringResource(R.string.no_folders_yet_create),
            items = folder_items,
            depths = folder_nodes.associate { it.label.label_token to it.depth },
            on_close = { show_folder_sheet = false },
            on_pick = { picked ->
                val display = picked.encrypted_name?.takeIf { it.isNotBlank() } ?: unnamed_folder_label
                show_folder_sheet = false
                mail_vm.move_to_folder_bulk(expand_selection(selected_ids.toList()), picked.label_token, display)
                exit_select_mode()
            },
        )
    }

    if (show_label_sheet) {
        val expanded_selection = expand_selection(selected_ids.toList()).toSet()
        val selected_items = search_state.all_items.filter { it.id in expanded_selection }
        val applied_tags = if (selected_items.isEmpty()) {
            emptySet()
        } else {
            selected_items.map { it.tag_tokens.toSet() }.reduce { acc, tokens -> acc intersect tokens }
        }
        val tag_items = org.astermail.android.labels.tag_rows(settings_state.tags)
        val unknown_label = stringResource(R.string.label_decrypt_failed)
        org.astermail.android.ui.mail.tag_picker_sheet(
            title = stringResource(R.string.edit_labels),
            empty_message = stringResource(R.string.no_labels_yet_create),
            items = tag_items,
            on_close = { show_label_sheet = false },
            on_pick = { picked ->
                val display = org.astermail.android.labels.tag_display_name(picked, unknown_label)
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
            folder = origin_folder,
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
            modifier = Modifier.weight(0.42f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = description,
            color = colors.text_muted,
            fontSize = 13.sp,
            modifier = Modifier.weight(0.58f),
        )
    }
}

internal fun operator_value_label(op: SearchOperator): Int? = when (op.key) {
    "in" -> when (op.value) {
        "anywhere" -> R.string.chip_everywhere
        "all" -> R.string.folder_all_mail
        "inbox" -> R.string.folder_inbox
        "sent" -> R.string.folder_sent
        "drafts", "draft" -> R.string.folder_drafts
        "archive", "archived" -> R.string.folder_archive
        "starred" -> R.string.folder_starred
        "spam" -> R.string.folder_spam
        "trash" -> R.string.folder_trash
        else -> null
    }
    "is" -> when (op.value) {
        "unread" -> R.string.filter_unread
        "read" -> R.string.inbox_a11y_state_read
        "starred" -> R.string.filter_starred
        "unstarred" -> R.string.not_starred
        "encrypted" -> R.string.filter_encrypted
        else -> null
    }
    "has" -> if (HAS_OPERATOR_VALUES.contains(op.value)) R.string.search_value_attachment else null
    "date" -> when (op.value) {
        "today" -> R.string.security_activity_today
        "yesterday" -> R.string.yesterday
        "this_week" -> R.string.search_value_this_week
        "last_week" -> R.string.search_value_last_week
        "this_month" -> R.string.search_value_this_month
        else -> null
    }
    else -> null
}

private fun filter_toggle_active(ops: List<SearchOperator>, toggle: FilterToggle): Boolean =
    if (toggle.key == "has") has_attachment_active(ops) else has_operator(ops, toggle.key, toggle.value)

private fun apply_filter_toggle(ops: List<SearchOperator>, toggle: FilterToggle): List<SearchOperator> =
    if (toggle.key == "has") toggle_attachment(ops) else toggle_operator(ops, toggle.key, toggle.value)

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
    val text_color = if (selected) colors.on_accent else colors.text_secondary
    val animated_bg by org.astermail.android.design.aster_animate_color(bg, label = "chip_bg")
    val animated_text by org.astermail.android.design.aster_animate_color(text_color, label = "chip_text")
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
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = search_chip_max_width),
        )
    }
}

private val search_bar_height = 52.dp

private val search_chip_max_width = 220.dp

private val search_field_text_style = TextStyle(
    fontSize = 16.sp,
    lineHeight = 22.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

@Composable
private fun search_input_bar(
    field_value: TextFieldValue,
    on_field_change: (TextFieldValue) -> Unit,
    show_placeholder: Boolean,
    placeholder: String,
    show_clear: Boolean,
    on_clear: () -> Unit,
    on_back: () -> Unit,
    focus_requester: FocusRequester,
    operators: List<SearchOperator>,
    on_operators_change: (List<SearchOperator>) -> Unit,
    on_submit: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var filter_menu_open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.sm)
            .padding(top = AsterSpacing.sm, bottom = AsterSpacing.xs)
            .heightIn(min = search_bar_height),
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
                .padding(horizontal = AsterSpacing.xs)
                .heightIn(min = search_bar_height)
                .acrylic(colors, SquircleShape(26.dp), search_field_bg_color(colors))
                .padding(
                    start = AsterSpacing.md + AsterSpacing.xs / 2,
                    end = if (show_clear) AsterSpacing.xs else AsterSpacing.md,
                    top = AsterSpacing.xs,
                    bottom = AsterSpacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.Search,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(AsterSpacing.sm + AsterSpacing.xs / 2))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val placeholder_alpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (show_placeholder) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(if (show_placeholder) 120 else 60),
                    label = "search_placeholder_alpha",
                )
                if (show_placeholder || placeholder_alpha > 0f) {
                    androidx.compose.animation.Crossfade(
                        targetState = placeholder,
                        animationSpec = androidx.compose.animation.core.tween(160),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = placeholder_alpha },
                        label = "search_placeholder",
                    ) { hint ->
                        Text(
                            text = hint,
                            style = search_field_text_style.copy(color = colors.text_muted),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().testTag("search_placeholder"),
                        )
                    }
                }
                BasicTextField(
                    value = field_value,
                    onValueChange = on_field_change,
                    singleLine = true,
                    textStyle = search_field_text_style.copy(color = colors.text_primary),
                    cursorBrush = SolidColor(colors.accent_blue),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { on_submit() },
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus_requester),
                )
            }
            if (show_clear) {
                Spacer(Modifier.width(AsterSpacing.xs))
                Box(
                    modifier = Modifier
                        .size(40.dp)
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
                    tint = if (FILTER_TOGGLES.any { filter_toggle_active(operators, it) }) {
                        colors.accent_blue
                    } else {
                        colors.text_secondary
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
            aster_menu(
                expanded = filter_menu_open,
                on_dismiss = { filter_menu_open = false },
            ) {
                FILTER_TOGGLES.forEach { toggle ->
                    aster_menu_item(
                        label = stringResource(toggle.label_res),
                        selected = filter_toggle_active(operators, toggle),
                        on_click = { on_operators_change(apply_filter_toggle(operators, toggle)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun search_status_section(
    mail_vm: MailViewModel,
    has_query: Boolean,
    metadata_indexing: Boolean,
    partial: Boolean,
    search_refreshing: Boolean,
    reduce_motion: Boolean,
) {
    val colors = AsterMaterial.colors
    val index_progress by mail_vm.search_index_progress.collectAsStateWithLifecycle()
    val index_paused by mail_vm.search_index_paused.collectAsStateWithLifecycle()
    val body_progress by mail_vm.body_index_progress.collectAsStateWithLifecycle()
    val search_status = search_status_of(
        has_query = has_query,
        metadata_indexing = metadata_indexing,
        paused = index_paused,
        metadata_progress = index_progress,
        body_progress = body_progress,
        partial = partial,
    )
    search_status_strip(
        status = search_status,
        on_action = { action ->
            when (action) {
                SearchStatusAction.pause -> mail_vm.pause_search_indexing()
                SearchStatusAction.resume -> mail_vm.resume_search_indexing()
                SearchStatusAction.retry -> mail_vm.build_search_index(force = true)
            }
        },
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
    ) {
        val refresh_bar_visible = org.astermail.android.design.remember_steady_flag(search_refreshing && search_status == null)
        androidx.compose.animation.AnimatedVisibility(
            visible = refresh_bar_visible,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if (reduce_motion) 0 else 160)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (reduce_motion) 0 else 220)),
        ) {
            org.astermail.android.design.components.aster_linear_progress(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = colors.accent_blue,
                track_color = Color.Transparent,
            )
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
    on_scroll_busy: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val selected_set = selected_ids.toSet()
    val list_state = rememberLazyListState()
    val live_scroll_busy by rememberUpdatedState(on_scroll_busy)
    LaunchedEffect(list_state) {
        androidx.compose.runtime.snapshotFlow { list_state.isScrollInProgress }
            .collect { live_scroll_busy(it) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { live_scroll_busy(false) }
    }
    val haptics = LocalHapticFeedback.current
    val settings_vm = org.astermail.android.settings.optional_shared_settings_view_model()
    val settings_state = settings_vm?.state?.collectAsStateWithLifecycle()?.value
    val search_row_geometry = org.astermail.android.ui.mail.remember_row_geometry(
        org.astermail.android.ui.mail.skeleton_geometry_of(settings_state?.preferences),
    )
    val live_select_mode by rememberUpdatedState(select_mode)
    val live_selected_set by rememberUpdatedState(selected_set)
    val live_threads by rememberUpdatedState(threads)
    val live_enter_select_mode by rememberUpdatedState(on_enter_select_mode)
    val live_set_selection by rememberUpdatedState(on_set_selection)
    val index_at_visible_offset: (Float) -> Int? = remember(list_state) {
        { y ->
            list_state.layoutInfo.visibleItemsInfo
                .firstOrNull { y >= it.offset && y < it.offset + it.size }
                ?.index
        }
    }
    val drag_anchor_id = remember { mutableStateOf<String?>(null) }
    val drag_last_id = remember { mutableStateOf<String?>(null) }
    val drag_last_rows = remember { mutableStateOf<Any?>(null) }
    val drag_pre_selected = remember { mutableStateListOf<String>() }
    val drag_pointer_y = remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    val drag_viewport_height = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var drag_selecting by remember { mutableStateOf(false) }

    fun index_at_offset(y: Float): Int? {
        val idx = index_at_visible_offset(y) ?: return null
        return idx.takeIf { it in live_threads.indices }
    }

    fun apply_drag_selection(y: Float) {
        val anchor_id = drag_anchor_id.value ?: return
        val rows = live_threads
        val current_id = index_at_offset(y)?.let { rows.getOrNull(it)?.newest?.id } ?: return
        if (current_id == drag_last_id.value && rows === drag_last_rows.value) return
        val anchor = rows.indexOfFirst { it.newest.id == anchor_id }
        val idx = rows.indexOfFirst { it.newest.id == current_id }
        if (anchor < 0 || idx < 0) return
        drag_last_id.value = current_id
        drag_last_rows.value = rows
        val target = LinkedHashSet(drag_pre_selected)
        for (i in minOf(anchor, idx)..maxOf(anchor, idx)) target.add(rows[i].newest.id)
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
            .lazy_top_fade(list_state)
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
                            drag_anchor_id.value = id
                            drag_last_id.value = id
                            drag_last_rows.value = live_threads
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
                        drag_anchor_id.value = null
                        drag_last_id.value = null
                        drag_last_rows.value = null
                        drag_pre_selected.clear()
                    },
                    onDragCancel = {
                        drag_selecting = false
                        drag_pointer_y.floatValue = -1f
                        drag_anchor_id.value = null
                        drag_last_id.value = null
                        drag_last_rows.value = null
                        drag_pre_selected.clear()
                    },
                )
            },
        contentPadding = PaddingValues(top = AsterSpacing.sm, bottom = AsterSpacing.lg),
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
                on_avatar_click = { on_enter_select_mode(row_id) },
                on_toggle_star = { on_toggle_star(row_id) },
                modifier = Modifier.testTag("search_row_$row_id"),
                is_first = index == 0,
                is_last = index == threads.lastIndex,
                is_selected = is_selected,
                select_mode = select_mode,
                is_pinned = thread.is_pinned,
                user_prefs = settings_state?.preferences,
                cached_geometry = search_row_geometry,
            )
        }
    }
        org.astermail.android.ui.common.vertical_scroll_bar(
            state = list_state,
            modifier = Modifier.align(Alignment.TopEnd),
            bottom_inset = AsterSpacing.lg,
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
            .glass_bar(colors)
            .heightIn(min = 56.dp)
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
                pluralStringResource(R.plurals.inbox_selected_count, selected_count, selected_count)
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
    selection_all_starred: Boolean = false,
    selection_all_read: Boolean = false,
    selection_all_trashed: Boolean = false,
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
                val shared_label_size = remember { androidx.compose.runtime.mutableFloatStateOf(11f) }
                androidx.compose.runtime.CompositionLocalProvider(local_search_label_size provides shared_label_size) {
                org.astermail.android.ui.mail.resolve_selection_toolbar(
                    custom_actions,
                    selection_all_starred,
                    selection_all_read,
                    selection_all_trashed,
                ).forEach { action ->
                    val action_id = action.id
                    search_select_action(
                        icon = action.icon,
                        label = stringResource(action.label_res),
                        enabled = enabled,
                        on_click = { on_action(action.id) },
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
}

private val local_search_label_size = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableFloatState?> { null }

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
        val shared_size = local_search_label_size.current
        var own_size by remember(label) { mutableStateOf(11f) }
        val label_size = shared_size?.floatValue ?: own_size
        Text(
            text = label,
            color = resolved_tint,
            fontSize = label_size.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (result.hasVisualOverflow && label_size > 8f) {
                    if (shared_size != null) shared_size.floatValue = label_size - 0.5f else own_size = label_size - 0.5f
                }
            },
        )
    }
}
