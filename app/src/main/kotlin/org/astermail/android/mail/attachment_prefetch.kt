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

suspend fun prefetch_thread_attachments(
    message_ids: List<String>,
    concurrency: Int = THREAD_PREFETCH_CONCURRENCY,
    fetch: suspend (String) -> Result<List<MessageAttachment>>,
): Map<String, List<MessageAttachment>> {
    if (message_ids.isEmpty()) return emptyMap()
    val permits = Semaphore(concurrency.coerceAtLeast(1))
    return coroutineScope {
        message_ids.map { id ->
            async { permits.withPermit { id to fetch(id) } }
        }.awaitAll()
    }.mapNotNull { (id, outcome) ->
        outcome.getOrNull()?.takeIf { it.isNotEmpty() }?.let { id to it }
    }.toMap()
}
