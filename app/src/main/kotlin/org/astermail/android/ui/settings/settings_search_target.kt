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

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object settings_search_target {
    private const val PENDING_TIMEOUT_MS = 4_000L
    private val pending_label = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = pending_label.asStateFlow()

    internal val timeout_ms: Long get() = PENDING_TIMEOUT_MS

    fun set(label: String) {
        pending_label.value = normalize(label).ifEmpty { null }
    }

    fun clear() {
        pending_label.value = null
    }

    fun matches(text: String?): Boolean {
        val target = pending_label.value ?: return false
        return text != null && normalize(text) == target
    }

    private fun normalize(value: String): String = value.trim().lowercase()
}

internal class settings_anchor_host(
    private val scroll_state: ScrollState,
    private val scope: CoroutineScope,
    private val top_margin_px: Int,
) {
    private var content: LayoutCoordinates? = null
    private var waiting_target: LayoutCoordinates? = null

    fun on_content_positioned(coordinates: LayoutCoordinates) {
        content = coordinates
        waiting_target?.let { reveal(it) }
    }

    fun reveal(target: LayoutCoordinates) {
        val container = content
        if (container == null || !container.isAttached) {
            waiting_target = target
            return
        }
        waiting_target = null
        if (!target.isAttached) return
        val y = container.localPositionOf(target, Offset.Zero).y.toInt()
        settings_search_target.clear()
        scope.launch { scroll_state.animateScrollTo((y - top_margin_px).coerceAtLeast(0)) }
    }
}

internal val local_settings_anchor_host = staticCompositionLocalOf<settings_anchor_host?> { null }

@Composable
internal fun remember_settings_anchor_host(scroll_state: ScrollState): settings_anchor_host {
    val scope = rememberCoroutineScope()
    val margin = with(LocalDensity.current) { 96.dp.roundToPx() }
    val host = remember(scroll_state, scope, margin) { settings_anchor_host(scroll_state, scope, margin) }
    LaunchedEffect(host) {
        delay(settings_search_target.timeout_ms)
        settings_search_target.clear()
    }
    return host
}

internal fun Modifier.settings_search_content(host: settings_anchor_host): Modifier =
    onGloballyPositioned { host.on_content_positioned(it) }

internal fun Modifier.settings_search_anchor(vararg texts: String?): Modifier = composed {
    val host = local_settings_anchor_host.current
    val pending by settings_search_target.pending.collectAsState()
    if (host == null || pending == null || texts.none { settings_search_target.matches(it) }) {
        Modifier
    } else {
        Modifier.onGloballyPositioned { host.reveal(it) }
    }
}
