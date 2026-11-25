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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.astermail.android.mail.LIVE_SYNC_POLL_CHECK_MS
import org.astermail.android.mail.LiveSyncEvent
import org.astermail.android.mail.LiveSyncSocket
import org.astermail.android.mail.live_sync_hub
import org.astermail.android.mail.live_sync_next_step
import org.astermail.android.mail.live_sync_poll_due
import org.astermail.android.mail.live_sync_poll_interval_ms

private const val LIVE_SYNC_START_GRACE_MS = 5_000L

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal interface LiveSyncHostDeps {
    fun live_sync_socket(): LiveSyncSocket
}

@Composable
fun live_sync_host(
    active: Boolean,
    on_folders_changed: () -> Unit,
    on_tags_changed: () -> Unit,
) {
    if (!active) return
    val context = LocalContext.current
    val socket = remember(context) {
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, LiveSyncHostDeps::class.java)
            .live_sync_socket()
    }
    val folders_changed by rememberUpdatedState(on_folders_changed)
    val tags_changed by rememberUpdatedState(on_tags_changed)
    val lifecycle_owner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle_owner, socket) {
        lifecycle_owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            live_sync_hub.reset_activity()
            coroutineScope {
                launch {
                    socket.run(
                        on_mutation = { mutation -> live_sync_hub.dispatch_mutation(mutation.action, mutation.item_ids) },
                    ) { event ->
                        when (event) {
                            LiveSyncEvent.folders_changed -> {
                                live_sync_hub.mark_activity()
                                folders_changed()
                            }
                            LiveSyncEvent.tags_changed -> {
                                live_sync_hub.mark_activity()
                                tags_changed()
                            }
                            LiveSyncEvent.connected -> {
                                folders_changed()
                                tags_changed()
                                live_sync_hub.dispatch_event(event)
                            }
                            else -> live_sync_hub.dispatch_event(event)
                        }
                    }
                }
                launch {
                    var step = 0
                    var seen = live_sync_hub.last_event_at()
                    var last_poll_at = System.currentTimeMillis() - live_sync_poll_interval_ms(0) + LIVE_SYNC_START_GRACE_MS
                    while (isActive) {
                        delay(LIVE_SYNC_POLL_CHECK_MS)
                        val now = System.currentTimeMillis()
                        val event_at = live_sync_hub.last_event_at()
                        step = live_sync_next_step(step, event_seen = event_at != seen, polled = false)
                        seen = event_at
                        if (!live_sync_poll_due(socket.connected.value, event_at, last_poll_at, step, now)) continue
                        live_sync_hub.dispatch_poll()
                        last_poll_at = now
                        step = live_sync_next_step(step, event_seen = false, polled = true)
                    }
                }
            }
        }
    }
}
