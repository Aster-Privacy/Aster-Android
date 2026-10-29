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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.astermail.android.ui.mail.MessageAttachment

const val THREAD_PREFETCH_CONCURRENCY = 2
const val THREAD_PREFETCH_MAX_ATTACHMENT_BYTES = 16L * 1024 * 1024
const val THREAD_PREFETCH_MAX_MESSAGE_BYTES = 32L * 1024 * 1024

fun select_attachment_prefetch(
    metas: Map<String, List<MessageAttachment>>,
    max_attachment_bytes: Long = THREAD_PREFETCH_MAX_ATTACHMENT_BYTES,
    max_message_bytes: Long = THREAD_PREFETCH_MAX_MESSAGE_BYTES,
): List<String> = metas.filter { (_, attachments) ->
    attachments.isNotEmpty() &&
        attachments.all { it.size_bytes in 0..max_attachment_bytes } &&
        attachments.sumOf { it.size_bytes } <= max_message_bytes
}.keys.toList()

data class AttachmentPrefetchPlan(
    val full_message_ids: List<String>,
    val inline_attachment_ids: Map<String, List<String>>,
)

fun plan_attachment_prefetch(
    metas: Map<String, List<MessageAttachment>>,
    max_attachment_bytes: Long = THREAD_PREFETCH_MAX_ATTACHMENT_BYTES,
    max_message_bytes: Long = THREAD_PREFETCH_MAX_MESSAGE_BYTES,
): AttachmentPrefetchPlan {
    val full = select_attachment_prefetch(metas, max_attachment_bytes, max_message_bytes)
    val full_set = full.toSet()
    val inline = metas.filterKeys { it !in full_set }.mapValues { (_, attachments) ->
        attachments.filter { a ->
            !a.content_id.isNullOrBlank() && a.encrypted_data.isNullOrBlank() &&
                a.size_bytes in 0..max_attachment_bytes
        }.map { it.id }
    }.filterValues { it.isNotEmpty() }
    return AttachmentPrefetchPlan(full, inline)
}

suspend fun prefetch_thread_attachments(
    message_ids: List<String>,
    concurrency: Int = THREAD_PREFETCH_CONCURRENCY,
    fetch: suspend (String) -> Result<List<MessageAttachment>>,
): Map<String, List<MessageAttachment>> = prefetch_thread_attachments(
    plan = AttachmentPrefetchPlan(message_ids, emptyMap()),
    metas = emptyMap(),
    concurrency = concurrency,
    fetch_message = fetch,
    fetch_attachment = { Result.failure(IllegalStateException()) },
)

suspend fun prefetch_thread_attachments(
    plan: AttachmentPrefetchPlan,
    metas: Map<String, List<MessageAttachment>>,
    concurrency: Int = THREAD_PREFETCH_CONCURRENCY,
    fetch_message: suspend (String) -> Result<List<MessageAttachment>>,
    fetch_attachment: suspend (String) -> Result<MessageAttachment>,
): Map<String, List<MessageAttachment>> {
    if (plan.full_message_ids.isEmpty() && plan.inline_attachment_ids.isEmpty()) return emptyMap()
    val permits = Semaphore(concurrency.coerceAtLeast(1))
    return coroutineScope {
        val full_jobs = plan.full_message_ids.map { id ->
            async { permits.withPermit { id to fetch_message(id) } }
        }
        val inline_jobs = plan.inline_attachment_ids.flatMap { (message_id, attachment_ids) ->
            attachment_ids.map { attachment_id ->
                async { permits.withPermit { message_id to fetch_attachment(attachment_id) } }
            }
        }
        val full = full_jobs.awaitAll().mapNotNull { (id, outcome) ->
            outcome.getOrNull()?.takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()
        val inline = inline_jobs.awaitAll()
            .mapNotNull { (message_id, outcome) -> outcome.getOrNull()?.let { message_id to it } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (message_id, fetched) ->
                val base = metas[message_id] ?: return@mapNotNull null
                val by_id = fetched.associateBy { it.id }
                message_id to base.map { meta ->
                    val loaded = by_id[meta.id] ?: return@map meta
                    meta.copy(
                        encrypted_data = loaded.encrypted_data,
                        data_nonce = loaded.data_nonce,
                        session_key = loaded.session_key ?: meta.session_key,
                    )
                }
            }.toMap()
        full + inline
    }
}
