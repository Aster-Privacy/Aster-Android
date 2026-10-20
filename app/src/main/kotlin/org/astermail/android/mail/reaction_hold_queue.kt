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
package org.astermail.android.mail

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class ReactionHoldOutcome {
    data class Sent(val confirmed_id: String?) : ReactionHoldOutcome()
    data class Failed(val error: Throwable) : ReactionHoldOutcome()
    object Canceled : ReactionHoldOutcome()
    object AccountChanged : ReactionHoldOutcome()
}

class HeldReaction(
    val id: String,
    val delay_ms: Long,
    private val cancel_action: () -> Boolean,
) {
    fun undo(): Boolean = cancel_action()
}

class ReactionHoldQueue(
    private val scope: CoroutineScope,
    private val current_account: () -> String?,
) {
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val handles = java.util.concurrent.ConcurrentHashMap<String, HeldReaction>()

    fun is_held(id: String): Boolean = jobs.containsKey(id)

    fun undo(id: String): Boolean = handles[id]?.undo() ?: false

    fun hold(
        id: String,
        delay_ms: Long,
        send: suspend () -> Result<String?>,
        on_outcome: (ReactionHoldOutcome) -> Unit,
    ): HeldReaction {
        val account = current_account()
        val claimed = java.util.concurrent.atomic.AtomicBoolean(false)
        val wait_ms = delay_ms.coerceAtLeast(0L)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            if (wait_ms > 0) delay(wait_ms)
            if (!claimed.compareAndSet(false, true)) return@launch
            jobs.remove(id)
            handles.remove(id)
            if (account != null && current_account() != account) {
                on_outcome(ReactionHoldOutcome.AccountChanged)
                return@launch
            }
            val result = try {
                send()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (err: Throwable) {
                Result.failure(err)
            }
            on_outcome(
                result.fold(
                    onSuccess = { ReactionHoldOutcome.Sent(it) },
                    onFailure = { ReactionHoldOutcome.Failed(it) },
                ),
            )
        }
        val handle = HeldReaction(id = id, delay_ms = wait_ms) {
            if (!claimed.compareAndSet(false, true)) {
                false
            } else {
                job.cancel()
                jobs.remove(id, job)
                handles.remove(id)
                on_outcome(ReactionHoldOutcome.Canceled)
                true
            }
        }
        if (wait_ms > 0) {
            jobs[id] = job
            handles[id] = handle
        }
        job.start()
        return handle
    }
}
