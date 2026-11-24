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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.ThreadMessageItem

const val envelope_backfill_chunk_size = 16
const val envelope_backfill_parallel_chunks = 4
const val envelope_backfill_attempts = 3
const val envelope_backfill_budget_ms = 9_000L
const val envelope_inline_budget_ms = 7_000L
const val envelope_refill_budget_ms = 15_000L
const val envelope_open_budget_ms = 4_000L
val envelope_refill_delays_ms = listOf(500L, 3_000L, 12_000L, 30_000L, 60_000L, 120_000L)
const val envelope_single_refetch_cap = 8

private val envelope_exempt_item_types = setOf("draft", "scheduled")

private fun is_envelope_exempt(item_type: String?, is_reaction: Boolean?): Boolean =
    is_reaction == true || item_type in envelope_exempt_item_types

fun list_envelope_backfill_budget_ms(inline: Boolean): Long = if (inline) envelope_inline_budget_ms else 0L

fun needs_envelope_backfill(item: MailItem): Boolean =
    item.id.isNotBlank() &&
        item.encrypted_envelope.isNullOrBlank() &&
        !is_envelope_exempt(item.item_type ?: item.metadata?.item_type, item.is_reaction)

fun needs_thread_envelope_refill(item: ThreadMessageItem): Boolean =
    item.id.isNotBlank() &&
        item.encrypted_envelope.isNullOrBlank() &&
        !is_envelope_exempt(item.item_type ?: item.metadata?.item_type, item.is_reaction)

