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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.mail

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.ui.search.MAX_SEARCH_HITS_PER_BODY
import org.astermail.android.ui.search.count_search_hits
import org.astermail.android.ui.search.plain_text_for_search
import org.astermail.android.ui.search.search_highlight_handoff
import org.astermail.android.ui.search.search_highlight_session
import org.astermail.android.ui.search.search_match_bar
import kotlin.math.abs

@Stable
internal class detail_search_controller(
    val session: search_highlight_session,
    private val scope: CoroutineScope,
) {
    var list_top by mutableFloatStateOf(0f)
    var list_height by mutableFloatStateOf(0f)
    internal var navigate: suspend (Int) -> Unit = {}
    private var job: Job? = null

    fun go_to(global: Int) {
        if (global < 0) return
        session.current = global
        job?.cancel()
        job = scope.launch { navigate(global) }
    }

    fun previous() = go_to(session.step(-1))

    fun next() = go_to(session.step(1))

    fun clear() {
        job?.cancel()
        session.dismiss()
    }
}

@Composable
internal fun remember_detail_search_controller(email_id: String): detail_search_controller? {
    val scope = rememberCoroutineScope()
    return remember(email_id) {
        search_highlight_handoff.take(email_id)?.let { terms ->
            detail_search_controller(search_highlight_session(terms, email_id), scope)
        }
    }
}

@Composable
internal fun detail_search_effects(
    controller: detail_search_controller,
    messages: List<ThreadMessage>,
    subject: String,
    layout_ready: Boolean,
    thread_revealed: Boolean,
    hidden_ids: Set<String>,
    expanded_ids: Set<String>,
    list_state: LazyListState,
    on_expand: (Set<String>) -> Unit,
    on_reveal_hidden: () -> Unit,
) {
    val session = controller.session
    val reduce_motion = aster_reduce_motion()
    val latest_messages by rememberUpdatedState(messages)
    val latest_expanded by rememberUpdatedState(expanded_ids)
    val latest_hidden by rememberUpdatedState(hidden_ids)
    val latest_on_expand by rememberUpdatedState(on_expand)
    val latest_on_reveal_hidden by rememberUpdatedState(on_reveal_hidden)
    val latest_reduce_motion by rememberUpdatedState(reduce_motion)

    val ids = remember(messages) { messages.map { it.id } }
    LaunchedEffect(ids) { session.order = ids }
    LaunchedEffect(subject, session.terms) {
        session.subject_count = count_search_hits(subject, session.terms.subject)
            .coerceAtMost(MAX_SEARCH_HITS_PER_BODY)
    }

    var seeded by remember(session) { mutableStateOf(false) }
    val auto_expanded = remember(session) { HashSet<String>() }
    LaunchedEffect(session, messages, layout_ready) {
        if (!layout_ready) return@LaunchedEffect
        val terms = session.terms.body
        if (terms.isEmpty()) {
            seeded = true
            return@LaunchedEffect
        }
        val found = withContext(Dispatchers.Default) {
            messages.filter { !it.is_undecryptable }.associate { msg ->
                msg.id to count_search_hits(plain_text_for_search(msg.body, msg.body_html), terms)
                    .coerceAtMost(MAX_SEARCH_HITS_PER_BODY)
            }
        }
        found.forEach { (id, count) -> session.seed_count(id, count) }
        if (!session.dismissed) {
            val matched = found.filterValues { it > 0 }.keys.filter { it !in auto_expanded }
            if (matched.isNotEmpty()) {
                auto_expanded.addAll(matched)
                if (matched.any { it in latest_hidden }) latest_on_reveal_hidden()
                val missing = matched.filter { it !in latest_expanded }
                if (missing.isNotEmpty()) latest_on_expand(latest_expanded + missing)
            }
        }
        seeded = true
    }

    controller.navigate = navigate@{ global ->
        val location = session.locate(global) ?: return@navigate
        val key = location.message_key
        if (key == null) {
            if (latest_reduce_motion) list_state.scrollToItem(0) else list_state.animateScrollToItem(0)
            return@navigate
        }
        val index = latest_messages.indexOfFirst { it.id == key }
        if (index < 0) return@navigate
        if (!session.is_ready(key)) {
            if (key in latest_hidden) latest_on_reveal_hidden()
            if (key !in latest_expanded) latest_on_expand(latest_expanded + key)
            val visible = list_state.layoutInfo.visibleItemsInfo.any { it.index == index + 1 }
            if (!visible) {
                if (latest_reduce_motion) {
                    list_state.scrollToItem(index + 1)
                } else {
                    list_state.animateScrollToItem(index + 1)
                }
            }
            withTimeoutOrNull(5_000L) { snapshotFlow { session.is_ready(key) }.first { it } } ?: return@navigate
        }
        val last_local = ((session.counts[key] ?: 1) - 1).coerceAtLeast(0)
        val y = session.reveal(key, location.local_index.coerceAtMost(last_local)) ?: return@navigate
        val viewport = if (controller.list_height > 0f) {
            controller.list_height
        } else {
            list_state.layoutInfo.viewportSize.height.toFloat()
        }
        val delta = y - (controller.list_top + viewport * 0.3f)
        if (abs(delta) < 2f) return@navigate
        if (latest_reduce_motion) list_state.scrollBy(delta) else list_state.animateScrollBy(delta)
    }

    LaunchedEffect(session, seeded, thread_revealed) {
        if (session.initial_done || !seeded || !thread_revealed || session.dismissed) return@LaunchedEffect
        delay(220)
        val focus_first = session.first_index_in(session.focus_key)
        val target = when {
            focus_first >= 0 -> focus_first
            session.body_total > 0 -> session.order.firstNotNullOfOrNull { key ->
                session.first_index_in(key).takeIf { it >= 0 }
            } ?: -1
            session.subject_count > 0 -> 0
            else -> -1
        }
        session.initial_done = true
        if (target < 0) return@LaunchedEffect
        if (target < session.subject_count) {
            session.current = target
            return@LaunchedEffect
        }
        controller.go_to(target)
    }
}

@Composable
internal fun BoxScope.detail_search_overlay(
    controller: detail_search_controller,
    bottom_inset: Dp,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned { coords ->
                controller.list_top = coords.positionInWindow().y
                controller.list_height = coords.size.height.toFloat()
            },
    )
    search_match_bar(
        session = controller.session,
        on_previous = { controller.previous() },
        on_next = { controller.next() },
        on_clear = { controller.clear() },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottom_inset + 12.dp),
    )
}
