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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class preload_cache<K : Any, V : Any>(
    private val capacity: Int,
    private val scope: CoroutineScope,
    private val ttl_ms: Long = Long.MAX_VALUE,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private class slot<V>(val job: Deferred<V?>, val started_at: Long)

    private val lock = Any()
    private val entries = LinkedHashMap<K, slot<V>>(capacity.coerceAtLeast(1), 0.75f, true)

    fun prepare(key: K, loader: suspend () -> V?): Deferred<V?> {
        val evicted = ArrayList<Deferred<V?>>()
        val result = synchronized(lock) {
            val existing = entries[key]
            if (existing != null && usable(existing)) return@synchronized existing.job
            existing?.let { entries.remove(key) }
            val job = scope.async(start = CoroutineStart.LAZY) { loader() }
            val created = slot(job, clock())
            entries[key] = created
            while (entries.size > capacity.coerceAtLeast(1)) {
                val eldest = entries.entries.iterator().next()
                entries.remove(eldest.key)
                evicted += eldest.value.job
            }
            job.invokeOnCompletion { error ->
                val failed = error != null || job_value_or_null(job) == null
                if (failed) {
                    synchronized(lock) {
                        if (entries[key] === created) entries.remove(key)
                    }
                }
            }
            job.start()
            job
        }
        evicted.forEach { if (!it.isCompleted) it.cancel() }
        return result
    }

    fun take(key: K): V? = synchronized(lock) {
        val current = entries[key] ?: return@synchronized null
        if (!current.job.isCompleted) return@synchronized null
        entries.remove(key)
        if (!fresh(current)) return@synchronized null
        job_value_or_null(current.job)
    }

    fun peek(key: K): V? = synchronized(lock) {
        val current = entries[key] ?: return@synchronized null
        if (!current.job.isCompleted || !fresh(current)) return@synchronized null
        job_value_or_null(current.job)
    }

    fun pending(key: K): Deferred<V?>? = synchronized(lock) {
        entries[key]?.takeIf { usable(it) && !it.job.isCompleted }?.job
    }

    suspend fun await_and_take(key: K): V? {
        val job = synchronized(lock) { entries[key]?.takeIf { usable(it) }?.job } ?: return null
        val value = try {
            job.await()
        } catch (cancelled: CancellationException) {
            if (!job.isCancelled) throw cancelled
            null
        } catch (_: Throwable) {
            null
        }
        synchronized(lock) {
            if (entries[key]?.job === job) entries.remove(key)
        }
        return value
    }

    fun cancel_if_running(key: K) {
        val job = synchronized(lock) {
            val current = entries[key] ?: return
            if (current.job.isCompleted) return
            entries.remove(key)
            current.job
        }
        job.cancel()
    }

    fun clear() {
        val jobs = synchronized(lock) {
            val all = entries.values.map { it.job }
            entries.clear()
            all
        }
        jobs.forEach { if (!it.isCompleted) it.cancel() }
    }

    fun remove_if(predicate: (V) -> Boolean) {
        val jobs = synchronized(lock) {
            val dropped = ArrayList<Deferred<V?>>()
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next().value
                val value = job_value_or_null(entry.job)
                val drop = !entry.job.isCompleted || value == null || predicate(value)
                if (drop) {
                    iterator.remove()
                    dropped += entry.job
                }
            }
            dropped
        }
        jobs.forEach { if (!it.isCompleted) it.cancel() }
    }

    fun size(): Int = synchronized(lock) { entries.size }

    fun contains(key: K): Boolean = synchronized(lock) { entries[key]?.let { usable(it) } == true }

    private fun usable(entry: slot<V>): Boolean {
        if (entry.job.isCancelled) return false
        if (!entry.job.isCompleted) return true
        return fresh(entry) && job_value_or_null(entry.job) != null
    }

    private fun fresh(entry: slot<V>): Boolean {
        if (ttl_ms == Long.MAX_VALUE) return true
        return clock() - entry.started_at <= ttl_ms
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun job_value_or_null(job: Deferred<V?>): V? {
        if (!job.isCompleted || job.isCancelled) return null
        return if (job.getCompletionExceptionOrNull() != null) null else job.getCompleted()
    }
}

data class preloaded_thread(
    val item: InboxItem,
    val messages: List<ThreadMessageDecrypted>,
    val thread_token: String?,
    val grouped: Boolean,
)

internal const val message_preload_capacity = 6
internal const val message_preload_ttl_ms = 60_000L
internal const val message_preload_visible_rows = 4
internal const val message_preload_neighbor_delay_ms = 1_200L

object message_preloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cache = preload_cache<String, preloaded_thread>(
        capacity = message_preload_capacity,
        scope = scope,
        ttl_ms = message_preload_ttl_ms,
    )

    fun prepare(id: String, loader: suspend () -> preloaded_thread?): Deferred<preloaded_thread?> =
        cache.prepare(id, loader)

    fun take(id: String): preloaded_thread? = cache.take(id)

    fun peek(id: String): preloaded_thread? = cache.peek(id)

    fun pending(id: String): Deferred<preloaded_thread?>? = cache.pending(id)

    suspend fun await_and_take(id: String): preloaded_thread? = cache.await_and_take(id)

    fun contains(id: String): Boolean = cache.contains(id)

    fun cancel_if_running(id: String) = cache.cancel_if_running(id)

    fun clear() = cache.clear()

    fun invalidate_thread(thread_token: String?, item_ids: Collection<String> = emptyList()) {
        val token = thread_token?.takeIf { it.isNotBlank() }
        if (token == null && item_ids.isEmpty()) {
            cache.clear()
            return
        }
        cache.remove_if { entry ->
            (token != null && entry.thread_token == token) ||
                entry.item.id in item_ids ||
                entry.messages.any { it.id in item_ids }
        }
    }

    @Volatile
    private var requester: ((String) -> Unit)? = null

    fun bind_requester(owner: (String) -> Unit) {
        requester = owner
    }

    fun unbind_requester(owner: (String) -> Unit) {
        if (requester === owner) requester = null
    }

    fun request(id: String) {
        if (id.isBlank()) return
        requester?.invoke(id)
    }

    fun request_visible(ids: List<String>) {
        preload_visible_candidates(ids) { cache.contains(it) }.forEach { request(it) }
    }
}

internal fun preload_neighbors(ids: List<String>, current_id: String): List<String> {
    val index = ids.indexOf(current_id)
    if (index < 0) return emptyList()
    return listOfNotNull(ids.getOrNull(index + 1), ids.getOrNull(index - 1)).filter { it != current_id }
}

internal fun preload_visible_candidates(
    visible_ids: List<String>,
    limit: Int = message_preload_visible_rows,
    already: (String) -> Boolean,
): List<String> = visible_ids.asSequence()
    .filter { it.isNotBlank() && !already(it) }
    .distinct()
    .take(limit.coerceAtLeast(0))
    .toList()