suspend fun refetch_missing_singly(
    ids: List<String>,
    fetch_one: suspend (String) -> MailItem?,
    cap: Int = envelope_single_refetch_cap,
): Map<String, MailItem> {
    val found = LinkedHashMap<String, MailItem>()
    for (id in ids.asSequence().filter { it.isNotBlank() }.distinct().take(cap)) {
        val fetched = try {
            fetch_one(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            null
        } ?: continue
        if (fetched.id == id && !fetched.encrypted_envelope.isNullOrBlank()) found[id] = fetched
    }
    return found
}

suspend fun refill_missing_envelopes_with_fallback(
    items: List<MailItem>,
    fetch_batch: suspend (List<String>) -> List<MailItem>,
    fetch_one: suspend (String) -> MailItem?,
    budget_ms: Long = envelope_refill_budget_ms,
    cap: Int = envelope_single_refetch_cap,
): List<MailItem> {
    val batched = backfill_missing_envelopes(items, fetch_batch, budget_ms = budget_ms)
    val still_missing = envelope_backfill_ids(batched)
    if (still_missing.isEmpty()) return batched
    val found = refetch_missing_singly(still_missing, fetch_one, cap)
    if (found.isEmpty()) return batched
    return batched.map { item -> found[item.id]?.let { merge_backfilled_envelope(item, it) } ?: item }
}

suspend fun refill_thread_envelopes(
    messages: List<ThreadMessageItem>,
    fetch_one: suspend (String) -> MailItem?,
    cap: Int = envelope_single_refetch_cap,
): List<ThreadMessageItem> {
    val ids = messages.filter(::needs_thread_envelope_refill).map { it.id }
    if (ids.isEmpty()) return messages
    val found = refetch_missing_singly(ids, fetch_one, cap)
    if (found.isEmpty()) return messages
    return messages.map { message ->
        val fetched = found[message.id] ?: return@map message
        if (!needs_thread_envelope_refill(message)) return@map message
        message.copy(
            encrypted_envelope = fetched.encrypted_envelope,
            envelope_nonce = fetched.envelope_nonce ?: message.envelope_nonce,
            encrypted_metadata = message.encrypted_metadata ?: fetched.encrypted_metadata,
            metadata_nonce = message.metadata_nonce ?: fetched.metadata_nonce,
        )
    }
}

fun merge_backfilled_envelope(item: MailItem, fetched: MailItem): MailItem {
    if (fetched.id != item.id || fetched.encrypted_envelope.isNullOrBlank()) return item
    return item.copy(
        encrypted_envelope = fetched.encrypted_envelope,
        envelope_nonce = fetched.envelope_nonce ?: item.envelope_nonce,
        sender_sealed = item.sender_sealed ?: fetched.sender_sealed,
        encrypted_metadata = item.encrypted_metadata ?: fetched.encrypted_metadata,
        metadata_nonce = item.metadata_nonce ?: fetched.metadata_nonce,
    )
}

fun envelope_backfill_ids(items: List<MailItem>): List<String> =
    items.asSequence().filter(::needs_envelope_backfill).map { it.id }.distinct().toList()

suspend fun fetch_missing_envelopes(
    ids: List<String>,
    fetch: suspend (List<String>) -> List<MailItem>,
    attempts: Int = envelope_backfill_attempts,
    budget_ms: Long = envelope_backfill_budget_ms,
    backoff_ms: (Int) -> Long = { attempt -> 300L * (1L shl attempt) },
    now_ms: () -> Long = System::currentTimeMillis,
): Map<String, MailItem> {
    if (ids.isEmpty() || budget_ms <= 0L) return emptyMap()
    val pending = LinkedHashSet(ids)
    val found = LinkedHashMap<String, MailItem>()
    val deadline = now_ms() + budget_ms
    val gate = Semaphore(envelope_backfill_parallel_chunks)
    for (attempt in 0 until attempts) {
        if (pending.isEmpty()) break
        if (attempt > 0) {
            val wait = backoff_ms(attempt - 1)
            if (now_ms() + wait >= deadline) break
            delay(wait)
        }
        val remaining = deadline - now_ms()
        if (remaining <= 0) break
        val batches = withTimeoutOrNull(remaining) {
            coroutineScope {
                pending.toList().chunked(envelope_backfill_chunk_size).map { chunk ->
                    async {
                        gate.withPermit {
                            try {
                                fetch(chunk)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                emptyList()
                            }
                        }
                    }
                }.awaitAll()
            }
        } ?: break
        for (fetched in batches.flatten()) {
            if (fetched.id in pending && !fetched.encrypted_envelope.isNullOrBlank()) {
                found[fetched.id] = fetched
                pending.remove(fetched.id)
            }
        }
    }
    return found
}

suspend fun backfill_missing_envelopes(
    items: List<MailItem>,
    fetch: suspend (List<String>) -> List<MailItem>,
    attempts: Int = envelope_backfill_attempts,
    budget_ms: Long = envelope_backfill_budget_ms,
): List<MailItem> {
    if (budget_ms <= 0L) return items
    val ids = envelope_backfill_ids(items)
    if (ids.isEmpty()) return items
    val found = fetch_missing_envelopes(ids, fetch, attempts, budget_ms)
    if (found.isEmpty()) return items
    return items.map { item -> found[item.id]?.let { merge_backfilled_envelope(item, it) } ?: item }
}

fun envelope_missing_ids(items: List<InboxItem>): Set<String> =
    items.asSequence().filter { it.is_envelope_missing }.map { it.id }.toCollection(LinkedHashSet())

fun replace_refilled_items(items: List<InboxItem>, refilled: Map<String, InboxItem>): List<InboxItem> {
    if (refilled.isEmpty()) return items
    return items.map { item ->
        val replacement = refilled[item.id]
        if (replacement != null && item.is_envelope_missing && !replacement.is_envelope_missing) {
            replacement.copy(
                is_read = item.is_read,
                is_starred = item.is_starred,
                is_trashed = item.is_trashed,
                is_archived = item.is_archived,
                is_spam = item.is_spam,
                labels = item.labels,
                tag_tokens = item.tag_tokens,
                thread_token = item.thread_token,
                thread_message_count = item.thread_message_count,
            )
        } else {
            item
        }
    }
}
