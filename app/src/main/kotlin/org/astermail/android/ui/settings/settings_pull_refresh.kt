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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.ui.common.aster_pull_refresh_indicator
import org.astermail.android.ui.common.remember_aster_pull_refresh_state

internal const val SETTINGS_REFRESH_TIMEOUT_MS = 15_000L

suspend fun ViewModel.join_launched(block: () -> Unit) {
    val parent = viewModelScope.coroutineContext.job
    val before: Set<Job> = parent.children.toSet()
    block()
    parent.children.filterNot { it in before }.toList().joinAll()
}

@Composable
internal fun settings_pull_refresh_box(
    on_refresh: (suspend () -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val action by rememberUpdatedState(on_refresh)
    val typing = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val can_refresh = on_refresh != null && enabled && !typing
    val pull_state = remember_aster_pull_refresh_state(
        refreshing = refreshing,
        enabled = can_refresh,
        on_refresh = {
            val run = action
            if (!refreshing && run != null) {
                refreshing = true
                scope.launch {
                    try {
                        withTimeoutOrNull(SETTINGS_REFRESH_TIMEOUT_MS) { run() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                    } finally {
                        refreshing = false
                    }
                }
            }
        },
    )
    Box(
        modifier = modifier.then(if (on_refresh != null) Modifier.nestedScroll(pull_state) else Modifier),
    ) {
        content()
        if (on_refresh != null) {
            aster_pull_refresh_indicator(
                state = pull_state,
                refreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
